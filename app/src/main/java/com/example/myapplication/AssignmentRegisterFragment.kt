package com.example.myapplication

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import java.util.*

class AssignmentRegisterFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_assignment_register, container, false)

        // ---- XML ID 연결 ----
        val dueDateLayout = view.findViewById<LinearLayout>(R.id.dueDateLayout)
        val textDueDate = view.findViewById<TextView>(R.id.textDueDate)

        val assigneeLayout = view.findViewById<LinearLayout>(R.id.assigneeLayout)
        val textAssignee = view.findViewById<TextView>(R.id.textAssignee)

        val spinnerType = view.findViewById<Spinner>(R.id.spinnerType)
        val submitBtn = view.findViewById<Button>(R.id.btnSubmit)

        // ---- 1) DatePicker ----
        dueDateLayout.setOnClickListener {
            val cal = Calendar.getInstance()
            val datePicker = DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    textDueDate.text = "$year-${month + 1}-$day"
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            )
            datePicker.show()
        }

        // ---- 2) Assignee 선택 ----
        assigneeLayout.setOnClickListener {
            Toast.makeText(requireContext(), "Assignee 선택 기능 추가 예정", Toast.LENGTH_SHORT).show()
        }

        // ---- 3) Assignment Type Spinner ----
        val typeItems = listOf("개인 프로젝트", "팀 프로젝트", "발표")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, typeItems)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerType.adapter = adapter

        // ---- 4) SUBMIT 버튼 ----
        submitBtn.setOnClickListener {

            val selectedType = spinnerType.selectedItem.toString()

            if (selectedType == "팀 프로젝트") {
                findNavController().navigate(R.id.teamProjectFragment)
            } else {
                Toast.makeText(requireContext(), "제출완료!", Toast.LENGTH_SHORT).show()
            }

        }

        return view
    }
}
