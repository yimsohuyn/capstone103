package com.example.myapplication

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
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

data class Schedule(
    val title: String,
    val time: String? = "시간 미지정"
)

private data class ScheduleUiItem(
    val sortMillis: Long,
    val timeLabel: String,
    val title: String,
    val subtitle: String,
    val accentColorRes: Int,
    val onClick: (() -> Unit)? = null,
    val isForSelectedDate: Boolean = true
)

class HomeFragment : Fragment() {

    private val koreaTimeZone = java.util.TimeZone.getTimeZone("Asia/Seoul")

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var googleSignInClient: GoogleSignInClient
    private var calendarService: Calendar? = null
    private var ddayDialog: DdayDialogFragment? = null

    private var selectedDateMillis: Long = System.currentTimeMillis()

    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
        timeZone = koreaTimeZone
    }

    private val fullDateFormatter = SimpleDateFormat("M월 d일 E요일", Locale.KOREAN).apply {
        timeZone = koreaTimeZone
    }

    private val schedulesByDate = mutableMapOf<Long, MutableList<Schedule>>()

    private val settingLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val account = GoogleSignIn.getLastSignedInAccount(requireContext())

                if (account != null) {
                    onAccountSignedIn(account)
                } else {
                    calendarService = null
                    showStatus("구글 계정을 연결해주세요.")
                }

                if (calendarService != null) {
                    fetchEventsForSelectedDay()
                } else {
                    renderSchedulesForDate(selectedDateMillis)
                }

                ddayDialog?.refreshFromParent()
            }
        }

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

    private val authRecoverLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                fetchEventsForSelectedDay()
            } else {
                showStatus("캘린더 권한이 허용되지 않았습니다.")
            }
        }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
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
        binding.calendarView.apply {
            selectedWeekBackgroundColor =
                ContextCompat.getColor(requireContext(), R.color.calendar_selected_date)

            focusedMonthDateColor =
                ContextCompat.getColor(requireContext(), R.color.calendar_text)

            unfocusedMonthDateColor =
                ContextCompat.getColor(requireContext(), R.color.calendar_weekend)

            weekSeparatorLineColor =
                ContextCompat.getColor(requireContext(), R.color.divider)
        }

        selectedDateMillis = binding.calendarView.date

        if (calendarService != null) {
            fetchEventsForSelectedDay()
        } else {
            renderSchedulesForDate(selectedDateMillis)
        }

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

    private fun fetchEventsForSelectedDay() {
        val service = calendarService
        showStatus("일정을 불러오는 중입니다...")

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val selectedAssignments = withContext(Dispatchers.IO) {
                    loadAssignmentsForSelectedDay()
                }

                val upcomingAssignments = withContext(Dispatchers.IO) {
                    loadUpcomingAssignmentsAfterSelectedDay()
                }

                if (service == null) {
                    val selectedItems = selectedAssignments.map {
                        buildAssignmentUiItem(it, isForSelectedDate = true)
                    }
                    val upcomingItems = upcomingAssignments.map {
                        buildAssignmentUiItem(it, isForSelectedDate = false)
                    }

                    renderScheduleItems(selectedItems, upcomingItems)
                    showStatus("총 ${selectedItems.size}개의 일정이 있습니다.")
                    return@launch
                }

                val (timeMin, timeMax) = withContext(Dispatchers.Default) {
                    selectedDayBounds(selectedDateMillis)
                }

                val selectedEvents = withContext(Dispatchers.IO) {
                    service.events().list("primary")
                        .setSingleEvents(true)
                        .setOrderBy("startTime")
                        .setTimeMin(timeMin)
                        .setTimeMax(timeMax)
                        .execute()
                        .items.orEmpty()
                }

                val upcomingEvents = withContext(Dispatchers.IO) {
                    service.events().list("primary")
                        .setSingleEvents(true)
                        .setOrderBy("startTime")
                        .setTimeMin(DateTime(selectedDayEndMillis() + 1))
                        .execute()
                        .items.orEmpty()
                }

                if (_binding == null) return@launch

                val filteredSelectedEvents = selectedEvents.filterNot { event ->
                    val title = event.summary ?: ""
                    title.startsWith("[팀 과제]") || title.startsWith("[개인 과제]")
                }

                val filteredUpcomingEvents = upcomingEvents.filterNot { event ->
                    val title = event.summary ?: ""
                    title.startsWith("[팀 과제]") || title.startsWith("[개인 과제]")
                }

                val selectedItems = mutableListOf<ScheduleUiItem>()
                selectedItems.addAll(selectedAssignments.map {
                    buildAssignmentUiItem(it, isForSelectedDate = true)
                })
                selectedItems.addAll(filteredSelectedEvents.map {
                    buildEventUiItem(it, isForSelectedDate = true)
                })

                val upcomingItems = mutableListOf<ScheduleUiItem>()
                upcomingItems.addAll(upcomingAssignments.map {
                    buildAssignmentUiItem(it, isForSelectedDate = false)
                })
                upcomingItems.addAll(filteredUpcomingEvents.map {
                    buildEventUiItem(it, isForSelectedDate = false)
                })

                renderScheduleItems(selectedItems, upcomingItems)
                showStatus("총 ${selectedItems.size}개의 일정이 있습니다.")
            } catch (ex: Exception) {
                when (ex) {
                    is UserRecoverableAuthIOException -> {
                        authRecoverLauncher.launch(ex.intent)
                    }
                    else -> {
                        showStatus("캘린더를 불러오지 못했습니다.")
                        renderScheduleItems(emptyList(), emptyList())
                    }
                }
            }
        }
    }

    private fun selectedDayBounds(dayMillis: Long): Pair<DateTime, DateTime> {
        val cal = JavaCalendar.getInstance(koreaTimeZone).apply {
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

    private fun selectedDayEndMillis(): Long {
        val cal = JavaCalendar.getInstance(koreaTimeZone).apply {
            timeInMillis = selectedDateMillis
            set(JavaCalendar.HOUR_OF_DAY, 23)
            set(JavaCalendar.MINUTE, 59)
            set(JavaCalendar.SECOND, 59)
            set(JavaCalendar.MILLISECOND, 999)
        }
        return cal.timeInMillis
    }

    private fun buildEventUiItem(
        event: Event,
        isForSelectedDate: Boolean
    ): ScheduleUiItem {
        val startMillis = (event.start?.dateTime ?: event.start?.date)?.value ?: Long.MAX_VALUE
        val start = (event.start?.dateTime ?: event.start?.date)?.value ?: -1L
        val end = (event.end?.dateTime ?: event.end?.date)?.value ?: -1L

        return ScheduleUiItem(
            sortMillis = startMillis,
            timeLabel = formatEventTime(event),
            title = event.summary ?: "제목 없음",
            subtitle = "구글 일정",
            accentColorRes = R.color.calendar_selected_date,
            onClick = {
                val intent = Intent(requireContext(), EventDetailActivity::class.java).apply {
                    putExtra("title", event.summary ?: "제목 없음")
                    putExtra("eventId", event.id)
                    putExtra("htmlLink", event.htmlLink)
                    putExtra("startMillis", start)
                    putExtra("endMillis", end)
                }
                detailLauncher.launch(intent)
            },
            isForSelectedDate = isForSelectedDate
        )
    }

    private fun buildAssignmentUiItem(
        a: AssignmentEntity,
        isForSelectedDate: Boolean
    ): ScheduleUiItem {
        val startMillis = assignmentDateTimeToMillis(a.dueDate, a.startTime ?: "09:00")
        val endMillis = assignmentDateTimeToMillis(a.dueDate, a.endTime ?: "10:00")
        val label = if (a.type == "팀 프로젝트") "[팀 과제]" else "[개인 과제]"

        return ScheduleUiItem(
            sortMillis = startMillis,
            timeLabel = a.startTime ?: "09:00",
            title = "$label ${a.title}",
            subtitle = if (a.type == "팀 프로젝트") "팀 프로젝트" else "개인 프로젝트",
            accentColorRes = if (a.type == "팀 프로젝트") {
                R.color.point_assignment
            } else {
                R.color.calendar_selected_date
            },
            onClick = {
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
            },
            isForSelectedDate = isForSelectedDate
        )
    }

    private fun buildLocalUiItem(dateMillis: Long, schedule: Schedule): ScheduleUiItem {
        val sortMillis = localScheduleDateTimeToMillis(dateMillis, schedule.time)
        return ScheduleUiItem(
            sortMillis = sortMillis,
            timeLabel = schedule.time ?: "시간 미지정",
            title = schedule.title,
            subtitle = "개인 일정",
            accentColorRes = R.color.calendar_selected_date,
            onClick = null,
            isForSelectedDate = true
        )
    }

    private fun renderScheduleItems(
        selectedItems: List<ScheduleUiItem>,
        upcomingItems: List<ScheduleUiItem>
    ) {
        val b = _binding ?: return
        b.calendarEventsContainer.removeAllViews()

        val sortedSelectedItems = selectedItems.sortedBy { it.sortMillis }
        val sortedUpcomingItems = upcomingItems.sortedBy { it.sortMillis }

        updateTodaySummary(sortedSelectedItems, sortedUpcomingItems)

        if (sortedSelectedItems.isEmpty()) {
            b.calendarEventsContainer.addView(createEmptyStateView())
            return
        }

        b.calendarEventsContainer.addView(createScheduleListCard(sortedSelectedItems))
    }

    private fun updateTodaySummary(
        selectedItems: List<ScheduleUiItem>,
        upcomingItems: List<ScheduleUiItem>
    ) {
        val dateLabel = fullDateFormatter.format(Date(selectedDateMillis))
        binding.todayMetaText.text = "$dateLabel · 일정 ${selectedItems.size}개"

        binding.summaryChipContainer.removeAllViews()

        val totalChip = createSummaryChip(
            text = "총 일정 ${selectedItems.size}",
            iconResId = R.drawable.ic_calendar
        )

        val upcomingChip = createSummaryChip(
            text = "다가오는 일정 ${upcomingItems.size}",
            iconResId = R.drawable.ic_bell
        )

        binding.summaryChipContainer.addView(totalChip)
        binding.summaryChipContainer.addView(upcomingChip)
    }

    private fun createSummaryChip(text: String, iconResId: Int): LinearLayout {
        val context = requireContext()

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(context, R.drawable.bg_today_summary_chip)
            setPadding(dp(14), dp(10), dp(14), dp(10))

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(10)
            }

            addView(ImageView(context).apply {
                setImageResource(iconResId)
                setColorFilter(ContextCompat.getColor(context, R.color.text_primary))
                layoutParams = LinearLayout.LayoutParams(dp(16), dp(16)).apply {
                    marginEnd = dp(8)
                }
            })

            addView(TextView(context).apply {
                this.text = text
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            })
        }
    }

    private fun createEmptyStateView(): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_today_empty_box)
            setPadding(dp(20))

            addView(TextView(requireContext()).apply {
                text = "선택한 날짜에 일정이 없습니다."
                setTypeface(null, Typeface.BOLD)
                textSize = 16f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                gravity = Gravity.CENTER
            })

            addView(TextView(requireContext()).apply {
                text = "+ 버튼으로 새 일정을 추가해보세요"
                textSize = 13f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                gravity = Gravity.CENTER
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = dp(8)
                layoutParams = lp
            })
        }
    }

    private fun createScheduleListCard(items: List<ScheduleUiItem>): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_today_schedule_list_card)
            setPadding(dp(14))

            items.forEachIndexed { index, item ->
                addView(createScheduleRow(item))

                if (index != items.lastIndex) {
                    addView(View(requireContext()).apply {
                        setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.divider))
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dp(1)
                        ).apply {
                            topMargin = dp(10)
                            bottomMargin = dp(10)
                        }
                    })
                }
            }
        }
    }

    private fun createScheduleRow(item: ScheduleUiItem): LinearLayout {
        val accentColor = ContextCompat.getColor(requireContext(), item.accentColorRes)

        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(64)

            addView(View(requireContext()).apply {
                background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_today_accent_bar)
                background.setTint(accentColor)
                layoutParams = LinearLayout.LayoutParams(dp(6), dp(40)).apply {
                    marginEnd = dp(12)
                }
            })

            addView(TextView(requireContext()).apply {
                text = item.timeLabel
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(accentColor)
                layoutParams = LinearLayout.LayoutParams(dp(76), ViewGroup.LayoutParams.WRAP_CONTENT)
            })

            addView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

                addView(TextView(requireContext()).apply {
                    text = item.title
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                })

                addView(TextView(requireContext()).apply {
                    text = item.subtitle
                    textSize = 13f
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                    val lp = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    lp.topMargin = dp(4)
                    layoutParams = lp
                })
            })

            addView(TextView(requireContext()).apply {
                text = "⋮"
                textSize = 18f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            })

            if (item.onClick != null) {
                isClickable = true
                isFocusable = true
                setOnClickListener { item.onClick.invoke() }
            }
        }
    }

    private fun formatEventTime(event: Event): String {
        val dateTime = event.start?.dateTime ?: event.start?.date
        return if (dateTime != null) {
            timeFormatter.format(Date(dateTime.value))
        } else {
            "종일"
        }
    }

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
                val calStart = JavaCalendar.getInstance().apply {
                    set(startYear, startMonth, startDay, 0, 0, 0)
                    set(JavaCalendar.MILLISECOND, 0)
                }
                val dateMillis = calStart.timeInMillis

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

    private fun renderSchedulesForDate(dateMillis: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            val assignments = withContext(Dispatchers.IO) {
                loadAssignmentsForDate(dateMillis)
            }

            val upcomingAssignments = withContext(Dispatchers.IO) {
                loadUpcomingAssignmentsAfterSelectedDay()
            }

            val locals = schedulesByDate[dateMillis].orEmpty().map {
                buildLocalUiItem(dateMillis, it)
            }

            val selectedItems = mutableListOf<ScheduleUiItem>()
            selectedItems.addAll(assignments.map { buildAssignmentUiItem(it, true) })
            selectedItems.addAll(locals)

            val upcomingItems = upcomingAssignments.map {
                buildAssignmentUiItem(it, false)
            }

            renderScheduleItems(selectedItems, upcomingItems)
        }
    }

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

        val (sh, sm) = parseTime(startTime) ?: (9 to 0)
        val calStart = JavaCalendar.getInstance(koreaTimeZone).apply {
            set(startYear, startMonth, startDay)
            set(JavaCalendar.HOUR_OF_DAY, sh)
            set(JavaCalendar.MINUTE, sm)
            set(JavaCalendar.SECOND, 0)
            set(JavaCalendar.MILLISECOND, 0)
        }

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

    private fun selectedDateString(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = koreaTimeZone
        }
        return sdf.format(Date(millis))
    }

    private suspend fun loadAssignmentsForSelectedDay(): List<AssignmentEntity> {
        val dateStr = selectedDateString(selectedDateMillis)
        val dao = AppDatabase.getDatabase(requireContext()).assignmentDao()
        return dao.getByDueDate(dateStr)
    }

    private suspend fun loadAssignmentsForDate(dateMillis: Long): List<AssignmentEntity> {
        val dateStr = selectedDateString(dateMillis)
        val dao = AppDatabase.getDatabase(requireContext()).assignmentDao()
        return dao.getByDueDate(dateStr)
    }

    private suspend fun loadUpcomingAssignmentsAfterSelectedDay(): List<AssignmentEntity> {
        val dao = AppDatabase.getDatabase(requireContext()).assignmentDao()
        val allAssignments = dao.getAll()
        val endMillis = selectedDayEndMillis()

        return allAssignments.filter { assignment ->
            val startMillis = assignmentDateTimeToMillis(
                assignment.dueDate,
                assignment.startTime ?: "09:00"
            )
            startMillis > endMillis
        }
    }

    private fun showStatus(message: String) {
        _binding?.statusTextView?.text = message
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun assignmentDateTimeToMillis(dueDate: String, hhmm: String): Long {
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

    private fun localScheduleDateTimeToMillis(dateMillis: Long, time: String?): Long {
        val cal = JavaCalendar.getInstance(koreaTimeZone).apply {
            timeInMillis = dateMillis
        }
        val parsed = parseTime(time)
        cal.set(JavaCalendar.HOUR_OF_DAY, parsed?.first ?: 23)
        cal.set(JavaCalendar.MINUTE, parsed?.second ?: 59)
        cal.set(JavaCalendar.SECOND, 0)
        cal.set(JavaCalendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}