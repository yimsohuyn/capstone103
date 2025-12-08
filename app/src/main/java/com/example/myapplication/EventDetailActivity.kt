package com.example.myapplication

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar as JavaCalendar

class EventDetailActivity : AppCompatActivity() {

    // ─── 넘어온 값들 ───
    private var eventId: String? = null
    private var htmlLink: String? = null
    var startMillis: Long = -1L
    var endMillis: Long = -1L

    // 상세 내용
    private var detail: String = ""

    // ★ [핵심] 알림 상태와 시간 저장 변수
    private var isAlarmOn: Boolean = false
    private var alarmTime: String? = null // "HHmm" 형식 (예: "1430")

    private lateinit var calendarStatusText: TextView
    private lateinit var calendarEventsContainer: LinearLayout
    private var selectedDateMillis: Long = System.currentTimeMillis()

    // ─── 구글 캘린더 서비스 (삭제용) ───
    private var calendarService: Calendar? = null

    // ─── UI 참조 ───
    private lateinit var tvTitle: TextView
    private lateinit var tvStartDate: TextView
    private lateinit var tvEndDate: TextView
    private lateinit var tvStartTime: TextView
    private lateinit var tvEndTime: TextView
    private lateinit var tvDetail: TextView

    // ★ 알림 관련 UI
    private lateinit var tvAlarmInfo: TextView
    private lateinit var btnAlarmToggle: ImageButton

    // 날짜/시간 포맷터
    private val dateFormatter = SimpleDateFormat("M월 d일 (E)", Locale.KOREAN)
    private val timeFormatter = SimpleDateFormat("a h:mm", Locale.KOREAN) // 예: 오후 2:30

    override fun finish() {
        setResult(RESULT_OK)
        super.finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        setResult(RESULT_OK)
        finish()
        return true
    }

    // ✅ 편집 화면(EditEventActivity)에서 돌아올 때 데이터 받기
    private val editEventLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val data = result.data ?: return@registerForActivityResult

            // 1. 기본 정보 갱신
            val newTitle = data.getStringExtra("title") ?: tvTitle.text.toString()
            val newStart = data.getLongExtra("startMillis", startMillis)
            val newEnd = data.getLongExtra("endMillis", endMillis)
            val newDetail = data.getStringExtra("detail") ?: detail

            // ★ 2. 알림 정보 갱신 (편집 화면에서 넘겨준 값 받기)
            // 편집 화면에서 "isAlarmOn", "alarmTime" 키로 값을 넘겨줘야 합니다.
            val newIsAlarmOn = data.getBooleanExtra("isAlarmOn", isAlarmOn)
            val newAlarmTime = data.getStringExtra("alarmTime") // "0930" 등

            // 3. 변수 업데이트
            startMillis = newStart
            endMillis = newEnd
            detail = newDetail
            isAlarmOn = newIsAlarmOn

            // 알림 시간이 새로 왔으면 갱신, 아니면 기존 시간 유지
            if (newAlarmTime != null) {
                alarmTime = newAlarmTime
            }

            // 4. UI 갱신
            tvTitle.text = newTitle
            tvDetail.text = detail
            updateAlarmUI() // ★ 알림 UI 즉시 반영

            if (startMillis > 0 && endMillis > 0) {
                val start = Date(startMillis)
                val end = Date(endMillis)
                tvStartDate.text = dateFormatter.format(start)
                tvEndDate.text = dateFormatter.format(end)
                tvStartTime.text = timeFormatter.format(start)
                tvEndTime.text = timeFormatter.format(end)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_detail)

        // ─── Intent에서 값 꺼내기 ───
        val titleFromIntent = intent.getStringExtra("title") ?: "제목 없음"
        eventId = intent.getStringExtra("eventId")
        htmlLink = intent.getStringExtra("htmlLink")
        startMillis = intent.getLongExtra("startMillis", -1L)
        endMillis = intent.getLongExtra("endMillis", -1L)
        detail = intent.getStringExtra("detail") ?: ""

        // ★ 알림 초기값 받기 (없으면 false, 시간은 null)
        isAlarmOn = intent.getBooleanExtra("isAlarmOn", false)
        alarmTime = intent.getStringExtra("alarmTime")

        // ─── UI 바인딩 ───
        tvTitle = findViewById(R.id.tvTitle)
        tvStartDate = findViewById(R.id.tvStartDate)
        tvEndDate = findViewById(R.id.tvEndDate)
        tvStartTime = findViewById(R.id.tvStartTime)
        tvEndTime = findViewById(R.id.tvEndTime)
        tvDetail = findViewById(R.id.tvDetail)

        // ★ 알림 UI 연결
        tvAlarmInfo = findViewById(R.id.tvAlarmInfo)
        btnAlarmToggle = findViewById(R.id.btnAlarmToggle)

        // 초기 텍스트 설정
        tvTitle.text = titleFromIntent
        tvDetail.text = detail

        // ★ 초기 알림 UI 상태 반영
        updateAlarmUI()

        if (startMillis > 0 && endMillis > 0) {
            val start = Date(startMillis)
            val end = Date(endMillis)
            tvStartDate.text = dateFormatter.format(start)
            tvEndDate.text = dateFormatter.format(end)
            tvStartTime.text = timeFormatter.format(start)
            tvEndTime.text = timeFormatter.format(end)
        }

        // ★ 알림 토글 버튼 클릭 이벤트
        btnAlarmToggle.setOnClickListener {
            toggleAlarm()
        }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            setResult(RESULT_OK)
            finish()
        }

        calendarService = buildCalendarService()

        findViewById<LinearLayout>(R.id.btnCopy).setOnClickListener {
            val copyText = if (detail.isNotBlank()) "${tvTitle.text}\n$detail" else tvTitle.text.toString()
            copyToClipboard(copyText)
        }

        // ✅ 편집 버튼 클릭
        findViewById<LinearLayout>(R.id.btnEdit).setOnClickListener {
            val intent = Intent(this, EditEventActivity::class.java).apply {
                putExtra("eventId", eventId)
                putExtra("title", tvTitle.text.toString())
                putExtra("startMillis", startMillis)
                putExtra("endMillis", endMillis)
                putExtra("detail", detail)
                // ★ 편집 화면으로 현재 알림 상태와 시간을 전달
                putExtra("isAlarmOn", isAlarmOn)
                putExtra("alarmTime", alarmTime)
            }
            editEventLauncher.launch(intent)
        }

        findViewById<LinearLayout>(R.id.btnShare).setOnClickListener {
            shareEvent()
        }

        findViewById<LinearLayout>(R.id.btnDelete).setOnClickListener {
            confirmAndDelete()
        }
    }

    // ★ [기능] 알림 토글 로직
    private fun toggleAlarm() {
        if (!isAlarmOn) {
            // 알림 켜기 시도
            if (alarmTime.isNullOrEmpty()) {
                // 시간이 없으면 켤 수 없습니다. (데이터 전달 문제로 4:30이 넘어오지 못한 경우)
                Toast.makeText(this, "알림 시간이 설정되지 않아 켤 수 없습니다. 편집 페이지에서 시간을 설정해주세요.", Toast.LENGTH_LONG).show()
                return // 여기서 함수 종료, isAlarmOn은 false로 유지
            } else {
                // 알림 시간이 있으니 켭니다.
                isAlarmOn = true
                Toast.makeText(this, "알림이 켜졌습니다.", Toast.LENGTH_SHORT).show()
            }
        } else {
            // 알림 끄기
            isAlarmOn = false
            Toast.makeText(this, "알림이 꺼졌습니다.", Toast.LENGTH_SHORT).show()
        }
        updateAlarmUI()

        // TODO: 여기서 실제로 알람 매니저를 등록/해제하거나 DB에 상태를 저장하세요.
    }

    // ★ [UI] 알림 상태에 따라 텍스트와 아이콘 업데이트
    private fun updateAlarmUI() {
        if (isAlarmOn) {
            // 켜졌을 때: "알림 켜짐 (오전 10:30)" 형태로 표시
            val formattedTime = formatAlarmTime(alarmTime)
            tvAlarmInfo.text = "알림 켜짐 ($formattedTime)"
            tvAlarmInfo.setTextColor(Color.RED) // 붉은색 텍스트

            btnAlarmToggle.setImageResource(R.drawable.outline_alarm_on_24)
            btnAlarmToggle.imageTintList = ColorStateList.valueOf(Color.RED) // 붉은색 아이콘
        } else {
            // 꺼졌을 때: "알림 꺼짐"
            tvAlarmInfo.text = "알림 꺼짐"
            tvAlarmInfo.setTextColor(Color.GRAY) // 회색 텍스트

            // 꺼짐 아이콘 (outline_alarm_off_24 리소스가 없다면 on 아이콘을 회색으로 사용)
            btnAlarmToggle.setImageResource(R.drawable.outline_alarm_off_24)
            btnAlarmToggle.imageTintList = ColorStateList.valueOf(Color.GRAY) // 회색 아이콘
        }
    }

    // ★ [헬퍼] "0930" 문자열을 "오전 9:30"으로 변환
    private fun formatAlarmTime(rawTime: String?): String {
        if (rawTime == null || rawTime.length != 4) return "시간 미설정"
        return try {
            val h = rawTime.substring(0, 2).toInt()
            val m = rawTime.substring(2, 4).toInt()

            // Calendar를 이용해 '오전/오후 h:mm' 포맷으로 변환
            val cal = JavaCalendar.getInstance().apply {
                set(JavaCalendar.HOUR_OF_DAY, h)
                set(JavaCalendar.MINUTE, m)
            }
            timeFormatter.format(cal.time) // 위에 정의한 포맷터 사용
        } catch (e: Exception) {
            rawTime ?: ""
        }
    }

    // ───────────────── 기타 기능들 ─────────────────
    private fun buildCalendarService(): Calendar? {
        val account = GoogleSignIn.getLastSignedInAccount(this) ?: return null
        val credential = GoogleAccountCredential.usingOAuth2(this, listOf(CalendarScopes.CALENDAR))
        credential.selectedAccount = account.account
        return Calendar.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName(getString(R.string.app_name)).build()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("event", text))
        Toast.makeText(this, "복사되었습니다.", Toast.LENGTH_SHORT).show()
    }

    private fun shareEvent() {
        val builder = StringBuilder()
        builder.appendLine(tvTitle.text.toString())
        if (startMillis > 0 && endMillis > 0) {
            val start = Date(startMillis)
            val end = Date(endMillis)
            builder.appendLine("${dateFormatter.format(start)} ${timeFormatter.format(start)} - ${dateFormatter.format(end)} ${timeFormatter.format(end)}")
        }
        if (detail.isNotBlank()) {
            builder.appendLine().appendLine(detail)
        }
        if (isAlarmOn) {
            // 공유할 때 알림 시간도 포함하고 싶다면 아래 주석 해제
            // builder.appendLine("알림: ${formatAlarmTime(alarmTime)}")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, builder.toString())
        }
        startActivity(Intent.createChooser(intent, "일정 공유"))
    }

    private fun confirmAndDelete() {
        val id = eventId ?: return
        AlertDialog.Builder(this)
            .setTitle("일정 삭제")
            .setMessage("이 일정을 정말 삭제할까요?")
            .setPositiveButton("삭제") { _, _ -> deleteEvent(id) }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun deleteEvent(id: String) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    calendarService?.events()?.delete("primary", id)?.execute()
                }
                Toast.makeText(this@EventDetailActivity, "삭제되었습니다.", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@EventDetailActivity, "오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}