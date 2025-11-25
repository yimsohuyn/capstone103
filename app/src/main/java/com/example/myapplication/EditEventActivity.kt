package com.example.myapplication

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class EditEventActivity : AppCompatActivity() {

    // 인텐트로 넘겨받는 값들
    private var eventId: String? = null
    private var startMillis: Long = -1L
    private var endMillis: Long = -1L
    private var detail: String = ""

    // 하루 종일 여부 + 원래 시간 저장
    private var isAllDay: Boolean = false
    private var originalStartTime: String = ""
    private var originalEndTime: String = ""

    // 구글 캘린더 서비스
    private var calendarService: com.google.api.services.calendar.Calendar? = null

    // UI
    private lateinit var editTitle: EditText
    private lateinit var editDetail: EditText
    private lateinit var tvStartDate: TextView
    private lateinit var tvEndDate: TextView
    private lateinit var etStartTime: EditText
    private lateinit var etEndTime: EditText
    private lateinit var btnCancel: Button
    private lateinit var btnSave: Button
    private lateinit var switchAllDay: Switch

    private val dateFormatter = SimpleDateFormat("M월 d일 (E)", Locale.KOREAN)
    private val timeInputFormatter = SimpleDateFormat("HH:mm", Locale.KOREAN)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_event)

        // 인텐트 데이터
        eventId = intent.getStringExtra("eventId")
        val oldTitle = intent.getStringExtra("title") ?: "제목 없음"
        startMillis = intent.getLongExtra("startMillis", -1L)
        endMillis = intent.getLongExtra("endMillis", -1L)
        detail = intent.getStringExtra("detail") ?: ""

        // UI 바인딩 (xml id와 맞춤)
        editTitle = findViewById(R.id.editTitle)
        editDetail = findViewById(R.id.etDetail)

        tvStartDate = findViewById(R.id.editStartDate)
        tvEndDate = findViewById(R.id.editEndDate)
        etStartTime = findViewById(R.id.editStartTime)
        etEndTime = findViewById(R.id.editEndTime)

        btnCancel = findViewById(R.id.btnCancel)
        btnSave = findViewById(R.id.btnSave)
        switchAllDay = findViewById(R.id.switchAllDay)

        findViewById<ImageButton?>(R.id.btnBack)?.setOnClickListener { finish() }

        // 기존 값 세팅
        editTitle.setText(oldTitle)
        editDetail.setText(detail)

        if (startMillis > 0 && endMillis > 0) {
            val start = Date(startMillis)
            val end = Date(endMillis)

            tvStartDate.text = dateFormatter.format(start)
            tvEndDate.text = dateFormatter.format(end)
            etStartTime.setText(timeInputFormatter.format(start)) // 예: 08:00
            etEndTime.setText(timeInputFormatter.format(end))     // 예: 09:00
        }

        // "원래 시간" 기억해두기 (하루 종일 on/off 시 되돌릴 용도)
        originalStartTime = etStartTime.text.toString()
        originalEndTime = etEndTime.text.toString()

        // 하루 종일 스위치 동작
        switchAllDay.setOnCheckedChangeListener { _, checked ->
            isAllDay = checked

            if (checked) {
                // UI 표시
                etStartTime.setText("00:00")
                etEndTime.setText("24:00")
                etStartTime.isEnabled = false
                etEndTime.isEnabled = false

                // millis 를 00:00 ~ 다음날 00:00 으로 설정
                val cal = Calendar.getInstance()
                if (startMillis > 0) {
                    cal.timeInMillis = startMillis
                }
                // 시작 날짜의 00:00
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                startMillis = cal.timeInMillis

                // 끝 시간 = 다음날 00:00
                cal.add(Calendar.DAY_OF_MONTH, 1)
                endMillis = cal.timeInMillis

            } else {
                // 다시 시간 수정 가능
                etStartTime.isEnabled = true
                etEndTime.isEnabled = true

                etStartTime.setText(
                    if (originalStartTime.isNotBlank()) originalStartTime else "09:00"
                )
                etEndTime.setText(
                    if (originalEndTime.isNotBlank()) originalEndTime else "10:00"
                )
                // 사용자가 다시 숫자를 바꾸면 아래 normalizeTimeToMillis 가 millis 재계산
            }
        }

        // 캘린더 서비스 생성
        calendarService = buildCalendarService()

        // 취소
        btnCancel.setOnClickListener { finish() }

        // 저장
        btnSave.setOnClickListener {
            val newTitle = editTitle.text.toString().ifBlank { "제목 없음" }
            val newDetail = editDetail.text.toString()

            if (!isAllDay) {
                // 하루 종일이 아닐 때만 사용자가 입력한 시간을 파싱
                val startText = etStartTime.text.toString().trim()
                val endText = etEndTime.text.toString().trim()

                if (startText.isNotEmpty()) {
                    normalizeTimeToMillis(startText, true)
                }
                if (endText.isNotEmpty()) {
                    normalizeTimeToMillis(endText, false)
                }
            }
            // isAllDay 인 경우에는 위에서 startMillis/endMillis 를 이미 00:00~다음날 00:00 으로 맞춰둔 상태

            updateEventOnCalendar(newTitle, newDetail)
        }
    }

    /** 사용자 입력(숫자들) -> startMillis/endMillis 갱신 + 입력칸 포맷 정리 */
    private fun normalizeTimeToMillis(raw: String, isStart: Boolean) {
        val digits = raw.filter { it.isDigit() }
        if (digits.isEmpty()) return

        val padded = digits.padStart(4, '0').take(4)
        val h = padded.substring(0, 2).toIntOrNull() ?: return
        val m = padded.substring(2, 4).toIntOrNull() ?: return

        val hour = h.coerceIn(0, 23)
        val minute = m.coerceIn(0, 59)

        val baseMillis = if (isStart) startMillis else endMillis
        val cal = Calendar.getInstance()

        if (baseMillis > 0) {
            cal.timeInMillis = baseMillis
        }

        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val newMillis = cal.timeInMillis
        val formatted = String.format("%02d:%02d", hour, minute)

        if (isStart) {
            startMillis = newMillis
            etStartTime.setText(formatted)
        } else {
            endMillis = newMillis
            etEndTime.setText(formatted)
        }
    }

    /** Google Calendar 서비스 생성 */
    private fun buildCalendarService(): com.google.api.services.calendar.Calendar? {
        val account = GoogleSignIn.getLastSignedInAccount(this) ?: return null

        val credential = GoogleAccountCredential.usingOAuth2(
            this,
            listOf(CalendarScopes.CALENDAR)
        )
        credential.selectedAccount = account.account

        return com.google.api.services.calendar.Calendar.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName(getString(R.string.app_name))
            .build()
    }

    /** 실제로 Google Calendar 이벤트 수정 */
    private fun updateEventOnCalendar(newTitle: String, newDetail: String) {
        val id = eventId
        val service = calendarService

        if (id == null || service == null) {
            Toast.makeText(this, "수정할 수 없는 일정입니다.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val event: Event = service.events().get("primary", id).execute()

                    event.summary = newTitle
                    event.description = newDetail

                    if (startMillis > 0 && endMillis > 0) {
                        val startDateTime = DateTime(startMillis)
                        val endDateTime = DateTime(endMillis)

                        if (event.start == null) event.start = EventDateTime()
                        if (event.end == null) event.end = EventDateTime()

                        event.start.dateTime = startDateTime
                        event.start.date = null
                        event.end.dateTime = endDateTime
                        event.end.date = null
                    }

                    service.events().update("primary", id, event).execute()
                }

                Toast.makeText(this@EditEventActivity, "일정이 수정되었습니다.", Toast.LENGTH_SHORT)
                    .show()

                val resultIntent = Intent().apply {
                    putExtra("title", newTitle)
                    putExtra("detail", newDetail)
                    putExtra("startMillis", startMillis)
                    putExtra("endMillis", endMillis)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@EditEventActivity,
                    "수정 중 오류가 발생했습니다.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}