package com.example.myapplication

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.data.DatabaseModule
import com.example.myapplication.data.MeetingEntity
import com.example.myapplication.data.ProjectFileEntity
import com.example.myapplication.data.TeamMemberEntity
import com.example.myapplication.databinding.FragmentTeamProjectBinding
import com.example.myapplication.utils.InviteUtils
import com.example.myapplication.utils.QRUtils
import com.example.myapplication.team.TeamManager
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import android.widget.Button
import android.content.ClipboardManager
import android.content.ClipData

class   TeamProjectFragment : Fragment() {

    private var _binding: FragmentTeamProjectBinding? = null
    private val binding get() = _binding!!

    private val memberList = mutableListOf<TeamMemberEntity>()
    private val meetingList = mutableListOf<MeetingEntity>()
    private val fileList = mutableListOf<ProjectFileEntity>()

    private val totalTasks = 5

    private val filePickLauncher =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let {
                val name = getFileNameFromUri(it)
                saveFileToDB(name, it.toString())
            }
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

        loadAllDataFromDB()
        setupChecklistProgress()

        binding.btnAddMember.setOnClickListener { showAddMemberDialog() }
        binding.btnAddMeeting.setOnClickListener { showAddMeetingDialog() }
        binding.btnAddFile.setOnClickListener { filePickLauncher.launch("*/*") }

        // 🔥 팀원 초대 버튼
        binding.btnTeamInvite.setOnClickListener {
            showInviteDialog()
        }

        listenTeamMembers()
    }

    // ============================================================
    // 1. Firestore 실시간 팀원 반영
    // ============================================================
    private fun listenTeamMembers() {
        val teamId = TeamManager.teamId ?: return

        FirebaseFirestore.getInstance()
            .collection("teams")
            .document(teamId)
            .collection("members")
            .addSnapshotListener { snapshot, _ ->

                if (snapshot != null) {
                    binding.memberListContainer.removeAllViews()

                    for (doc in snapshot.documents) {
                        val name = doc.getString("name") ?: continue
                        addMemberRow(name)
                    }
                }
            }
    }

    // ============================================================
    // 2. 팀 초대 팝업 (QR + 링크)
    // ============================================================
    private fun showInviteDialog() {

        val teamId = TeamManager.teamId ?: return

        // 🔵 초대 링크 생성
        val inviteLink = InviteUtils.createInviteLink(teamId)

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_invite, null)

        val qrImage = dialogView.findViewById<ImageView>(R.id.qrImage)
        val linkText = dialogView.findViewById<TextView>(R.id.inviteLink)
        val copyBtn = dialogView.findViewById<Button>(R.id.copyLinkBtn)

        // 🔵 QR 생성
        val qrBitmap = QRUtils.generateQr(inviteLink)
        qrImage.setImageBitmap(qrBitmap)

        linkText.text = inviteLink

        // 📌 링크 복사 기능
        copyBtn.setOnClickListener {
            val clipboard =
                requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("inviteLink", inviteLink)
            clipboard.setPrimaryClip(clip)

            Toast.makeText(requireContext(), "초대 링크가 복사되었습니다.", Toast.LENGTH_SHORT).show()
        }

        AlertDialog.Builder(requireContext())
            .setTitle("팀원 초대하기")
            .setView(dialogView)
            .setPositiveButton("닫기", null)
            .show()
    }

    // ============================================================
    // 기존 기능 그대로 유지
    // ============================================================

    private fun loadAllDataFromDB() {
        val db = DatabaseModule.getDatabase(requireContext())
        val memberDao = db.teamMemberDao()
        val meetingDao = db.meetingDao()
        val fileDao = db.projectFileDao()

        lifecycleScope.launch {
            val members = withContext(Dispatchers.IO) { memberDao.getAll() }
            val meetings = withContext(Dispatchers.IO) { meetingDao.getAll() }
            val files = withContext(Dispatchers.IO) { fileDao.getAll() }

            memberList.clear()
            meetingList.clear()
            fileList.clear()

            memberList.addAll(members)
            meetingList.addAll(meetings)
            fileList.addAll(files)

            binding.memberListContainer.removeAllViews()
            binding.meetingListContainer.removeAllViews()
            binding.fileListContainer.removeAllViews()

            if (members.isEmpty()) binding.textEmptyMembers.visibility = View.VISIBLE
            else {
                binding.textEmptyMembers.visibility = View.GONE
                members.forEach { addMemberRow(it.name) }
            }

            if (meetings.isEmpty()) binding.textEmptyMeetings.visibility = View.VISIBLE
            else {
                binding.textEmptyMeetings.visibility = View.GONE
                meetings.forEach { addMeetingRow(it.datetime) }
            }

            if (files.isEmpty()) binding.textEmptyFiles.visibility = View.VISIBLE
            else {
                binding.textEmptyFiles.visibility = View.GONE
                files.forEach { addFileRow(it.fileName, Uri.parse(it.fileUri)) }
            }
        }
    }

    private fun showAddMemberDialog() {
        val editText = EditText(requireContext()).apply { hint = "팀원 이름을 입력하세요" }

        AlertDialog.Builder(requireContext())
            .setTitle("팀원 추가")
            .setView(editText)
            .setPositiveButton("추가") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) saveMemberToDB(name)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun saveMemberToDB(name: String) {
        val dao = DatabaseModule.getDatabase(requireContext()).teamMemberDao()

        lifecycleScope.launch {
            val entity = TeamMemberEntity(name = name)
            withContext(Dispatchers.IO) { dao.insert(entity) }
            memberList.add(entity)
            addMemberRow(name)
        }
    }

    // ============================================================
    // 🔥 팀원 하나(텍스트뷰) 렌더링 + 삭제 기능 추가됨
    // ============================================================
    private fun addMemberRow(name: String) {
        binding.textEmptyMembers.visibility = View.GONE

        val row = TextView(requireContext()).apply {
            text = "• $name"
            textSize = 16f
            setPadding(4, 8, 4, 8)
        }

        // 🔥 길게 눌러 삭제
        row.setOnLongClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("팀원 삭제")
                .setMessage("'$name' 팀원을 삭제할까요?")
                .setPositiveButton("삭제") { _, _ ->
                    deleteMember(name)
                }
                .setNegativeButton("취소", null)
                .show()
            true
        }

        binding.memberListContainer.addView(row)
    }

    // 🔥 팀원 삭제 함수
    private fun deleteMember(name: String) {
        val dao = DatabaseModule.getDatabase(requireContext()).teamMemberDao()

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                dao.deleteByName(name)
            }

            Toast.makeText(requireContext(), "팀원이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
            loadAllDataFromDB()
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

        checkBoxes.forEach {
            it.setOnClickListener { updateProgress(checkBoxes) }
        }

        updateProgress(checkBoxes)
    }

    private fun updateProgress(checkBoxes: List<CheckBox>) {
        val done = checkBoxes.count { it.isChecked }
        val percent = (done * 100) / totalTasks
        binding.progressBar.progress = percent
        binding.textProgress.text = "진행률: $percent% ($done/$totalTasks)"
    }

    private fun showAddMeetingDialog() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, y, m, d -> showTimePicker(y, m, d) },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showTimePicker(year: Int, month: Int, day: Int) {
        val cal = Calendar.getInstance()
        TimePickerDialog(
            requireContext(),
            { _, h, min ->
                val text = "%04d-%02d-%02d %02d:%02d".format(year, month + 1, day, h, min)
                saveMeetingToDB(text)
            },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun saveMeetingToDB(datetime: String) {
        val dao = DatabaseModule.getDatabase(requireContext()).meetingDao()

        lifecycleScope.launch {
            val entity = MeetingEntity(datetime = datetime)
            withContext(Dispatchers.IO) { dao.insert(entity) }
            meetingList.add(entity)
            addMeetingRow(datetime)
        }
    }

    // ============================================================
    // 🔥 회의 일정 렌더링 + 삭제 기능
    // ============================================================
    private fun addMeetingRow(text: String) {
        binding.textEmptyMeetings.visibility = View.GONE

        val row = TextView(requireContext()).apply {
            this.text = "• $text"
            textSize = 16f
            setPadding(4, 8, 4, 8)

            setOnLongClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle("삭제하시겠습니까?")
                    .setMessage("해당 회의 일정을 삭제합니다.")
                    .setPositiveButton("삭제") { _, _ ->
                        deleteMeeting(text)
                    }
                    .setNegativeButton("취소", null)
                    .show()
                true
            }
        }

        binding.meetingListContainer.addView(row)
    }

    private fun deleteMeeting(text: String) {
        val dao = DatabaseModule.getDatabase(requireContext()).meetingDao()

        lifecycleScope.launch {
            withContext(Dispatchers.IO) { dao.deleteByText(text) }

            loadAllDataFromDB()

            Toast.makeText(requireContext(), "삭제되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveFileToDB(fileName: String, uriString: String) {
        val dao = DatabaseModule.getDatabase(requireContext()).projectFileDao()

        lifecycleScope.launch {
            val entity = ProjectFileEntity(fileName = fileName, fileUri = uriString)
            withContext(Dispatchers.IO) { dao.insert(entity) }
            fileList.add(entity)
            addFileRow(fileName, Uri.parse(uriString))
        }
    }

    private fun addFileRow(name: String, uri: Uri) {
        binding.textEmptyFiles.visibility = View.GONE
        val row = TextView(requireContext()).apply {
            text = "📎 $name"
            textSize = 16f
            setPadding(4, 12, 4, 12)
            setOnClickListener {
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "*/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(
                        requireContext(),
                        "파일을 열 수 없습니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        binding.fileListContainer.addView(row)
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var fileName = "파일"
        val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex("_display_name")
                if (idx >= 0) fileName = it.getString(idx)
            }
        }
        return fileName
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
