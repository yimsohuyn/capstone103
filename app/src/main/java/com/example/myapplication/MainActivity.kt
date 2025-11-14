package com.example.myapplication

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.CalendarView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
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

class MainActivity : ComponentActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var syncCalendarButton: MaterialButton
    private lateinit var calendarStatusText: TextView
    private lateinit var calendarEventsContainer: LinearLayout
    private lateinit var calendarView: CalendarView

    private var calendarService: Calendar? = null
    private var selectedDateMillis: Long = System.currentTimeMillis()

    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

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
                    onAccountSignedIn(account)
                } else {
                    showStatus("로그인한 계정을 찾지 못했습니다.")
                }
            } catch (ex: ApiException) {
                showStatus("로그인 오류: ${ex.statusCode}")
                Toast.makeText(this, "Google 로그인 실패: ${ex.localizedMessage}", Toast.LENGTH_LONG)
                    .show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupGoogleClient()
        initTopMenuActions()
        initCalendarUi()

        GoogleSignIn.getLastSignedInAccount(this)?.let {
            onAccountSignedIn(it)
        } ?: showStatus("구글 계정을 연결해주세요.")
    }

    private fun setupGoogleClient() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(CalendarScopes.CALENDAR_READONLY))
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun initTopMenuActions() {
        findViewById<ImageView>(R.id.searchIcon).setOnClickListener { openSearch() }
        findViewById<ImageView>(R.id.bellIcon).setOnClickListener { openAlert() }
        findViewById<ImageView>(R.id.settingIcon).setOnClickListener { openSettings() }
    }

    private fun initCalendarUi() {
        calendarView = findViewById(R.id.calendarView)
        syncCalendarButton = findViewById(R.id.syncCalendarButton)
        calendarStatusText = findViewById(R.id.calendarStatusText)
        calendarEventsContainer = findViewById(R.id.calendarEventsContainer)

        selectedDateMillis = calendarView.date
        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val calendar = JavaCalendar.getInstance().apply {
                set(JavaCalendar.YEAR, year)
                set(JavaCalendar.MONTH, month)
                set(JavaCalendar.DAY_OF_MONTH, dayOfMonth)
                set(JavaCalendar.HOUR_OF_DAY, 0)
                set(JavaCalendar.MINUTE, 0)
                set(JavaCalendar.SECOND, 0)
                set(JavaCalendar.MILLISECOND, 0)
            }
            selectedDateMillis = calendar.timeInMillis
            fetchEventsForSelectedDay()
        }

        syncCalendarButton.setOnClickListener {
            val account = GoogleSignIn.getLastSignedInAccount(this)
            if (account == null) {
                signInLauncher.launch(googleSignInClient.signInIntent)
            } else {
                onAccountSignedIn(account)
            }
        }
    }

    private fun onAccountSignedIn(account: GoogleSignInAccount) {
        calendarService = buildCalendarService(account)
        syncCalendarButton.text = "일정 새로고침"
        showStatus("연결됨: ${account.email ?: account.displayName ?: "Google 계정"}")
        fetchEventsForSelectedDay()
    }

    private fun buildCalendarService(account: GoogleSignInAccount): Calendar {
        val credential = GoogleAccountCredential.usingOAuth2(
            this,
            listOf(CalendarScopes.CALENDAR_READONLY)
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
                renderEvents(items)
                showStatus("총 ${items.size}개의 일정이 있습니다.")
            } catch (ex: Exception) {
                showStatus("캘린더를 불러오지 못했습니다.")
                Toast.makeText(
                    this@MainActivity,
                    "캘린더 호출 실패: ${ex.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun renderEvents(events: List<Event>) {
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

        events.forEach { event ->
            calendarEventsContainer.addView(createEventRow(event))
        }
    }

    private fun createEventRow(event: Event): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 8)
        }

        val timeLayoutParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            0.3f
        )
        val titleLayoutParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            0.7f
        )

        val timeView = TextView(this).apply {
            layoutParams = timeLayoutParams
            text = formatEventTime(event)
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
        }
        val titleView = TextView(this).apply {
            layoutParams = titleLayoutParams
            text = event.summary ?: "제목 없음"
            setTextColor(Color.BLACK)
        }

        container.addView(timeView)
        container.addView(titleView)
        return container
    }

    private fun formatEventTime(event: Event): String {
        val dateTime = event.start?.dateTime ?: event.start?.date
        return if (dateTime != null) {
            val date = java.util.Date(dateTime.value)
            timeFormatter.format(date)
        } else {
            "종일"
        }
    }

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
        val endMillis = calendar.timeInMillis
        return DateTime(startMillis) to DateTime(endMillis)
    }

    private fun showStatus(message: String) {
        calendarStatusText.text = message
    }

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
        startActivity(Intent(this, SettingActivity::class.java))
    }
}
