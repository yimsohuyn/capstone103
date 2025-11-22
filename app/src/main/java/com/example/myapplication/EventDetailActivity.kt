package com.example.myapplication

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar as JavaCalendar
import java.util.Date
import java.util.Locale
import kotlin.collections.forEach
import kotlin.collections.orEmpty

class EventDetailActivity : AppCompatActivity() {

    // ─── 넘어온 값들 ───
    private var eventId: String? = null
    private var htmlLink: String? = null
    var startMillis: Long = -1L
    var endMillis: Long = -1L
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

    private val dateFormatter = SimpleDateFormat("M월 d일 (E)", Locale.KOREAN)
    private val timeFormatter = SimpleDateFormat("a h:mm", Locale.KOREAN)
    override fun finish() {
        setResult(RESULT_OK)
        super.finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        setResult(RESULT_OK)
        finish()
        return true
    }

    // ✅ 편집 화면에서 돌아올 때 결과 받는 런처
    private val editEventLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val data = result.data ?: return@registerForActivityResult

            val newTitle = data.getStringExtra("title") ?: tvTitle.text.toString()
            val newStart = data.getLongExtra("startMillis", startMillis)
            val newEnd = data.getLongExtra("endMillis", endMillis)

            // 내부 값 갱신
            startMillis = newStart
            endMillis = newEnd
            tvTitle.text = newTitle

            if (startMillis > 0 && endMillis > 0) {
                val start = Date(startMillis)
                val end = Date(endMillis)
                tvStartDate.text = dateFormatter.format(start)
                tvEndDate.text = dateFormatter.format(end)
                tvStartTime.text = timeFormatter.format(start)
                tvEndTime.text = timeFormatter.format(end)
            } else {
                tvStartDate.text = ""
                tvEndDate.text = ""
                tvStartTime.text = ""
                tvEndTime.text = ""
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

        // ─── UI 바인딩 ───
        tvTitle = findViewById(R.id.tvTitle)
        tvStartDate = findViewById(R.id.tvStartDate)
        tvEndDate = findViewById(R.id.tvEndDate)
        tvStartTime = findViewById(R.id.tvStartTime)
        tvEndTime = findViewById(R.id.tvEndTime)

        tvTitle.text = titleFromIntent

        if (startMillis > 0 && endMillis > 0) {
            val start = Date(startMillis)
            val end = Date(endMillis)
            tvStartDate.text = dateFormatter.format(start)
            tvEndDate.text = dateFormatter.format(end)
            tvStartTime.text = timeFormatter.format(start)
            tvEndTime.text = timeFormatter.format(end)
        } else {
            tvStartDate.text = ""
            tvEndDate.text = ""
            tvStartTime.text = ""
            tvEndTime.text = ""
        }

        // 뒤로가기
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            setResult(RESULT_OK)
            finish()
        }


        // 캘린더 서비스 (삭제용)
        calendarService = buildCalendarService()

        // ─── 하단 버튼 동작 ───
        findViewById<LinearLayout>(R.id.btnCopy).setOnClickListener {
            copyToClipboard(tvTitle.text.toString())
        }

        // ✅ 편집 버튼: EditEventActivity 로 이동 (결과 받기)
        findViewById<LinearLayout>(R.id.btnEdit).setOnClickListener {
            val intent = Intent(this, EditEventActivity::class.java).apply {
                putExtra("eventId", eventId)
                putExtra("title", tvTitle.text.toString())
                putExtra("startMillis", startMillis)
                putExtra("endMillis", endMillis)
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

    // ───────────────── 캘린더 서비스 생성 (삭제용) ─────────────────
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

    // ───────────────── 복사/공유/삭제 ─────────────────
    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("event", text))
        Toast.makeText(this, "제목이 복사되었습니다.", Toast.LENGTH_SHORT).show()
    }

    private fun shareEvent() {
        val builder = StringBuilder()
        builder.appendLine(tvTitle.text.toString())

        if (startMillis > 0 && endMillis > 0) {
            val start = Date(startMillis)
            val end = Date(endMillis)
            builder.appendLine(
                "${dateFormatter.format(start)} ${timeFormatter.format(start)} - " +
                        "${dateFormatter.format(end)} ${timeFormatter.format(end)}"
            )
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, builder.toString())
        }
        startActivity(Intent.createChooser(intent, "일정 공유"))
    }

    private fun confirmAndDelete() {
        val id = eventId
        val service = calendarService
        if (id == null || service == null) {
            Toast.makeText(this, "삭제할 수 없는 일정입니다.", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("일정 삭제")
            .setMessage("이 일정을 정말 삭제할까요?")
            .setPositiveButton("삭제") { _, _ ->
                deleteEvent(id)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun deleteEvent(id: String) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    calendarService?.events()
                        ?.delete("primary", id)
                        ?.execute()
                }
                Toast.makeText(this@EventDetailActivity, "삭제되었습니다.", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@EventDetailActivity,
                    "삭제 중 오류가 발생했습니다.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing) {
            setResult(RESULT_OK)
        }
    }
}