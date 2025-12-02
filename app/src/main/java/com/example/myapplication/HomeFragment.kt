package com.example.myapplication

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.databinding.FragmentHomeBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar as JavaCalendar
import java.util.Date
import java.util.Locale
import kotlin.collections.orEmpty

data class Schedule(
    val title: String,
    val time: String? = "시간 미지정"
)

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!   // ← 이건 onCreateView~onDestroyView 사이에서만 사용

    // 구글 로그인 / 캘린더
    private lateinit var googleSignInClient: GoogleSignInClient
    private var calendarService: Calendar? = null
    private var ddayDialog: DdayDialogFragment? = null

    // setting
    private val settingLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val account = GoogleSignIn.getLastSignedInAccount(requireContext())

                if (account != null) {
                    onAccountSignedIn(account)
                } else {
                    calendarService = null
                    showStatus("구글 계정을 연결해주세요.")
                    _binding?.calendarEventsContainer?.removeAllViews()
                }
                fetchEventsForSelectedDay()
                ddayDialog?.refreshFromParent()
            }
        }

    // 날짜 + 시간 포맷
    private var selectedDateMillis: Long = System.currentTimeMillis()
    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

    // 앱 내부에서 관리하는 로컬 일정
    private val schedulesByDate = mutableMapOf<Long, MutableList<Schedule>>()

    private val detailLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                fetchEventsForSelectedDay()      // 홈 화면 갱신 (네가 만든 함수)
                ddayDialog?.refreshFromParent()  // 팝업 안 리스트 갱신
            }
        }

    // Google 로그인 결과
    private val signInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data ?: return@registerForActivityResult
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
            }
        }

    // 캘린더 권한 재요청
    private val authRecoverLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                fetchEventsForSelectedDay()
            } else {
                showStatus("캘린더 권한이 허용되지 않았습니다.")
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupGoogleClient()
        initTopMenu()
        initCalendarUi()
        initFab()

        // 이미 로그인 되어 있으면 바로 연동
        GoogleSignIn.getLastSignedInAccount(requireContext())?.let {
            onAccountSignedIn(it)
        } ?: showStatus("구글 계정을 연결해주세요.")
    }

    // -------------------- 초기 설정 --------------------

    private fun setupGoogleClient() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestScopes(Scope(CalendarScopes.CALENDAR))
            .build()

        googleSignInClient = GoogleSignIn.getClient(requireContext(), gso)
    }
    //=====================bell 관련=================
    private fun openDdayPopup() {
        val dialog = DdayDialogFragment().apply {
            this.calendarService = this@HomeFragment.calendarService

            this.onEventClick = { item ->
                val intent = Intent(requireContext(), EventDetailActivity::class.java).apply {
                    putExtra("title", item.title)
                    putExtra("eventId", item.eventId)
                    putExtra("htmlLink", item.htmlLink)
                    putExtra("startMillis", item.startMillis)
                    putExtra("endMillis", item.endMillis)
                }
                detailLauncher.launch(intent)   // 이미 HomeFragment 에 있는 런처
            }
        }
        ddayDialog = dialog
        dialog.show(parentFragmentManager, "ddayDialog")
    }

    override fun onResume() {
        super.onResume()
        fetchEventsForSelectedDay()
        ddayDialog?.refreshFromParent()
    }
    //=============================================
    private fun initTopMenu() {
        binding.searchIcon.setOnClickListener {
            startActivity(Intent(requireContext(), SearchActivity::class.java))
        }

        binding.bellIcon.setOnClickListener {
            openDdayPopup()
        }

        binding.settingIcon.setOnClickListener {
            val intent = Intent(requireContext(), SettingActivity::class.java)
            settingLauncher.launch(intent)
        }
    }


    private fun initCalendarUi() {
        // 처음 선택 날짜
        selectedDateMillis = binding.calendarView.date
        renderSchedulesForDate(selectedDateMillis)

        // 날짜 바뀔 때마다 로컬 + 구글 일정 갱신
        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val cal = JavaCalendar.getInstance().apply {
                set(year, month, dayOfMonth, 0, 0, 0)
                set(JavaCalendar.MILLISECOND, 0)
            }
            selectedDateMillis = cal.timeInMillis

            renderSchedulesForDate(selectedDateMillis)
            fetchEventsForSelectedDay()
        }

//        // 구글 캘린더 연동 버튼
//        binding.syncCalendarButton.setOnClickListener {
//            googleSignInClient.signOut().addOnCompleteListener {
//                signInLauncher.launch(googleSignInClient.signInIntent)
//                showStatus("로그인 화면을 표시합니다.")
//            }
//        }
    }

    private fun initFab() {
        binding.fabAdd.setOnClickListener {
            showScheduleBottomSheet()
        }
    }

    // -------------------- 구글 계정 / 캘린더 --------------------

    private fun onAccountSignedIn(account: GoogleSignInAccount) {
        val credential = GoogleAccountCredential.usingOAuth2(
            requireContext(),
            listOf(CalendarScopes.CALENDAR)
        )
        credential.selectedAccount = account.account

        calendarService = Calendar.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName(getString(R.string.app_name))
            .build()

        // 🔐 View가 이미 파괴된 상태일 수 있으니 가드
        val b = _binding ?: return
//        b.syncCalendarButton.text = "일정 새로고침"
        showStatus(account.email ?: "Google 계정 연결됨")
        fetchEventsForSelectedDay()
    }

    private fun fetchEventsForSelectedDay() {
        val service = calendarService
        if (service == null) {
            showStatus("구글 계정을 먼저 연결해주세요.")
            return
        }

        showStatus("일정을 불러오는 중입니다...")

        // ✅ viewLifecycleOwner 기반 → View가 사라지면 코루틴도 자동 cancel
        viewLifecycleOwner.lifecycleScope.launch {
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

                // 🔐 View 사라졌으면 더 이상 UI 건들지 말고 종료
                if (_binding == null) return@launch

                renderGoogleEvents(items)
                showStatus("총 ${items.size}개의 일정이 있습니다.")
            } catch (ex: Exception) {
                when (ex) {
                    is UserRecoverableAuthIOException -> {
                        authRecoverLauncher.launch(ex.intent)
                    }
                    else -> {
                        showStatus("캘린더를 불러오지 못했습니다.")
                    }
                }
            }
        }
    }

    // 선택한 날짜의 0시 ~ 24시 범위
    private fun selectedDayBounds(dayMillis: Long): Pair<DateTime, DateTime> {
        val cal = JavaCalendar.getInstance().apply {
            timeInMillis = dayMillis
            set(JavaCalendar.HOUR_OF_DAY, 0)
            set(JavaCalendar.MINUTE, 0)
            set(JavaCalendar.SECOND, 0)
            set(JavaCalendar.MILLISECOND, 0)
        }
        val startMillis = cal.timeInMillis
        cal.add(JavaCalendar.DAY_OF_MONTH, 1)
        return DateTime(startMillis) to DateTime(cal.timeInMillis)
    }

    // -------------------- 구글 일정 UI --------------------

    private fun renderGoogleEvents(events: List<Event>) {
        val b = _binding ?: return   // 🔐 가드
        b.calendarEventsContainer.removeAllViews()

        if (events.isEmpty()) {
            b.calendarEventsContainer.addView(
                TextView(requireContext()).apply {
                    text = "선택한 날짜에 일정이 없습니다."
                    setTextColor(Color.DKGRAY)
                }
            )
            return
        }

        events.forEach { event ->
            b.calendarEventsContainer.addView(createEventRow(event))
        }

        // 구글 일정 아래에 로컬 일정도 이어서 붙이고 싶으면 여기에 호출
        renderSchedulesForDate(selectedDateMillis)
    }

    private fun createEventRow(event: Event): LinearLayout {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 8)
        }

        val timeView = TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                0.3f
            )
            text = formatEventTime(event)
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
        }

        val titleView = TextView(requireContext()).apply {
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

        container.setOnClickListener {
            val start = (event.start?.dateTime ?: event.start?.date)?.value ?: -1L
            val end = (event.end?.dateTime ?: event.end?.date)?.value ?: -1L

            val intent = Intent(requireContext(), EventDetailActivity::class.java).apply {
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
            timeFormatter.format(Date(dateTime.value))
        } else {
            "종일"
        }
    }

    // -------------------- 로컬 일정 + BottomSheet --------------------

    private fun showScheduleBottomSheet() {
        val bottomSheet = ScheduleBottomSheetFragment()
        bottomSheet.listener = object : ScheduleBottomSheetFragment.OnScheduleAddedListener {
            override fun onScheduleAdded(
                title: String,
                year: Int,
                month: Int,
                day: Int,
                time: String?,
                detail: String?,
                isAlarmOn: Boolean,
                alarmTime: String?

            ) {
                val cal = JavaCalendar.getInstance().apply {
                    set(year, month, day, 0, 0, 0)
                    set(JavaCalendar.MILLISECOND, 0)
                }
                val dateMillis = cal.timeInMillis

                val list = schedulesByDate.getOrPut(dateMillis) { mutableListOf() }
                list.add(Schedule(title, time ?: "시간 미지정"))

                if (selectedDateMillis == dateMillis) {
                    renderSchedulesForDate(selectedDateMillis)
                }

                Toast.makeText(requireContext(), "앱에 일정이 추가되었습니다.", Toast.LENGTH_SHORT).show()

                addEventToGoogleCalendar(title, year, month, day, time, detail)
            }
        }
        bottomSheet.show(parentFragmentManager, "ScheduleBottomSheet")
    }

    // 로컬 일정 리스트 표시 (구글 일정 아래에 append 방식)
    private fun renderSchedulesForDate(dateMillis: Long) {
        val b = _binding ?: return   // 🔐 가드
        val list = schedulesByDate[dateMillis] ?: return

        list.forEach { schedule ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
            }

            row.addView(TextView(requireContext()).apply {
                text = schedule.time
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.BLACK)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    0.3f
                )
            })

            row.addView(TextView(requireContext()).apply {
                text = schedule.title
                setTextColor(Color.BLACK)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    0.7f
                )
            })

            b.calendarEventsContainer.addView(row)
        }
    }

    private fun addEventToGoogleCalendar(
        title: String,
        year: Int,
        month: Int,
        day: Int,
        time: String?,
        detail: String?
    ) {
        val service = calendarService
        if (service == null) {
            Toast.makeText(requireContext(), "구글 계정을 먼저 연결해주세요.", Toast.LENGTH_LONG).show()
            return
        }

        val calStart = JavaCalendar.getInstance().apply {
            set(year, month, day)

            if (!time.isNullOrBlank() && time.contains(":")) {
                val parts = time.split(":")
                val h = parts.getOrNull(0)?.toIntOrNull() ?: 9
                val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                set(JavaCalendar.HOUR_OF_DAY, h)
                set(JavaCalendar.MINUTE, m)
            } else {
                set(JavaCalendar.HOUR_OF_DAY, 9)
                set(JavaCalendar.MINUTE, 0)
            }
            set(JavaCalendar.SECOND, 0)
            set(JavaCalendar.MILLISECOND, 0)
        }

        val calEnd = calStart.clone() as JavaCalendar
        calEnd.add(JavaCalendar.HOUR_OF_DAY, 1)

        val startDateTime = DateTime(calStart.timeInMillis)
        val endDateTime = DateTime(calEnd.timeInMillis)

        val event = Event().apply {
            summary = title
            description = detail
            start = EventDateTime().setDateTime(startDateTime)
            end = EventDateTime().setDateTime(endDateTime)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    service.events()
                        .insert("primary", event)
                        .execute()
                }

                if (_binding == null) return@launch   // 🔐 View 사라졌으면 토스트만 스킵

                Toast.makeText(
                    requireContext(),
                    "구글 캘린더에 일정이 추가되었습니다.",
                    Toast.LENGTH_SHORT
                ).show()

                fetchEventsForSelectedDay()
            } catch (e: UserRecoverableAuthIOException) {
                authRecoverLauncher.launch(e.intent)
            } catch (e: Exception) {
                e.printStackTrace()
                if (_binding != null) {
                    Toast.makeText(
                        requireContext(),
                        "구글 캘린더 추가 중 오류가 발생했습니다.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // -------------------- 공통 --------------------

    private fun showStatus(message: String) {
        // 🔐 여기서 binding!! 쓰면 바로 NPE라 _binding 체크 후 사용
        _binding?.let { b ->
            b.statusTextView.text = message
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
