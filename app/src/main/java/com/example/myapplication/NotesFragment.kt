package com.example.myapplication

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class NotesFragment : Fragment(R.layout.fragment_notes) {

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            saveFileToInternalStorage(uri)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // fragment_notes.xml 안의 뒤로가기 버튼
        val backButton: ImageButton = view.findViewById(R.id.btnBack)
        backButton.setOnClickListener {
            // 지금 NotesFragment를 닫고 바로 이전 화면(아래에 있던 Fragment)으로 돌아감
            parentFragmentManager.popBackStack()
        }

        // settingInfo1 (AI 필기 요약) - 파일 가져오기
        val settingInfo1: LinearLayout = view.findViewById(R.id.settingInfo1)
        settingInfo1.setOnClickListener {
            // 모든 파일 형식 허용
            pickFileLauncher.launch("*/*")
        }

        // settingInfo2 (요약 파일) - 저장된 파일 관리
        val settingInfo2: LinearLayout = view.findViewById(R.id.settingInfo2)
        settingInfo2.setOnClickListener {
            val dialog = ManageFilesDialogFragment()
            dialog.show(parentFragmentManager, "ManageFilesDialog")
        }
    }

    private fun saveFileToInternalStorage(uri: Uri) {
        val fileName = getFileName(uri) ?: "unknown_file_${System.currentTimeMillis()}"
        val saveDirectory = File(requireContext().filesDir, "saved_notes")

        if (!saveDirectory.exists()) {
            saveDirectory.mkdirs()
        }

        val destinationFile = File(saveDirectory, fileName)

        try {
            val inputStream: InputStream? = requireContext().contentResolver.openInputStream(uri)
            val outputStream = FileOutputStream(destinationFile)

            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            Toast.makeText(requireContext(), "파일이 성공적으로 추가되었습니다.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "파일 저장에 실패했습니다.", Toast.LENGTH_SHORT).show()
        }
    }

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


//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import androidx.fragment.app.Fragment
// class NotesFragment : Fragment() {
//    override fun onCreateView(
//        inflater: LayoutInflater,
//        container: ViewGroup?,
//        savedInstanceState: Bundle?
//    ): View? {
//        return inflater.inflate(R.layout.fragment_notes, container, false)
//    }
//}