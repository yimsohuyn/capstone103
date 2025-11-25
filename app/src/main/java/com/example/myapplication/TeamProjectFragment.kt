package com.example.myapplication

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.myapplication.databinding.FragmentTeamProjectBinding
import java.util.Calendar

class TeamProjectFragment : Fragment() {

    private var _binding: FragmentTeamProjectBinding? = null
    private val binding get() = _binding!!

    // 팀원 / 회의 데이터 (간단히 메모리에서만 관리)
    private val memberList = mutableListOf<String>()
    private val meetingList = mutableListOf<String>()

    // 체크리스트 아이템 개수 (5개 고정)
    private val totalTasks = 5

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

        // 1. 팀원 추가 버튼
        binding.btnAddMember.setOnClickListener {
            showAddMemberDialog()
        }

        // 2. 체크박스 진행률 갱신
        setupChecklistProgress()

        // 3. 회의 일정 추가
        binding.btnAddMeeting.setOnClickListener {
            showAddMeetingDialog()
        }
    }

    // ---------------------------
    // 1) 팀원 추가 다이얼로그
    // ---------------------------
    private fun showAddMemberDialog() {
        val editText = EditText(requireContext()).apply {
            hint = "팀원 이름을 입력하세요"
        }

        AlertDialog.Builder(requireContext())
            .setTitle("팀원 추가")
            .setView(editText)
            .setPositiveButton("추가") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    memberList.add(name)
                    addMemberRow(name)
                } else {
                    Toast.makeText(requireContext(), "이름을 입력하세요.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun addMemberRow(name: String) {
        // "등록된 팀원이 없습니다" 문구가 있다면 숨기기
        binding.textEmptyMembers.visibility = View.GONE

        val row = TextView(requireContext()).apply {
            text = "• $name"
            textSize = 16f
            setTextColor(resources.getColor(android.R.color.black, null))
            setPadding(4, 8, 4, 8)
        }

        binding.memberListContainer.addView(row)
    }

    // ---------------------------
    // 2) 체크박스 진행률
    // ---------------------------
    private fun setupChecklistProgress() {
        val checkBoxes = listOf<CheckBox>(
            binding.checkTopic,
            binding.checkResearch,
            binding.checkMeeting,
            binding.checkSlides,
            binding.checkPractice
        )

        val listener = View.OnClickListener {
            updateProgress(checkBoxes)
        }

        checkBoxes.forEach { cb ->
            cb.setOnClickListener(listener)
        }

        // 초기 상태 갱신
        updateProgress(checkBoxes)
    }

    private fun updateProgress(checkBoxes: List<CheckBox>) {
        val doneCount = checkBoxes.count { it.isChecked }
        val percent = (doneCount * 100) / totalTasks

        binding.progressBar.progress = percent
        binding.textProgress.text = "진행률: $percent% ($doneCount/$totalTasks)"
    }

    // ---------------------------
    // 3) 회의 일정 추가
    // ---------------------------
    private fun showAddMeetingDialog() {
        val cal = Calendar.getInstance()

        // 1단계: 날짜 선택
        val datePicker = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                // 날짜 선택 후 -> 시간 선택
                showTimePicker(year, month, dayOfMonth)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.show()
    }

    private fun showTimePicker(year: Int, month: Int, day: Int) {
        val cal = Calendar.getInstance()

        val timePicker = TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                val text = String.format(
                    "%04d-%02d-%02d  %02d:%02d",
                    year,
                    month + 1,
                    day,
                    hourOfDay,
                    minute
                )
                meetingList.add(text)
                addMeetingRow(text)
            },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            true
        )
        timePicker.show()
    }

    private fun addMeetingRow(text: String) {
        binding.textEmptyMeetings.visibility = View.GONE

        val row = TextView(requireContext()).apply {
            this.text = "• $text"
            textSize = 16f
            setTextColor(resources.getColor(android.R.color.black, null))
            setPadding(4, 8, 4, 8)
        }

        binding.meetingListContainer.addView(row)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
