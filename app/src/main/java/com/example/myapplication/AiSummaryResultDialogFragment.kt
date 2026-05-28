package com.example.myapplication

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AiSummaryResultDialogFragment : DialogFragment() {

    private var summaryText: String = ""

    companion object {
        private const val ARG_SUMMARY = "arg_summary"

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
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_Material_Light_Dialog)
        summaryText = arguments?.getString(ARG_SUMMARY) ?: ""
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (resources.displayMetrics.widthPixels * 0.92f).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_ai_summary_result, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvSummaryResult: TextView = view.findViewById(R.id.tvSummaryResult)
        val btnSaveAsTxt: TextView = view.findViewById(R.id.btnSaveAsTxt)
        val btnShare: TextView = view.findViewById(R.id.btnShare)
        val btnCancelResult: TextView = view.findViewById(R.id.btnCancelResult)
        val btnCloseTop: ImageView = view.findViewById(R.id.btnCloseTop)

        tvSummaryResult.text = summaryText

        btnSaveAsTxt.setOnClickListener {
            showSaveFileNameDialog()
        }

        btnShare.setOnClickListener {
            shareSummary()
        }

        btnCancelResult.setOnClickListener {
            dismiss()
        }

        btnCloseTop.setOnClickListener {
            dismiss()
        }
    }

    private fun showSaveFileNameDialog() {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val defaultName = "Summary_${dateFormat.format(Date())}"

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_save_file_name, null)

        val editFileName = dialogView.findViewById<EditText>(R.id.editFileNameInput)
        val btnCancel = dialogView.findViewById<TextView>(R.id.btnCancelSaveDialog)
        val btnSave = dialogView.findViewById<TextView>(R.id.btnConfirmSaveDialog)

        editFileName.setText(defaultName)
        editFileName.inputType = InputType.TYPE_CLASS_TEXT
        editFileName.selectAll()

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnSave.setOnClickListener {
            val inputName = editFileName.text.toString().trim()

            if (inputName.isEmpty()) {
                Toast.makeText(requireContext(), "파일 이름을 입력해 주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (inputName.contains("/") || inputName.contains("\\")) {
                Toast.makeText(requireContext(), "파일 이름에 / 또는 \\를 사용할 수 없습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            dialog.dismiss()
            performSave(inputName)
        }
    }

    private fun performSave(fileName: String) {
        try {
            val saveDirectory = File(requireContext().filesDir, "saved_notes")
            if (!saveDirectory.exists()) saveDirectory.mkdirs()

            val finalName =
                if (fileName.endsWith(".txt", ignoreCase = true)) fileName else "$fileName.txt"
            val file = File(saveDirectory, finalName)

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

    private fun writeAndNotify(file: File, fileName: String) {
        try {
            file.writeText(summaryText)
            Toast.makeText(requireContext(), "저장 완료: $fileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareSummary() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "AI 요약 결과")
            putExtra(Intent.EXTRA_TEXT, summaryText)
        }
        startActivity(Intent.createChooser(shareIntent, "요약 공유"))
    }
}