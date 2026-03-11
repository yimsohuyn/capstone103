package com.example.myapplication

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class EditEventActivity : AppCompatActivity() {

    // 인텐트로 받은 값들
    private var eventId: String? = null
    private var startMillis: Long = -1L
    private var endMillis: Long = -1L

    // 날짜/시간 계산용 캘린더
    private val startCal: Calendar = Calendar.getInstance()
    private val endCal: Calendar = Calendar.getInstance()

    // 알림 관련
    private var isAlarmOn: Boolean = false
    private var alarmTime: String? = null

    // 포맷터
    private val dateFormatter = SimpleDateFormat("yyyy년 M월 d일 (E)", Locale.KOREAN)
    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_event)

        // 1. Intent 데이터 수신
        val title = intent.getStringExtra("title") ?: ""
        val detail = intent.getStringExtra("detail") ?: ""
        eventId = intent.getStringExtra("eventId")
        startMillis = intent.getLongExtra("startMillis", -1L)
        endMillis = intent.getLongExtra("endMillis", -1L)

        isAlarmOn = intent.getBooleanExtra("isAlarmOn", false)
        alarmTime = intent.getStringExtra("alarmTime") // "HHmm" 형태

        // 2. UI 연결
        val editTitle = findViewById<EditText>(R.id.editTitle)
        val editStartDate = findViewById<TextView>(R.id.editStartDate)
        val editStartTime = findViewById<EditText>(R.id.editStartTime)
        val editEndDate = findViewById<TextView>(R.id.editEndDate)
        val editEndTime = findViewById<EditText>(R.id.editEndTime)
        val etDetail = findViewById<EditText>(R.id.etDetail)

        val switchAllDay = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchAllDay)
        val switchAlarm = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchAlarm)
        val rowAlarmTime = findViewById<LinearLayout>(R.id.rowAlarmTime)
        val editAlarmTime = findViewById<EditText>(R.id.editAlarmTime)

        val btnCancel = findViewById<Button>(R.id.btnCancel)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)

        // 3. 캘린더 초기화 (startMillis / endMillis 를 실제 날짜/시간으로 세팅)
        if (startMillis > 0) {
            startCal.timeInMillis = startMillis
        } else {
            startCal.timeInMillis = System.currentTimeMillis()
        }

        if (endMillis > 0) {
            endCal.timeInMillis = endMillis
        } else {
            endCal.timeInMillis = startCal.timeInMillis + 60 * 60 * 1000 // +1시간
        }

        // 4. UI 초기값 세팅
        editTitle.setText(title)
        etDetail.setText(detail)

        editStartDate.text = dateFormatter.format(startCal.time)
        editEndDate.text = dateFormatter.format(endCal.time)
        editStartTime.setText(timeFormatter.format(startCal.time))
        editEndTime.setText(timeFormatter.format(endCal.time))

        // 알림 초기값
        switchAlarm.isChecked = isAlarmOn
        if (isAlarmOn) {
            rowAlarmTime.visibility = View.VISIBLE
            if (!alarmTime.isNullOrEmpty() && alarmTime!!.length == 4) {
                val formatted = "${alarmTime!!.substring(0, 2)}:${alarmTime!!.substring(2, 4)}"
                editAlarmTime.setText(formatted)
            }
        } else {
            rowAlarmTime.visibility = View.GONE
        }

        // ───────── 시간 입력 포맷터 (자동 콜론) ─────────
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
        editStartTime.filters = arrayOf(lengthFilter)
        editEndTime.filters = arrayOf(lengthFilter)
        editAlarmTime.filters = arrayOf(lengthFilter)

        editStartTime.addTextChangedListener(TimeFormattingTextWatcher(editStartTime))
        editEndTime.addTextChangedListener(TimeFormattingTextWatcher(editEndTime))
        editAlarmTime.addTextChangedListener(TimeFormattingTextWatcher(editAlarmTime))

        // ───────── 날짜 선택 (DatePickerDialog) ─────────
        fun showDatePicker(isStart: Boolean) {
            val cal = if (isStart) startCal else endCal

            val listener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
                cal.set(year, month, dayOfMonth)

                if (isStart) {
                    editStartDate.text = dateFormatter.format(cal.time)
                } else {
                    editEndDate.text = dateFormatter.format(cal.time)
                }
            }

            DatePickerDialog(
                this,
                R.style.CustomDatePickerDialogTheme, // 팝업으로 지원하는 테마 사용
                listener,
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        editStartDate.setOnClickListener { showDatePicker(isStart = true) }
        editEndDate.setOnClickListener { showDatePicker(isStart = false) }

        // ───────── 하루 종일 스위치 ─────────
        switchAllDay.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // 단순히 00:00 ~ 23:59 로 고정 + 입력 비활성화
                editStartTime.setText("00:00")
                editEndTime.setText("23:59")
                editStartTime.isEnabled = false
                editEndTime.isEnabled = false
            } else {
                editStartTime.isEnabled = true
                editEndTime.isEnabled = true
            }
        }

        // ───────── 알림 스위치 ─────────
        switchAlarm.setOnCheckedChangeListener { _, checked ->
            isAlarmOn = checked
            rowAlarmTime.visibility = if (checked) View.VISIBLE else View.GONE
        }

        // ───────── 버튼 리스너 ─────────
        btnCancel.setOnClickListener { finish() }
        btnBack.setOnClickListener { finish() }

        btnSave.setOnClickListener {
            val newTitle = editTitle.text.toString().trim()
            val newDetail = etDetail.text.toString().trim()

            if (newTitle.isEmpty()) {
                Toast.makeText(this, "제목을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 시간 파싱 함수
            fun parseTime(text: String, label: String): Pair<Int, Int>? {
                val raw = text.replace(":", "").trim()
                if (raw.length != 4) {
                    Toast.makeText(this, "$label 을(를) 정확히 입력해주세요 (예: 09:30)", Toast.LENGTH_SHORT).show()
                    return null
                }
                val h = raw.substring(0, 2).toIntOrNull()
                val m = raw.substring(2, 4).toIntOrNull()
                if (h == null || m == null || h !in 0..23 || m !in 0..59) {
                    Toast.makeText(this, "$label 값이 올바르지 않습니다.", Toast.LENGTH_SHORT).show()
                    return null
                }
                return h to m
            }

            // 시작/종료 시간 반영
            val (startH, startM) = parseTime(editStartTime.text.toString(), "시작 시간") ?: return@setOnClickListener
            val (endH, endM) = parseTime(editEndTime.text.toString(), "종료 시간") ?: return@setOnClickListener

            startCal.set(Calendar.HOUR_OF_DAY, startH)
            startCal.set(Calendar.MINUTE, startM)
            startCal.set(Calendar.SECOND, 0)
            startCal.set(Calendar.MILLISECOND, 0)

            endCal.set(Calendar.HOUR_OF_DAY, endH)
            endCal.set(Calendar.MINUTE, endM)
            endCal.set(Calendar.SECOND, 0)
            endCal.set(Calendar.MILLISECOND, 0)

            // (선택) 종료 시간이 시작 시간보다 빠르면 오류
            if (endCal.timeInMillis <= startCal.timeInMillis) {
                Toast.makeText(this, "종료 시간이 시작 시간보다 늦어야 합니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            startMillis = startCal.timeInMillis
            endMillis = endCal.timeInMillis

            // 알림 시간 처리
            var newAlarmTimeStr: String? = null
            if (isAlarmOn) {
                val rawTime = editAlarmTime.text.toString().replace(":", "").trim()
                if (rawTime.length != 4) {
                    Toast.makeText(this, "알림 시간을 정확히 입력해주세요 (예: 09:00)", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                newAlarmTimeStr = rawTime
            }

            // 결과 인텐트
            val resultIntent = Intent().apply {
                putExtra("eventId", eventId)
                putExtra("title", newTitle)
                putExtra("detail", newDetail)
                putExtra("startMillis", startMillis)
                putExtra("endMillis", endMillis)
                putExtra("isAlarmOn", isAlarmOn)
                putExtra("alarmTime", newAlarmTimeStr)
            }

            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }
}
