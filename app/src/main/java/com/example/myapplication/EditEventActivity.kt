package com.example.myapplication

import android.app.Activity            // ✅ 추가
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
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
import android.content.Intent

class EditEventActivity : AppCompatActivity() {

    // 넘겨받은 값들
    private var eventId: String? = null
    private var startMillis: Long = -1L
    private var endMillis: Long = -1L

    // 구글 캘린더 서비스
    private var calendarService: Calendar? = null

    // UI
    private lateinit var editTitle: EditText
    private lateinit var btnCancel: Button
    private lateinit var btnSave: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_event)

        // ─── Intent 데이터 받기 ───
        eventId = intent.getStringExtra("eventId")
        val oldTitle = intent.getStringExtra("title") ?: "제목 없음"
        startMillis = intent.getLongExtra("startMillis", -1L)
        endMillis = intent.getLongExtra("endMillis", -1L)

        // ─── UI 바인딩 ───
        editTitle = findViewById(R.id.editTitle)
        btnCancel = findViewById(R.id.btnCancel)
        btnSave = findViewById(R.id.btnSave)

        // 상단 뒤로가기 버튼
        findViewById<ImageButton?>(R.id.btnBack)?.setOnClickListener {
            finish()
        }

        editTitle.setText(oldTitle)

        // 캘린더 서비스 준비
        calendarService = buildCalendarService()

        // 취소
        btnCancel.setOnClickListener { finish() }

        // ✅ 저장: 구글 캘린더에 일정 수정 반영
        btnSave.setOnClickListener {
            val newTitle = editTitle.text.toString().ifBlank { "제목 없음" }
            updateEventOnCalendar(newTitle)
        }
    }

    /** Google Calendar 서비스 객체 생성 */
    private fun buildCalendarService(): Calendar? {
        val account = GoogleSignIn.getLastSignedInAccount(this) ?: return null

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

    /** 실제로 Google Calendar 이벤트 수정 */
    private fun updateEventOnCalendar(newTitle: String) {
        val id = eventId
        val service = calendarService

        if (id == null || service == null) {
            Toast.makeText(this, "수정할 수 없는 일정입니다.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // 1) 기존 이벤트 가져오기
                    val event: Event = service.events().get("primary", id).execute()

                    // 2) 제목 수정
                    event.summary = newTitle

                    // 3) 시간 정보도 기존 것을 유지 (startMillis/endMillis가 있으면 덮어쓰기)
                    if (startMillis > 0 && endMillis > 0) {
                        val startDateTime = DateTime(startMillis)
                        val endDateTime = DateTime(endMillis)

                        if (event.start == null) event.start = EventDateTime()
                        if (event.end == null) event.end = EventDateTime()

                        event.start.dateTime = startDateTime
                        event.start.date = null      // 종일 이벤트 방지
                        event.end.dateTime = endDateTime
                        event.end.date = null
                    }

                    // 4) 구글 캘린더에 업데이트
                    service.events().update("primary", id, event).execute()
                }

                Toast.makeText(this@EditEventActivity, "일정이 수정되었습니다.", Toast.LENGTH_SHORT)
                    .show()

                // ✅ 수정된 값들을 이전 화면(EventDetailActivity)에 돌려주기
                val resultIntent = Intent().apply {
                    putExtra("title", newTitle)
                    putExtra("startMillis", startMillis)
                    putExtra("endMillis", endMillis)
                }
                setResult(Activity.RESULT_OK, resultIntent)   // ✅ 결과 설정
                finish()                                      // ✅ 화면 닫기

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
