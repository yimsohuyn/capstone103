package com.example.myapplication

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.AssignmentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import java.util.*
import android.app.AlertDialog
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher

class AssignmentRegisterFragment : Fragment() {

    // ① 파일 첨부 URI 저장 변수
    private var attachedFileUri: String? = null

    // ② 파일 선택 런처
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

        // ---- XML ID 연결 ----
        val backBtn = view.findViewById<ImageButton>(R.id.btnBack)
        val dueDateLayout = view.findViewById<LinearLayout>(R.id.dueDateLayout)
        val textDueDate = view.findViewById<TextView>(R.id.textDueDate)

        val assigneeLayout = view.findViewById<LinearLayout>(R.id.assigneeLayout)
        val textAssignee = view.findViewById<TextView>(R.id.textAssignee)

        val attachLayout = view.findViewById<LinearLayout>(R.id.attachLayout)
        val spinnerType = view.findViewById<Spinner>(R.id.spinnerType)
        val submitBtn = view.findViewById<Button>(R.id.btnSubmit)

        backBtn.setOnClickListener {
            // NavController 사용 중이니까 이게 가장 자연스러운 뒤로가기
            findNavController().navigateUp()
        }

        // ---- 1) DatePicker ----
        dueDateLayout.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    textDueDate.text = "%04d-%02d-%02d".format(year, month + 1, day)
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // ---- 2) Assignee 선택 기능 (완성됨!) ----
        assigneeLayout.setOnClickListener {
            val input = EditText(requireContext())
            input.hint = "담당자 이름 입력"

            AlertDialog.Builder(requireContext())
                .setTitle("담당자 입력")
                .setView(input)
                .setPositiveButton("확인") { _, _ ->
                    val name = input.text.toString().trim()

                    if (name.isNotEmpty()) {
                        textAssignee.text = name
                    } else {
                        textAssignee.text = "담당자 선택"
                    }
                }
                .setNegativeButton("취소", null)
                .show()
        }
        attachLayout.setOnClickListener {
            filePickerLauncher.launch("*/*")
        }

        // ---- 3) Assignment Type Spinner ----
        val typeItems = listOf("개인 프로젝트", "팀 프로젝트")
        val adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, typeItems)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerType.adapter = adapter

        // ---- 4) SUBMIT 버튼 ----
        submitBtn.setOnClickListener {

            val type = spinnerType.selectedItem.toString()
            val dueDate = textDueDate.text.toString()
            val assignee = textAssignee.text.toString()

            if (dueDate.isEmpty()) {
                Toast.makeText(requireContext(), "마감일을 선택하세요!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val dao = AppDatabase.getDatabase(requireContext()).assignmentDao()

            // --- DB 저장 ---
            lifecycleScope.launch(Dispatchers.IO) {
                dao.insert(
                    AssignmentEntity(
                        type = type,
                        dueDate = dueDate,
                        assignee = if (assignee == "담당자 선택") null else assignee,
                        fileUri = attachedFileUri
                    )
                )
                launch(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "과제가 저장되었습니다!", Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                }

                launch(Dispatchers.Main) {
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
}