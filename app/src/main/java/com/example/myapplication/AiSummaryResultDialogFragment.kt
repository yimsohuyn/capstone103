package com.example.myapplication

import android.app.AlertDialog
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

//AI 요약 결과를 표시
// txt 파일 저장 및 공유 기능을 제공
class AiSummaryResultDialogFragment : DialogFragment() {

    private var summaryText: String = ""

    companion object {
        private const val ARG_SUMMARY = "arg_summary"

        // 요약 결과 텍스트를 전달받아 인스턴스를 생성
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

    // 요약 결과를 txt 파일로 앱 내부 저장소에 저장
    // 사용자가 파일 이름을 직접 지정할 수 있으며, 기본값은 Summary_yyyyMMdd_HHmmss 형식
    private fun saveSummaryAsTxt() {
        // 기본 파일 이름 생성 (기존 방식)
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val defaultName = "Summary_${dateFormat.format(Date())}"

        // 파일 이름 입력 다이얼로그 표시
        val editText = android.widget.EditText(requireContext()).apply {
            setText(defaultName)
            setPadding(48, 32, 48, 32)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            selectAll() // 전체 선택하여 바로 수정 가능
        }

        AlertDialog.Builder(requireContext())
            .setTitle("파일 이름 입력")
            .setMessage("저장할 파일 이름을 입력하세요.")
            .setView(editText)
            .setPositiveButton("저장") { _, _ ->
                val inputName = editText.text.toString().trim()
                if (inputName.isEmpty()) {
                    Toast.makeText(requireContext(), "파일 이름을 입력해 주세요.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (inputName.contains("/") || inputName.contains("\\")) {
                    Toast.makeText(requireContext(), "파일 이름에 / 또는 \\를 사용할 수 없습니다.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                performSave(inputName)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    // 실제 파일 저장 처리 (동일 파일명 존재 시 덮어쓰기 확인)
    private fun performSave(fileName: String) {
        try {
            val saveDirectory = File(requireContext().filesDir, "saved_notes")
            if (!saveDirectory.exists()) saveDirectory.mkdirs()

            // .txt 확장자가 없으면 자동 추가
            val finalName = if (fileName.endsWith(".txt", ignoreCase = true)) fileName else "$fileName.txt"
            val file = File(saveDirectory, finalName)

            // 동일한 이름의 파일이 이미 존재하는 경우 덮어쓰기 확인
            if (file.exists()) {
                AlertDialog.Builder(requireContext())
                    .setTitle("파일 덮어쓰기")
                    .setMessage("'$finalName' 파일이 이미 존재합니다. 덮어쓰시겠습니까?")
                    .setPositiveButton("덮어쓰기") { _, _ ->
                        writeAndNotify(file, finalName)
                    }
                    .setNegativeButton("취소", null)
                    .show()
                return
            }

            writeAndNotify(file, finalName)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // 파일 쓰기 및 결과 토스트 표시
    private fun writeAndNotify(file: File, fileName: String) {
        try {
            file.writeText(summaryText)
            Toast.makeText(requireContext(), "저장 완료: $fileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // 요약 결과를 다른 앱으로 공유한다.
    private fun shareSummary() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "AI 필기 요약 결과")
            putExtra(Intent.EXTRA_TEXT, summaryText)
        }
        startActivity(Intent.createChooser(shareIntent, "요약 공유"))
    }
}
