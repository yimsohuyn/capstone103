package com.example.myapplication

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.utils.QuizItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class QuizResultActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton

    private lateinit var tvCorrectSummary: TextView
    private lateinit var tvWrongSummary: TextView
    private lateinit var tvAccuracySummary: TextView

    private lateinit var resultItemsContainer: LinearLayout

    private lateinit var btnWrongNote: TextView
    private lateinit var btnRetryWrong: TextView
    private lateinit var btnClose: TextView

    private var quizList: ArrayList<QuizItem> =
        arrayListOf()

    private var wrongList: ArrayList<QuizItem> =
        arrayListOf()

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_quiz_result)

        btnBack =
            findViewById(R.id.btnBack)

        tvCorrectSummary =
            findViewById(R.id.tvCorrectSummary)

        tvWrongSummary =
            findViewById(R.id.tvWrongSummary)

        tvAccuracySummary =
            findViewById(R.id.tvAccuracySummary)

        resultItemsContainer =
            findViewById(R.id.resultItemsContainer)

        btnWrongNote =
            findViewById(R.id.btnWrongNote)

        btnRetryWrong =
            findViewById(R.id.btnRetryWrong)

        btnClose =
            findViewById(R.id.btnClose)

        val correctCount =
            intent.getIntExtra(
                "correctCount",
                0
            )

        val totalCount =
            intent.getIntExtra(
                "totalCount",
                0
            )

        val countForAnalyticsStats =
            intent.getBooleanExtra(
                "countForAnalyticsStats",
                false
            )

        quizList =
            getQuizListExtra("quizList")

        val receivedWrongList =
            getQuizListExtra("wrongList")

        wrongList =
            if (receivedWrongList.isNotEmpty()) {

                ArrayList(
                    receivedWrongList.distinctBy {
                        it.id to it.question
                    }
                )

            } else {

                ArrayList(
                    quizList
                        .filter {
                            !it.isCorrect
                        }
                        .distinctBy {
                            it.id to it.question
                        }
                )
            }

        saveWrongQuizzes(wrongList)

        // 생성해서 처음 푼 퀴즈일 때만 학습 통계 반영
        if (savedInstanceState == null && countForAnalyticsStats) {
            QuizStatsStorage.addSession(
                context = this,
                solvedCount = totalCount,
                correctCount = correctCount
            )
        }

        val wrongCount =
            wrongList.size

        val accuracy =
            if (totalCount > 0) {

                (
                        (
                                correctCount * 100f
                                ) / totalCount
                        ).toInt()

            } else {

                0
            }

        bindSummary(
            correctCount = correctCount,
            totalCount = totalCount,
            wrongCount = wrongCount,
            accuracy = accuracy
        )

        bindResultItems()

        btnBack.setOnClickListener {
            finish()
        }

        btnWrongNote.setOnClickListener {
            showWrongNoteDialog()
        }

        btnRetryWrong.setOnClickListener {
            retryWrongAnswers()
        }

        btnClose.setOnClickListener {

            val intent =
                Intent(
                    this,
                    MainActivity::class.java
                )

            intent.flags =
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK

            intent.putExtra(
                "open_tab",
                "learning_analysis"
            )

            startActivity(intent)

            finish()
        }
    }

    private fun bindSummary(
        correctCount: Int,
        totalCount: Int,
        wrongCount: Int,
        accuracy: Int
    ) {

        tvCorrectSummary.text =
            "$correctCount / $totalCount"

        tvWrongSummary.text =
            wrongCount.toString()

        tvAccuracySummary.text =
            "${accuracy}%"
    }

    private fun bindResultItems() {

        resultItemsContainer.removeAllViews()

        if (wrongList.isEmpty()) {

            val itemView =
                LayoutInflater.from(this)
                    .inflate(
                        R.layout.item_quiz_result,
                        resultItemsContainer,
                        false
                    )

            val tvItemIndex =
                itemView.findViewById<TextView>(R.id.tvItemIndex)

            val tvItemTitle =
                itemView.findViewById<TextView>(R.id.tvItemTitle)

            val tvItemStatus =
                itemView.findViewById<TextView>(R.id.tvItemStatus)

            tvItemIndex.text =
                "✓"

            tvItemTitle.text =
                "모든 문제를 맞혔습니다."

            tvItemStatus.text =
                "정답"

            tvItemStatus.setBackgroundResource(
                R.drawable.bg_result_status_correct
            )

            resultItemsContainer.addView(itemView)

            return
        }

        wrongList.forEachIndexed { index, item ->

            val itemView =
                LayoutInflater.from(this)
                    .inflate(
                        R.layout.item_quiz_result,
                        resultItemsContainer,
                        false
                    )

            val tvItemIndex =
                itemView.findViewById<TextView>(R.id.tvItemIndex)

            val tvItemTitle =
                itemView.findViewById<TextView>(R.id.tvItemTitle)

            val tvItemStatus =
                itemView.findViewById<TextView>(R.id.tvItemStatus)

            tvItemIndex.text =
                (index + 1).toString()

            tvItemTitle.text =
                item.question

            tvItemStatus.text =
                "오답"

            tvItemStatus.setBackgroundResource(
                R.drawable.bg_result_status_error
            )

            resultItemsContainer.addView(itemView)
        }
    }

    private fun showWrongNoteDialog() {

        if (wrongList.isEmpty()) {

            AlertDialog.Builder(this)
                .setTitle("오답노트")
                .setMessage("오답이 없습니다.")
                .setPositiveButton(
                    "확인",
                    null
                )
                .show()

            return
        }

        val message =
            buildString {

                wrongList.forEachIndexed {
                        index,
                        item ->

                    append(
                        "${index + 1}. ${item.question}\n"
                    )

                    append(
                        "내 답: ${item.selectedAnswerText()}\n"
                    )

                    append(
                        "정답: ${item.correctAnswerText()}\n"
                    )

                    append(
                        "해설: ${
                            item.explanation.ifBlank {
                                "해설 없음"
                            }
                        }"
                    )

                    if (index != wrongList.lastIndex) {
                        append("\n\n")
                    }
                }
            }

        AlertDialog.Builder(this)
            .setTitle("오답노트")
            .setMessage(message)
            .setPositiveButton(
                "닫기",
                null
            )
            .show()
    }

    private fun retryWrongAnswers() {

        if (wrongList.isEmpty()) {

            AlertDialog.Builder(this)
                .setTitle("안내")
                .setMessage("다시 풀 오답이 없습니다.")
                .setPositiveButton(
                    "확인",
                    null
                )
                .show()

            return
        }

        val retryList =
            ArrayList(
                wrongList.map {
                    it.resetForRetry()
                }
            )

        val intent =
            Intent(
                this,
                QuizActivity::class.java
            ).apply {

                putParcelableArrayListExtra(
                    "quizList",
                    retryList
                )

                putExtra(
                    "countForAnalyticsStats",
                    false
                )
            }

        startActivity(intent)
    }

    private fun saveWrongQuizzes(
        newWrongList: List<QuizItem>
    ) {

        val prefs =
            getSharedPreferences(
                "wrong_quiz_prefs",
                MODE_PRIVATE
            )

        val gson =
            Gson()

        val type =
            object :
                TypeToken<ArrayList<QuizItem>>() {}.type

        val existingJson =
            prefs.getString(
                "wrong_quiz_list",
                null
            )

        val existingList:
                ArrayList<QuizItem> =

            if (existingJson != null) {

                gson.fromJson(
                    existingJson,
                    type
                )

            } else {

                arrayListOf()
            }

        val mergedList =
            ArrayList(existingList)

        newWrongList.forEach { newItem ->

            val alreadyExists =
                mergedList.any {
                    it.id == newItem.id &&
                            it.question == newItem.question
                }

            if (!alreadyExists) {
                mergedList.add(newItem)
            }
        }

        prefs.edit()
            .putString(
                "wrong_quiz_list",
                gson.toJson(mergedList)
            )
            .apply()
    }

    private fun getQuizListExtra(
        key: String
    ): ArrayList<QuizItem> {

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            intent.getParcelableArrayListExtra(
                key,
                QuizItem::class.java
            ) ?: arrayListOf()

        } else {

            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<QuizItem>(
                key
            ) ?: arrayListOf()
        }
    }
}