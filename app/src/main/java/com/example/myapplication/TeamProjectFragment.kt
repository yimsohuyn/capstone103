package com.example.myapplication

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.databinding.FragmentTeamProjectBinding
import com.example.myapplication.team.TeamManager
import com.example.myapplication.utils.InviteUtils
import com.example.myapplication.utils.QRUtils
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.net.URI
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class TeamProjectFragment : Fragment() {

    private var _binding: FragmentTeamProjectBinding? = null
    private val binding get() = _binding!!

    private val totalTasks = 5
    private var assignmentId: Int = -1
    private var currentAssignment: AssignmentEntity? = null

    private var membersListener: ListenerRegistration? = null
    private var checklistListener: ListenerRegistration? = null
    private var meetingsListener: ListenerRegistration? = null
    private var filesListener: ListenerRegistration? = null

    private data class RemoteMember(
        val docId: String,
        val name: String
    )

    private data class RemoteMeeting(
        val docId: String,
        val datetime: String,
        val memo: String
    )

    private data class RemoteFile(
        val docId: String,
        val fileName: String,
        val fileUri: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        assignmentId = arguments?.getInt("assignmentId", -1) ?: -1
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTeamProjectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (assignmentId == -1) {
            Toast.makeText(requireContext(), "과제 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
        }

        setupChecklistProgress()

        binding.btnAddMember.setOnClickListener {
            runIfAssignee { showAddMemberDialog() }
        }

        binding.btnAddMeeting.setOnClickListener {
            runIfAssignee { showAddMeetingDialog() }
        }

        binding.btnAddFile.setOnClickListener {
            runIfAssignee { showAddFileDialog() }
        }

        binding.btnResetChecklist.setOnClickListener {
            runIfAssignee { resetChecklist() }
        }

        binding.btnTeamInvite.setOnClickListener {
            runIfAssignee { showInviteDialog() }
        }

        loadProjectInfo()
    }

    private fun getCurrentUserEmail(): String? {
        return GoogleSignIn.getLastSignedInAccount(requireContext())
            ?.email
            ?.trim()
            ?.lowercase(Locale.ROOT)
    }

    private fun isCurrentUserAssignee(): Boolean {
        val currentEmail = getCurrentUserEmail()
        val assigneeEmail = currentAssignment?.assigneeEmail?.trim()?.lowercase(Locale.ROOT)

        return !currentEmail.isNullOrBlank() &&
                !assigneeEmail.isNullOrBlank() &&
                currentEmail == assigneeEmail
    }

    private fun showAssigneeOnlyMessage() {
        Toast.makeText(requireContext(), "담당자만 사용할 수 있습니다.", Toast.LENGTH_SHORT).show()
    }

    private fun runIfAssignee(action: () -> Unit) {
        if (isCurrentUserAssignee()) {
            action()
        } else {
            showAssigneeOnlyMessage()
        }
    }

    private fun updatePermissionUi() {
        val isAssignee = isCurrentUserAssignee()
        val alphaValue = if (isAssignee) 1.0f else 0.6f

        binding.btnAddMember.alpha = alphaValue
        binding.btnAddMeeting.alpha = alphaValue
        binding.btnAddFile.alpha = alphaValue
        binding.btnResetChecklist.alpha = alphaValue
        binding.btnTeamInvite.alpha = alphaValue

        binding.checkTopic.isEnabled = isAssignee
        binding.checkResearch.isEnabled = isAssignee
        binding.checkMeeting.isEnabled = isAssignee
        binding.checkSlides.isEnabled = isAssignee
        binding.checkPractice.isEnabled = isAssignee

        binding.textTeamDesc.text = if (isAssignee) {
            "팀원 추가와 초대 링크 공유가 가능합니다."
        } else {
            "담당자만 수정할 수 있습니다."
        }
    }

    private fun isDarkMode(): Boolean {
        val currentNightMode =
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun currentTeamId(): String? {
        val assignmentTeamId = currentAssignment?.teamId
        return when {
            !assignmentTeamId.isNullOrBlank() -> assignmentTeamId
            TeamManager.teamId.isNotBlank() -> TeamManager.teamId
            else -> null
        }
    }

    private fun currentRemoteAssignmentId(): String? {
        return currentAssignment?.remoteId
    }

    private fun loadProjectInfo() {
        if (assignmentId == -1) return

        val dao = AppDatabase.getDatabase(requireContext()).assignmentDao()
        val googleAccount = GoogleSignIn.getLastSignedInAccount(requireContext())

        lifecycleScope.launch {
            val assignment = withContext(Dispatchers.IO) {
                try {
                    val original = dao.getById(assignmentId)
                    if (original != null) {
                        recoverSharedProject(original, dao, googleAccount)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    dao.getById(assignmentId)
                }
            }

            currentAssignment = assignment

            if (assignment != null) {
                binding.textProjectTitle.text = assignment.title
                binding.textProjectDueDate.text = "마감일: ${assignment.dueDate}"

                val assigneeText = if (assignment.assignee.isNullOrBlank()) {
                    "담당자: 미지정"
                } else {
                    "담당자: ${assignment.assignee}"
                }
                binding.textProjectAssignee.text = assigneeText
            } else {
                binding.textProjectTitle.text = "프로젝트 정보 없음"
                binding.textProjectDueDate.text = "마감일: -"
                binding.textProjectAssignee.text = "담당자: -"
            }

            updatePermissionUi()
            startFirestoreListeners()
        }
    }

    private suspend fun recoverSharedProject(
        assignment: AssignmentEntity,
        dao: AssignmentDao,
        googleAccount: com.google.android.gms.auth.api.signin.GoogleSignInAccount?
    ): AssignmentEntity {
        return try {
            val safeTeamId = assignment.teamId?.takeIf { it.isNotBlank() }
                ?: TeamManager.teamId.takeIf { it.isNotBlank() }
                ?: "demo_team"

            val safeRemoteId = assignment.remoteId?.takeIf { it.isNotBlank() }
                ?: UUID.randomUUID().toString()

            val updated = if (assignment.teamId == safeTeamId && assignment.remoteId == safeRemoteId) {
                assignment
            } else {
                assignment.copy(
                    teamId = safeTeamId,
                    remoteId = safeRemoteId
                )
            }

            dao.update(updated)

            val db = FirebaseFirestore.getInstance()
            val assignmentRef = db.collection("teams")
                .document(safeTeamId)
                .collection("assignments")
                .document(safeRemoteId)

            val projectData = hashMapOf(
                "title" to updated.title,
                "dueDate" to updated.dueDate,
                "assignee" to (updated.assignee ?: ""),
                "assigneeEmail" to (updated.assigneeEmail ?: ""),
                "type" to updated.type,
                "createdAt" to FieldValue.serverTimestamp()
            )

            assignmentRef.set(projectData, SetOptions.merge()).await()

            val ownerUid = googleAccount?.id
                ?: googleAccount?.email
                ?: "owner_${updated.id}"

            val ownerName = googleAccount?.displayName
                ?: updated.assignee
                ?: "담당자"

            val ownerEmail = googleAccount?.email
                ?: updated.assigneeEmail
                ?: ""

            val memberData = hashMapOf(
                "name" to ownerName,
                "email" to ownerEmail,
                "uid" to ownerUid,
                "manual" to false,
                "joinedAt" to FieldValue.serverTimestamp()
            )

            assignmentRef.collection("members")
                .document(ownerUid)
                .set(memberData, SetOptions.merge())
                .await()

            updated
        } catch (e: Exception) {
            e.printStackTrace()
            assignment
        }
    }

    private fun startFirestoreListeners() {
        removeFirestoreListeners()

        val teamId = currentTeamId()
        val remoteAssignmentId = currentRemoteAssignmentId()

        if (teamId.isNullOrBlank() || remoteAssignmentId.isNullOrBlank()) {
            Toast.makeText(
                requireContext(),
                "공유 프로젝트 정보가 부족합니다. 새 팀 프로젝트부터 공유가 적용됩니다.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val db = FirebaseFirestore.getInstance()

        membersListener = db.collection("teams")
            .document(teamId)
            .collection("assignments")
            .document(remoteAssignmentId)
            .collection("members")
            .addSnapshotListener { snapshot, _ ->
                val assigneeName = currentAssignment?.assignee?.trim()

                val members = snapshot?.documents?.mapNotNull { doc ->
                    val name = doc.getString("name")?.trim() ?: return@mapNotNull null

                    if (!assigneeName.isNullOrBlank() && name == assigneeName) {
                        return@mapNotNull null
                    }

                    RemoteMember(
                        docId = doc.id,
                        name = name
                    )
                }.orEmpty()

                renderMembers(members)
            }

        checklistListener = db.collection("teams")
            .document(teamId)
            .collection("assignments")
            .document(remoteAssignmentId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    binding.checkTopic.isChecked = snapshot.getBoolean("checkTopic") ?: false
                    binding.checkResearch.isChecked = snapshot.getBoolean("checkResearch") ?: false
                    binding.checkMeeting.isChecked = snapshot.getBoolean("checkMeeting") ?: false
                    binding.checkSlides.isChecked = snapshot.getBoolean("checkSlides") ?: false
                    binding.checkPractice.isChecked = snapshot.getBoolean("checkPractice") ?: false

                    updateProgress(
                        listOf(
                            binding.checkTopic,
                            binding.checkResearch,
                            binding.checkMeeting,
                            binding.checkSlides,
                            binding.checkPractice
                        )
                    )
                }
            }

        meetingsListener = db.collection("teams")
            .document(teamId)
            .collection("assignments")
            .document(remoteAssignmentId)
            .collection("meetings")
            .addSnapshotListener { snapshot, _ ->
                val meetings = snapshot?.documents
                    ?.map { doc ->
                        RemoteMeeting(
                            docId = doc.id,
                            datetime = doc.getString("datetime") ?: "",
                            memo = doc.getString("memo") ?: ""
                        )
                    }
                    ?.sortedByDescending { it.datetime }
                    .orEmpty()

                renderMeetings(meetings)
            }

        filesListener = db.collection("teams")
            .document(teamId)
            .collection("assignments")
            .document(remoteAssignmentId)
            .collection("files")
            .addSnapshotListener { snapshot, _ ->
                val files = snapshot?.documents?.map { doc ->
                    RemoteFile(
                        docId = doc.id,
                        fileName = doc.getString("fileName") ?: "파일",
                        fileUri = doc.getString("fileUri") ?: ""
                    )
                }.orEmpty()

                renderFiles(files)
            }
    }

    private fun removeFirestoreListeners() {
        membersListener?.remove()
        checklistListener?.remove()
        meetingsListener?.remove()
        filesListener?.remove()

        membersListener = null
        checklistListener = null
        meetingsListener = null
        filesListener = null
    }

    private fun showInviteDialog() {
        val teamId = currentTeamId() ?: return
        val remoteAssignmentId = currentRemoteAssignmentId() ?: return

        val inviteLink = InviteUtils.createInviteLink(teamId, remoteAssignmentId)

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_invite_custom, null)

        val qrImage = dialogView.findViewById<ImageView>(R.id.qrImage)
        val linkText = dialogView.findViewById<TextView>(R.id.inviteLink)
        val copyBtn = dialogView.findViewById<TextView>(R.id.btnCopyLink)
        val closeBtn = dialogView.findViewById<TextView>(R.id.btnCloseInvite)

        val qrBitmap = QRUtils.generateQr(inviteLink)
        qrImage.setImageBitmap(qrBitmap)

        linkText.text = inviteLink

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        copyBtn.setOnClickListener {
            val clipboard =
                requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("inviteLink", inviteLink)
            clipboard.setPrimaryClip(clip)

            Toast.makeText(requireContext(), "초대 링크가 복사되었습니다.", Toast.LENGTH_SHORT).show()
        }

        closeBtn.setOnClickListener {
            dialog.dismiss()
        }
    }

    private fun renderMembers(members: List<RemoteMember>) {
        binding.memberListContainer.removeAllViews()

        if (members.isEmpty()) {
            binding.textEmptyMembers.visibility = View.VISIBLE
            binding.memberListContainer.addView(binding.textEmptyMembers)
            return
        }

        binding.textEmptyMembers.visibility = View.GONE

        members.forEach { member ->
            addMemberRow(member)
        }
    }

    private fun showAddMemberDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_member, null)

        val editMemberName = dialogView.findViewById<EditText>(R.id.editMemberName)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("추가", null)
            .setNegativeButton("취소", null)
            .create()

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val buttonColor = if (isDarkMode()) Color.WHITE else Color.BLACK
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(buttonColor)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(buttonColor)

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = editMemberName.text.toString().trim()

            if (name.isEmpty()) {
                Toast.makeText(requireContext(), "팀원 이름을 입력하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            saveMemberToFirestore(name)
            dialog.dismiss()
        }
    }

    private fun saveMemberToFirestore(name: String) {
        val teamId = currentTeamId() ?: return
        val remoteAssignmentId = currentRemoteAssignmentId() ?: return

        val data = hashMapOf(
            "name" to name,
            "email" to "",
            "uid" to "",
            "manual" to true,
            "joinedAt" to FieldValue.serverTimestamp()
        )

        FirebaseFirestore.getInstance()
            .collection("teams")
            .document(teamId)
            .collection("assignments")
            .document(remoteAssignmentId)
            .collection("members")
            .add(data)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "팀원이 추가되었습니다.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "팀원 추가 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun addMemberRow(member: RemoteMember) {
        val row = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_team_member, binding.memberListContainer, false)

        val tvAvatar = row.findViewById<TextView>(R.id.tvMemberAvatar)
        val tvName = row.findViewById<TextView>(R.id.tvMemberName)
        val tvSub = row.findViewById<TextView>(R.id.tvMemberSub)

        tvAvatar.text = getInitial(member.name)
        tvName.text = member.name
        tvSub.text = "팀원"

        row.setOnLongClickListener {
            if (!isCurrentUserAssignee()) {
                showAssigneeOnlyMessage()
                return@setOnLongClickListener true
            }

            val dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_delete_member, null)

            val textDeleteTitle = dialogView.findViewById<TextView>(R.id.textDeleteTitle)
            val textDeleteMessage = dialogView.findViewById<TextView>(R.id.textDeleteMessage)
            val btnCancelDelete = dialogView.findViewById<TextView>(R.id.btnCancelDelete)
            val btnConfirmDelete = dialogView.findViewById<TextView>(R.id.btnConfirmDelete)

            textDeleteTitle.text = "팀원 삭제"
            textDeleteMessage.text = "'${member.name}' 팀원을 삭제할까요?"

            val dialog = AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create()

            dialog.show()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            btnCancelDelete.setOnClickListener { dialog.dismiss() }
            btnConfirmDelete.setOnClickListener {
                deleteMember(member.docId)
                dialog.dismiss()
            }

            true
        }

        binding.memberListContainer.addView(row)
    }

    private fun deleteMember(docId: String) {
        val teamId = currentTeamId() ?: return
        val remoteAssignmentId = currentRemoteAssignmentId() ?: return

        FirebaseFirestore.getInstance()
            .collection("teams")
            .document(teamId)
            .collection("assignments")
            .document(remoteAssignmentId)
            .collection("members")
            .document(docId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "팀원이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "팀원 삭제 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupChecklistProgress() {
        val checkBoxes = listOf(
            binding.checkTopic,
            binding.checkResearch,
            binding.checkMeeting,
            binding.checkSlides,
            binding.checkPractice
        )

        checkBoxes.forEach { checkBox ->
            checkBox.setOnClickListener {
                if (!isCurrentUserAssignee()) {
                    checkBox.isChecked = !checkBox.isChecked
                    showAssigneeOnlyMessage()
                    return@setOnClickListener
                }
                saveChecklistToFirestore()
                updateProgress(checkBoxes)
            }
        }

        updateProgress(checkBoxes)
    }

    private fun saveChecklistToFirestore() {
        val teamId = currentTeamId() ?: return
        val remoteAssignmentId = currentRemoteAssignmentId() ?: return

        val data = hashMapOf(
            "checkTopic" to binding.checkTopic.isChecked,
            "checkResearch" to binding.checkResearch.isChecked,
            "checkMeeting" to binding.checkMeeting.isChecked,
            "checkSlides" to binding.checkSlides.isChecked,
            "checkPractice" to binding.checkPractice.isChecked
        )

        FirebaseFirestore.getInstance()
            .collection("teams")
            .document(teamId)
            .collection("assignments")
            .document(remoteAssignmentId)
            .set(data, SetOptions.merge())
    }

    private fun resetChecklist() {
        binding.checkTopic.isChecked = false
        binding.checkResearch.isChecked = false
        binding.checkMeeting.isChecked = false
        binding.checkSlides.isChecked = false
        binding.checkPractice.isChecked = false

        saveChecklistToFirestore()

        val checkBoxes = listOf(
            binding.checkTopic,
            binding.checkResearch,
            binding.checkMeeting,
            binding.checkSlides,
            binding.checkPractice
        )
        updateProgress(checkBoxes)

        Toast.makeText(requireContext(), "체크리스트가 초기화되었습니다.", Toast.LENGTH_SHORT).show()
    }

    private fun updateProgress(checkBoxes: List<CheckBox>) {
        val done = checkBoxes.count { it.isChecked }
        val percent = (done * 100) / totalTasks
        binding.progressBar.progress = percent
        binding.textProgress.text = "진행률: $percent% ($done/$totalTasks)"
    }

    private fun renderMeetings(meetings: List<RemoteMeeting>) {
        binding.meetingListContainer.removeAllViews()

        if (meetings.isEmpty()) {
            binding.textEmptyMeetings.visibility = View.VISIBLE
            binding.meetingListContainer.addView(binding.textEmptyMeetings)
            return
        }

        binding.textEmptyMeetings.visibility = View.GONE

        meetings.forEach { meeting ->
            addMeetingRow(meeting)
        }
    }

    private fun showAddMeetingDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_meeting_custom, null)

        val btnSelectMeetingDate =
            dialogView.findViewById<TextView>(R.id.btnSelectMeetingDate)
        val btnSelectMeetingTime =
            dialogView.findViewById<TextView>(R.id.btnSelectMeetingTime)
        val editMeetingMemo =
            dialogView.findViewById<EditText>(R.id.editMeetingMemo)
        val btnCancelMeetingDialog =
            dialogView.findViewById<TextView>(R.id.btnCancelMeetingDialog)
        val btnSaveMeetingDialog =
            dialogView.findViewById<TextView>(R.id.btnSaveMeetingDialog)

        val selectedCalendar = Calendar.getInstance()
        var isDateSelected = false
        var isTimeSelected = false

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnSelectMeetingDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    selectedCalendar.set(Calendar.YEAR, year)
                    selectedCalendar.set(Calendar.MONTH, month)
                    selectedCalendar.set(Calendar.DAY_OF_MONTH, day)
                    isDateSelected = true
                    btnSelectMeetingDate.text =
                        "%04d-%02d-%02d".format(year, month + 1, day)
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        btnSelectMeetingTime.setOnClickListener {
            val cal = Calendar.getInstance()
            android.app.TimePickerDialog(
                requireContext(),
                { _, hour, minute ->
                    selectedCalendar.set(Calendar.HOUR_OF_DAY, hour)
                    selectedCalendar.set(Calendar.MINUTE, minute)
                    isTimeSelected = true
                    btnSelectMeetingTime.text =
                        "%02d:%02d".format(hour, minute)
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                true
            ).show()
        }

        btnCancelMeetingDialog.setOnClickListener {
            dialog.dismiss()
        }

        btnSaveMeetingDialog.setOnClickListener {
            if (!isDateSelected) {
                Toast.makeText(requireContext(), "날짜를 선택하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isTimeSelected) {
                Toast.makeText(requireContext(), "시간을 선택하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val datetime = "%04d-%02d-%02d %02d:%02d".format(
                selectedCalendar.get(Calendar.YEAR),
                selectedCalendar.get(Calendar.MONTH) + 1,
                selectedCalendar.get(Calendar.DAY_OF_MONTH),
                selectedCalendar.get(Calendar.HOUR_OF_DAY),
                selectedCalendar.get(Calendar.MINUTE)
            )

            val memo = editMeetingMemo.text.toString().trim()

            saveMeetingToFirestore(datetime, memo)
            dialog.dismiss()
        }
    }

    private fun saveMeetingToFirestore(datetime: String, memo: String) {
        val teamId = currentTeamId() ?: return
        val remoteAssignmentId = currentRemoteAssignmentId() ?: return

        val data = hashMapOf(
            "datetime" to datetime,
            "memo" to memo,
            "createdAt" to FieldValue.serverTimestamp()
        )

        FirebaseFirestore.getInstance()
            .collection("teams")
            .document(teamId)
            .collection("assignments")
            .document(remoteAssignmentId)
            .collection("meetings")
            .add(data)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "회의 일정이 추가되었습니다.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "회의 일정 추가 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun addMeetingRow(meeting: RemoteMeeting) {
        val row = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_team_meeting, binding.meetingListContainer, false)

        val tvDate = row.findViewById<TextView>(R.id.tvMeetingDate)
        val tvMemo = row.findViewById<TextView>(R.id.tvMeetingMemo)

        tvDate.text = meeting.datetime
        tvMemo.text = if (meeting.memo.isBlank()) "메모 없음" else meeting.memo

        row.setOnLongClickListener {
            if (!isCurrentUserAssignee()) {
                showAssigneeOnlyMessage()
                return@setOnLongClickListener true
            }

            val dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_delete_member, null)

            val textDeleteTitle = dialogView.findViewById<TextView>(R.id.textDeleteTitle)
            val textDeleteMessage = dialogView.findViewById<TextView>(R.id.textDeleteMessage)
            val btnCancelDelete = dialogView.findViewById<TextView>(R.id.btnCancelDelete)
            val btnConfirmDelete = dialogView.findViewById<TextView>(R.id.btnConfirmDelete)

            textDeleteTitle.text = "회의 삭제"
            textDeleteMessage.text = "해당 회의 일정을 삭제할까요?"

            val dialog = AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create()

            dialog.show()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            btnCancelDelete.setOnClickListener { dialog.dismiss() }
            btnConfirmDelete.setOnClickListener {
                deleteMeeting(meeting.docId)
                dialog.dismiss()
            }

            true
        }

        binding.meetingListContainer.addView(row)
    }

    private fun deleteMeeting(docId: String) {
        val teamId = currentTeamId() ?: return
        val remoteAssignmentId = currentRemoteAssignmentId() ?: return

        FirebaseFirestore.getInstance()
            .collection("teams")
            .document(teamId)
            .collection("assignments")
            .document(remoteAssignmentId)
            .collection("meetings")
            .document(docId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "삭제되었습니다.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "삭제 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showAddFileDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_file, null)

        val editFileName = dialogView.findViewById<EditText>(R.id.editFileName)
        val editFileUrl = dialogView.findViewById<EditText>(R.id.editFileUrl)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("추가", null)
            .setNegativeButton("취소", null)
            .create()

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.WHITE)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.WHITE)

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = editFileName.text.toString().trim()
            val url = editFileUrl.text.toString().trim()

            if (name.isEmpty() || url.isEmpty()) {
                Toast.makeText(requireContext(), "이름과 링크를 입력하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            saveFileLinkToFirestore(name, url)
            dialog.dismiss()
        }
    }

    private fun saveFileLinkToFirestore(fileName: String, fileUrl: String) {
        val teamId = currentTeamId() ?: return
        val remoteAssignmentId = currentRemoteAssignmentId() ?: return

        val data = hashMapOf(
            "fileName" to fileName,
            "fileUri" to fileUrl,
            "createdAt" to FieldValue.serverTimestamp()
        )

        FirebaseFirestore.getInstance()
            .collection("teams")
            .document(teamId)
            .collection("assignments")
            .document(remoteAssignmentId)
            .collection("files")
            .add(data)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "파일 링크가 추가되었습니다.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "파일 링크 저장 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun renderFiles(files: List<RemoteFile>) {
        binding.fileListContainer.removeAllViews()

        if (files.isEmpty()) {
            binding.textEmptyFiles.visibility = View.VISIBLE
            binding.fileListContainer.addView(binding.textEmptyFiles)
            return
        }

        binding.textEmptyFiles.visibility = View.GONE

        files.forEach { file ->
            addFileRow(file)
        }
    }

    private fun addFileRow(file: RemoteFile) {
        val row = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_team_file, binding.fileListContainer, false)

        val tvName = row.findViewById<TextView>(R.id.tvFileName)
        val tvUrl = row.findViewById<TextView>(R.id.tvFileUrl)

        tvName.text = file.fileName
        tvUrl.text = getHostName(file.fileUri)

        row.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(file.fileUri))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "파일을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        row.setOnLongClickListener {
            if (!isCurrentUserAssignee()) {
                showAssigneeOnlyMessage()
                return@setOnLongClickListener true
            }

            val dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_delete_member, null)

            val textDeleteTitle = dialogView.findViewById<TextView>(R.id.textDeleteTitle)
            val textDeleteMessage = dialogView.findViewById<TextView>(R.id.textDeleteMessage)
            val btnCancelDelete = dialogView.findViewById<TextView>(R.id.btnCancelDelete)
            val btnConfirmDelete = dialogView.findViewById<TextView>(R.id.btnConfirmDelete)

            textDeleteTitle.text = "파일 삭제"
            textDeleteMessage.text = "'${file.fileName}' 파일을 목록에서 삭제할까요?"

            val dialog = AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create()

            dialog.show()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            btnCancelDelete.setOnClickListener { dialog.dismiss() }
            btnConfirmDelete.setOnClickListener {
                deleteFile(file.docId)
                dialog.dismiss()
            }

            true
        }

        binding.fileListContainer.addView(row)
    }

    private fun deleteFile(docId: String) {
        val teamId = currentTeamId() ?: return
        val remoteAssignmentId = currentRemoteAssignmentId() ?: return

        lifecycleScope.launch {
            try {
                FirebaseFirestore.getInstance()
                    .collection("teams")
                    .document(teamId)
                    .collection("assignments")
                    .document(remoteAssignmentId)
                    .collection("files")
                    .document(docId)
                    .delete()
                    .await()

                Toast.makeText(requireContext(), "파일이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "파일 삭제 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getInitial(name: String): String {
        return name.trim().take(1).ifEmpty { "?" }
    }

    private fun getHostName(url: String): String {
        return try {
            val host = URI(url).host ?: return "링크 열기"
            host.removePrefix("www.")
        } catch (e: Exception) {
            "링크 열기"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        removeFirestoreListeners()
        _binding = null
    }
}