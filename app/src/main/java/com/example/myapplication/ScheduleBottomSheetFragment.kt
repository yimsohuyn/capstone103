package com.example.myapplication

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.util.*

class ScheduleBottomSheetFragment : BottomSheetDialogFragment() {

    interface OnScheduleAddedListener {
        fun onScheduleAdded(
            title: String,
            year: Int,
            month: Int,
            day: Int,
            time: String?,      // 화면에 보여줄 시간 "HH:mm" (예: 09:30)
            detail: String?,    // 메모/상세 내용
            isAlarmOn: Boolean, // 알람 설정 여부
            alarmTime: String?  // 원본 알람 문자열 "HHmm" (예: 0930)
        )
    }

    var listener: OnScheduleAddedListener? = null
    private var isAlarmOn = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_schedule_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnBack = view.findViewById<ImageButton>(R.id.btnBack)
        val btnSave = view.findViewById<Button>(R.id.btnSaveSchedule)

        val btnAlarmToggle = view.findViewById<ImageButton>(R.id.btnAlarmToggle)
        val layoutAlarmDetail = view.findViewById<LinearLayout>(R.id.layoutAlarmDetail)
        val editAlarmTime = view.findViewById<EditText>(R.id.editAlarmTime)
        val editAlarmContent = view.findViewById<EditText>(R.id.editAlarmContent)

        val editScheduleTitle = view.findViewById<EditText>(R.id.editScheduleTitle)
        val editScheduleDetail = view.findViewById<EditText>(R.id.editScheduleDetail)
        val datePicker = view.findViewById<DatePicker>(R.id.datePicker)

        // 🔹 알람 토글 버튼
        btnAlarmToggle.setOnClickListener {
            isAlarmOn = !isAlarmOn
            layoutAlarmDetail.visibility = if (isAlarmOn) View.VISIBLE else View.GONE
            btnAlarmToggle.setImageResource(
                if (isAlarmOn) R.drawable.outline_alarm_on_24
                else R.drawable.outline_alarm_off_24
            )
            btnAlarmToggle.imageTintList = ColorStateList.valueOf(
                if (isAlarmOn) Color.RED else Color.GRAY
            )
        }

        // 뒤로가기
        btnBack.setOnClickListener { dismiss() }

        // 저장 버튼
        btnSave.setOnClickListener {
            val title = editScheduleTitle.text.toString().trim()
            val detail = editScheduleDetail.text.toString().trim()
            val year = datePicker.year
            val month = datePicker.month      // Calendar 와 동일하게 0부터 시작
            val day = datePicker.dayOfMonth

            if (title.isBlank()) {
                Toast.makeText(requireContext(), "제목을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 🔹 알람 시간 (원본 문자열, 예: "0930")
            var alarmTimeStr: String? = null
            var displayTime: String? = null   // UI / 구글캘린더에 쓸 "HH:mm" 형식

            // 알람 설정
            if (isAlarmOn) {
                alarmTimeStr = editAlarmTime.text.toString().trim()
                val alarmContent = editAlarmContent.text.toString().ifEmpty { "일정 알림" }

                if (alarmTimeStr.length >= 4) {
                    val h = alarmTimeStr.substring(0, 2).toIntOrNull()?.coerceIn(0, 23) ?: 0
                    val m = alarmTimeStr.substring(2, 4).toIntOrNull()?.coerceIn(0, 59) ?: 0

                    displayTime = String.format("%02d:%02d", h, m)

                    val calendar = Calendar.getInstance().apply {
                        set(year, month, day, h, m, 0)
                        if (timeInMillis < System.currentTimeMillis()) {
                            // 과거면 다음날로 밀기
                            add(Calendar.DAY_OF_YEAR, 1)
                        }
                    }

                    val intent = Intent(requireContext(), AlarmReceiver::class.java).apply {
                        putExtra("title", title)
                        putExtra("content", alarmContent)
                    }

                    val requestCode = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
                    val pendingIntent = PendingIntent.getBroadcast(
                        requireContext(),
                        requestCode,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val alarmManager =
                        requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )

                    Toast.makeText(
                        requireContext(),
                        "알람 설정됨: $displayTime",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "알람 시간을 4자리로 입력해주세요 (예: 0930)",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
            }

            // 🔹 상위(HomeFragment)로 일정 정보 전달
            listener?.onScheduleAdded(
                title = title,
                year = year,
                month = month,
                day = day,
                time = displayTime,                 // "HH:mm" 또는 null
                detail = detail.ifBlank { null },
                isAlarmOn = isAlarmOn,
                alarmTime = alarmTimeStr
            )

            // 바텀시트 닫기
            dismiss()
        }
    }
}
