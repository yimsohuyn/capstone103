package com.example.myapplication

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.AssignmentEntity
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
import java.util.*

class AssignmentRegisterFragment : Fragment() {

    private var attachedFileUri: String? = null
    private val koreaTimeZone = TimeZone.getTimeZone("Asia/Seoul")

    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                attachedFileUri = uri.toString()
                Toast.makeText(requireContext(), "파일 첨부 완료", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_assignment_register, container, false)

        val backBtn = view.findViewById<ImageButton>(R.id.btnBack)
        val editTitle = view.findViewById<EditText>(R.id.editTitle)

        val dueDateLayout = view.findViewById<LinearLayout>(R.id.dueDateLayout)
        val textDueDate = view.findViewById<TextView>(R.id.textDueDate)

        val assigneeLayout = view.findViewById<LinearLayout>(R.id.assigneeLayout)
        val textAssignee = view.findViewById<TextView>(R.id.textAssignee)

        val attachLayout = view.findViewById<LinearLayout>(R.id.attachLayout)
        val spinnerType = view.findViewById<Spinner>(R.id.spinnerType)
        val submitBtn = view.findViewById<Button>(R.id.btnSubmit)

        backBtn.setOnClickListener {
            findNavController().navigateUp()
        }

        dueDateLayout.setOnClickListener {
            val cal = java.util.Calendar.getInstance(koreaTimeZone)
            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    textDueDate.text = "%04d-%02d-%02d".format(year, month + 1, day)
                },
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH),
                cal.get(java.util.Calendar.DAY_OF_MONTH)
            ).show()
        }

        assigneeLayout.setOnClickListener {
            val input = EditText(requireContext())
            input.hint = "담당자 이름 입력"

            AlertDialog.Builder(requireContext())
                .setTitle("담당자 입력")
                .setView(input)
                .setPositiveButton("확인") { _, _ ->
                    val name = input.text.toString().trim()
                    textAssignee.text = if (name.isNotEmpty()) name else "담당자 선택"
                }
                .setNegativeButton("취소", null)
                .show()
        }

        attachLayout.setOnClickListener {
            filePickerLauncher.launch("*/*")
        }

        val typeItems = listOf("개인 프로젝트", "팀 프로젝트")
        val adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, typeItems)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerType.adapter = adapter

        submitBtn.setOnClickListener {

            submitBtn.isEnabled = false

            val title = editTitle.text.toString().trim()
            val type = spinnerType.selectedItem.toString()
            val dueDate = textDueDate.text.toString()
            val assignee = textAssignee.text.toString()

            if (title.isEmpty()) {
                Toast.makeText(requireContext(), "제목을 입력하세요!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (dueDate.isEmpty() || dueDate == "마감일 선택") {
                Toast.makeText(requireContext(), "마감일을 선택하세요!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val assignment = AssignmentEntity(
                title = title,
                type = type,
                dueDate = dueDate,
                assignee = if (assignee == "담당자 선택") null else assignee,
                fileUri = attachedFileUri,
                startTime = "09:00",
                endTime = "10:00",
                googleEventId = null
            )

            val dao = AppDatabase.getDatabase(requireContext()).assignmentDao()

            lifecycleScope.launch(Dispatchers.IO) {
                // 1) 먼저 DB 저장
                val insertedId = dao.insert(assignment).toInt()

                // 2) 방금 저장된 과제 다시 읽기
                val insertedAssignment = dao.getById(insertedId)

                // 3) 구글 캘린더 동기화 후 eventId 받기
                val googleEventId = if (insertedAssignment != null) {
                    syncAssignmentToGoogleCalendar(insertedAssignment)
                } else {
                    null
                }

                // 4) googleEventId를 DB에 다시 저장
                if (insertedAssignment != null && googleEventId != null) {
                    val updatedAssignment = insertedAssignment.copy(googleEventId = googleEventId)
                    dao.update(updatedAssignment)
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "과제가 저장되었습니다!", Toast.LENGTH_SHORT).show()

                    if (type == "팀 프로젝트") {
                        findNavController().navigate(R.id.teamProjectFragment)
                    } else {
                        findNavController().navigateUp()
                    }
                }
            }
        }

        return view
    }

    // -------------------- 구글 캘린더 자동 동기화 --------------------

    private fun buildCalendarService(): Calendar? {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext()) ?: return null
        val credential = GoogleAccountCredential.usingOAuth2(
            requireContext(),
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

    private fun parseTime(time: String?): Pair<Int, Int> {
        val safe = time ?: "09:00"
        val parts = safe.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 9
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return h to m
    }

    private fun buildAssignmentCalendarTitle(a: AssignmentEntity): String {
        val label = if (a.type == "팀 프로젝트") "[팀 과제]" else "[개인 과제]"
        return "$label ${a.title}"
    }

    private fun syncAssignmentToGoogleCalendar(a: AssignmentEntity): String? {
        return try {
            val service = buildCalendarService() ?: return null

            val (sh, sm) = parseTime(a.startTime)
            val (eh, em) = parseTime(a.endTime)

            val startCal = java.util.Calendar.getInstance(koreaTimeZone).apply {
                val parts = a.dueDate.split("-")
                val year = parts.getOrNull(0)?.toIntOrNull() ?: return null
                val month = (parts.getOrNull(1)?.toIntOrNull() ?: return null) - 1
                val day = parts.getOrNull(2)?.toIntOrNull() ?: return null

                set(year, month, day, sh, sm, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }

            val endCal = java.util.Calendar.getInstance(koreaTimeZone).apply {
                val parts = a.dueDate.split("-")
                val year = parts.getOrNull(0)?.toIntOrNull() ?: return null
                val month = (parts.getOrNull(1)?.toIntOrNull() ?: return null) - 1
                val day = parts.getOrNull(2)?.toIntOrNull() ?: return null

                set(year, month, day, eh, em, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }

            val description = buildString {
                append("과제 유형: ${a.type}")
                if (!a.assignee.isNullOrBlank()) {
                    append("\n담당자: ${a.assignee}")
                }
                if (!a.fileUri.isNullOrBlank()) {
                    append("\n첨부파일 있음")
                }
            }

            val event = Event().apply {
                summary = buildAssignmentCalendarTitle(a)
                this.description = description

                start = EventDateTime()
                    .setDateTime(DateTime(startCal.timeInMillis))
                    .setTimeZone("Asia/Seoul")

                end = EventDateTime()
                    .setDateTime(DateTime(endCal.timeInMillis))
                    .setTimeZone("Asia/Seoul")
            }

            val insertedEvent = service.events()
                .insert("primary", event)
                .execute()

            insertedEvent.id
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}