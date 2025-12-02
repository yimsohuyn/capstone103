package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar as JavaCalendar

class SearchActivity : AppCompatActivity() {

    private lateinit var etKeyword: EditText
    private lateinit var btnSearch: Button
    private lateinit var tvStatus: TextView
    private lateinit var rvSearchResult: RecyclerView

    private var calendarService: Calendar? = null

    // 마지막 검색어 저장
    private var lastKeyword: String = ""

    // 디테일 화면 다녀온 뒤 자동 갱신용
    private val detailLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && lastKeyword.isNotBlank()) {
                // 마지막 검색어로 다시 검색
                doSearch(fromUser = false)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.search_activity)

        etKeyword = findViewById(R.id.etKeyword)
        btnSearch = findViewById(R.id.btnSearch)
        tvStatus = findViewById(R.id.tvStatus)
        rvSearchResult = findViewById(R.id.rvSearchResult)

        rvSearchResult.layoutManager = LinearLayoutManager(this)

        initCalendarService()
        initUi()

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            intent.putExtra("go_to_home", true) // 홈 화면 이동 신호
            startActivity(intent)
            finish()
        }
    }

    private fun initCalendarService() {
        val account = GoogleSignIn.getLastSignedInAccount(this)

        if (account == null) {
            Toast.makeText(this, "구글 계정을 먼저 연결해주세요.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (!GoogleSignIn.hasPermissions(account, Scope(CalendarScopes.CALENDAR))) {
            Toast.makeText(this, "홈 화면에서 캘린더 연동을 다시 해주세요.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val credential = GoogleAccountCredential.usingOAuth2(
            this,
            listOf(CalendarScopes.CALENDAR)
        ).apply {
            selectedAccount = account.account
        }

        calendarService = Calendar.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName(getString(R.string.app_name))
            .build()
    }

    private fun initUi() {
        btnSearch.setOnClickListener {
            doSearch()
        }

        etKeyword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearch()
                true
            } else {
                false
            }
        }
    }
    private fun doSearch(fromUser: Boolean = true) {
        val keyword = if (fromUser) {
            etKeyword.text.toString().trim()
        } else {
            lastKeyword
        }

        if (keyword.isEmpty()) {
            Toast.makeText(this, "검색어를 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        lastKeyword = keyword

        val service = calendarService
        if (service == null) {
            Toast.makeText(this, "구글 캘린더 서비스가 준비되지 않았습니다.", Toast.LENGTH_LONG).show()
            return
        }

        tvStatus.text = "검색 중입니다..."

        lifecycleScope.launch {
            try {
                val items = withContext(Dispatchers.IO) {
                    searchEvents(service, keyword)
                }

                if (items.isEmpty()) {
                    tvStatus.text = "검색 결과가 없습니다."
                } else {
                    tvStatus.text = "총 ${items.size}개의 일정이 검색되었습니다."
                }

                rvSearchResult.adapter = SearchEventAdapter(items) { item ->
                    openDetail(item)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                tvStatus.text = "검색 중 오류가 발생했습니다."
            }
        }
    }

    private fun openDetail(item: SearchEventItem) {
        val intent = Intent(this, EventDetailActivity::class.java).apply {
            putExtra("title", item.title)
            putExtra("eventId", item.eventId)
            putExtra("htmlLink", item.htmlLink)
            putExtra("startMillis", item.startMillis)
            putExtra("endMillis", item.endMillis)
        }
        detailLauncher.launch(intent)
    }

    private fun searchEvents(service: Calendar, keyword: String): List<SearchEventItem> {
        val now = System.currentTimeMillis()

        val calPast = JavaCalendar.getInstance().apply {
            timeInMillis = now
            add(JavaCalendar.DAY_OF_YEAR, -30)
        }
        val timeMin = DateTime(calPast.timeInMillis)

        val items: List<Event> = service.events()
            .list("primary")
            .setSingleEvents(true)
            .setOrderBy("startTime")
            .setTimeMin(timeMin)
            .setQ(keyword)
            .setMaxResults(200)
            .execute()
            .items.orEmpty()

        return items.mapNotNull { event ->
            val start = (event.start?.dateTime ?: event.start?.date)?.value
            val end = (event.end?.dateTime ?: event.end?.date)?.value

            if (start == null || end == null) return@mapNotNull null

            SearchEventItem(
                title = event.summary ?: "제목 없음",
                startMillis = start,
                endMillis = end,
                eventId = event.id,
                htmlLink = event.htmlLink
            )
        }
    }
}
