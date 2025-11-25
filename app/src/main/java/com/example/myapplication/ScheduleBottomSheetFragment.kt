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

    // 시간 + 상세내용까지 전달하는 인터페이스
    interface OnScheduleAddedListener {
        fun onScheduleAdded(
            title: String,
            year: Int,
            month: Int,
            day: Int,
            time: String?,   // "09:00 ~ 10:00" 같은 문자열 (없으면 null)
            detail: String?  // 상세 내용 (없으면 null)
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
        val startTimeEditText = view.findViewById<EditText>(R.id.editStartTime)
        val endTimeEditText = view.findViewById<EditText>(R.id.editEndTime)
        val detailEditText = view.findViewById<EditText>(R.id.editScheduleDetail)
        val saveButton = view.findViewById<Button>(R.id.btnSaveSchedule)
        val datePicker = view.findViewById<DatePicker>(R.id.datePicker)

        // 혹시 예전에 걸려 있던 필터 제거
        titleEditText.filters = arrayOf<InputFilter>()
        detailEditText.filters = arrayOf<InputFilter>()

        // 제목: 한 줄 텍스트 (한글 포함)
        titleEditText.inputType =
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES

        // 상세 내용: 여러 줄 + 한글
        detailEditText.inputType =
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE

        // 시간 입력: 숫자만 (0930 이런 식으로)
        startTimeEditText.inputType =
            InputType.TYPE_CLASS_NUMBER
        endTimeEditText.inputType =
            InputType.TYPE_CLASS_NUMBER

        // ★ 시간 자동 포맷팅 함수
        fun setupTimeFormatter(editText: EditText) {
            var isEditing = false
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int
                ) { }

                override fun onTextChanged(
                    s: CharSequence?, start: Int, before: Int, count: Int
                ) { }

                override fun afterTextChanged(s: Editable?) {
                    if (isEditing) return

                    val raw = s?.toString() ?: return
                    val digits = raw.filter { it.isDigit() }

                    // 아직 4자리 안 되면 그대로
                    if (digits.length < 4) return

                    isEditing = true

                    val padded = digits.take(4)      // 최대 4자리
                    val h = padded.substring(0, 2).toIntOrNull() ?: 0
                    val m = padded.substring(2, 4).toIntOrNull() ?: 0

                    val hour = h.coerceIn(0, 23)
                    val minute = m.coerceIn(0, 59)

                    val formatted = String.format("%02d:%02d", hour, minute)
                    editText.setText(formatted)
                    editText.setSelection(formatted.length)

                    isEditing = false
                }
            })
        }

        // 시작/종료 시간에 포맷터 적용
        setupTimeFormatter(startTimeEditText)
        setupTimeFormatter(endTimeEditText)

        // 뒤로가기
        backButton.setOnClickListener { dismiss() }

        // 제목이 비어 있으면 저장 비활성화
        saveButton.isEnabled = false
        titleEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                saveButton.isEnabled = !s.isNullOrBlank()
            }
            override fun afterTextChanged(s: Editable?) { }
        })

        // 저장 버튼 클릭
        saveButton.setOnClickListener {
            val title = titleEditText.text.toString().trim()

            val startTimeText = startTimeEditText.text.toString().trim()
            val endTimeText = endTimeEditText.text.toString().trim()
            val detailText = detailEditText.text.toString().trim()

            // TextWatcher가 "HH:mm" 형식으로 만들어 줌
            val time: String? = when {
                startTimeText.isNotEmpty() && endTimeText.isNotEmpty() ->
                    "$startTimeText ~ $endTimeText"
                startTimeText.isNotEmpty() ->
                    startTimeText
                else -> null
            }

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