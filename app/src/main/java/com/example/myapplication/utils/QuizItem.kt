package com.example.myapplication.utils

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class QuizItem(
    val id: Int = 0,
    val question: String = "",

    // 객관식
    val options: List<String> = emptyList(),
    val answerIndex: Int = -1,

    // 주관식
    val answerText: String = "",

    // 해설
    val explanation: String = "",

    // choice = 객관식, blank = 주관식
    var type: String = "choice",

    // 사용자 답변
    var selectedIndex: Int = -1,
    var userAnswer: String? = null,

    // 채점 결과
    var isCorrect: Boolean = false
) : Parcelable {

    fun isBlankType(): Boolean {
        return type == "blank" || answerText.isNotBlank()
    }

    fun isChoiceType(): Boolean {
        return !isBlankType()
    }

    fun hasValidOptions(): Boolean {
        return if (isBlankType()) {
            true
        } else {
            options.size >= 2 && options.all { it.isNotBlank() }
        }
    }

    fun hasValidAnswer(): Boolean {
        return if (isBlankType()) {
            answerText.isNotBlank()
        } else {
            answerIndex in options.indices
        }
    }

    fun isAnswered(): Boolean {
        return if (isBlankType()) {
            !userAnswer.isNullOrBlank()
        } else {
            selectedIndex in options.indices
        }
    }

    fun isValidQuiz(): Boolean {
        if (question.isBlank()) return false
        if (!hasValidOptions()) return false
        if (!hasValidAnswer()) return false
        return true
    }

    private fun normalize(text: String?): String {
        return text
            ?.trim()
            ?.lowercase()
            ?.replace("\\s+".toRegex(), "")
            ?: ""
    }

    fun checkAnswer(): Boolean {
        isCorrect = if (isBlankType()) {
            normalize(userAnswer) == normalize(answerText)
        } else {
            selectedIndex == answerIndex
        }

        return isCorrect
    }

    fun resetForRetry(): QuizItem {
        return copy(
            selectedIndex = -1,
            userAnswer = null,
            isCorrect = false
        )
    }

    fun selectedAnswerText(): String {
        return if (isBlankType()) {
            userAnswer?.ifBlank { "미응답" } ?: "미응답"
        } else {
            if (selectedIndex in options.indices) {
                "${selectedIndex + 1}번 ${options[selectedIndex]}"
            } else {
                "미응답"
            }
        }
    }

    fun correctAnswerText(): String {
        return if (isBlankType()) {
            answerText.ifBlank { "정답 없음" }
        } else {
            if (answerIndex in options.indices) {
                "${answerIndex + 1}번 ${options[answerIndex]}"
            } else {
                "정답 없음"
            }
        }
    }

    fun selectedOptionText(): String {
        return if (isBlankType()) {
            userAnswer?.ifBlank { "미응답" } ?: "미응답"
        } else {
            if (selectedIndex in options.indices) {
                options[selectedIndex]
            } else {
                "미응답"
            }
        }
    }

    fun answerOptionText(): String {
        return if (isBlankType()) {
            answerText.ifBlank { "정답 없음" }
        } else {
            if (answerIndex in options.indices) {
                options[answerIndex]
            } else {
                "정답 없음"
            }
        }
    }
}
