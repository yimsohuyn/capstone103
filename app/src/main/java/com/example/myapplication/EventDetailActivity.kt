package com.example.myapplication

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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
import com.google.api.services.calendar.model.EventDateTime
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar as JavaCalendar

class EventDetailActivity : AppCompatActivity() {

    private var eventId: String? = null
    private var htmlLink: String? = null
    var startMillis: Long = -1L
    var endMillis: Long = -1L

    private var detail: String = ""

    private var isAlarmOn: Boolean = false
    private var alarmTime: String? = null

    private var isAssignment: Boolean = false
    private var assignmentId: Int = -1

    private var calendarService: Calendar? = null

    private lateinit var tvTitle: TextView
    private lateinit var tvStartDate: TextView
    private lateinit var tvEndDate: TextView
    private lateinit var tvStartTime: TextView
    private lateinit var tvEndTime: TextView
    private lateinit var tvDetail: TextView

    private lateinit var tvAlarmInfo: TextView
    private lateinit var btnAlarmToggle: ImageButton

    private val koreaTimeZone = java.util.TimeZone.getTimeZone("Asia/Seoul")

    private val dateFormatter = SimpleDateFormat("M월 d일 (E)", Locale.KOREAN).apply {
        timeZone = koreaTimeZone
    }

    private val timeFormatter = SimpleDateFormat("a h:mm", Locale.KOREAN).apply {
        timeZone = koreaTimeZone
    }

    override fun finish() {
        setResult(RESULT_OK)
        super.finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        setResult(RESULT_OK)
        finish()
        return true
    }

    private val editEventLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val data = result.data ?: return@registerForActivityResult

            val newTitle = data.getStringExtra("title") ?: tvTitle.text.toString()
            val newStart = data.getLongExtra("startMillis", startMillis)
            val newEnd = data.getLongExtra("endMillis", endMillis)
            val newDetail = data.getStringExtra("detail") ?: detail
            val newIsAlarmOn = data.getBooleanExtra("isAlarmOn", isAlarmOn)
            val newAlarmTime = data.getStringExtra("alarmTime")

            startMillis = newStart
            endMillis = newEnd
            detail = newDetail
            isAlarmOn = newIsAlarmOn
            if (newAlarmTime != null) alarmTime = newAlarmTime

            if (!isAssignment && !eventId.isNullOrBlank()) {
                updateGoogleEvent(
                    id = eventId!!,
                    title = newTitle,
                    detail = newDetail,
                    startMillis = newStart,
                    endMillis = newEnd
                )

                tvTitle.text = newTitle
                tvDetail.text = detail
                updateAlarmUI()

                if (startMillis > 0 && endMillis > 0) {
                    val start = Date(startMillis)
                    val end = Date(endMillis)
                    tvStartDate.text = dateFormatter.format(start)
                    tvEndDate.text = dateFormatter.format(end)
                    tvStartTime.text = timeFormatter.format(start)
                    tvEndTime.text = timeFormatter.format(end)
                }
            }

            if (isAssignment && assignmentId > 0) {
                lifecycleScope.launch {
                    try {
                        val updatedDisplayTitle = withContext(Dispatchers.IO) {
                            val dao = AppDatabase.getDatabase(this@EventDetailActivity).assignmentDao()
                            val assignment = dao.getById(assignmentId)
                                ?: throw IllegalStateException("과제를 찾을 수 없습니다.")

                            val cleanedTitle = normalizeAssignmentTitle(newTitle)

                            val updated = assignment.copy(
                                title = cleanedTitle,
                                startTime = formatTime(newStart),
                                endTime = formatTime(newEnd)
                            )

                            dao.update(updated)

                            if (!updated.googleEventId.isNullOrBlank()) {
                                updateAssignmentGoogleEvent(updated, newStart, newEnd)
                            }

                            buildAssignmentDisplayTitle(updated.type, updated.title)
                        }

                        tvTitle.text = updatedDisplayTitle
                        tvDetail.text = detail
                        updateAlarmUI()

                        if (startMillis > 0 && endMillis > 0) {
                            val start = Date(startMillis)
                            val end = Date(endMillis)
                            tvStartDate.text = dateFormatter.format(start)
                            tvEndDate.text = dateFormatter.format(end)
                            tvStartTime.text = timeFormatter.format(start)
                            tvEndTime.text = timeFormatter.format(end)
                        }

                        Toast.makeText(
                            this@EventDetailActivity,
                            "과제 시간이 수정되었습니다.",
                            Toast.LENGTH_SHORT
                        ).show()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(
                            this@EventDetailActivity,
                            "과제 수정 중 오류가 발생했습니다.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_detail)

        val titleFromIntent = intent.getStringExtra("title") ?: "제목 없음"
        eventId = intent.getStringExtra("eventId")
        htmlLink = intent.getStringExtra("htmlLink")
        startMillis = intent.getLongExtra("startMillis", -1L)
        endMillis = intent.getLongExtra("endMillis", -1L)
        detail = intent.getStringExtra("detail") ?: ""

        isAlarmOn = intent.getBooleanExtra("isAlarmOn", false)
        alarmTime = intent.getStringExtra("alarmTime")

        isAssignment = intent.getBooleanExtra("isAssignment", false)
        assignmentId = intent.getIntExtra("assignmentId", -1)

        tvTitle = findViewById(R.id.tvTitle)
        tvStartDate = findViewById(R.id.tvStartDate)
        tvEndDate = findViewById(R.id.tvEndDate)
        tvStartTime = findViewById(R.id.tvStartTime)
        tvEndTime = findViewById(R.id.tvEndTime)
        tvDetail = findViewById(R.id.tvDetail)

        tvAlarmInfo = findViewById(R.id.tvAlarmInfo)
        btnAlarmToggle = findViewById(R.id.btnAlarmToggle)

        tvTitle.text = titleFromIntent
        tvDetail.text = detail
        updateAlarmUI()

        if (startMillis > 0 && endMillis > 0) {
            val start = Date(startMillis)
            val end = Date(endMillis)
            tvStartDate.text = dateFormatter.format(start)
            tvEndDate.text = dateFormatter.format(end)
            tvStartTime.text = timeFormatter.format(start)
            tvEndTime.text = timeFormatter.format(end)
        }

        btnAlarmToggle.setOnClickListener { toggleAlarm() }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            setResult(RESULT_OK)
            finish()
        }

        calendarService = if (isAssignment) null else buildCalendarService()

        findViewById<LinearLayout>(R.id.btnCopy).setOnClickListener {
            val copyText =
                if (detail.isNotBlank()) "${tvTitle.text}\n$detail" else tvTitle.text.toString()
            copyToClipboard(copyText)
        }

        findViewById<LinearLayout>(R.id.btnEdit).setOnClickListener {
            val intent = Intent(this, EditEventActivity::class.java).apply {
                putExtra("eventId", eventId)
                putExtra("title", tvTitle.text.toString())
                putExtra("startMillis", startMillis)
                putExtra("endMillis", endMillis)
                putExtra("detail", detail)
                putExtra("isAlarmOn", isAlarmOn)
                putExtra("alarmTime", alarmTime)
                putExtra("isAssignment", isAssignment)
                putExtra("assignmentId", assignmentId)
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

    private fun toggleAlarm() {
        if (!isAlarmOn) {
            if (alarmTime.isNullOrEmpty()) {
                Toast.makeText(
                    this,
                    "알림 시간이 설정되지 않아 켤 수 없습니다. 편집 페이지에서 시간을 설정해주세요.",
                    Toast.LENGTH_LONG
                ).show()
                return
            } else {
                isAlarmOn = true
                Toast.makeText(this, "알림이 켜졌습니다.", Toast.LENGTH_SHORT).show()
            }
        } else {
            isAlarmOn = false
            Toast.makeText(this, "알림이 꺼졌습니다.", Toast.LENGTH_SHORT).show()
        }
        updateAlarmUI()
    }

    private fun updateAlarmUI() {
        if (isAlarmOn) {
            val formattedTime = formatAlarmTime(alarmTime)
            tvAlarmInfo.text = "알림 켜짐 ($formattedTime)"
            tvAlarmInfo.setTextColor(Color.RED)

            btnAlarmToggle.setImageResource(R.drawable.outline_alarm_on_24)
            btnAlarmToggle.imageTintList = ColorStateList.valueOf(Color.RED)
        } else {
            tvAlarmInfo.text = "알림 꺼짐"
            tvAlarmInfo.setTextColor(Color.GRAY)

            btnAlarmToggle.setImageResource(R.drawable.outline_alarm_off_24)
            btnAlarmToggle.imageTintList = ColorStateList.valueOf(Color.GRAY)
        }
    }

    private fun formatAlarmTime(rawTime: String?): String {
        if (rawTime == null || rawTime.length != 4) return "시간 미설정"
        return try {
            val h = rawTime.substring(0, 2).toInt()
            val m = rawTime.substring(2, 4).toInt()
            val cal = JavaCalendar.getInstance(koreaTimeZone).apply {
                set(JavaCalendar.HOUR_OF_DAY, h)
                set(JavaCalendar.MINUTE, m)
            }
            timeFormatter.format(cal.time)
        } catch (e: Exception) {
            rawTime ?: ""
        }
    }

    private fun formatTime(millis: Long): String {
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
            timeZone = koreaTimeZone
        }
        return formatter.format(Date(millis))
    }

    private fun buildAssignmentDisplayTitle(type: String, title: String): String {
        val label = if (type == "팀 프로젝트") "[팀 과제]" else "[개인 과제]"
        return "$label $title"
    }

    private fun normalizeAssignmentTitle(input: String): String {
        return input
            .removePrefix("[팀 과제] ")
            .removePrefix("[개인 과제] ")
            .trim()
    }

    private fun buildCalendarService(): Calendar? {
        val account = GoogleSignIn.getLastSignedInAccount(this) ?: return null
        val credential = GoogleAccountCredential.usingOAuth2(this, listOf(CalendarScopes.CALENDAR))
        credential.selectedAccount = account.account
        return Calendar.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName(getString(R.string.app_name))
            .build()
    }

    private fun updateGoogleEvent(
        id: String,
        title: String,
        detail: String,
        startMillis: Long,
        endMillis: Long
    ) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val event = calendarService?.events()?.get("primary", id)?.execute()
                        ?: throw IllegalStateException("이벤트를 찾을 수 없습니다.")

                    event.summary = title
                    event.description = detail
                    event.start = EventDateTime()
                        .setDateTime(DateTime(startMillis))
                        .setTimeZone("Asia/Seoul")

                    event.end = EventDateTime()
                        .setDateTime(DateTime(endMillis))
                        .setTimeZone("Asia/Seoul")

                    calendarService?.events()
                        ?.update("primary", id, event)
                        ?.execute()
                }

                Toast.makeText(this@EventDetailActivity, "일정이 수정되었습니다.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@EventDetailActivity,
                    "일정 수정 중 오류가 발생했습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun updateAssignmentGoogleEvent(
        assignment: AssignmentEntity,
        startMillis: Long,
        endMillis: Long
    ) {
        val service = buildCalendarService() ?: return
        val googleId = assignment.googleEventId ?: return

        val event = service.events().get("primary", googleId).execute()
        event.summary = buildAssignmentDisplayTitle(assignment.type, assignment.title)
        event.start = EventDateTime()
            .setDateTime(DateTime(startMillis))
            .setTimeZone("Asia/Seoul")
        event.end = EventDateTime()
            .setDateTime(DateTime(endMillis))
            .setTimeZone("Asia/Seoul")

        service.events()
            .update("primary", googleId, event)
            .execute()
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
            builder.appendLine(
                "${dateFormatter.format(start)} ${timeFormatter.format(start)} - " +
                        "${dateFormatter.format(end)} ${timeFormatter.format(end)}"
            )
        }
        if (detail.isNotBlank()) {
            builder.appendLine().appendLine(detail)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, builder.toString())
        }
        startActivity(Intent.createChooser(intent, "일정 공유"))
    }

    private fun confirmAndDelete() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_member, null)

        val textDeleteTitle = dialogView.findViewById<TextView>(R.id.textDeleteTitle)
        val textDeleteMessage = dialogView.findViewById<TextView>(R.id.textDeleteMessage)
        val btnCancelDelete = dialogView.findViewById<TextView>(R.id.btnCancelDelete)
        val btnConfirmDelete = dialogView.findViewById<TextView>(R.id.btnConfirmDelete)

        textDeleteTitle.text = if (isAssignment) "과제 삭제" else "일정 삭제"
        textDeleteMessage.text = "이 항목을 정말 삭제할까요?"

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancelDelete.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirmDelete.setOnClickListener {
            dialog.dismiss()

            if (isAssignment) {
                deleteAssignment()
            } else {
                val id = eventId ?: return@setOnClickListener
                deleteEvent(id)
            }
        }
    }

    private fun deleteAssignment() {
        if (assignmentId <= 0) {
            Toast.makeText(this, "과제 ID를 찾지 못했습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val dao = AppDatabase.getDatabase(this@EventDetailActivity).assignmentDao()

            try {
                withContext(Dispatchers.IO) {
                    val assignment = dao.getById(assignmentId)
                        ?: throw IllegalStateException("과제를 찾을 수 없습니다.")

                    if (!assignment.googleEventId.isNullOrBlank()) {
                        try {
                            val service = buildCalendarService()
                            service?.events()
                                ?.delete("primary", assignment.googleEventId)
                                ?.execute()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    if (
                        assignment.type == "팀 프로젝트" &&
                        !assignment.teamId.isNullOrBlank() &&
                        !assignment.remoteId.isNullOrBlank()
                    ) {
                        try {
                            val db = FirebaseFirestore.getInstance()
                            val assignmentRef = db.collection("teams")
                                .document(assignment.teamId!!)
                                .collection("assignments")
                                .document(assignment.remoteId!!)

                            val meetingsSnapshot = assignmentRef.collection("meetings").get().await()
                            for (doc in meetingsSnapshot.documents) {
                                try {
                                    doc.reference.delete().await()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }

                            val filesSnapshot = assignmentRef.collection("files").get().await()
                            for (doc in filesSnapshot.documents) {
                                val fileUrl = doc.getString("fileUri")

                                try {
                                    if (!fileUrl.isNullOrBlank()) {
                                        FirebaseStorage.getInstance()
                                            .getReferenceFromUrl(fileUrl)
                                            .delete()
                                            .await()
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }

                                try {
                                    doc.reference.delete().await()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }

                            val membersSnapshot = assignmentRef.collection("members").get().await()
                            for (doc in membersSnapshot.documents) {
                                try {
                                    doc.reference.delete().await()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }

                            try {
                                assignmentRef.delete().await()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    dao.deleteById(assignmentId)
                }

                Toast.makeText(this@EventDetailActivity, "삭제되었습니다.", Toast.LENGTH_SHORT).show()
                finish()

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@EventDetailActivity, "오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
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