package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AI 요약 결과를 표시하는 다이얼로그.
 * txt 파일 저장 및 공유 기능을 제공한다.
 */
class AiSummaryResultDialogFragment : DialogFragment() {

    private var summaryText: String = ""

    companion object {
        private const val ARG_SUMMARY = "arg_summary"

        /** 요약 결과 텍스트를 전달받아 인스턴스를 생성한다 */
        fun newInstance(summary: String): AiSummaryResultDialogFragment {
            val fragment = AiSummaryResultDialogFragment()
            val args = Bundle()
            args.putString(ARG_SUMMARY, summary)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_Material_Light_Dialog_MinWidth)
        summaryText = arguments?.getString(ARG_SUMMARY) ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_ai_summary_result, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvSummaryResult: TextView = view.findViewById(R.id.tvSummaryResult)
        val btnSaveAsTxt: Button = view.findViewById(R.id.btnSaveAsTxt)
        val btnShare: Button = view.findViewById(R.id.btnShare)

        // 요약 결과 텍스트 표시
        tvSummaryResult.text = summaryText

        // txt 파일로 저장
        btnSaveAsTxt.setOnClickListener {
            saveSummaryAsTxt()
        }

        // 공유
        btnShare.setOnClickListener {
            shareSummary()
        }
    }

    /**
     * 요약 결과를 txt 파일로 앱 내부 저장소에 저장한다.
     */
    private fun saveSummaryAsTxt() {
        try {
            val saveDirectory = File(requireContext().filesDir, "saved_notes")
            if (!saveDirectory.exists()) {
                saveDirectory.mkdirs()
            }

            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val fileName = "Summary_${dateFormat.format(Date())}.txt"
            val file = File(saveDirectory, fileName)

            file.writeText(summaryText)

            Toast.makeText(
                requireContext(),
                "저장 완료: $fileName",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 요약 결과를 다른 앱으로 공유한다.
     */
    private fun shareSummary() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "AI 필기 요약 결과")
            putExtra(Intent.EXTRA_TEXT, summaryText)
        }
        startActivity(Intent.createChooser(shareIntent, "요약 공유"))
    }
}
