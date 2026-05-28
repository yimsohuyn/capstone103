package com.example.myapplication

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.utils.QuizItem

class QuizActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var tvScreenTitle: TextView
    private lateinit var tvModeBadge: TextView
    private lateinit var tvPageIndicator: TextView
    private lateinit var tvQuestion: TextView
    private lateinit var tvHint: TextView
    private lateinit var hintBox: LinearLayout
    private lateinit var progressQuiz: ProgressBar

    private lateinit var optionLayouts: List<LinearLayout>
    private lateinit var optionIcons: List<ImageView>
    private lateinit var optionTexts: List<TextView>

    private lateinit var etAnswer: EditText

    private lateinit var btnPrev: TextView
    private lateinit var btnNext: TextView
    private lateinit var btnSubmit: TextView

    private var quizList: ArrayList<QuizItem> = arrayListOf()
    private var currentIndex: Int = 0
    private var isWrongReviewMode: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz)

        bindViews()
        loadExtras()
        updateScreenModeUi()

        if (quizList.isEmpty()) {
            Toast.makeText(this, "퀴즈 데이터가 없습니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupClickListeners()
        updateQuizPage()
    }

    private fun bindViews() {
        btnBack = findViewById(R.id.btnBack)
        tvScreenTitle = findViewById(R.id.tvScreenTitle)
        tvModeBadge = findViewById(R.id.tvModeBadge)
        tvPageIndicator = findViewById(R.id.tvPageIndicator)
        tvQuestion = findViewById(R.id.tvQuestion)
        tvHint = findViewById(R.id.tvHint)
        hintBox = findViewById(R.id.hintBox)
        progressQuiz = findViewById(R.id.progressQuiz)

        optionLayouts = listOf(
            findViewById(R.id.optionLayout1),
            findViewById(R.id.optionLayout2),
            findViewById(R.id.optionLayout3),
            findViewById(R.id.optionLayout4)
        )

        optionIcons = listOf(
            findViewById(R.id.ivOption1),
            findViewById(R.id.ivOption2),
            findViewById(R.id.ivOption3),
            findViewById(R.id.ivOption4)
        )

        optionTexts = listOf(
            findViewById(R.id.tvOption1),
            findViewById(R.id.tvOption2),
            findViewById(R.id.tvOption3),
            findViewById(R.id.tvOption4)
        )

        etAnswer = findViewById(R.id.etAnswer)

        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)
        btnSubmit = findViewById(R.id.btnSubmit)
    }

    private fun loadExtras() {
        quizList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra("quizList", QuizItem::class.java) ?: arrayListOf()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<QuizItem>("quizList") ?: arrayListOf()
        }

        currentIndex = intent.getIntExtra("currentIndex", 0)

        if (currentIndex !in quizList.indices) {
            currentIndex = 0
        }

        isWrongReviewMode = !intent.getBooleanExtra("countForAnalyticsStats", false)
    }

    private fun updateScreenModeUi() {
        if (isWrongReviewMode) {
            tvScreenTitle.text = "오답복습"
            tvModeBadge.text = "오답 복습 모드"
        } else {
            tvScreenTitle.text = "퀴즈 풀기"
            tvModeBadge.text = "생성 퀴즈"
        }
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        optionLayouts.forEachIndexed { index, layout ->
            layout.setOnClickListener {
                selectOption(index)
            }
        }

        btnPrev.setOnClickListener {
            movePage(-1)
        }

        btnNext.setOnClickListener {
            movePage(1)
        }

        btnSubmit.setOnClickListener {
            saveCurrentAnswer()
            submitQuiz()
        }
    }

    private fun movePage(direction: Int) {
        saveCurrentAnswer()

        val nextIndex = currentIndex + direction

        if (nextIndex in quizList.indices) {
            currentIndex = nextIndex
            updateQuizPage()
        }
    }

    private fun saveCurrentAnswer() {
        val item = quizList.getOrNull(currentIndex) ?: return

        if (item.isBlankType()) {
            item.userAnswer = etAnswer.text.toString().trim()
        }
    }

    private fun selectOption(index: Int) {
        val item = quizList.getOrNull(currentIndex) ?: return
        if (item.isBlankType()) return

        item.selectedIndex = index
        updateOptionSelection(index)
    }

    private fun updateQuizPage() {
        val item = quizList.getOrNull(currentIndex) ?: return

        tvPageIndicator.text = "문제 ${currentIndex + 1} / ${quizList.size}"
        tvQuestion.text = item.question

        val progress = ((currentIndex + 1) * 100f / quizList.size).toInt()
        progressQuiz.progress = progress

        if (isWrongReviewMode) {
            hintBox.visibility = View.VISIBLE

            tvHint.text = if (item.explanation.isNullOrBlank()) {
                "문제를 다시 읽고 핵심 키워드를 떠올려보세요."
            } else {
                item.explanation
            }
        } else {
            hintBox.visibility = View.GONE
        }

        if (item.isBlankType()) {
            showBlankQuestion(item)
        } else {
            showChoiceQuestion(item)
        }

        btnPrev.visibility =
            if (currentIndex == 0) View.INVISIBLE else View.VISIBLE

        btnNext.visibility =
            if (currentIndex == quizList.lastIndex) View.GONE else View.VISIBLE

        btnSubmit.visibility =
            if (currentIndex == quizList.lastIndex) View.VISIBLE else View.GONE
    }

    private fun showBlankQuestion(item: QuizItem) {
        optionLayouts.forEach {
            it.visibility = View.GONE
        }

        etAnswer.visibility = View.VISIBLE
        etAnswer.setText(item.userAnswer ?: "")
        updateOptionSelection(-1)
    }

    private fun showChoiceQuestion(item: QuizItem) {
        etAnswer.visibility = View.GONE

        optionLayouts.forEach {
            it.visibility = View.VISIBLE
        }

        for (i in 0 until 4) {
            optionTexts[i].text = "${i + 1}번 ${item.options.getOrNull(i).orEmpty()}"
        }

        updateOptionSelection(item.selectedIndex)
    }

    private fun updateOptionSelection(selectedIndex: Int) {
        optionLayouts.forEachIndexed { index, layout ->
            if (index == selectedIndex) {
                layout.setBackgroundResource(R.drawable.bg_quiz_option_selected)
            } else {
                layout.setBackgroundResource(R.drawable.bg_quiz_option_unselected)
            }
        }

        optionIcons.forEachIndexed { index, imageView ->
            imageView.setImageResource(
                if (index == selectedIndex) {
                    R.drawable.ic_quiz_circle_selected
                } else {
                    R.drawable.ic_quiz_circle_unselected
                }
            )
        }
    }

    private fun isAnswered(item: QuizItem): Boolean {
        return if (item.isBlankType()) {
            !item.userAnswer.isNullOrBlank()
        } else {
            item.selectedIndex in 0..3
        }
    }

    private fun submitQuiz() {
        saveCurrentAnswer()

        val unansweredIndex = quizList.indexOfFirst { !isAnswered(it) }

        if (unansweredIndex != -1) {
            currentIndex = unansweredIndex
            updateQuizPage()
            Toast.makeText(this, "안 푼 문제가 있습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        quizList.forEach {
            it.checkAnswer()
        }

        val correctCount = quizList.count { it.isCorrect }
        val wrongList = ArrayList(quizList.filter { !it.isCorrect })

        val resultIntent = Intent(this, QuizResultActivity::class.java).apply {
            putExtra("correctCount", correctCount)
            putExtra("totalCount", quizList.size)
            putParcelableArrayListExtra("quizList", quizList)
            putParcelableArrayListExtra("wrongList", wrongList)
            putExtra(
                "countForAnalyticsStats",
                intent.getBooleanExtra("countForAnalyticsStats", false)
            )
        }

        startActivity(resultIntent)
        finish()
    }
}