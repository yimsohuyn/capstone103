package com.example.myapplication

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class ManageFilesFragment : Fragment(R.layout.fragment_manage_files) {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private val fileList = mutableListOf<File>()
    private lateinit var adapter: FileListAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 뒤로가기 버튼
        val btnBack: ImageButton = view.findViewById(R.id.btnBackManageFiles)
        btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // 빈 목록 안내 텍스트
        emptyText = view.findViewById(R.id.tvEmptyFiles)

        // RecyclerView 세팅
        recyclerView = view.findViewById(R.id.rvManagedFiles)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = FileListAdapter(
            fileList,
            onDeleteClick = { file, position ->
                showDeleteConfirmDialog(file, position)
            }
        )
        recyclerView.adapter = adapter

        // 파일 목록 로드
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
        AlertDialog.Builder(requireContext())
            .setTitle("파일 삭제")
            .setMessage("'${file.name}' 파일을 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                if (file.delete()) {
                    fileList.removeAt(position)
                    adapter.notifyItemRemoved(position)
                    adapter.notifyItemRangeChanged(position, fileList.size)
                    updateEmptyState()
                    Toast.makeText(requireContext(), "파일이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "파일 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    // ───────── RecyclerView 어댑터 ─────────
    private class FileListAdapter(
        private val files: MutableList<File>,
        private val onDeleteClick: (File, Int) -> Unit
    ) : RecyclerView.Adapter<FileListAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvFileName: TextView = view.findViewById(R.id.tvFileName)
            val tvFileSize: TextView = view.findViewById(R.id.tvFileSize)
            val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteFile)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_manage_file, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = files[position]
            holder.tvFileName.text = file.name
            holder.tvFileSize.text = formatFileSize(file.length())
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
