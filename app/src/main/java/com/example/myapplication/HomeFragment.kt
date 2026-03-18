package com.example.myapplication

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.AssignmentEntity
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

// 앱 내부 로컬 일정 데이터 (time = 시작 시간)
data class Schedule(
    val title: String,
    val time: String? = "시간 미지정"
)

class HomeFragment : Fragment() {

    private val koreaTimeZone = java.util.TimeZone.getTimeZone("Asia/Seoul")

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // 구글 로그인 / 캘린더
    private lateinit var googleSignInClient: GoogleSignInClient
    private var calendarService: Calendar? = null
    private var ddayDialog: DdayDialogFragment? = null

    // 날짜 + 시간 포맷
    private var selectedDateMillis: Long = System.currentTimeMillis()
    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
        timeZone = java.util.TimeZone.getTimeZone("Asia/Seoul")
    }

    // 앱 내부에서 관리하는 로컬 일정 (구글 계정 X 일 때만 사용)
    private val schedulesByDate = mutableMapOf<Long, MutableList<Schedule>>()

    // 설정 화면 → 돌아왔을 때
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

                // 다시 그리기
                if (calendarService != null) {
                    fetchEventsForSelectedDay()
                } else {
                    renderSchedulesForDate(selectedDateMillis)
                }

                ddayDialog?.refreshFromParent()
            }
        }

    // 상세 화면에서 돌아왔을 때
    private val detailLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                if (calendarService != null) {
                    fetchEventsForSelectedDay()
                } else {
                    renderSchedulesForDate(selectedDateMillis)
                }
                ddayDialog?.refreshFromParent()
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
        } ?: run {
            calendarService = null
            showStatus("구글 계정을 연결해주세요.")
            renderSchedulesForDate(selectedDateMillis)
        }
    }

    override fun onResume() {
        super.onResume()
        if (calendarService != null) {
            fetchEventsForSelectedDay()
            ddayDialog?.refreshFromParent()
        } else {
            renderSchedulesForDate(selectedDateMillis)
        }
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
        // CalendarView 색상 설정 (프로그래밍 방식)
        binding.calendarView.apply {
            // 선택된 날짜 배경색
            selectedWeekBackgroundColor =
                ContextCompat.getColor(requireContext(), R.color.calendar_selected_date)

            // 포커스된 월의 날짜 색상
            focusedMonthDateColor =
                ContextCompat.getColor(requireContext(), R.color.calendar_text)

            // 비포커스 월의 날짜 색상 (주말/다른 달)
            unfocusedMonthDateColor =
                ContextCompat.getColor(requireContext(), R.color.calendar_weekend)

            // 주 구분선 색상
            weekSeparatorLineColor =
                ContextCompat.getColor(requireContext(), R.color.divider)
        }

        // 처음 선택 날짜
        selectedDateMillis = binding.calendarView.date

        if (calendarService != null) {
            fetchEventsForSelectedDay()
        } else {
            renderSchedulesForDate(selectedDateMillis)
        }

        // 날짜 바뀔 때마다 로컬 또는 구글 일정 갱신
        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val cal = JavaCalendar.getInstance().apply {
                set(year, month, dayOfMonth, 0, 0, 0)
                set(JavaCalendar.MILLISECOND, 0)
            }
            selectedDateMillis = cal.timeInMillis

            if (calendarService != null) {
                fetchEventsForSelectedDay()
            } else {
                renderSchedulesForDate(selectedDateMillis)
            }
        }
    }

    private fun initFab() {
        binding.fabAdd.setOnClickListener {
            showScheduleBottomSheet()
        }
    }

    // -------------------- D-day 팝업 --------------------

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
                detailLauncher.launch(intent)
            }
        }
        ddayDialog = dialog
        dialog.show(parentFragmentManager, "ddayDialog")
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

        showStatus(account.email ?: "Google 계정 연결됨")
        fetchEventsForSelectedDay()
    }

    /**
     * ✅ 수정됨:
     * - 선택한 날짜의 "과제(Assignment)"를 Room DB에서 읽어옴
     * - 구글 이벤트 + 과제를 같이 렌더링
     * - 구글 계정이 없어도 과제는 표시됨
     */
    private fun fetchEventsForSelectedDay() {
        val service = calendarService
        showStatus("일정을 불러오는 중입니다...")

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // 1) 선택 날짜의 과제 먼저 로드
                val assignments = withContext(Dispatchers.IO) {
                    loadAssignmentsForSelectedDay()
                }

                // 2) 구글 계정이 없으면: 과제만 표시하고 종료
                if (service == null) {
                    renderGoogleEvents(emptyList(), assignments)
                    showStatus("총 ${assignments.size}개의 일정이 있습니다.")
                    return@launch
                }

                val (timeMin, timeMax) = withContext(Dispatchers.Default) {
                    selectedDayBounds(selectedDateMillis)
                }

                // 3) 선택 날짜의 구글 이벤트 로드
                val items = withContext(Dispatchers.IO) {
                    service.events().list("primary")
                        .setSingleEvents(true)
                        .setOrderBy("startTime")
                        .setTimeMin(timeMin)
                        .setTimeMax(timeMax)
                        .execute()
                        .items.orEmpty()
                }

                if (_binding == null) return@launch

                val filteredEvents = items.filterNot { event ->
                    val title = event.summary ?: ""
                    title.startsWith("[팀 과제]") || title.startsWith("[개인 과제]")
                }
                // 4) 같이 렌더링
                renderGoogleEvents(items, assignments)
                showStatus("총 ${filteredEvents.size + assignments.size}개의 일정이 있습니다.")
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

    /**
     * ✅ 수정됨:
     * - events(구글) + assignments(과제) 둘 다 받음
     * - 과제를 먼저 띄우고, 그 다음 구글 이벤트 띄움
     */
    private fun renderGoogleEvents(events: List<Event>, assignments: List<AssignmentEntity>) {
        val b = _binding ?: return
        b.calendarEventsContainer.removeAllViews()

        // 과제로 동기화된 구글 이벤트는 숨김
        val filteredEvents = events.filterNot { event ->
            val title = event.summary ?: ""
            title.startsWith("[팀 과제]") || title.startsWith("[개인 과제]")
        }

        if (filteredEvents.isEmpty() && assignments.isEmpty()) {
            b.calendarEventsContainer.addView(
                TextView(requireContext()).apply {
                    text = "선택한 날짜에 일정이 없습니다."
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                }
            )
            return
        }

        // ✅ 과제 + 일반 일정을 한 리스트로 합쳐서 시간순 정렬
        val mergedItems = mutableListOf<Pair<Long, View>>()

        assignments.forEach { assignment ->
            val startMillis = assignmentDateTimeToMillis(
                assignment.dueDate,
                assignment.startTime ?: "09:00"
            )
            mergedItems.add(startMillis to createAssignmentRow(assignment))
        }

        filteredEvents.forEach { event ->
            val startMillis = (event.start?.dateTime ?: event.start?.date)?.value ?: Long.MAX_VALUE
            mergedItems.add(startMillis to createEventRow(event))
        }

        mergedItems
            .sortedBy { it.first }
            .forEach { (_, rowView) ->
                b.calendarEventsContainer.addView(rowView)
            }
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
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
        }

        val titleView = TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                0.7f
            )
            text = event.summary ?: "제목 없음"
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
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
                startYear: Int,
                startMonth: Int,
                startDay: Int,
                endYear: Int,
                endMonth: Int,
                endDay: Int,
                startTime: String?,
                endTime: String?,
                detail: String?,
                isAlarmOn: Boolean,
                alarmTime: String?
            ) {
                // 로컬 일정은 "시작 날짜 기준"으로만 저장
                val calStart = JavaCalendar.getInstance().apply {
                    set(startYear, startMonth, startDay, 0, 0, 0)
                    set(JavaCalendar.MILLISECOND, 0)
                }
                val dateMillis = calStart.timeInMillis

                // ✅ 구글 계정이 연결되어 있으면 → 구글 캘린더에만 추가
                if (calendarService != null) {
                    addEventToGoogleCalendar(
                        title = title,
                        startYear = startYear,
                        startMonth = startMonth,
                        startDay = startDay,
                        endYear = endYear,
                        endMonth = endMonth,
                        endDay = endDay,
                        startTime = startTime,
                        endTime = endTime,
                        detail = detail
                    )
                    return
                }

                // ✅ 구글 계정이 없으면 → 로컬 일정으로만 관리
                val list = schedulesByDate.getOrPut(dateMillis) { mutableListOf() }
                list.add(Schedule(title, startTime ?: "시간 미지정"))

                if (selectedDateMillis == dateMillis) {
                    renderSchedulesForDate(selectedDateMillis)
                }

                Toast.makeText(requireContext(), "앱에 일정이 추가되었습니다.", Toast.LENGTH_SHORT)
                    .show()
            }
        }
        bottomSheet.show(parentFragmentManager, "ScheduleBottomSheet")
    }

    // 로컬 일정 리스트 표시 (구글 계정이 없을 때만 사용)
    private fun renderSchedulesForDate(dateMillis: Long) {
        val b = _binding ?: return
        val list = schedulesByDate[dateMillis]

        b.calendarEventsContainer.removeAllViews()

        if (list.isNullOrEmpty()) {
            b.calendarEventsContainer.addView(
                TextView(requireContext()).apply {
                    text = "선택한 날짜에 일정이 없습니다."
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                }
            )
            return
        }

        list.forEach { schedule ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
            }

            row.addView(TextView(requireContext()).apply {
                text = schedule.time
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    0.3f
                )
            })

            row.addView(TextView(requireContext()).apply {
                text = schedule.title
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    0.7f
                )
            })

            b.calendarEventsContainer.addView(row)
        }
    }

    // 시간 문자열 "HH:mm" → Pair(h, m) 로 변환
    private fun parseTime(time: String?): Pair<Int, Int>? {
        if (time.isNullOrBlank()) return null
        val parts = time.split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return h to m
    }

    private fun addEventToGoogleCalendar(
        title: String,
        startYear: Int,
        startMonth: Int,
        startDay: Int,
        endYear: Int,
        endMonth: Int,
        endDay: Int,
        startTime: String?,
        endTime: String?,
        detail: String?
    ) {
        val service = calendarService
        if (service == null) {
            Toast.makeText(requireContext(), "구글 계정을 먼저 연결해주세요.", Toast.LENGTH_LONG).show()
            return
        }

        val koreaTimeZone = java.util.TimeZone.getTimeZone("Asia/Seoul")

        // 시작 시간
        val (sh, sm) = parseTime(startTime) ?: (9 to 0)
        val calStart = JavaCalendar.getInstance(koreaTimeZone).apply {
            set(startYear, startMonth, startDay)
            set(JavaCalendar.HOUR_OF_DAY, sh)
            set(JavaCalendar.MINUTE, sm)
            set(JavaCalendar.SECOND, 0)
            set(JavaCalendar.MILLISECOND, 0)
        }

        // 종료 시간
        val calEnd = JavaCalendar.getInstance(koreaTimeZone).apply {
            set(endYear, endMonth, endDay)

            val parsedEnd = parseTime(endTime)
            if (parsedEnd != null) {
                set(JavaCalendar.HOUR_OF_DAY, parsedEnd.first)
                set(JavaCalendar.MINUTE, parsedEnd.second)
            } else {
                if (startYear == endYear && startMonth == endMonth && startDay == endDay) {
                    set(JavaCalendar.HOUR_OF_DAY, sh)
                    set(JavaCalendar.MINUTE, sm)
                    add(JavaCalendar.HOUR_OF_DAY, 1)
                } else {
                    set(JavaCalendar.HOUR_OF_DAY, 23)
                    set(JavaCalendar.MINUTE, 59)
                }
            }

            set(JavaCalendar.SECOND, 0)
            set(JavaCalendar.MILLISECOND, 0)
        }

        val event = Event().apply {
            summary = title
            description = detail

            start = EventDateTime()
                .setDateTime(DateTime(calStart.timeInMillis))
                .setTimeZone("Asia/Seoul")

            end = EventDateTime()
                .setDateTime(DateTime(calEnd.timeInMillis))
                .setTimeZone("Asia/Seoul")
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    service.events()
                        .insert("primary", event)
                        .execute()
                }

                if (_binding == null) return@launch

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

    // -------------------- ✅ 과제(Assignment) 관련 추가 --------------------

    private fun selectedDateString(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    private suspend fun loadAssignmentsForSelectedDay(): List<AssignmentEntity> {
        val dateStr = selectedDateString(selectedDateMillis)
        val dao = AppDatabase.getDatabase(requireContext()).assignmentDao()
        return dao.getByDueDate(dateStr)
    }

    /**
     * ✅ 여기가 핵심 수정:
     * - 과제 행을 눌렀을 때 EventDetailActivity로 이동하도록 setOnClickListener 추가
     */
    private fun createAssignmentRow(a: AssignmentEntity): LinearLayout {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 8)
            isClickable = true
            isFocusable = true
        }

        val timeView = TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                0.3f
            )
            text = a.startTime ?: "09:00"
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
        }

        val titleView = TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                0.7f
            )

            val label = if (a.type == "팀 프로젝트") "[팀 과제]" else "[개인 과제]"
            text = "$label ${a.title}"

            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
        }

        container.addView(timeView)
        container.addView(titleView)

        container.setOnClickListener {
            val label = if (a.type == "팀 프로젝트") "[팀 과제]" else "[개인 과제]"

            val startMillis = assignmentDateTimeToMillis(a.dueDate, a.startTime ?: "09:00")
            val endMillis = assignmentDateTimeToMillis(a.dueDate, a.endTime ?: "10:00")

            val intent = Intent(requireContext(), EventDetailActivity::class.java).apply {
                putExtra("title", "$label ${a.title}")
                putExtra("eventId", "assignment:${a.id}")
                putExtra("htmlLink", "")
                putExtra("startMillis", startMillis)
                putExtra("endMillis", endMillis)
                putExtra("isAssignment", true)
                putExtra("assignmentId", a.id)
            }
            detailLauncher.launch(intent)
        }

        return container
    }

    // ✅ dueDate("yyyy-MM-dd") -> 해당 날짜 00:00 millis 로 변환
    private fun dueDateToDayStartMillis(dueDate: String): Long {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val parsed = sdf.parse(dueDate) ?: Date()
        val cal = JavaCalendar.getInstance().apply {
            time = parsed
            set(JavaCalendar.HOUR_OF_DAY, 0)
            set(JavaCalendar.MINUTE, 0)
            set(JavaCalendar.SECOND, 0)
            set(JavaCalendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    // -------------------- 공통 --------------------

    private fun showStatus(message: String) {
        _binding?.let { b ->
            b.statusTextView.text = message
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    private fun assignmentDateTimeToMillis(dueDate: String, hhmm: String): Long {
        val koreaTimeZone = java.util.TimeZone.getTimeZone("Asia/Seoul")

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = koreaTimeZone
        }

        val parsedDate = sdf.parse(dueDate) ?: Date()

        val parts = hhmm.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 9
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val cal = JavaCalendar.getInstance(koreaTimeZone).apply {
            time = parsedDate
            set(JavaCalendar.HOUR_OF_DAY, hour)
            set(JavaCalendar.MINUTE, minute)
            set(JavaCalendar.SECOND, 0)
            set(JavaCalendar.MILLISECOND, 0)
        }

        return cal.timeInMillis
    }
}