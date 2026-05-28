package com.example.myapplication

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import java.io.File

class AiQuizPromptDialogFragment : DialogFragment() {

    private lateinit var btnAddQuizFile: LinearLayout
    private lateinit var btnLoadSavedSummary: LinearLayout
    private lateinit var selectedFileContainer: LinearLayout

    private lateinit var tvSelectedFileName: TextView
    private lateinit var tvSelectedFileType: TextView
    private lateinit var btnRemoveSelectedFile: TextView

    private lateinit var etQuizPrompt: EditText
    private lateinit var btnGenerateQuiz: TextView

    private lateinit var checkMultipleChoice: CheckBox
    private lateinit var checkShortAnswer: CheckBox

    private val selectedFiles =
        mutableListOf<SelectedFileItem>()

    private val pickFilesLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { uris ->

            if (uris.isNullOrEmpty()) {

                showErrorDialog(
                    "파일 선택 실패",
                    "선택한 파일이 없습니다."
                )

                return@registerForActivityResult
            }

            var addedCount = 0

            uris.forEach { uri ->

                val mimeType =
                    requireContext()
                        .contentResolver
                        .getType(uri)
                        .orEmpty()

                val fileName =
                    getDisplayName(uri)
                        ?: "이름없는파일"

                val fileType =
                    detectFileType(
                        fileName,
                        mimeType
                    )

                if (fileType == FileType.UNKNOWN) {
                    return@forEach
                }

                if (selectedFiles.any { it.uri == uri }) {
                    return@forEach
                }

                try {
                    requireContext()
                        .contentResolver
                        .takePersistableUriPermission(
                            uri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                } catch (_: Exception) {
                }

                selectedFiles.add(
                    SelectedFileItem(
                        fileName = fileName,
                        uri = uri,
                        type = fileType
                    )
                )

                addedCount++
            }

            updateSelectedFileUi()

            if (addedCount <= 0) {

                showErrorDialog(
                    "파일 추가 실패",
                    "PDF, TXT, JPG, PNG 파일만 추가할 수 있습니다."
                )

            } else {

                Toast.makeText(
                    requireContext(),
                    "${addedCount}개 추가됨",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onCreateDialog(
        savedInstanceState: Bundle?
    ): Dialog {

        val dialog =
            super.onCreateDialog(savedInstanceState)

        dialog.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        dialog.setCanceledOnTouchOutside(true)

        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        return inflater.inflate(
            R.layout.dialog_ai_quiz_prompt,
            container,
            false
        )
    }

    override fun onStart() {
        super.onStart()

        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {

        bindViews(view)

        setupQuestionType()

        setupAddFileButton()

        setupLoadSavedSummaryButton()

        setupRemoveSelectedFileButton()

        setupGenerateButton()

        updateSelectedFileUi()
    }

    private fun bindViews(view: View) {

        btnAddQuizFile =
            view.findViewById(R.id.btnAddQuizFile)

        btnLoadSavedSummary =
            view.findViewById(R.id.btnLoadSavedSummary)

        selectedFileContainer =
            view.findViewById(R.id.selectedFileContainer)

        tvSelectedFileName =
            view.findViewById(R.id.tvSelectedFileName)

        tvSelectedFileType =
            view.findViewById(R.id.tvSelectedFileType)

        btnRemoveSelectedFile =
            view.findViewById(R.id.btnRemoveSelectedFile)

        etQuizPrompt =
            view.findViewById(R.id.etQuizPrompt)

        btnGenerateQuiz =
            view.findViewById(R.id.btnGenerateQuiz)

        checkMultipleChoice =
            view.findViewById(R.id.checkMultipleChoice)

        checkShortAnswer =
            view.findViewById(R.id.checkShortAnswer)
    }

    private var isQuestionTypeChanging = false

    private fun setupQuestionType() {

        checkMultipleChoice.isChecked = true
        checkShortAnswer.isChecked = false

        checkMultipleChoice.setOnCheckedChangeListener { _, isChecked ->

            if (isQuestionTypeChanging) return@setOnCheckedChangeListener

            isQuestionTypeChanging = true

            if (isChecked) {
                checkShortAnswer.isChecked = false
            } else {
                if (!checkShortAnswer.isChecked) {
                    checkMultipleChoice.isChecked = true
                }
            }

            updateQuestionTypeUi()

            isQuestionTypeChanging = false
        }

        checkShortAnswer.setOnCheckedChangeListener { _, isChecked ->

            if (isQuestionTypeChanging) return@setOnCheckedChangeListener

            isQuestionTypeChanging = true

            if (isChecked) {
                checkMultipleChoice.isChecked = false
            } else {
                if (!checkMultipleChoice.isChecked) {
                    checkShortAnswer.isChecked = true
                }
            }

            updateQuestionTypeUi()

            isQuestionTypeChanging = false
        }

        updateQuestionTypeUi()
    }

    private fun updateQuestionTypeUi() {

        if (checkMultipleChoice.isChecked) {

            checkMultipleChoice.setBackgroundResource(
                R.drawable.bg_filter_chip_selected
            )

            checkMultipleChoice.setTextColor(
                requireContext().getColor(android.R.color.white)
            )

        } else {

            checkMultipleChoice.setBackgroundResource(
                R.drawable.bg_filter_chip_unselected
            )

            checkMultipleChoice.setTextColor(
                requireContext().getColor(R.color.text_primary)
            )
        }

        if (checkShortAnswer.isChecked) {

            checkShortAnswer.setBackgroundResource(
                R.drawable.bg_filter_chip_selected
            )

            checkShortAnswer.setTextColor(
                requireContext().getColor(android.R.color.white)
            )

        } else {

            checkShortAnswer.setBackgroundResource(
                R.drawable.bg_filter_chip_unselected
            )

            checkShortAnswer.setTextColor(
                requireContext().getColor(R.color.text_primary)
            )
        }
    }

    private fun setupAddFileButton() {

        btnAddQuizFile.setOnClickListener {

            pickFilesLauncher.launch(
                arrayOf(
                    "application/pdf",
                    "text/plain",
                    "image/jpeg",
                    "image/png"
                )
            )
        }
    }

    private fun setupLoadSavedSummaryButton() {

        btnLoadSavedSummary.setOnClickListener {

            val dir =
                File(
                    requireContext().filesDir,
                    "saved_notes"
                )

            if (!dir.exists()) {

                showErrorDialog(
                    "저장된 요약 없음",
                    "AI 요약에서 저장한 파일이 없습니다."
                )

                return@setOnClickListener
            }

            val files =
                dir.listFiles { file ->

                    file.isFile &&
                            file.name.endsWith(
                                ".txt",
                                ignoreCase = true
                            )

                } ?: emptyArray()

            if (files.isEmpty()) {

                showErrorDialog(
                    "저장된 요약 없음",
                    "AI 요약 txt 파일이 없습니다."
                )

                return@setOnClickListener
            }

            val fileNames =
                files.map {
                    it.name
                }.toTypedArray()

            AlertDialog.Builder(requireContext())
                .setTitle("AI 요약 선택")
                .setItems(fileNames) { _, which ->

                    val selectedFile =
                        files[which]

                    val fakeUri =
                        Uri.fromFile(selectedFile)

                    if (
                        selectedFiles.any {
                            it.fileName == selectedFile.name
                        }
                    ) {

                        Toast.makeText(
                            requireContext(),
                            "이미 추가된 파일입니다.",
                            Toast.LENGTH_SHORT
                        ).show()

                        return@setItems
                    }

                    selectedFiles.add(
                        SelectedFileItem(
                            fileName = selectedFile.name,
                            uri = fakeUri,
                            type = FileType.TXT
                        )
                    )

                    updateSelectedFileUi()

                    Toast.makeText(
                        requireContext(),
                        "${selectedFile.name} 추가됨",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .show()
        }
    }

    private fun setupRemoveSelectedFileButton() {

        btnRemoveSelectedFile.setOnClickListener {

            if (selectedFiles.isNotEmpty()) {

                selectedFiles.clear()

                updateSelectedFileUi()

                Toast.makeText(
                    requireContext(),
                    "선택 파일이 제거되었습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun setupGenerateButton() {

        btnGenerateQuiz.setOnClickListener {

            val inputPrompt =
                etQuizPrompt.text.toString().trim()

            if (
                selectedFiles.isEmpty() &&
                inputPrompt.isBlank()
            ) {

                showErrorDialog(
                    "내용 없음",
                    "파일을 추가하거나 저장된 AI 요약을 불러와 주세요."
                )

                return@setOnClickListener
            }

            if (
                !checkMultipleChoice.isChecked &&
                !checkShortAnswer.isChecked
            ) {

                showErrorDialog(
                    "문제 유형 선택 필요",
                    "객관식 또는 단답형 중 최소 1개를 선택해주세요."
                )

                return@setOnClickListener
            }

            val typeText =
                when {

                    checkMultipleChoice.isChecked &&
                            checkShortAnswer.isChecked -> {
                        "객관식과 괄호문제를 섞어서"
                    }

                    checkShortAnswer.isChecked -> {
                        "괄호문제만"
                    }

                    else -> {
                        "객관식만"
                    }
                }

            val questionCount =
                extractQuestionCount(inputPrompt)

            val finalPrompt =
                buildStrictPrompt(
                    inputPrompt = inputPrompt,
                    typeText = typeText,
                    questionCount = questionCount
                )

            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                bundleOf(
                    BUNDLE_KEY_PROMPT to finalPrompt,
                    BUNDLE_KEY_FILES to selectedFiles.map { it.fileName }.toTypedArray(),
                    BUNDLE_KEY_FILE_URIS to ArrayList(selectedFiles.map { it.uri }),
                    BUNDLE_KEY_QUESTION_COUNT to questionCount
                )
            )

            dismissAllowingStateLoss()
        }
    }

    private fun buildStrictPrompt(
        inputPrompt: String,
        typeText: String,
        questionCount: Int
    ): String {

        val userRequest =
            if (inputPrompt.isBlank()) {
                "아래 자료를 바탕으로 퀴즈를 만들어줘."
            } else {
                inputPrompt
            }

        val typeRule =
            when (typeText) {

                "괄호문제만" -> {
                    """
- 모든 문제는 반드시 괄호문제 형식으로 만들어
- 객관식으로 만들지 마
- 보기(선택지) 1번, 2번, 3번, 4번을 절대 넣지 마
- 정답을 직접 작성하는 형식으로 만들어
- 문제 문장 안에 빈칸 또는 괄호를 포함해 만들어
                    """.trimIndent()
                }

                "객관식만" -> {
                    """
- 모든 문제는 반드시 객관식으로 만들어
- 각 문제마다 선택지 4개를 제공해
- 1번, 2번, 3번, 4번 형식으로 보기를 작성해
- 괄호문제나 단답형으로 만들지 마
                    """.trimIndent()
                }

                else -> {
                    """
- 문제는 객관식과 괄호문제를 섞어서 만들어
- 객관식 문제에는 반드시 선택지 4개를 제공해
- 괄호문제에는 선택지를 넣지 마
- 괄호문제는 정답을 직접 쓰는 형식으로 만들어
                    """.trimIndent()
                }
            }

        return """
$userRequest

[반드시 지켜야 할 조건]
- 문제 유형: $typeText
- 문제 개수: 정확히 ${questionCount}개
- ${questionCount}개보다 많이 만들지 마
- ${questionCount}개보다 적게 만들지 마
- 예시 문제 만들지 마
- 보너스 문제 만들지 마
- 사용자가 1문제라고 했으면 반드시 1문제만 만들어
$typeRule
        """.trimIndent()
    }

    private fun extractQuestionCount(
        prompt: String
    ): Int {

        if (prompt.isBlank()) {
            return 1
        }

        val numberRegex =
            Regex("""(\d+)\s*(문제|개|문항)""")

        val numberMatch =
            numberRegex.find(prompt)

        if (numberMatch != null) {

            val count =
                numberMatch.groupValues[1]
                    .toIntOrNull()

            if (
                count != null &&
                count in 1..50
            ) {
                return count
            }
        }

        return when {
            prompt.contains("한 문제") -> 1
            prompt.contains("한문제") -> 1
            prompt.contains("하나") -> 1
            prompt.contains("두 문제") -> 2
            prompt.contains("두문제") -> 2
            prompt.contains("세 문제") -> 3
            prompt.contains("세문제") -> 3
            else -> 1
        }
    }

    private fun showErrorDialog(
        title: String,
        message: String
    ) {

        if (!isAdded) return

        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("확인", null)
            .show()
    }

    private fun updateSelectedFileUi() {

        if (selectedFiles.isEmpty()) {

            tvSelectedFileName.text =
                "선택된 파일이 없습니다."

            tvSelectedFileType.text =
                "파일을 추가해주세요"

            btnRemoveSelectedFile.visibility =
                View.INVISIBLE

            return
        }

        val firstFile =
            selectedFiles.first()

        tvSelectedFileName.text =
            if (selectedFiles.size == 1) {
                firstFile.fileName
            } else {
                "${firstFile.fileName} 외 ${selectedFiles.size - 1}개"
            }

        tvSelectedFileType.text =
            if (selectedFiles.size == 1) {
                getFileTypeLabel(firstFile.type)
            } else {
                "총 ${selectedFiles.size}개 파일 선택됨"
            }

        btnRemoveSelectedFile.visibility =
            View.VISIBLE
    }

    private fun getDisplayName(
        uri: Uri
    ): String? {

        val cursor =
            requireContext()
                .contentResolver
                .query(
                    uri,
                    null,
                    null,
                    null,
                    null
                )

        cursor?.use {

            val index =
                it.getColumnIndex(
                    OpenableColumns.DISPLAY_NAME
                )

            if (
                it.moveToFirst() &&
                index >= 0
            ) {
                return it.getString(index)
            }
        }

        return uri.lastPathSegment
    }

    private fun getFileTypeLabel(
        type: FileType
    ): String {

        return when (type) {
            FileType.PDF -> "PDF 파일"
            FileType.TXT -> "텍스트 파일"
            FileType.JPG -> "이미지 파일"
            FileType.PNG -> "이미지 파일"
            FileType.UNKNOWN -> "파일"
        }
    }

    private fun detectFileType(
        name: String,
        mime: String
    ): FileType {

        val lowerName =
            name.lowercase()

        val lowerMime =
            mime.lowercase()

        return when {

            lowerMime.contains("pdf") ||
                    lowerName.endsWith(".pdf") -> {
                FileType.PDF
            }

            lowerMime.contains("text") ||
                    lowerName.endsWith(".txt") -> {
                FileType.TXT
            }

            lowerMime.contains("jpeg") ||
                    lowerMime.contains("jpg") ||
                    lowerName.endsWith(".jpg") ||
                    lowerName.endsWith(".jpeg") -> {
                FileType.JPG
            }

            lowerMime.contains("png") ||
                    lowerName.endsWith(".png") -> {
                FileType.PNG
            }

            else -> {
                FileType.UNKNOWN
            }
        }
    }

    enum class FileType {
        PDF,
        TXT,
        JPG,
        PNG,
        UNKNOWN
    }

    data class SelectedFileItem(
        val fileName: String,
        val uri: Uri,
        val type: FileType
    )

    companion object {

        const val REQUEST_KEY =
            "ai_quiz_prompt_result"

        const val BUNDLE_KEY_PROMPT =
            "prompt"

        const val BUNDLE_KEY_FILES =
            "files"

        const val BUNDLE_KEY_FILE_URIS =
            "file_uris"

        const val BUNDLE_KEY_EXTRACTED_TEXT =
            "extracted_text"

        const val BUNDLE_KEY_QUESTION_COUNT =
            "question_count"
    }
}