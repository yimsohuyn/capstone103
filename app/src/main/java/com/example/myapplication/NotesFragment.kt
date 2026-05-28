package com.example.myapplication

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotesFragment : Fragment(R.layout.fragment_notes) {

    private lateinit var tvTotalSummaryCount: TextView

    private lateinit var quickSummaryCard: LinearLayout
    private lateinit var recentSummaryCard1: LinearLayout
    private lateinit var recentSummaryCard2: LinearLayout
    private lateinit var recentSummaryCard3: LinearLayout

    private lateinit var tvRecentTitle1: TextView
    private lateinit var tvRecentSub1: TextView
    private lateinit var tvRecentTitle2: TextView
    private lateinit var tvRecentSub2: TextView
    private lateinit var tvRecentTitle3: TextView
    private lateinit var tvRecentSub3: TextView
    private lateinit var tvViewAllRecent: TextView

    private val pickMultipleFilesLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            if (uris.size > 5) {
                Toast.makeText(requireContext(), "최대 5개까지만 선택할 수 있습니다.", Toast.LENGTH_SHORT).show()
                val limitedUris = uris.take(5)
                showAiSummaryPromptDialog(limitedUris)
            } else {
                showAiSummaryPromptDialog(uris)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvTotalSummaryCount = view.findViewById(R.id.tvTotalSummaryCount)

        quickSummaryCard = view.findViewById(R.id.quickSummaryCard)

        recentSummaryCard1 = view.findViewById(R.id.recentSummaryCard1)
        recentSummaryCard2 = view.findViewById(R.id.recentSummaryCard2)
        recentSummaryCard3 = view.findViewById(R.id.recentSummaryCard3)

        tvRecentTitle1 = view.findViewById(R.id.tvRecentTitle1)
        tvRecentSub1 = view.findViewById(R.id.tvRecentSub1)
        tvRecentTitle2 = view.findViewById(R.id.tvRecentTitle2)
        tvRecentSub2 = view.findViewById(R.id.tvRecentSub2)
        tvRecentTitle3 = view.findViewById(R.id.tvRecentTitle3)
        tvRecentSub3 = view.findViewById(R.id.tvRecentSub3)

        tvViewAllRecent = view.findViewById(R.id.tvViewAllRecent)

        val backButton: ImageButton = view.findViewById(R.id.btnBack)
        backButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val aiSummaryCard: LinearLayout = view.findViewById(R.id.settingInfo1)
        aiSummaryCard.setOnClickListener {
            openFilePicker()
        }

        quickSummaryCard.setOnClickListener {
            openFilePicker()
        }

        val manageFilesCard: LinearLayout = view.findViewById(R.id.settingInfo2)
        manageFilesCard.setOnClickListener {
            findNavController().navigate(R.id.action_notesFragment_to_manageFilesFragment)
        }

        val recentClickListener = View.OnClickListener {
            findNavController().navigate(R.id.action_notesFragment_to_manageFilesFragment)
        }

        recentSummaryCard1.setOnClickListener(recentClickListener)
        recentSummaryCard2.setOnClickListener(recentClickListener)
        recentSummaryCard3.setOnClickListener(recentClickListener)
        tvViewAllRecent.setOnClickListener(recentClickListener)

        updateSummaryCounts()
        updateRecentSummaryCards()
    }

    override fun onResume() {
        super.onResume()
        updateSummaryCounts()
        updateRecentSummaryCards()
    }

    private fun openFilePicker() {
        pickMultipleFilesLauncher.launch(arrayOf("image/*", "text/*"))
    }

    private fun updateSummaryCounts() {
        val rootDirectory = File(requireContext().filesDir, "saved_notes")

        if (!rootDirectory.exists()) {
            tvTotalSummaryCount.text = "0"
            return
        }

        // 폴더는 제외하고, 하위 폴더 안까지 포함한 실제 파일만 카운트
        val totalFiles = rootDirectory
            .walkTopDown()
            .count { it.isFile }

        tvTotalSummaryCount.text = totalFiles.toString()
    }

    private fun updateRecentSummaryCards() {
        val rootDirectory = File(requireContext().filesDir, "saved_notes")

        val recentFolders = if (rootDirectory.exists()) {
            rootDirectory.listFiles()
                ?.filter { it.isDirectory }
                ?.sortedByDescending { it.lastModified() }
                ?.take(3)
                ?: emptyList()
        } else {
            emptyList()
        }

        bindRecentCard(
            card = recentSummaryCard1,
            titleView = tvRecentTitle1,
            subView = tvRecentSub1,
            folder = recentFolders.getOrNull(0)
        )

        bindRecentCard(
            card = recentSummaryCard2,
            titleView = tvRecentTitle2,
            subView = tvRecentSub2,
            folder = recentFolders.getOrNull(1)
        )

        bindRecentCard(
            card = recentSummaryCard3,
            titleView = tvRecentTitle3,
            subView = tvRecentSub3,
            folder = recentFolders.getOrNull(2)
        )
    }

    private fun bindRecentCard(
        card: LinearLayout,
        titleView: TextView,
        subView: TextView,
        folder: File?
    ) {
        if (folder == null) {
            card.visibility = View.GONE
            return
        }

        card.visibility = View.VISIBLE
        titleView.text = folder.name
        subView.text = formatDate(folder.lastModified())
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun showAiSummaryPromptDialog(uris: List<Uri>) {
        val dialog = AiSummaryPromptDialogFragment.newInstance(uris)
        dialog.show(parentFragmentManager, "AiSummaryPromptDialog")
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