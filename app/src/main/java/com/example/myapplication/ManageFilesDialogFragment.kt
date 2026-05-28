package com.example.myapplication

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.io.File

class ManageFilesDialogFragment : BottomSheetDialogFragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private val fileList = mutableListOf<File>()
    private lateinit var adapter: FileListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_manage_files, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED

        val btnClose: ImageButton = view.findViewById(R.id.btnBackManageFiles)
        btnClose.setOnClickListener { dismiss() }

        emptyText = view.findViewById(R.id.tvEmptyFiles)

        recyclerView = view.findViewById(R.id.rvManagedFiles)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = FileListAdapter(
            fileList,
            onDeleteClick = { file, position ->
                showDeleteConfirmDialog(file, position)
            }
        )
        recyclerView.adapter = adapter

        loadFiles()
    }

    private fun loadFiles() {
        fileList.clear()
        val saveDirectory = File(requireContext().filesDir, "saved_notes")
        if (saveDirectory.exists() && saveDirectory.isDirectory) {
            val files = saveDirectory.listFiles()
            if (files != null) {
                fileList.addAll(files.sortedByDescending { it.lastModified() })
            }
        }
        adapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun updateEmptyState() {
        if (fileList.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun showDeleteConfirmDialog(file: File, position: Int) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_delete_member, null)

        val textDeleteTitle = dialogView.findViewById<TextView>(R.id.textDeleteTitle)
        val textDeleteMessage = dialogView.findViewById<TextView>(R.id.textDeleteMessage)
        val btnCancelDelete = dialogView.findViewById<TextView>(R.id.btnCancelDelete)
        val btnConfirmDelete = dialogView.findViewById<TextView>(R.id.btnConfirmDelete)

        textDeleteTitle.text = "파일 삭제"
        textDeleteMessage.text = "'${file.name}' 파일을 삭제할까요?"

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancelDelete.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirmDelete.setOnClickListener {
            if (file.delete()) {
                fileList.removeAt(position)
                adapter.notifyItemRemoved(position)
                adapter.notifyItemRangeChanged(position, fileList.size)
                updateEmptyState()
                Toast.makeText(requireContext(), "파일이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "파일 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
    }

    private class FileListAdapter(
        private val files: MutableList<File>,
        private val onDeleteClick: (File, Int) -> Unit
    ) : RecyclerView.Adapter<FileListAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvFileName: TextView = view.findViewById(R.id.tvFileName)
            val tvFileInfo: TextView = view.findViewById(R.id.tvFileInfo)
            val btnDelete: ImageButton = view.findViewById(R.id.btnItemMenu)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_manage_file, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = files[position]
            holder.tvFileName.text = file.name
            holder.tvFileInfo.text = formatFileSize(file.length())
            holder.btnDelete.setOnClickListener {
                onDeleteClick(file, position)
            }
        }

        override fun getItemCount(): Int = files.size

        private fun formatFileSize(size: Long): String {
            return when {
                size < 1024 -> "${size} B"
                size < 1024 * 1024 -> "${size / 1024} KB"
                else -> String.format("%.1f MB", size / (1024.0 * 1024.0))
            }
        }
    }
}