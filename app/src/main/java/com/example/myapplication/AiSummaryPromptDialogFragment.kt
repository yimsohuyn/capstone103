package com.example.myapplication

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.utils.SummaryHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

//AI 요약 프롬프트 다이얼로그
class AiSummaryPromptDialogFragment : DialogFragment() {

    private var selectedUris: MutableList<Uri> = mutableListOf()
    private lateinit var adapter: SelectedFileAdapter

    companion object {
        private const val ARG_URIS = "arg_uris"

        // 선택된 URI 목록을 전달받아 인스턴스를 생성
        fun newInstance(uris: List<Uri>): AiSummaryPromptDialogFragment {
            val fragment = AiSummaryPromptDialogFragment()
            val args = Bundle()
            args.putParcelableArrayList(ARG_URIS, ArrayList(uris))
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 전체 화면에 가깝게 다이얼로그 스타일 적용
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_Material_Light_Dialog_MinWidth)

        @Suppress("DEPRECATION")
        selectedUris = arguments?.getParcelableArrayList<Uri>(ARG_URIS)?.toMutableList() ?: mutableListOf()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_ai_summary_prompt, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rvSelectedFiles: RecyclerView = view.findViewById(R.id.rvSelectedFiles)
        val etPrompt: EditText = view.findViewById(R.id.etPrompt)
        val btnSummarize: Button = view.findViewById(R.id.btnSummarize)
        val layoutLoading: LinearLayout = view.findViewById(R.id.layoutLoading)

        // 선택된 파일 목록 어댑터 설정
        adapter = SelectedFileAdapter(
            selectedUris,
            onRemoveClick = { position ->
                selectedUris.removeAt(position)
                adapter.notifyItemRemoved(position)
                adapter.notifyItemRangeChanged(position, selectedUris.size)

                // 파일이 모두 제거되면 다이얼로그 닫기
                if (selectedUris.isEmpty()) {
                    Toast.makeText(requireContext(), "선택된 파일이 없습니다.", Toast.LENGTH_SHORT).show()
                    dismiss()
                }
            }
        )
        rvSelectedFiles.layoutManager = LinearLayoutManager(requireContext())
        rvSelectedFiles.adapter = adapter

        // 요약하기 버튼 클릭
        btnSummarize.setOnClickListener {
            val userPrompt = etPrompt.text.toString().trim()

            // UI 상태 변경: 로딩 표시
            btnSummarize.isEnabled = false
            layoutLoading.visibility = View.VISIBLE

            // 백그라운드에서 OCR 및 텍스트 추출 → Gemini AI 요약 수행
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val extractedText = withContext(Dispatchers.IO) {
                        extractAllTexts()
                    }

                    if (extractedText.isBlank()) {
                        Toast.makeText(requireContext(), "텍스트를 추출할 수 없습니다.", Toast.LENGTH_SHORT).show()
                        btnSummarize.isEnabled = true
                        layoutLoading.visibility = View.GONE
                        return@launch
                    }

                    // Gemini AI를 사용하여 요약 수행
                    val summaryResult = withContext(Dispatchers.IO) {
                        SummaryHelper.summarizeWithGemini(extractedText, userPrompt)
                    }

                    btnSummarize.isEnabled = true
                    layoutLoading.visibility = View.GONE

                    // 결과 다이얼로그 표시
                    val resultDialog = AiSummaryResultDialogFragment.newInstance(summaryResult)
                    resultDialog.show(parentFragmentManager, "AiSummaryResultDialog")

                    // 현재 프롬프트 다이얼로그는 닫기
                    dismiss()

                } catch (e: Exception) {
                    e.printStackTrace()
                    btnSummarize.isEnabled = true
                    layoutLoading.visibility = View.GONE
                    Toast.makeText(requireContext(), "오류: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    //선택된 모든 파일에서 텍스트를 추출한다.
    //이미지: OCR(ML Kit) 사용 / 텍스트 파일: 직접 읽기
    private suspend fun extractAllTexts(): String {
        val context = requireContext()
        val textParts = mutableListOf<String>()

        for (uri in selectedUris) {
            val text = when {
                SummaryHelper.isImageFile(context, uri) -> {
                    val bitmap = SummaryHelper.getBitmapFromUri(context, uri)
                    SummaryHelper.extractTextFromBitmap(bitmap)
                }
                SummaryHelper.isTextFile(context, uri) -> {
                    SummaryHelper.readTextFromUri(context, uri)
                }
                else -> {
                    // 지원하지 않는 형식은 건너뛰기
                    ""
                }
            }
            if (text.isNotBlank()) {
                textParts.add(text)
            }
        }

        return textParts.joinToString("\n\n---\n\n")
    }

    // URI에서 파일명을 가져온다.
    private fun getFileName(uri: Uri): String {
        var result = "알 수 없는 파일"
        if (uri.scheme == "content") {
            val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = it.getString(index)
                    }
                }
            }
        }
        return result
    }

    // ───────── RecyclerView 어댑터 ─────────
    private inner class SelectedFileAdapter(
        private val uris: MutableList<Uri>,
        private val onRemoveClick: (Int) -> Unit
    ) : RecyclerView.Adapter<SelectedFileAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivThumbnail: ImageView = view.findViewById(R.id.ivFileThumbnail)
            val tvFileName: TextView = view.findViewById(R.id.tvSelectedFileName)
            val btnRemove: ImageButton = view.findViewById(R.id.btnRemoveFile)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_selected_file, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val uri = uris[position]
            holder.tvFileName.text = getFileName(uri)

            // 이미지인 경우 썸네일 표시
            val context = holder.itemView.context
            if (SummaryHelper.isImageFile(context, uri)) {
                holder.ivThumbnail.setImageURI(uri)
                holder.ivThumbnail.imageTintList = null
            }

            holder.btnRemove.setOnClickListener {
                onRemoveClick(position)
            }
        }

        override fun getItemCount(): Int = uris.size
    }
}
