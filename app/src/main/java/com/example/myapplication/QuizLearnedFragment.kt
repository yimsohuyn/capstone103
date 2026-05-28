package com.example.myapplication

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.utils.QuizItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class QuizLearnedFragment : Fragment() {

    private lateinit var recyclerLearnedQuiz: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: LearnedQuizAdapter
    private lateinit var btnBack: ImageButton

    private lateinit var tvTotalQuizCount: TextView
    private lateinit var tvRecentDate: TextView
    private lateinit var etSearch: EditText
    private lateinit var btnClearSearch: ImageButton

    private lateinit var chipAll: TextView
    private lateinit var chipRecent: TextView
    private lateinit var chipWrongOnly: TextView

    private val wrongQuizList = mutableListOf<QuizItem>()
    private val savedQuizItems = mutableListOf<SavedQuizItem>()
    private val filteredItems = mutableListOf<SavedQuizItem>()

    private var currentFilter = FilterType.ALL
    private var currentQuery = ""

    enum class FilterType {
        ALL, RECENT, WRONG_ONLY
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(
            R.layout.fragment_learned_quiz,
            container,
            false
        )
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        bindViews(view)
        setupRecyclerView()
        setupListeners()
        loadWrongQuizzes()

        btnBack.setOnClickListener {
            requireActivity()
                .onBackPressedDispatcher
                .onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        loadWrongQuizzes()
    }

    private fun bindViews(view: View) {
        recyclerLearnedQuiz = view.findViewById(R.id.recyclerLearnedQuiz)
        tvEmpty = view.findViewById(R.id.tvEmpty)
        btnBack = view.findViewById(R.id.btnBack)

        tvTotalQuizCount = view.findViewById(R.id.tvTotalQuizCount)
        tvRecentDate = view.findViewById(R.id.tvRecentDate)
        etSearch = view.findViewById(R.id.etSearch)
        btnClearSearch = view.findViewById(R.id.btnClearSearch)

        chipAll = view.findViewById(R.id.chipAll)
        chipRecent = view.findViewById(R.id.chipRecent)
        chipWrongOnly = view.findViewById(R.id.chipWrongOnly)
    }

    private fun setupRecyclerView() {
        adapter = LearnedQuizAdapter(
            items = filteredItems,
            onItemClick = { item ->
                openWrongQuiz(item)
            },
            onRenameClick = {
                Toast.makeText(
                    requireContext(),
                    "오답퀴즈는 이름 변경이 불가능합니다.",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDeleteClick = { item ->
                showDeleteDialog(item)
            }
        )

        recyclerLearnedQuiz.layoutManager =
            LinearLayoutManager(requireContext())

        recyclerLearnedQuiz.adapter = adapter
    }

    private fun setupListeners() {
        btnClearSearch.setOnClickListener {
            etSearch.setText("")
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                currentQuery = s?.toString().orEmpty()
                applyFilterAndSearch()
            }

            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {}
        })

        chipAll.setOnClickListener {
            currentFilter = FilterType.ALL
            updateChipUi()
            applyFilterAndSearch()
        }

        chipRecent.setOnClickListener {
            currentFilter = FilterType.RECENT
            updateChipUi()
            applyFilterAndSearch()
        }

        chipWrongOnly.setOnClickListener {
            currentFilter = FilterType.WRONG_ONLY
            updateChipUi()
            applyFilterAndSearch()
        }
    }

    private fun updateChipUi() {
        chipAll.setBackgroundResource(
            if (currentFilter == FilterType.ALL) {
                R.drawable.bg_filter_chip_selected
            } else {
                R.drawable.bg_filter_chip_unselected
            }
        )

        chipRecent.setBackgroundResource(
            if (currentFilter == FilterType.RECENT) {
                R.drawable.bg_filter_chip_selected
            } else {
                R.drawable.bg_filter_chip_unselected
            }
        )

        chipWrongOnly.setBackgroundResource(
            if (currentFilter == FilterType.WRONG_ONLY) {
                R.drawable.bg_filter_chip_selected
            } else {
                R.drawable.bg_filter_chip_unselected
            }
        )

        chipAll.setTextColor(
            if (currentFilter == FilterType.ALL) {
                Color.WHITE
            } else {
                requireContext().getColor(R.color.text_primary)
            }
        )

        chipRecent.setTextColor(
            if (currentFilter == FilterType.RECENT) {
                Color.WHITE
            } else {
                requireContext().getColor(R.color.text_primary)
            }
        )

        chipWrongOnly.setTextColor(
            if (currentFilter == FilterType.WRONG_ONLY) {
                Color.WHITE
            } else {
                requireContext().getColor(R.color.text_primary)
            }
        )
    }

    private fun loadWrongQuizzes() {
        if (!isAdded) return

        val prefs =
            requireContext().getSharedPreferences(
                "wrong_quiz_prefs",
                android.content.Context.MODE_PRIVATE
            )

        val json =
            prefs.getString(
                "wrong_quiz_list",
                null
            )

        wrongQuizList.clear()

        if (!json.isNullOrBlank()) {
            val type =
                object : TypeToken<ArrayList<QuizItem>>() {}.type

            val loadedList: ArrayList<QuizItem> =
                Gson().fromJson(json, type)

            wrongQuizList.addAll(loadedList)
        }

        savedQuizItems.clear()

        wrongQuizList.forEachIndexed { index, item ->
            savedQuizItems.add(
                SavedQuizItem(
                    fileName = item.question.ifBlank {
                        "오답퀴즈 ${index + 1}"
                    },
                    filePath = "",
                    savedAt = System.currentTimeMillis() - (index * 100000L)
                )
            )
        }

        bindSummaryCard()
        updateChipUi()
        applyFilterAndSearch()
    }

    private fun bindSummaryCard() {
        tvTotalQuizCount.text = "${savedQuizItems.size}개"

        if (savedQuizItems.isNotEmpty()) {
            val latest = savedQuizItems.maxByOrNull { it.savedAt }
            val dateText =
                SimpleDateFormat(
                    "yyyy.MM.dd",
                    Locale.getDefault()
                ).format(Date(latest?.savedAt ?: System.currentTimeMillis()))

            tvRecentDate.text = dateText
        } else {
            tvRecentDate.text = "-"
        }
    }

    private fun applyFilterAndSearch() {
        var list = savedQuizItems.toList()

        list = when (currentFilter) {
            FilterType.ALL -> list
            FilterType.RECENT -> list.sortedByDescending { it.savedAt }.take(5)
            FilterType.WRONG_ONLY -> list
        }

        if (currentQuery.isNotBlank()) {
            list = list.filter {
                it.fileName.contains(
                    currentQuery,
                    ignoreCase = true
                )
            }
        }

        filteredItems.clear()
        filteredItems.addAll(list)

        adapter.submitList(filteredItems.toList())
        updateEmptyView()
    }

    private fun updateEmptyView() {
        val isEmpty = filteredItems.isEmpty()

        tvEmpty.visibility =
            if (isEmpty) {
                View.VISIBLE
            } else {
                View.GONE
            }

        recyclerLearnedQuiz.visibility =
            if (isEmpty) {
                View.GONE
            } else {
                View.VISIBLE
            }
    }

    private fun openWrongQuiz(savedItem: SavedQuizItem) {
        val index = savedQuizItems.indexOf(savedItem)

        if (index == -1) {
            Toast.makeText(
                requireContext(),
                "해당 오답퀴즈를 찾을 수 없습니다.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val quizItem =
            wrongQuizList.getOrNull(index)

        if (quizItem == null) {
            Toast.makeText(
                requireContext(),
                "다시 풀 오답이 없습니다.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val retryQuiz =
            arrayListOf(
                quizItem.resetForRetry()
            )

        val intent =
            Intent(
                requireContext(),
                QuizActivity::class.java
            ).apply {

                putParcelableArrayListExtra(
                    "quizList",
                    retryQuiz
                )

                putExtra(
                    "currentIndex",
                    0
                )

                putExtra(
                    "countForAnalyticsStats",
                    false
                )
            }

        startActivity(intent)
    }

    private fun showDeleteDialog(item: SavedQuizItem) {
        val dialog =
            AlertDialog.Builder(requireContext())
                .setTitle("삭제")
                .setMessage("이 오답퀴즈를 삭제하시겠습니까?")
                .setPositiveButton("삭제", null)
                .setNegativeButton("취소", null)
                .create()

        dialog.setOnShowListener {
            val positiveButton =
                dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
                )

            val negativeButton =
                dialog.getButton(
                    AlertDialog.BUTTON_NEGATIVE
                )

            positiveButton.setTextColor(Color.RED)
            negativeButton.setTextColor(Color.BLACK)

            positiveButton.setOnClickListener {
                val index = savedQuizItems.indexOf(item)

                if (index != -1) {
                    wrongQuizList.removeAt(index)
                    saveWrongQuizList()
                    loadWrongQuizzes()

                    Toast.makeText(
                        requireContext(),
                        "삭제되었습니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                dialog.dismiss()
            }

            negativeButton.setOnClickListener {
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun saveWrongQuizList() {
        val prefs =
            requireContext().getSharedPreferences(
                "wrong_quiz_prefs",
                android.content.Context.MODE_PRIVATE
            )

        val json =
            Gson().toJson(wrongQuizList)

        prefs.edit()
            .putString(
                "wrong_quiz_list",
                json
            )
            .apply()
    }
}