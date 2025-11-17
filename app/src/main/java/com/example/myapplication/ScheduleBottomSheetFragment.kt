package com.example.myapplication

import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.DatePicker
import android.widget.EditText
import android.widget.ImageButton
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ScheduleBottomSheetFragment : BottomSheetDialogFragment() {

    // ✅ 시간 / 상세내용까지 넘기도록 인터페이스 확장
    interface OnScheduleAddedListener {
        fun onScheduleAdded(
            title: String,
            year: Int,
            month: Int,
            day: Int,
            time: String?,     // 예: "12:30" (없으면 null)
            detail: String?    // 상세 내용 (없으면 null)
        )
    }

    var listener: OnScheduleAddedListener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_schedule_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val backButton = view.findViewById<ImageButton>(R.id.btnBack)
        val titleEditText = view.findViewById<EditText>(R.id.editScheduleTitle)
        val timeEditText = view.findViewById<EditText>(R.id.editScheduleTime)
        val detailEditText = view.findViewById<EditText>(R.id.editScheduleDetail)
        val saveButton = view.findViewById<Button>(R.id.btnSaveSchedule)
        val datePicker = view.findViewById<DatePicker>(R.id.datePicker)

        // ✅ 혹시 모를 필터 전부 제거 (영어만 허용 같은 것 방지)
        titleEditText.filters = arrayOf<InputFilter>()
        detailEditText.filters = arrayOf<InputFilter>()

        // ✅ 한글 포함 모든 텍스트 입력 허용 (제목: 한 줄)
        titleEditText.inputType =
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES

        // ✅ 상세 내용: 여러 줄 + 한글
        detailEditText.inputType =
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE

        // ✅ 시간 입력: 시간 형식(숫자 + :) 위주
        timeEditText.inputType =
            InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME

        backButton.setOnClickListener { dismiss() }

        // 제목이 비어 있으면 저장 비활성화
        saveButton.isEnabled = false
        titleEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                saveButton.isEnabled = !s.isNullOrBlank()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        saveButton.setOnClickListener {
            val title = titleEditText.text.toString().trim()

            val timeText = timeEditText.text.toString().trim()
            val detailText = detailEditText.text.toString().trim()

            val time = if (timeText.isEmpty()) null else timeText
            val detail = if (detailText.isEmpty()) null else detailText

            val year = datePicker.year
            val month = datePicker.month      // 0부터 시작
            val day = datePicker.dayOfMonth

            listener?.onScheduleAdded(title, year, month, day, time, detail)
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        val bottomSheet =
            dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(it)
            behavior.state =
                com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            behavior.isHideable = true
        }
    }
}
