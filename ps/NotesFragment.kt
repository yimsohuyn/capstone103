package com.example.myapplication

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class NotesFragment : Fragment(R.layout.fragment_notes) {

    // AI 필기 요약용: 이미지/텍스트 다중 파일 선택 (최대 5개)
    private val pickMultipleFilesLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            if (uris.size > 5) {
                Toast.makeText(requireContext(), "최대 5개까지만 선택할 수 있습니다.", Toast.LENGTH_SHORT).show()
                // 앞에서 5개만 사용
                val limitedUris = uris.take(5)
                showAiSummaryPromptDialog(limitedUris)
            } else {
                showAiSummaryPromptDialog(uris)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // fragment_notes.xml 안의 뒤로가기 버튼
        val backButton: ImageButton = view.findViewById(R.id.btnBack)
        backButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // settingInfo1 (AI 필기 요약) - 이미지/텍스트 파일 다중 선택
        val settingInfo1: LinearLayout = view.findViewById(R.id.settingInfo1)
        settingInfo1.setOnClickListener {
            // 이미지 및 텍스트 파일 선택 가능
            pickMultipleFilesLauncher.launch(arrayOf("image/*", "text/*"))
        }

        // settingInfo2 (요약 파일) - 요약 파일 관리 화면으로 이동
        val settingInfo2: LinearLayout = view.findViewById(R.id.settingInfo2)
        settingInfo2.setOnClickListener {
            findNavController().navigate(R.id.action_notesFragment_to_manageFilesFragment)
        }
    }

    /**
     * 선택된 파일 URI 목록을 가지고 AI 요약 프롬프트 다이얼로그를 표시한다.
     */
    private fun showAiSummaryPromptDialog(uris: List<Uri>) {
        val dialog = AiSummaryPromptDialogFragment.newInstance(uris)
        dialog.show(parentFragmentManager, "AiSummaryPromptDialog")
    }

    /** URI에서 파일명을 추출하는 유틸 함수 */
    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = cursor.getString(index)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result.substring(cut + 1)
            }
        }
        return result
    }
}