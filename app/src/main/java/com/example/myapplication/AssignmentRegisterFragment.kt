package com.example.myapplication

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.team.TeamManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.TimeZone

class AssignmentRegisterFragment : Fragment() {

    private var attachedFileUri: String? = null
    private val koreaTimeZone = TimeZone.getTimeZone("Asia/Seoul")

    private lateinit var rvAssignments: RecyclerView
    private lateinit var tvEmptyAssignments: TextView
    private lateinit var btnViewAllAssignments: TextView
    private lateinit var textAttachFile: TextView
    private val assignmentList = mutableListOf<AssignmentEntity>()

    private fun isDarkMode(): Boolean {
        val currentNightMode =
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun dialogButtonColor(): Int {
        return if (isDarkMode()) Color.WHITE else Color.BLACK
    }

    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                attachedFileUri = uri.toString()
                textAttachFile.text = "파일 첨부 완료"
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
        val editDescription = view.findViewById<EditText>(R.id.editDescription)

        val dueDateLayout = view.findViewById<LinearLayout>(R.id.dueDateLayout)
        val textDueDate = view.findViewById<TextView>(R.id.textDueDate)

        val assigneeLayout = view.findViewById<LinearLayout>(R.id.assigneeLayout)
        val textAssignee = view.findViewById<TextView>(R.id.textAssignee)

        val attachLayout = view.findViewById<LinearLayout>(R.id.attachLayout)
        textAttachFile = view.findViewById(R.id.textAttachFile)

        val spinnerType = view.findViewById<Spinner>(R.id.spinnerType)
        val submitBtn = view.findViewById<Button>(R.id.btnSubmit)

        rvAssignments = view.findViewById(R.id.rvAssignments)
        tvEmptyAssignments = view.findViewById(R.id.tvEmptyAssignments)
        btnViewAllAssignments = view.findViewById(R.id.btnViewAllAssignments)

        rvAssignments.layoutManager = LinearLayoutManager(requireContext())

        backBtn.setOnClickListener {
            findNavController().navigateUp()
        }

        dueDateLayout.setOnClickListener {
            val cal = java.util.Calendar.getInstance(koreaTimeZone)

            val dialog = DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    textDueDate.text = "%04d-%02d-%02d".format(year, month + 1, day)
                },
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH),
                cal.get(java.util.Calendar.DAY_OF_MONTH)
            )

            dialog.setOnShowListener {
                val buttonColor = if (isDarkMode()) Color.WHITE else Color.BLACK
                dialog.getButton(DatePickerDialog.BUTTON_POSITIVE)?.setTextColor(buttonColor)
                dialog.getButton(DatePickerDialog.BUTTON_NEGATIVE)?.setTextColor(buttonColor)
            }

            dialog.show()
        }

        assigneeLayout.setOnClickListener {
            val dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_assignee_input, null)

            val editAssignee = dialogView.findViewById<EditText>(R.id.editAssigneeName)

            val dialog = AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setPositiveButton("확인", null)
                .setNegativeButton("취소", null)
                .create()

            dialog.show()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.WHITE)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.WHITE)

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                val name = editAssignee.text.toString().trim()
                textAssignee.text = if (name.isNotEmpty()) name else "담당자 선택"
                dialog.dismiss()
            }
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
            val typedAssignee = textAssignee.text.toString()

            if (title.isEmpty()) {
                Toast.makeText(requireContext(), "제목을 입력하세요!", Toast.LENGTH_SHORT).show()
                submitBtn.isEnabled = true
                return@setOnClickListener
            }

            if (dueDate.isEmpty() || dueDate == "마감일 선택") {
                Toast.makeText(requireContext(), "마감일을 선택하세요!", Toast.LENGTH_SHORT).show()
                submitBtn.isEnabled = true
                return@setOnClickListener
            }

            val account = GoogleSignIn.getLastSignedInAccount(requireContext())
            val currentUserName = account?.displayName?.trim()
            val currentUserEmail = account?.email?.trim()?.lowercase(Locale.ROOT)
            val currentUserUid = account?.id?.trim()

            val finalAssigneeName =
                if (type == "팀 프로젝트") {
                    currentUserName ?: if (typedAssignee == "담당자 선택") null else typedAssignee
                } else {
                    if (typedAssignee == "담당자 선택") null else typedAssignee
                }

            val finalAssigneeEmail =
                if (type == "팀 프로젝트") currentUserEmail else null

            if (type == "팀 프로젝트" && finalAssigneeEmail.isNullOrBlank()) {
                Toast.makeText(
                    requireContext(),
                    "팀 프로젝트는 구글 로그인 후 등록해야 담당자 권한을 사용할 수 있습니다.",
                    Toast.LENGTH_LONG
                ).show()
                submitBtn.isEnabled = true
                return@setOnClickListener
            }

            val currentTeamId =
                if (type == "팀 프로젝트") TeamManager.teamId else null

            val assignment = AssignmentEntity(
                title = title,
                type = type,
                dueDate = dueDate,
                assignee = finalAssigneeName,
                assigneeEmail = finalAssigneeEmail,
                fileUri = attachedFileUri,
                startTime = "09:00",
                endTime = "10:00",
                googleEventId = null,
                remoteId = null,
                teamId = currentTeamId
            )

            val dao = AppDatabase.getDatabase(requireContext()).assignmentDao()

            lifecycleScope.launch(Dispatchers.IO) {
                val insertedId = dao.insert(assignment).toInt()
                var insertedAssignment = dao.getById(insertedId)

                val googleEventId = if (insertedAssignment != null) {
                    syncAssignmentToGoogleCalendar(insertedAssignment)
                } else {
                    null
                }

                if (insertedAssignment != null && googleEventId != null) {
                    insertedAssignment = insertedAssignment.copy(googleEventId = googleEventId)
                    dao.update(insertedAssignment)
                }

                if (
                    type == "팀 프로젝트" &&
                    insertedAssignment != null &&
                    !currentTeamId.isNullOrBlank()
                ) {
                    val remoteId = saveTeamAssignmentToFirestore(
                        teamId = currentTeamId,
                        assignment = insertedAssignment,
                        currentUserUid = currentUserUid,
                        currentUserName = currentUserName,
                        currentUserEmail = currentUserEmail
                    )

                    if (!remoteId.isNullOrBlank()) {
                        val updated = insertedAssignment.copy(remoteId = remoteId)
                        dao.update(updated)
                        insertedAssignment = updated
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "과제가 저장되었습니다!", Toast.LENGTH_SHORT).show()

                    loadAssignmentList()

                    clearInputFields(
                        editTitle = editTitle,
                        editDescription = editDescription,
                        textDueDate = textDueDate,
                        textAssignee = textAssignee,
                        spinnerType = spinnerType
                    )

                    if (type == "팀 프로젝트") {
                        val bundle = Bundle().apply {
                            putInt("assignmentId", insertedId)
                        }
                        findNavController().navigate(R.id.teamProjectFragment, bundle)
                    } else {
                        findNavController().navigateUp()
                    }

                    submitBtn.isEnabled = true
                }
            }
        }

        loadAssignmentList()

        return view
    }

    private fun clearInputFields(
        editTitle: EditText,
        editDescription: EditText,
        textDueDate: TextView,
        textAssignee: TextView,
        spinnerType: Spinner
    ) {
        editTitle.text.clear()
        editDescription.text.clear()
        textDueDate.text = "마감일 선택"
        textAssignee.text = "담당자 선택"
        textAttachFile.text = "파일 첨부"
        spinnerType.setSelection(0)
        attachedFileUri = null
    }

    private fun loadAssignmentList() {
        val dao = AppDatabase.getDatabase(requireContext()).assignmentDao()

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                syncTeamAssignmentsFromFirestore(dao)
            }

            val assignments = withContext(Dispatchers.IO) {
                dao.getAll()
            }

            assignmentList.clear()
            assignmentList.addAll(assignments)

            if (assignmentList.isEmpty()) {
                tvEmptyAssignments.visibility = View.VISIBLE
                rvAssignments.visibility = View.GONE
                btnViewAllAssignments.visibility = View.GONE
            } else {
                val recentAssignments = assignmentList.take(3)

                tvEmptyAssignments.visibility = View.GONE
                rvAssignments.visibility = View.VISIBLE
                btnViewAllAssignments.visibility =
                    if (assignmentList.size > 3) View.VISIBLE else View.GONE

                rvAssignments.adapter = AssignmentListAdapter(recentAssignments) { item ->
                    openAssignment(item)
                }

                btnViewAllAssignments.setOnClickListener {
                    showAllAssignmentsDialog()
                }
            }
        }
    }

    private fun showAllAssignmentsDialog() {
        if (assignmentList.isEmpty()) return

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_all_assignments, null)

        val rvDialogAssignments =
            dialogView.findViewById<RecyclerView>(R.id.rvDialogAssignments)
        val btnCloseDialog =
            dialogView.findViewById<Button>(R.id.btnCloseDialog)

        rvDialogAssignments.layoutManager = LinearLayoutManager(requireContext())

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        rvDialogAssignments.adapter = AssignmentListAdapter(assignmentList) { item ->
            dialog.dismiss()
            openAssignment(item)
        }

        btnCloseDialog.setOnClickListener {
            dialog.dismiss()
        }
    }

    private fun openAssignment(item: AssignmentEntity) {
        if (item.type == "팀 프로젝트") {
            val bundle = Bundle().apply {
                putInt("assignmentId", item.id)
            }
            findNavController().navigate(R.id.teamProjectFragment, bundle)
        } else {
            Toast.makeText(
                requireContext(),
                "개인 프로젝트는 목록에서만 확인할 수 있습니다.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

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

    private suspend fun saveTeamAssignmentToFirestore(
        teamId: String,
        assignment: AssignmentEntity,
        currentUserUid: String?,
        currentUserName: String?,
        currentUserEmail: String?
    ): String? {
        return try {
            val db = FirebaseFirestore.getInstance()
            val docRef = db.collection("teams")
                .document(teamId)
                .collection("assignments")
                .document()

            val data = hashMapOf(
                "title" to assignment.title,
                "type" to assignment.type,
                "dueDate" to assignment.dueDate,
                "assignee" to assignment.assignee,
                "assigneeEmail" to assignment.assigneeEmail,
                "fileUri" to assignment.fileUri,
                "startTime" to assignment.startTime,
                "endTime" to assignment.endTime,
                "googleEventId" to assignment.googleEventId,
                "teamId" to teamId,
                "createdAt" to FieldValue.serverTimestamp()
            )

            docRef.set(data).await()

            val memberUid = currentUserUid ?: currentUserEmail ?: "owner"
            val memberData = hashMapOf(
                "name" to (currentUserName ?: assignment.assignee ?: "이름없음"),
                "email" to (currentUserEmail ?: assignment.assigneeEmail ?: ""),
                "uid" to (currentUserUid ?: ""),
                "joinedAt" to FieldValue.serverTimestamp()
            )

            docRef.collection("members")
                .document(memberUid)
                .set(memberData)
                .await()

            docRef.id
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun syncTeamAssignmentsFromFirestore(
        dao: AssignmentDao
    ) {
        val currentTeamId = TeamManager.teamId
        if (currentTeamId.isBlank()) return

        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        val currentUserUid = account?.id?.trim()
        val currentUserEmail = account?.email?.trim()?.lowercase(Locale.ROOT)

        if (currentUserUid.isNullOrBlank() && currentUserEmail.isNullOrBlank()) return

        try {
            val snapshot = FirebaseFirestore.getInstance()
                .collection("teams")
                .document(currentTeamId)
                .collection("assignments")
                .get()
                .await()

            snapshot.documents.forEach { doc ->
                val remoteId = doc.id
                val existing = dao.getByRemoteId(remoteId)

                val membersSnapshot = doc.reference
                    .collection("members")
                    .get()
                    .await()

                val isMyProject = membersSnapshot.documents.any { memberDoc ->
                    val memberUid = memberDoc.getString("uid")?.trim()
                    val memberEmail = memberDoc.getString("email")?.trim()?.lowercase(Locale.ROOT)

                    (!currentUserUid.isNullOrBlank() && memberUid == currentUserUid) ||
                            (!currentUserEmail.isNullOrBlank() && memberEmail == currentUserEmail)
                }

                if (!isMyProject) {
                    return@forEach
                }

                if (existing == null) {
                    val entity = AssignmentEntity(
                        title = doc.getString("title") ?: "제목 없음",
                        type = doc.getString("type") ?: "팀 프로젝트",
                        dueDate = doc.getString("dueDate") ?: "",
                        assignee = doc.getString("assignee"),
                        assigneeEmail = doc.getString("assigneeEmail"),
                        fileUri = doc.getString("fileUri"),
                        startTime = doc.getString("startTime") ?: "09:00",
                        endTime = doc.getString("endTime") ?: "10:00",
                        googleEventId = doc.getString("googleEventId"),
                        remoteId = remoteId,
                        teamId = currentTeamId
                    )
                    dao.insert(entity)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}