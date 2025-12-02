package com.example.myapplication

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
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
            time: String?,
            detail: String?,
            isAlarmOn: Boolean,        // 추가
            alarmTime: String?         // 추가
        )
    }

    var listener: OnScheduleAddedListener? = null
    private var isAlarmOn = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
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

        // 🔹 알람 토글 버튼 클릭
        btnAlarmToggle.setOnClickListener {
            isAlarmOn = !isAlarmOn
            layoutAlarmDetail.visibility = if (isAlarmOn) View.VISIBLE else View.GONE
            btnAlarmToggle.setImageResource(
                if (isAlarmOn) R.drawable.outline_alarm_on_24 else R.drawable.outline_alarm_off_24
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
            val month = datePicker.month
            val day = datePicker.dayOfMonth

            // 알람 설정
            if (isAlarmOn) {
                val alarmTimeStr = editAlarmTime.text.toString().trim()
                val alarmContent = editAlarmContent.text.toString().ifEmpty { "일정 알림" }

                if (alarmTimeStr.length >= 4) {
                    val h = alarmTimeStr.substring(0, 2).toIntOrNull()?.coerceIn(0, 23) ?: 0
                    val m = alarmTimeStr.substring(2, 4).toIntOrNull()?.coerceIn(0, 59) ?: 0

                    val calendar = Calendar.getInstance().apply {
                        set(year, month, day, h, m, 0)
                        if (timeInMillis < System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
                    }

                    val intent = Intent(requireContext(), AlarmReceiver::class.java)
                    intent.putExtra("title", title)
                    intent.putExtra("content", alarmContent)

                    val requestCode = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
                    val pendingIntent = PendingIntent.getBroadcast(
                        requireContext(),
                        requestCode,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val alarmManager =
                        requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)

                    Toast.makeText(
                        requireContext(),
                        "알람 설정됨: $alarmTimeStr",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(requireContext(), "알람 시간을 4자리로 입력해주세요 (예: 0930)", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            // 일정 저장 로직 (리스트 추가 등) 여기에 넣기
            dismiss()
        }
    }
}
