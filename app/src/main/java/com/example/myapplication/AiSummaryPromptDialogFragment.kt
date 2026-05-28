package com.example.myapplication

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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

class AiSummaryPromptDialogFragment : DialogFragment() {

    private var selectedUris: MutableList<Uri> = mutableListOf()
    private lateinit var adapter: SelectedFileAdapter

    companion object {
        private const val ARG_URIS = "arg_uris"

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
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_Material_Light_Dialog_MinWidth)

        @Suppress("DEPRECATION")
        selectedUris = arguments?.getParcelableArrayList<Uri>(ARG_URIS)?.toMutableList()
            ?: mutableListOf()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (resources.displayMetrics.widthPixels * 0.92).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }
        isCancelable = true
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_ai_summary_prompt, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnCloseDialog: ImageButton = view.findViewById(R.id.btnCloseDialog)
        val tvFileCount: TextView = view.findViewById(R.id.tvFileCount)
        val rvSelectedFiles: RecyclerView = view.findViewById(R.id.rvSelectedFiles)
        val etPrompt: EditText = view.findViewById(R.id.etPrompt)
        val btnSummarize: Button = view.findViewById(R.id.btnSummarize)
        val layoutLoading: LinearLayout = view.findViewById(R.id.layoutLoading)

        btnCloseDialog.setOnClickListener { dismiss() }

        tvFileCount.text = "선택 파일 ${selectedUris.size}개"

        adapter = SelectedFileAdapter(
            selectedUris,
            onRemoveClick = { position ->
                selectedUris.removeAt(position)
                adapter.notifyItemRemoved(position)
                adapter.notifyItemRangeChanged(position, selectedUris.size)
                tvFileCount.text = "선택 파일 ${selectedUris.size}개"

                if (selectedUris.isEmpty()) {
                    Toast.makeText(requireContext(), "선택된 파일이 없습니다.", Toast.LENGTH_SHORT).show()
                    dismiss()
                }
            }
        )

        rvSelectedFiles.layoutManager = LinearLayoutManager(requireContext())
        rvSelectedFiles.adapter = adapter

        btnSummarize.setOnClickListener {
            val userPrompt = etPrompt.text.toString().trim()

            btnSummarize.isEnabled = false
            layoutLoading.visibility = View.VISIBLE

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

                    val summaryResult = withContext(Dispatchers.IO) {
                        SummaryHelper.summarize(requireContext(), extractedText, userPrompt)
                    }

                    btnSummarize.isEnabled = true
                    layoutLoading.visibility = View.GONE

                    val resultDialog = AiSummaryResultDialogFragment.newInstance(summaryResult)
                    resultDialog.show(parentFragmentManager, "AiSummaryResultDialog")
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
                else -> ""
            }

            if (text.isNotBlank()) {
                textParts.add(text)
            }
        }

        return textParts.joinToString("\n\n---\n\n")
    }

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

    private inner class SelectedFileAdapter(
        private val uris: MutableList<Uri>,
        private val onRemoveClick: (Int) -> Unit
    ) : RecyclerView.Adapter<SelectedFileAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivThumbnail: ImageView = view.findViewById(R.id.ivFileThumbnail)
            val tvFileName: TextView = view.findViewById(R.id.tvSelectedFileName)
            val tvFileType: TextView = view.findViewById(R.id.tvSelectedFileType)
            val btnRemove: ImageButton = view.findViewById(R.id.btnRemoveFile)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_selected_file, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val uri = uris[position]
            val context = holder.itemView.context

            holder.tvFileName.text = getFileName(uri)

            if (SummaryHelper.isImageFile(context, uri)) {
                holder.ivThumbnail.setImageURI(uri)
                holder.ivThumbnail.imageTintList = null
                holder.tvFileType.text = "이미지 파일"
            } else {
                holder.ivThumbnail.setImageResource(R.drawable.outline_files_24)
                holder.ivThumbnail.setColorFilter(context.getColor(R.color.accent_teal))
                holder.tvFileType.text = "텍스트 파일"
            }

            holder.btnRemove.setOnClickListener {
                onRemoveClick(position)
            }
        }

        override fun getItemCount(): Int = uris.size
    }
}