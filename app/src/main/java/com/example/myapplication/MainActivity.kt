package com.example.myapplication

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar as JavaCalendar
import java.util.Locale
import com.google.api.services.calendar.model.EventDateTime
import kotlin.collections.orEmpty

data class Schedule(val title: String, val time: String? = "시간 미지정")

class MainActivity : AppCompatActivity() {
    private lateinit var googleSignInClient: GoogleSignInClient
    //private lateinit var syncCalendarButton: MaterialButton
    private lateinit var calendarStatusText: TextView
    private lateinit var calendarEventsContainer: LinearLayout
    private lateinit var calendarView: CalendarView

    private var calendarService: Calendar? = null
    private var selectedDateMillis: Long = System.currentTimeMillis()
    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

    // 앱 내부에서 추가한 일정들(구글과 상관없는 로컬 일정)
    private val schedulesByDate = mutableMapOf<Long, MutableList<Schedule>>()

    private val detailLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                fetchEventsForSelectedDay()
            }
        }

    // ✅ Google 로그인 결과 처리
    private val signInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (data == null) {
                showStatus("로그인 응답을 받을 수 없습니다.")
                return@registerForActivityResult
            }

            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                if (account != null) {
                    navigateToMainPage()
                } else {
                    showStatus("로그인한 계정을 찾지 못했습니다.")
                }
            } catch (ex: ApiException) {
                showStatus("로그인 오류: ${ex.statusCode}")
                Toast.makeText(
                    this,
                    "Google 로그인 실패: ${ex.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }



    private fun navigateToMainPage() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    // ✅ 캘린더 권한 추가 동의용 런처
    private val authRecoverLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // 권한 허용 후 다시 일정 불러오기
                fetchEventsForSelectedDay()
            } else {
                showStatus("캘린더 권한이 허용되지 않았습니다.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 뷰 초기화
        calendarView = findViewById(R.id.calendarView)
        //syncCalendarButton = findViewById(R.id.syncCalendarButton)
        calendarStatusText = findViewById(R.id.calendarStatusText)
        calendarEventsContainer = findViewById(R.id.calendarEventsContainer)

        initTopMenuActions()
        initCalendarUi()
        setupGoogleClient()

        googleSignInClient = GoogleSignIn.getClient(
            this,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build()
        )
        // 로그인 상태 체크
        GoogleSignIn.getLastSignedInAccount(this)?.let {
            onAccountSignedIn(it)
        } ?: showStatus("구글 계정을 연결해주세요.")

        // FAB – 로컬 일정 추가 bottom sheet
        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            showScheduleBottomSheet()
        }
    }

    // ✅ Google 로그인 옵션 설정 (Calendar 전체 권한)
    private fun setupGoogleClient() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestScopes(Scope(CalendarScopes.CALENDAR))
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun initTopMenuActions() {
        findViewById<ImageView>(R.id.searchIcon).setOnClickListener { openSearch() }
        findViewById<ImageView>(R.id.bellIcon).setOnClickListener { openAlert() }
        findViewById<ImageView>(R.id.settingIcon).setOnClickListener { openSettings() }
    }

    private fun initCalendarUi() {
        selectedDateMillis = calendarView.date
        renderSchedulesForDate(selectedDateMillis)

        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val calendar = JavaCalendar.getInstance().apply {
                set(year, month, dayOfMonth, 0, 0, 0)
                set(JavaCalendar.MILLISECOND, 0)
            }
            selectedDateMillis = calendar.timeInMillis
            renderSchedulesForDate(selectedDateMillis)  // 로컬 일정
            fetchEventsForSelectedDay()                 // 구글 일정
        }

//        syncCalendarButton.setOnClickListener {
//            googleSignInClient.signOut().addOnCompleteListener {
//                signInLauncher.launch(googleSignInClient.signInIntent)
//                showStatus("로그인 화면을 표시합니다.")
//            }
//        }
    }

    private fun onAccountSignedIn(account: GoogleSignInAccount) {
        calendarService = buildCalendarService(account)
    //    syncCalendarButton.text = "일정 새로고침"
        showStatus("연결됨: ${account.email ?: account.displayName ?: "Google 계정"}")
        fetchEventsForSelectedDay()
    }

    // ✅ Google Calendar 서비스 객체 생성
    private fun buildCalendarService(account: GoogleSignInAccount): Calendar {
        val credential = GoogleAccountCredential.usingOAuth2(
            this,
            listOf(CalendarScopes.CALENDAR)
        )
        credential.selectedAccount = account.account

        return Calendar.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName(getString(R.string.app_name))
            .build()
    }

    // ✅ 선택한 날짜의 구글 캘린더 일정 가져오기
    private fun fetchEventsForSelectedDay() {
        val service = calendarService
        if (service == null) {
            showStatus("구글 계정을 먼저 연결해주세요.")
            return
        }

        showStatus("일정을 불러오는 중입니다...")
        lifecycleScope.launch {
            try {
                val (timeMin, timeMax) = withContext(Dispatchers.Default) {
                    selectedDayBounds(selectedDateMillis)
                }

                val items = withContext(Dispatchers.IO) {
                    service.events().list("primary")
                        .setSingleEvents(true)
                        .setOrderBy("startTime")
                        .setTimeMin(timeMin)
                        .setTimeMax(timeMax)
                        .execute()
                        .items.orEmpty()
                }

                Log.d("CalendarAPI", "가져온 일정 개수 = ${items.size}")
                renderEvents(items)
                showStatus("총 ${items.size}개의 일정이 있습니다.")

            } catch (ex: Exception) {
                when (ex) {
                    // 🔥 추가 권한 동의가 필요할 때
                    is UserRecoverableAuthIOException -> {
                        Log.e("CalendarAPI", "권한 동의 필요: ${ex.message}", ex)
                        withContext(Dispatchers.Main) {
                            authRecoverLauncher.launch(ex.intent)
                        }
                    }
                    else -> {
                        Log.e("CalendarAPI", "캘린더 호출 실패", ex)
                        showStatus("캘린더를 불러오지 못했습니다.")
                        Toast.makeText(
                            this@MainActivity,
                            "캘린더 호출 실패: ${ex.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    // ✅ 구글 캘린더 일정 리스트를 화면에 표시
    private fun renderEvents(events: List<Event>) {
        // 구글 일정은 로컬 일정 리스트와 섞어서 보여주고 싶다면
        // 필요에 따라 합치는 로직을 추가할 수도 있음.
        calendarEventsContainer.removeAllViews()
        if (events.isEmpty()) {
            calendarEventsContainer.addView(
                TextView(this).apply {
                    text = "선택한 날짜에 일정이 없습니다."
                    setTextColor(Color.DKGRAY)
                }
            )
            return
        }
        events.forEach { calendarEventsContainer.addView(createEventRow(it)) }
    }

    private fun createEventRow(event: Event): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 8)
        }

        val timeView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                0.3f
            )
            text = formatEventTime(event)
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
        }

        val titleView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,  
                LinearLayout.LayoutParams.WRAP_CONTENT,
                0.7f
            )
            text = event.summary ?: "제목 없음"
            setTextColor(Color.BLACK)
        }

        container.addView(timeView)
        container.addView(titleView)

        // ✅ 한 줄 전체를 눌렀을 때 옵션 다이얼로그 띄우기
        container.setOnClickListener {
            val start = (event.start?.dateTime ?: event.start?.date)?.value ?: -1L
            val end = (event.end?.dateTime ?: event.end?.date)?.value ?: -1L

            val intent = Intent(this, EventDetailActivity::class.java).apply {
                putExtra("title", event.summary ?: "제목 없음")
                putExtra("eventId", event.id)
                putExtra("htmlLink", event.htmlLink)
                putExtra("startMillis", start)
                putExtra("endMillis", end)
            }
            detailLauncher.launch(intent)
        }

        return container
    }

    private fun formatEventTime(event: Event): String {
        val dateTime = event.start?.dateTime ?: event.start?.date
        return if (dateTime != null) {
            timeFormatter.format(java.util.Date(dateTime.value))
        } else {
            "종일"
        }
    }

    // ✅ 선택한 날짜의 0시 ~ 24시 범위
    private fun selectedDayBounds(dayMillis: Long): Pair<DateTime, DateTime> {
        val calendar = JavaCalendar.getInstance().apply {
            timeInMillis = dayMillis
            set(JavaCalendar.HOUR_OF_DAY, 0)
            set(JavaCalendar.MINUTE, 0)
            set(JavaCalendar.SECOND, 0)
            set(JavaCalendar.MILLISECOND, 0)
        }
        val startMillis = calendar.timeInMillis
        calendar.add(JavaCalendar.DAY_OF_MONTH, 1)
        return DateTime(startMillis) to DateTime(calendar.timeInMillis)
    }

    private fun showStatus(message: String) {
        calendarStatusText.text = message
    }

    // ===================== 상단/설정 화면 =====================

    private fun openSearch() {
        startActivity(Intent(this, SearchActivity::class.java))
    }

    private fun openAlert() {
        AlertDialog.Builder(this)
            .setTitle("알림")
            .setMessage("알림 화면 준비중입니다.")
            .setPositiveButton("확인", null)
            .show()
    }

    private fun openSettings() {
        val intent = Intent(this, SettingActivity::class.java)
        startActivity(intent)
    }

    // ===================== 로컬 일정 추가 BottomSheet =====================

    private fun showScheduleBottomSheet() {
        val bottomSheet = ScheduleBottomSheetFragment()
        bottomSheet.listener = object : ScheduleBottomSheetFragment.OnScheduleAddedListener {
            override fun onScheduleAdded(
                title: String,
                year: Int,
                month: Int,
                day: Int,
                time: String?,
                detail: String?
            ) {
                // 1) 앱 로컬 일정에 추가
                val calendar = JavaCalendar.getInstance().apply {
                    set(year, month, day, 0, 0, 0)
                    set(JavaCalendar.MILLISECOND, 0)
                }
                val dateMillis = calendar.timeInMillis

                val list = schedulesByDate.getOrPut(dateMillis) { mutableListOf() }
                list.add(Schedule(title, time ?: "시간 미지정"))

                if (selectedDateMillis == dateMillis) {
                    renderSchedulesForDate(selectedDateMillis)
                }

                Toast.makeText(this@MainActivity, "앱에 일정이 추가되었습니다.", Toast.LENGTH_SHORT).show()

                // 2) 구글 캘린더에도 추가
                addEventToGoogleCalendar(title, year, month, day, time, detail)
            }
        }
        bottomSheet.show(supportFragmentManager, "ScheduleBottomSheet")
    }

    // ✅ 로컬(앱에서 추가한) 일정 리스트 표시
    private fun renderSchedulesForDate(dateMillis: Long) {
        val list = schedulesByDate[dateMillis]

        if (list.isNullOrEmpty()) {
            return
        }

        list.forEach { schedule ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
            }
            row.addView(TextView(this).apply {
                text = schedule.time
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.BLACK)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    0.3f
                )
            })
            row.addView(TextView(this).apply {
                text = schedule.title
                setTextColor(Color.BLACK)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    0.7f
                )
            })
            calendarEventsContainer.addView(row)
        }
    }

    private fun addEventToGoogleCalendar(
        title: String,
        year: Int,
        month: Int,      // DatePicker에서 넘어온 0-based month
        day: Int,
        time: String?,
        detail: String?
    ) {
        val service = calendarService
        if (service == null) {
            Toast.makeText(this, "구글 계정을 먼저 연결해주세요.", Toast.LENGTH_LONG).show()
            return
        }

        // 시작/끝 시간 계산
        val calStart = JavaCalendar.getInstance().apply {
            set(year, month, day)

            if (!time.isNullOrBlank() && time.contains(":")) {
                // "HH:mm" 형식인 경우
                val parts = time.split(":")
                val h = parts.getOrNull(0)?.toIntOrNull() ?: 9
                val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                set(JavaCalendar.HOUR_OF_DAY, h)
                set(JavaCalendar.MINUTE, m)
            } else {
                // 시간 안 적으면 9:00 로
                set(JavaCalendar.HOUR_OF_DAY, 9)
                set(JavaCalendar.MINUTE, 0)
            }
            set(JavaCalendar.SECOND, 0)
            set(JavaCalendar.MILLISECOND, 0)
        }

        val calEnd = calStart.clone() as JavaCalendar
        calEnd.add(JavaCalendar.HOUR_OF_DAY, 1)   // 기본 1시간짜리 이벤트

        val startDateTime = DateTime(calStart.timeInMillis)
        val endDateTime = DateTime(calEnd.timeInMillis)

        val event = Event().apply {
            summary = title
            description = detail
            start = EventDateTime().setDateTime(startDateTime)
            end = EventDateTime().setDateTime(endDateTime)
        }

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    service.events()
                        .insert("primary", event)
                        .execute()
                }
                Toast.makeText(
                    this@MainActivity,
                    "구글 캘린더에 일정이 추가되었습니다.",
                    Toast.LENGTH_SHORT
                ).show()

                // 오늘 날짜라면 리스트도 새로고침
                fetchEventsForSelectedDay()
            } catch (e: UserRecoverableAuthIOException) {
                // 권한 추가 동의 필요하면 동의 화면 표시
                authRecoverLauncher.launch(e.intent)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@MainActivity,
                    "구글 캘린더 추가 중 오류가 발생했습니다.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}