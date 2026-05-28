package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.example.myapplication.utils.QuizItem

class AiQuizResultDialogFragment : DialogFragment() {

    private var quizList: ArrayList<QuizItem> = arrayListOf()

    companion object {
        private const val ARG_QUIZ_LIST = "quiz_list"

        fun newInstance(quizList: ArrayList<QuizItem>): AiQuizResultDialogFragment {
            return AiQuizResultDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelableArrayList(ARG_QUIZ_LIST, quizList)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        quizList = loadQuizListFromArguments(arguments)
        Log.d("QUIZ_DEBUG", "AiQuizResultDialogFragment quizList size = ${quizList.size}")
    }

    override fun onResume() {
        super.onResume()

        if (!isAdded) return

        if (quizList.isEmpty()) {
            Toast.makeText(requireContext(), "표시할 퀴즈가 없습니다.", Toast.LENGTH_SHORT).show()
            dismissAllowingStateLoss()
            return
        }

        runCatching {
            val intent = Intent(requireContext(), QuizActivity::class.java).apply {
                putParcelableArrayListExtra("quizList", quizList)
            }
            startActivity(intent)
            dismissAllowingStateLoss()
        }.onFailure {
            Toast.makeText(
                requireContext(),
                "퀴즈 화면을 열 수 없습니다: ${it.message}",
                Toast.LENGTH_LONG
            ).show()
            Log.e("QUIZ_DEBUG", "Failed to open QuizActivity", it)
            dismissAllowingStateLoss()
        }
    }

    private fun loadQuizListFromArguments(bundle: Bundle?): ArrayList<QuizItem> {
        if (bundle == null) return arrayListOf()

        val directList = bundle.getParcelableArrayList<QuizItem>(ARG_QUIZ_LIST)
        if (!directList.isNullOrEmpty()) {
            return ArrayList(directList)
        }

        val questions = bundle.getStringArrayList("questions") ?: arrayListOf()
        val choices = bundle.getStringArrayList("choices") ?: arrayListOf()
        val answers = bundle.getStringArrayList("answers") ?: arrayListOf()
        val explanations = bundle.getStringArrayList("explanations") ?: arrayListOf()

        val count = listOf(
            questions.size,
            choices.size,
            answers.size,
            explanations.size
        ).minOrNull() ?: 0

        if (count == 0) {
            return arrayListOf()
        }

        val result = ArrayList<QuizItem>()

        for (i in 0 until count) {
            val optionList = choices[i]
                .split("\n")
                .map { it.trim() }
                .filter { it.isNotBlank() }

            val answerIndex = parseAnswerIndex(answers[i])

            val item = QuizItem(
                id = i,
                question = questions[i].trim(),
                options = optionList,
                answerIndex = answerIndex,
                explanation = explanations[i].trim(),
                selectedIndex = -1,
                isCorrect = false
            )

            if (item.isValidQuiz()) {
                result.add(item)
            }
        }

        return result
    }

    private fun parseAnswerIndex(answerText: String): Int {
        val match = Regex("""([1-4])\s*번""").find(answerText)
        val number = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return -1
        return number - 1
    }
}