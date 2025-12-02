package com.example.myapplication

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar as JavaCalendar

class DdayDialogFragment : DialogFragment() {
    var calendarService: Calendar? = null
    var onEventClick: ((DdayEvent) -> Unit)? = null

    private var recyclerView: RecyclerView? = null
    private var tvEmpty: TextView? = null
    private var tvTitle: TextView? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dday_dialog, null)  // xml 이름 확인

        recyclerView = view.findViewById(R.id.rvEvents)
        tvEmpty = view.findViewById(R.id.tvEmpty)
        tvTitle = view.findViewById(R.id.tvDialogTitle)

        recyclerView?.layoutManager = LinearLayoutManager(requireContext())

        val service = calendarService
        if (service == null) {
            tvTitle?.text = "구글 캘린더가 연결되지 않았습니다."
            tvEmpty?.visibility = View.VISIBLE
            recyclerView?.visibility = View.GONE
        } else {
            // 처음 열릴 때 한 번 로딩
            loadAndRenderEvents()
        }

        builder.setView(view)
        builder.setNegativeButton("닫기", null)
        return builder.create()
    }

    //HomeFragment 에서 디테일 화면 다녀온 후 호출/ 팝업 리스트를 다시 채우는 함수

    fun refreshFromParent() {
        loadAndRenderEvents()
    }

    // 실제로 구글 캘린더에서 이벤트 가져와서 RecyclerView 에 반영
    private fun loadAndRenderEvents() {
        val service = calendarService ?: return

        lifecycleScope.launch {
            try {
                val events = withContext(Dispatchers.IO) {
                    loadUpcomingEvents(service)
                }

                if (!isAdded || dialog == null || dialog?.isShowing != true) return@launch

                if (events.isEmpty()) {
                    tvEmpty?.visibility = View.VISIBLE
                    recyclerView?.visibility = View.GONE
                } else {
                    tvEmpty?.visibility = View.GONE
                    recyclerView?.visibility = View.VISIBLE

                    recyclerView?.adapter = DdayEventAdapter(events) { item ->
                        onEventClick?.invoke(item)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                tvTitle?.text = "D-Day 로딩 중 오류가 발생했습니다."
                tvEmpty?.visibility = View.VISIBLE
                recyclerView?.visibility = View.GONE
            }
        }
    }

    // 오늘 ~ 30일 뒤까지의 일정을 DdayEvent 로 변환
    private fun loadUpcomingEvents(service: Calendar): List<DdayEvent> {
        val now = System.currentTimeMillis()
        val in30Days = now + 30L * 24 * 60 * 60 * 1000L

        val items: List<Event> = service.events()
            .list("primary")
            .setSingleEvents(true)
            .setOrderBy("startTime")
            .setTimeMin(DateTime(now))
            .setTimeMax(DateTime(in30Days))
            .execute()
            .items.orEmpty()

        return items.mapNotNull { event ->
            val start = (event.start?.dateTime ?: event.start?.date)?.value
            val end = (event.end?.dateTime ?: event.end?.date)?.value

            if (start == null || end == null) return@mapNotNull null

            val title = event.summary ?: "제목 없음"
            val ddayLabel = calcDdayLabel(start)

            DdayEvent(
                title = title,
                startMillis = start,
                endMillis = end,
                eventId = event.id,
                htmlLink = event.htmlLink,
                ddayLabel = ddayLabel
            )
        }
    }

    private fun calcDdayLabel(startMillis: Long): String {
        val today = truncateToDay(System.currentTimeMillis())
        val eventDay = truncateToDay(startMillis)
        val diffDays =
            ((eventDay - today) / (24 * 60 * 60 * 1000L)).toInt()

        return when {
            diffDays > 0 -> "D-$diffDays"
            diffDays == 0 -> "D-Day"
            else -> "D+${-diffDays}"
        }
    }

    private fun truncateToDay(timeMillis: Long): Long {
        val cal = JavaCalendar.getInstance()
        cal.timeInMillis = timeMillis
        cal.set(JavaCalendar.HOUR_OF_DAY, 0)
        cal.set(JavaCalendar.MINUTE, 0)
        cal.set(JavaCalendar.SECOND, 0)
        cal.set(JavaCalendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}