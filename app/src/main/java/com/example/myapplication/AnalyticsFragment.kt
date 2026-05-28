package com.example.myapplication

import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.myapplication.utils.QuizHelper
import com.example.myapplication.utils.QuizItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class AnalyticsFragment : Fragment(R.layout.fragment_analytics) {

    private lateinit var btnBack: ImageButton
    private lateinit var cardHero: LinearLayout
    private lateinit var btnHeroStart: TextView
    private lateinit var menuQuizCreate: LinearLayout
    private lateinit var menuLearnedQuiz: LinearLayout
    private lateinit var tvQuizCount: TextView
    private lateinit var tvCorrectRate: TextView
    private lateinit var tvViewAllRecent: TextView
    private lateinit var btnResetAccuracy: TextView

    private lateinit var recentStudyCard1: LinearLayout
    private lateinit var recentStudyCard2: LinearLayout
    private lateinit var recentStudyCard3: LinearLayout

    private lateinit var tvRecentItemTitle1: TextView
    private lateinit var tvRecentItemSub1: TextView
    private lateinit var tvRecentItemTitle2: TextView
    private lateinit var tvRecentItemSub2: TextView
    private lateinit var tvRecentItemTitle3: TextView
    private lateinit var tvRecentItemSub3: TextView

    private var isGenerating = false
    private var loadingDialog: ProgressDialog? = null
    private var generateJob: Job? = null
    private val timeoutHandler = Handler(Looper.getMainLooper())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnBack = view.findViewById(R.id.btnBack)
        cardHero = view.findViewById(R.id.cardHero)
        btnHeroStart = view.findViewById(R.id.btnHeroStart)
        menuQuizCreate = view.findViewById(R.id.menuQuizCreate)
        menuLearnedQuiz = view.findViewById(R.id.menuLearnedQuiz)
        tvQuizCount = view.findViewById(R.id.tvQuizCount)
        tvCorrectRate = view.findViewById(R.id.tvCorrectRate)
        tvViewAllRecent = view.findViewById(R.id.tvViewAllRecent)
        btnResetAccuracy = view.findViewById(R.id.btnResetAccuracy)

        recentStudyCard1 = view.findViewById(R.id.recentStudyCard1)
        recentStudyCard2 = view.findViewById(R.id.recentStudyCard2)
        recentStudyCard3 = view.findViewById(R.id.recentStudyCard3)

        tvRecentItemTitle1 = view.findViewById(R.id.tvRecentItemTitle1)
        tvRecentItemSub1 = view.findViewById(R.id.tvRecentItemSub1)
        tvRecentItemTitle2 = view.findViewById(R.id.tvRecentItemTitle2)
        tvRecentItemSub2 = view.findViewById(R.id.tvRecentItemSub2)
        tvRecentItemTitle3 = view.findViewById(R.id.tvRecentItemTitle3)
        tvRecentItemSub3 = view.findViewById(R.id.tvRecentItemSub3)

        parentFragmentManager.setFragmentResultListener(
            AiQuizPromptDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->

            val prompt = bundle.getString(
                AiQuizPromptDialogFragment.BUNDLE_KEY_PROMPT
            ).orEmpty()

            val fileUris = getUriListFromBundle(
                bundle,
                AiQuizPromptDialogFragment.BUNDLE_KEY_FILE_URIS
            )

            generateQuiz(prompt, fileUris)
        }

        btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        btnResetAccuracy.setOnClickListener {
            showResetStatsDialog()
        }

        val openQuizDialog = {
            if (isGenerating) {
                Toast.makeText(requireContext(), "이미 퀴즈 생성 중입니다.", Toast.LENGTH_SHORT).show()
            } else {
                AiQuizPromptDialogFragment()
                    .show(parentFragmentManager, "AiQuizPromptDialog")
            }
        }

        cardHero.setOnClickListener { openQuizDialog() }
        btnHeroStart.setOnClickListener { openQuizDialog() }
        menuQuizCreate.setOnClickListener { openQuizDialog() }

        menuLearnedQuiz.setOnClickListener {
            findNavController().navigate(R.id.action_analyticsFragment_to_quizLearnedFragment)
        }

        tvViewAllRecent.setOnClickListener {
            findNavController().navigate(R.id.action_analyticsFragment_to_quizLearnedFragment)
        }

        recentStudyCard1.setOnClickListener {
            findNavController().navigate(R.id.action_analyticsFragment_to_quizLearnedFragment)
        }
        recentStudyCard2.setOnClickListener {
            findNavController().navigate(R.id.action_analyticsFragment_to_quizLearnedFragment)
        }
        recentStudyCard3.setOnClickListener {
            findNavController().navigate(R.id.action_analyticsFragment_to_quizLearnedFragment)
        }

        bindAnalyticsSummary()
        bindRecentStudyPreview()
    }

    override fun onResume() {
        super.onResume()
        bindAnalyticsSummary()
    }

    private fun bindAnalyticsSummary() {
        val learnedQuizCount = getWrongQuizCount(requireContext())
        val stats = QuizStatsStorage.getStats(requireContext())

        tvQuizCount.text = learnedQuizCount.toString()
        tvCorrectRate.text = "${stats.accuracyPercent}%"
    }

    private fun getWrongQuizCount(context: Context): Int {
        val prefs = context.getSharedPreferences(
            "wrong_quiz_prefs",
            Context.MODE_PRIVATE
        )

        val json = prefs.getString("wrong_quiz_list", null)
        if (json.isNullOrBlank()) return 0

        return try {
            val type = object : TypeToken<ArrayList<QuizItem>>() {}.type
            val list: ArrayList<QuizItem> = Gson().fromJson(json, type) ?: arrayListOf()
            list.size
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    private fun bindRecentStudyPreview() {
        tvRecentItemTitle1.text = "네트워크 퀴즈"
        tvRecentItemSub1.text = "2024.05.21 · 20문제"

        tvRecentItemTitle2.text = "운영체제 복습"
        tvRecentItemSub2.text = "2024.05.18 · 15문제"

        tvRecentItemTitle3.text = "데이터베이스 기초"
        tvRecentItemSub3.text = "2024.05.15 · 18문제"
    }

    private fun generateQuiz(prompt: String, fileUris: List<Uri>) {
        if (isGenerating) return

        if (prompt.isBlank()) {
            showFailDialog("프롬프트가 비어 있습니다.")
            return
        }

        if (fileUris.isEmpty()) {
            showFailDialog("파일을 먼저 선택하세요.")
            return
        }

        isGenerating = true
        menuQuizCreate.isEnabled = false
        cardHero.isEnabled = false
        btnHeroStart.isEnabled = false

        showLoadingDialog("AI가 퀴즈를 생성 중입니다...")

        timeoutHandler.removeCallbacksAndMessages(null)
        timeoutHandler.postDelayed({
            if (isGenerating) {
                generateJob?.cancel()
                finishGenerating()

                showFailDialog(
                    "퀴즈 생성 시간이 너무 오래 걸려 중단했습니다.\n\n" +
                            "사진이 흐리거나 파일 크기가 크면 오래 걸릴 수 있습니다.\n" +
                            "더 선명한 이미지나 짧은 TXT 파일로 다시 시도해주세요."
                )
            }
        }, 120000L)

        generateJob = lifecycleScope.launch {
            try {
                val rawResponse = withTimeoutOrNull(110000L) {
                    withContext(Dispatchers.IO) {
                        QuizHelper.generateQuizWithGeminiFromFiles(
                            context = requireContext(),
                            fileUris = fileUris,
                            userPrompt = prompt
                        )
                    }
                }

                if (rawResponse.isNullOrBlank()) {
                    finishGenerating()
                    showFailDialog(
                        "퀴즈 생성 실패\n\n" +
                                "AI 응답이 없습니다.\n" +
                                "잠시 후 다시 시도해주세요."
                    )
                    return@launch
                }

                val quizJsonText = cleanGeminiJson(rawResponse)

                if (quizJsonText.isBlank() || !quizJsonText.startsWith("[")) {
                    finishGenerating()
                    showFailDialog(
                        "퀴즈 생성 실패\n\n" +
                                "AI 응답 형식이 올바르지 않습니다.\n\n" +
                                "응답 일부:\n${rawResponse.take(500)}"
                    )
                    return@launch
                }

                val quizItems = withContext(Dispatchers.Default) {
                    QuizHelper.parseQuizItems(quizJsonText)
                        .filter { it.isValidQuiz() }
                }

                if (quizItems.isEmpty()) {
                    finishGenerating()
                    showFailDialog(
                        "퀴즈 파싱 실패\n\n" +
                                "AI가 문제를 만들었지만 앱에서 읽을 수 없는 형식입니다.\n" +
                                "다시 시도해주세요."
                    )
                    return@launch
                }

                finishGenerating()
                openQuizScreen(ArrayList(quizItems))

            } catch (e: Exception) {
                e.printStackTrace()
                finishGenerating()

                showFailDialog(
                    "퀴즈 생성 실패\n\n" +
                            (e.message ?: "알 수 없는 오류가 발생했습니다.")
                )
            }
        }
    }

    private fun showResetStatsDialog() {
        if (!isAdded) return

        val dialogView = layoutInflater.inflate(
            R.layout.dialog_reset_quiz_stats,
            null
        )

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnCancel = dialogView.findViewById<TextView>(R.id.btnCancelResetStats)
        val btnConfirm = dialogView.findViewById<TextView>(R.id.btnConfirmResetStats)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            QuizStatsStorage.reset(requireContext())
            bindAnalyticsSummary()
            Toast.makeText(requireContext(), "정답률이 초기화되었습니다.", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun cleanGeminiJson(response: String): String {
        val text = response
            .trim()
            .replace("```json", "")
            .replace("```JSON", "")
            .replace("```", "")
            .trim()

        val startIndex = text.indexOf("[")
        val endIndex = text.lastIndexOf("]")

        return if (startIndex >= 0 && endIndex >= startIndex) {
            text.substring(startIndex, endIndex + 1).trim()
        } else {
            text
        }
    }

    private fun finishGenerating() {
        timeoutHandler.removeCallbacksAndMessages(null)
        hideLoadingDialog()
        isGenerating = false

        if (isAdded) {
            menuQuizCreate.isEnabled = true
            cardHero.isEnabled = true
            btnHeroStart.isEnabled = true
            bindAnalyticsSummary()
        }
    }

    private fun showLoadingDialog(message: String) {
        if (!isAdded) return

        loadingDialog?.dismiss()

        loadingDialog = ProgressDialog(requireContext()).apply {
            setMessage(message)
            setCancelable(false)
            show()
        }
    }

    private fun hideLoadingDialog() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    private fun showFailDialog(message: String) {
        if (!isAdded) return

        hideLoadingDialog()

        AlertDialog.Builder(requireContext())
            .setTitle("퀴즈 생성 실패")
            .setMessage(message)
            .setPositiveButton("확인", null)
            .show()
    }

    private fun openQuizScreen(items: ArrayList<QuizItem>) {
        if (!isAdded) return

        val intent = Intent(requireContext(), QuizActivity::class.java).apply {
            putParcelableArrayListExtra("quizList", items)
            putExtra("currentIndex", 0)
            putExtra("autoSaveWrongQuiz", true)
            putExtra("countForAnalyticsStats", true)
        }

        startActivity(intent)
    }

    private fun getUriListFromBundle(bundle: Bundle, key: String): ArrayList<Uri> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bundle.getParcelableArrayList(key, Uri::class.java) ?: arrayListOf()
        } else {
            @Suppress("DEPRECATION")
            bundle.getParcelableArrayList<Uri>(key) ?: arrayListOf()
        }
    }

    override fun onDestroyView() {
        timeoutHandler.removeCallbacksAndMessages(null)
        generateJob?.cancel()
        hideLoadingDialog()
        isGenerating = false
        super.onDestroyView()
    }
}