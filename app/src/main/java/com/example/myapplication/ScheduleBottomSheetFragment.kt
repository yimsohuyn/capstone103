package com.example.myapplication

import android.app.AlarmManager
import android.app.Dialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.util.*

class ScheduleBottomSheetFragment : BottomSheetDialogFragment() {

    interface OnScheduleAddedListener {
        fun onScheduleAdded(
            title: String,
            startYear: Int,
            startMonth: Int,
            startDay: Int,
            endYear: Int,
            endMonth: Int,
            endDay: Int,
            startTime: String?,   // 시작 시간 "HH:mm"
            endTime: String?,     // 종료 시간 "HH:mm"
            detail: String?,      // 메모/상세 내용
            isAlarmOn: Boolean,   // 알람 설정 여부
            alarmTime: String?    // 알림 시간 "HHmm"
        )
    }

    var listener: OnScheduleAddedListener? = null
    private var isAlarmOn = false

    private var initialAlarmTime: String? = null
    private var initialIsAlarmOn: Boolean = false

    // 날짜 선택 상태 (공용 DatePicker용)
    private var selectingStart = true
    private var startYear = 0
    private var startMonth = 0
    private var startDay = 0
    private var endYear = 0
    private var endMonth = 0
    private var endDay = 0

    // ─────────────────────────────────────────────
    // 1. 바텀시트를 전체 화면으로 확장
    // ─────────────────────────────────────────────
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog

        dialog.setOnShowListener { d ->
            val bottomSheetDialog = d as BottomSheetDialog
            val bottomSheet =
                bottomSheetDialog.findViewById<View>(
                    com.google.android.material.R.id.design_bottom_sheet
                ) ?: return@setOnShowListener

            bottomSheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT

            BottomSheetBehavior.from(bottomSheet).apply {
                state = BottomSheetBehavior.STATE_EXPANDED
                skipCollapsed = true
                isFitToContents = true
            }
        }

        return dialog
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            initialAlarmTime = it.getString("ALARM_TIME")
            initialIsAlarmOn = it.getBoolean("IS_ALARM_ON", false)
            isAlarmOn = initialIsAlarmOn
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_schedule_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ─── UI 요소 연결 ───
        val btnBack = view.findViewById<ImageButton>(R.id.btnBack)
        val btnSave = view.findViewById<Button>(R.id.btnSaveSchedule)
        val btnAlarmToggle = view.findViewById<ImageButton>(R.id.btnAlarmToggle)

        val layoutAlarmDetail = view.findViewById<LinearLayout>(R.id.layoutAlarmDetail)

        val editScheduleTitle = view.findViewById<EditText>(R.id.editScheduleTitle)
        val editScheduleDetail = view.findViewById<EditText>(R.id.editScheduleDetail)
        val datePicker = view.findViewById<DatePicker>(R.id.datePicker)

        val layoutStartDate = view.findViewById<LinearLayout>(R.id.layoutStartDate)
        val layoutEndDate = view.findViewById<LinearLayout>(R.id.layoutEndDate)
        val tvStartDateValue = view.findViewById<TextView>(R.id.tvStartDateValue)
        val tvEndDateValue = view.findViewById<TextView>(R.id.tvEndDateValue)

        val editAlarmContent = view.findViewById<EditText>(R.id.editAlarmContent)

        // ★ 시간 입력 칸들
        val editStartTime = view.findViewById<EditText>(R.id.editStartTime) // 시작 시간
        val editEndTime = view.findViewById<EditText>(R.id.editEndTime)     // 종료 시간
        val editAlarmTime = view.findViewById<EditText>(R.id.editAlarmTime) // 알림 시간

        // ─── 날짜 초기값 설정 (오늘 날짜 기준) ───
        startYear = datePicker.year
        startMonth = datePicker.month
        startDay = datePicker.dayOfMonth

        endYear = startYear
        endMonth = startMonth
        endDay = startDay

        fun formatDate(y: Int, m: Int, d: Int): String {
            // m 은 0부터 시작하므로 +1
            return String.format("%04d-%02d-%02d", y, m + 1, d)
        }

        fun refreshDateTexts() {
            tvStartDateValue.text = formatDate(startYear, startMonth, startDay)
            tvEndDateValue.text = formatDate(endYear, endMonth, endDay)

            if (selectingStart) {
                tvStartDateValue.setTextColor(0xFF673AB7.toInt()) // 보라색
                tvEndDateValue.setTextColor(0xFF808080.toInt())   // 회색
            } else {
                tvStartDateValue.setTextColor(0xFF808080.toInt())
                tvEndDateValue.setTextColor(0xFF673AB7.toInt())
            }
        }

        refreshDateTexts()

        // 시작 날짜 영역 클릭 → 시작 선택 모드
        layoutStartDate.setOnClickListener {
            selectingStart = true
            datePicker.updateDate(startYear, startMonth, startDay)
            refreshDateTexts()
        }

        // 종료 날짜 영역 클릭 → 종료 선택 모드
        layoutEndDate.setOnClickListener {
            selectingStart = false
            datePicker.updateDate(endYear, endMonth, endDay)
            refreshDateTexts()
        }

        // DatePicker에서 날짜가 바뀔 때
        datePicker.init(startYear, startMonth, startDay) { _, y, m, d ->
            if (selectingStart) {
                startYear = y
                startMonth = m
                startDay = d
            } else {
                endYear = y
                endMonth = m
                endDay = d
            }
            refreshDateTexts()
        }

        // ─── 시간 입력 포맷터 (자동 콜론, 4자리 제한) ───
        class TimeFormattingTextWatcher(private val editText: EditText) : TextWatcher {
            private var isFormatting = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isFormatting || s == null) return
                isFormatting = true

                val digits = s.toString().replace(Regex("[^\\d]"), "")
                val trimmed = if (digits.length > 4) digits.substring(0, 4) else digits

                val formatted = StringBuilder()
                for (i in trimmed.indices) {
                    if (i == 2) formatted.append(":")
                    formatted.append(trimmed[i])
                }

                if (s.toString() != formatted.toString()) {
                    editText.setText(formatted.toString())
                    editText.setSelection(formatted.length)
                }

                isFormatting = false
            }
        }

        val lengthFilter = InputFilter.LengthFilter(5)

        // 시작 시간
        editStartTime.filters = arrayOf(lengthFilter)
        editStartTime.addTextChangedListener(TimeFormattingTextWatcher(editStartTime))

        // 종료 시간
        editEndTime.filters = arrayOf(lengthFilter)
        editEndTime.addTextChangedListener(TimeFormattingTextWatcher(editEndTime))

        // 알림 시간
        editAlarmTime.filters = arrayOf(lengthFilter)
        editAlarmTime.addTextChangedListener(TimeFormattingTextWatcher(editAlarmTime))

        // ─── 초기 알림 값 세팅 ───
        if (isAlarmOn && !initialAlarmTime.isNullOrEmpty() && initialAlarmTime!!.length == 4) {
            val formatted =
                "${initialAlarmTime!!.substring(0, 2)}:${initialAlarmTime!!.substring(2, 4)}"
            editAlarmTime.setText(formatted)
        }
        updateAlarmToggleUI(btnAlarmToggle, layoutAlarmDetail, isAlarmOn)

        // ─── 버튼 리스너 ───
        btnAlarmToggle.setOnClickListener {
            isAlarmOn = !isAlarmOn
            updateAlarmToggleUI(btnAlarmToggle, layoutAlarmDetail, isAlarmOn)
        }

        btnBack.setOnClickListener { dismiss() }

        // ─── 저장 버튼 로직 ───
        btnSave.setOnClickListener {
            val title = editScheduleTitle.text.toString().trim()
            val detail = editScheduleDetail.text.toString().trim()

            if (title.isBlank()) {
                Toast.makeText(requireContext(), "제목을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 날짜 순서 체크 (종료가 시작보다 빠르면 오류)
            val calStart = Calendar.getInstance().apply {
                set(startYear, startMonth, startDay, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val calEnd = Calendar.getInstance().apply {
                set(endYear, endMonth, endDay, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (calEnd.timeInMillis < calStart.timeInMillis) {
                Toast.makeText(
                    requireContext(),
                    "종료 날짜가 시작 날짜보다 빠를 수 없습니다.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            // 1) 시작/종료 시간 파싱
            var startTimeFormatted: String? = null
            var endTimeFormatted: String? = null

            // 시작 시간
            val rawStartTime = editStartTime.text.toString().replace(":", "").trim()
            if (rawStartTime.isNotEmpty()) {
                if (rawStartTime.length == 4) {
                    val h = rawStartTime.substring(0, 2).toIntOrNull() ?: 0
                    val m = rawStartTime.substring(2, 4).toIntOrNull() ?: 0
                    startTimeFormatted = String.format("%02d:%02d", h, m)
                } else {
                    Toast.makeText(
                        requireContext(),
                        "시작 시간을 4자리 숫자로 입력해주세요 (예: 0930)",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
            }

            // 종료 시간
            val rawEndTime = editEndTime.text.toString().replace(":", "").trim()
            if (rawEndTime.isNotEmpty()) {
                if (rawEndTime.length == 4) {
                    val h = rawEndTime.substring(0, 2).toIntOrNull() ?: 0
                    val m = rawEndTime.substring(2, 4).toIntOrNull() ?: 0
                    endTimeFormatted = String.format("%02d:%02d", h, m)
                } else {
                    Toast.makeText(
                        requireContext(),
                        "종료 시간을 4자리 숫자로 입력해주세요 (예: 1030)",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
            }

            // 2) 알림 설정 (시작 날짜 기준으로 알림 설정)
            var finalAlarmTimeStr: String? = null

            if (isAlarmOn) {
                val rawAlarmTime = editAlarmTime.text.toString().replace(":", "").trim()
                val alarmContent = editAlarmContent.text.toString().ifEmpty { "일정 알림" }

                if (rawAlarmTime.length == 4) {
                    finalAlarmTimeStr = rawAlarmTime

                    val h = rawAlarmTime.substring(0, 2).toIntOrNull() ?: 0
                    val m = rawAlarmTime.substring(2, 4).toIntOrNull() ?: 0

                    val calendar = Calendar.getInstance().apply {
                        set(startYear, startMonth, startDay, h, m, 0)
                        if (timeInMillis < System.currentTimeMillis()) {
                            add(Calendar.DAY_OF_YEAR, 1)
                        }
                    }

                    val intent = Intent(requireContext(), AlarmReceiver::class.java).apply {
                        putExtra("title", title)
                        putExtra("content", alarmContent)
                    }

                    val pendingIntent = PendingIntent.getBroadcast(
                        requireContext(),
                        (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val alarmManager =
                        requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )

                    Toast.makeText(
                        requireContext(),
                        "알람 설정됨: ${String.format("%02d:%02d", h, m)}",
                        Toast.LENGTH_SHORT
                    ).show()

                } else {
                    Toast.makeText(
                        requireContext(),
                        "알림 시간을 4자리 숫자로 입력해주세요.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
            }

            val finalDetail = if (detail.isEmpty()) null else detail

            // 상위(HomeFragment)로 전달
            listener?.onScheduleAdded(
                title = title,
                startYear = startYear,
                startMonth = startMonth,
                startDay = startDay,
                endYear = endYear,
                endMonth = endMonth,
                endDay = endDay,
                startTime = startTimeFormatted,
                endTime = endTimeFormatted,
                detail = finalDetail,
                isAlarmOn = isAlarmOn,
                alarmTime = finalAlarmTimeStr
            )

            dismiss()
        }
    }

    private fun updateAlarmToggleUI(btn: ImageButton, layout: LinearLayout, isOn: Boolean) {
        layout.visibility = if (isOn) View.VISIBLE else View.GONE
        btn.setImageResource(
            if (isOn) R.drawable.outline_alarm_on_24
            else R.drawable.outline_alarm_off_24
        )
        btn.imageTintList =
            ColorStateList.valueOf(if (isOn) Color.RED else Color.GRAY)
    }

    companion object {
        fun newInstance(alarmTime: String?, isAlarmOn: Boolean): ScheduleBottomSheetFragment {
            return ScheduleBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    putString("ALARM_TIME", alarmTime)
                    putBoolean("IS_ALARM_ON", isAlarmOn)
                }
            }
        }
    }
}
