package com.example.myapplication

import android.app.AlertDialog
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ManageFilesFragment : Fragment(R.layout.fragment_manage_files) {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var emptyStateCard: LinearLayout
    private lateinit var emptyStateSubText: TextView
    private val fileList = mutableListOf<File>()
    private lateinit var adapter: FileListAdapter
    private lateinit var breadcrumbContainer: LinearLayout
    private lateinit var breadcrumbScrollView: HorizontalScrollView

    private lateinit var currentDirectory: File
    private lateinit var rootDirectory: File

    private var isSelectionMode = false

    private lateinit var normalTopBar: View
    private lateinit var selectionActionBar: LinearLayout
    private lateinit var tvSelectionCount: TextView
    private lateinit var fabNewFolder: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var tvFileCount: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rootDirectory = File(requireContext().filesDir, "saved_notes")
        if (!rootDirectory.exists()) rootDirectory.mkdirs()
        currentDirectory = rootDirectory

        normalTopBar = view.findViewById(R.id.manageFilesTopBar)
        selectionActionBar = view.findViewById(R.id.selectionActionBar)
        tvSelectionCount = view.findViewById(R.id.tvSelectionCount)
        tvFileCount = view.findViewById(R.id.tvFileCount)

        val btnBack: ImageButton = view.findViewById(R.id.btnBackManageFiles)
        btnBack.setOnClickListener {
            if (isSelectionMode) {
                exitSelectionMode()
            } else if (currentDirectory != rootDirectory) {
                navigateTo(currentDirectory.parentFile ?: rootDirectory)
            } else {
                findNavController().popBackStack()
            }
        }

        emptyText = view.findViewById(R.id.tvEmptyFiles)
        emptyStateCard = view.findViewById(R.id.emptyStateCard)
        emptyStateSubText = view.findViewById(R.id.tvEmptyFilesSub)

        breadcrumbContainer = view.findViewById(R.id.breadcrumbContainer)
        breadcrumbScrollView = view.findViewById(R.id.breadcrumbScrollView)

        recyclerView = view.findViewById(R.id.rvManagedFiles)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = FileListAdapter(
            fileList,
            onItemClick = { file, position ->
                if (isSelectionMode) {
                    adapter.toggleSelection(position)
                    updateSelectionCount()
                } else {
                    if (file.isDirectory) {
                        navigateTo(file)
                    } else {
                        showFileContentDialog(file)
                    }
                }
            },
            onLongClick = { _, position ->
                if (!isSelectionMode) {
                    enterSelectionMode()
                }
                adapter.toggleSelection(position)
                updateSelectionCount()
            },
            onMenuClick = { file, position ->
                showContextMenu(file, position)
            }
        )
        recyclerView.adapter = adapter

        view.findViewById<ImageButton>(R.id.btnCancelSelection).setOnClickListener {
            exitSelectionMode()
        }
        view.findViewById<TextView>(R.id.btnMoveSelected).setOnClickListener {
            moveSelectedFiles()
        }
        view.findViewById<TextView>(R.id.btnDeleteSelected).setOnClickListener {
            deleteSelectedFiles()
        }

        loadFiles()
        updateTitle()
        updateBreadcrumb()

        fabNewFolder = view.findViewById(R.id.fabNewFolder)
        fabNewFolder.setOnClickListener {
            showNewFolderDialog()
        }
    }

    private fun isDarkMode(): Boolean {
        val currentNightMode =
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun dialogButtonColor(): Int {
        return if (isDarkMode()) Color.WHITE else Color.BLACK
    }

    private fun enterSelectionMode() {
        isSelectionMode = true
        adapter.setSelectionMode(true)
        normalTopBar.visibility = View.GONE
        selectionActionBar.visibility = View.VISIBLE
        fabNewFolder.visibility = View.GONE
        updateSelectionCount()
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        adapter.setSelectionMode(false)
        normalTopBar.visibility = View.VISIBLE
        selectionActionBar.visibility = View.GONE
        fabNewFolder.visibility = View.VISIBLE
    }

    private fun updateSelectionCount() {
        val count = adapter.getSelectedCount()
        tvSelectionCount.text = "${count}개 선택"
    }

    private fun moveSelectedFiles() {
        val selectedFiles = adapter.getSelectedFiles()
        if (selectedFiles.isEmpty()) {
            Toast.makeText(requireContext(), "항목을 선택해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val allFolders = mutableListOf<File>()
        val excludeDirs = selectedFiles.filter { it.isDirectory }.toSet()
        collectFoldersExcluding(rootDirectory, allFolders, excludeDirs)

        if (allFolders.isEmpty()) {
            Toast.makeText(requireContext(), "이동할 수 있는 폴더가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val folderLabels = allFolders.map { folder ->
            val relative = folder.toRelativeString(rootDirectory)
            if (relative.isEmpty()) "홈 (최상위)" else relative
        }

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_move_target, null)

        val textMoveTitle = dialogView.findViewById<TextView>(R.id.textMoveTitle)
        val moveListContainer = dialogView.findViewById<LinearLayout>(R.id.moveListContainer)

        textMoveTitle.text = "${selectedFiles.size}개 항목 이동"

        val moveDialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        folderLabels.forEachIndexed { index, label ->
            val itemView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_move_target, moveListContainer, false)

            val textTarget = itemView.findViewById<TextView>(R.id.textMoveTarget)
            textTarget.text = label

            itemView.setOnClickListener {
                val targetFolder = allFolders[index]
                var successCount = 0
                var failCount = 0

                for (file in selectedFiles) {
                    if (targetFolder == file.parentFile) {
                        failCount++
                        continue
                    }

                    val targetFile = File(targetFolder, file.name)
                    if (targetFile.exists()) {
                        failCount++
                        continue
                    }

                    if (file.renameTo(targetFile)) {
                        successCount++
                    } else {
                        failCount++
                    }
                }

                exitSelectionMode()
                loadFiles()

                val message = buildString {
                    if (successCount > 0) append("${successCount}개 이동 완료")
                    if (failCount > 0) {
                        if (successCount > 0) append(", ")
                        append("${failCount}개 실패")
                    }
                }

                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                moveDialog.dismiss()
            }

            moveListContainer.addView(itemView)
        }

        moveDialog.show()
        moveDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun deleteSelectedFiles() {
        val selectedFiles = adapter.getSelectedFiles()
        if (selectedFiles.isEmpty()) {
            Toast.makeText(requireContext(), "항목을 선택해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_delete_member, null)

        val textDeleteTitle = dialogView.findViewById<TextView>(R.id.textDeleteTitle)
        val textDeleteMessage = dialogView.findViewById<TextView>(R.id.textDeleteMessage)
        val btnCancelDelete = dialogView.findViewById<TextView>(R.id.btnCancelDelete)
        val btnConfirmDelete = dialogView.findViewById<TextView>(R.id.btnConfirmDelete)

        textDeleteTitle.text = "${selectedFiles.size}개 항목 삭제"
        textDeleteMessage.text = "선택된 ${selectedFiles.size}개 항목을 삭제하시겠습니까?"

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancelDelete.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirmDelete.setOnClickListener {
            var successCount = 0

            for (file in selectedFiles) {
                val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
                if (deleted) successCount++
            }

            exitSelectionMode()
            loadFiles()
            Toast.makeText(requireContext(), "${successCount}개 삭제 완료", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
    }

    private fun showNewFolderDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_folder, null)

        val editFolderName = dialogView.findViewById<EditText>(R.id.editFolderName)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("생성", null)
            .setNegativeButton("취소", null)
            .create()

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val buttonColor = dialogButtonColor()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(buttonColor)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(buttonColor)

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val folderName = editFolderName.text.toString().trim()

            if (folderName.isEmpty()) {
                Toast.makeText(requireContext(), "폴더 이름을 입력해 주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (folderName.contains("/") || folderName.contains("\\")) {
                Toast.makeText(requireContext(), "폴더 이름에 / 또는 \\를 사용할 수 없습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newFolder = File(currentDirectory, folderName)
            if (newFolder.exists()) {
                Toast.makeText(requireContext(), "이미 존재하는 이름입니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newFolder.mkdirs()) {
                loadFiles()
                Toast.makeText(requireContext(), "'$folderName' 폴더가 생성되었습니다.", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            } else {
                Toast.makeText(requireContext(), "폴더 생성에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateTo(directory: File) {
        if (isSelectionMode) exitSelectionMode()
        currentDirectory = directory
        loadFiles()
        updateTitle()
        updateBreadcrumb()
    }

    private fun updateBreadcrumb() {
        breadcrumbContainer.removeAllViews()

        val pathSegments = mutableListOf<File>()
        var dir: File? = currentDirectory
        while (dir != null && dir.absolutePath.startsWith(rootDirectory.absolutePath)) {
            pathSegments.add(0, dir)
            if (dir == rootDirectory) break
            dir = dir.parentFile
        }

        val context = requireContext()
        val dpToPx = { dp: Int -> (dp * context.resources.displayMetrics.density).toInt() }

        pathSegments.forEachIndexed { index, segment ->
            if (index > 0) {
                val separator = TextView(context).apply {
                    text = "  ›  "
                    textSize = 13f
                    setTextColor(context.getColor(R.color.text_hint))
                }
                breadcrumbContainer.addView(separator)
            }

            val label = if (segment == rootDirectory) "홈" else segment.name
            val isCurrentDir = (segment == currentDirectory)

            val crumb = TextView(context).apply {
                text = label
                textSize = 13f
                if (isCurrentDir) {
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(context.getColor(R.color.text_primary))
                } else {
                    setTextColor(context.getColor(R.color.accent_teal))
                }
                setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
                if (!isCurrentDir) {
                    setOnClickListener { navigateTo(segment) }
                }
            }
            breadcrumbContainer.addView(crumb)
        }

        breadcrumbScrollView.post {
            breadcrumbScrollView.fullScroll(HorizontalScrollView.FOCUS_RIGHT)
        }
    }

    private fun updateTitle() {
        val titleTextView: TextView? = view?.findViewById(R.id.manageFilesTitleText)
        titleTextView?.text =
            if (currentDirectory == rootDirectory) "요약 파일 관리" else currentDirectory.name
    }

    private fun loadFiles() {
        fileList.clear()
        if (currentDirectory.exists() && currentDirectory.isDirectory) {
            val entries = currentDirectory.listFiles()
            if (entries != null) {
                val sorted = entries.sortedWith(
                    compareByDescending<File> { it.isDirectory }
                        .thenBy { it.name.lowercase(Locale.getDefault()) }
                )
                fileList.addAll(sorted)
            }
        }
        adapter.notifyDataSetChanged()
        updateEmptyState()
        updateFileCount()
    }

    private fun updateFileCount() {
        if (::tvFileCount.isInitialized) {
            tvFileCount.text = "현재 항목 ${fileList.size}개"
        }
    }

    private fun updateEmptyState() {
        if (fileList.isEmpty()) {
            emptyStateCard.visibility = View.VISIBLE
            emptyText.visibility = View.VISIBLE
            emptyStateSubText.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyStateCard.visibility = View.GONE
            emptyText.visibility = View.GONE
            emptyStateSubText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun showContextMenu(file: File, position: Int) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_file_menu, null)

        val textMenuTitle = dialogView.findViewById<TextView>(R.id.textMenuTitle)
        val btnRename = dialogView.findViewById<TextView>(R.id.btnMenuRename)
        val btnMove = dialogView.findViewById<TextView>(R.id.btnMenuMove)
        val btnDelete = dialogView.findViewById<TextView>(R.id.btnMenuDelete)

        textMenuTitle.text = file.name

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnRename.setOnClickListener {
            dialog.dismiss()
            showRenameDialog(file, position)
        }

        btnMove.setOnClickListener {
            dialog.dismiss()
            showMoveDialog(file, position)
        }

        btnDelete.setOnClickListener {
            dialog.dismiss()
            showDeleteConfirmDialog(file, position)
        }
    }

    private fun showFileContentDialog(file: File) {
        val content = try {
            file.readText()
        } catch (e: Exception) {
            "파일을 읽을 수 없습니다: ${e.message}"
        }

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_saved_file_preview, null)

        val tvPreviewFileName = dialogView.findViewById<TextView>(R.id.tvPreviewFileName)
        val tvPreviewContent = dialogView.findViewById<TextView>(R.id.tvPreviewContent)
        val btnClosePreview = dialogView.findViewById<TextView>(R.id.btnClosePreview)

        tvPreviewFileName.text = file.name
        tvPreviewContent.text = content

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )

        btnClosePreview.setOnClickListener {
            dialog.dismiss()
        }
    }

    private fun showMoveDialog(file: File, position: Int) {
        val typeText = if (file.isDirectory) "폴더" else "파일"

        val allFolders = mutableListOf<File>()
        collectFolders(rootDirectory, allFolders, excludeDir = if (file.isDirectory) file else null)

        if (allFolders.isEmpty()) {
            Toast.makeText(requireContext(), "이동할 수 있는 폴더가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val folderLabels = allFolders.map { folder ->
            val relative = folder.toRelativeString(rootDirectory)
            if (relative.isEmpty()) "홈 (최상위)" else relative
        }

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_move_target, null)

        val textMoveTitle = dialogView.findViewById<TextView>(R.id.textMoveTitle)
        val moveListContainer = dialogView.findViewById<LinearLayout>(R.id.moveListContainer)

        textMoveTitle.text = "'${file.name}' $typeText 이동"

        val moveDialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        folderLabels.forEachIndexed { index, label ->
            val itemView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_move_target, moveListContainer, false)

            val textTarget = itemView.findViewById<TextView>(R.id.textMoveTarget)
            textTarget.text = label

            itemView.setOnClickListener {
                val targetFolder = allFolders[index]
                val targetFile = File(targetFolder, file.name)

                if (targetFolder == file.parentFile) {
                    Toast.makeText(requireContext(), "현재 위치와 같은 폴더입니다.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (targetFile.exists()) {
                    Toast.makeText(requireContext(), "대상 폴더에 같은 이름이 이미 있습니다.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (file.renameTo(targetFile)) {
                    loadFiles()
                    updateEmptyState()
                    updateFileCount()
                    Toast.makeText(requireContext(), "${typeText}이(가) 이동되었습니다.", Toast.LENGTH_SHORT).show()
                    moveDialog.dismiss()
                } else {
                    Toast.makeText(requireContext(), "${typeText} 이동에 실패했습니다.", Toast.LENGTH_SHORT).show()
                }
            }

            moveListContainer.addView(itemView)
        }

        moveDialog.show()
        moveDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun collectFolders(dir: File, result: MutableList<File>, excludeDir: File?) {
        result.add(dir)
        val children = dir.listFiles()?.filter { it.isDirectory } ?: return
        for (child in children.sortedBy { it.name.lowercase(Locale.getDefault()) }) {
            if (child == excludeDir) continue
            collectFolders(child, result, excludeDir)
        }
    }

    private fun collectFoldersExcluding(dir: File, result: MutableList<File>, excludeDirs: Set<File>) {
        result.add(dir)
        val children = dir.listFiles()?.filter { it.isDirectory } ?: return
        for (child in children.sortedBy { it.name.lowercase(Locale.getDefault()) }) {
            if (child in excludeDirs) continue
            collectFoldersExcluding(child, result, excludeDirs)
        }
    }

    private fun showRenameDialog(file: File, position: Int) {
        val typeText = if (file.isDirectory) "폴더" else "파일"

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_rename_file, null)

        val textRenameTitle = dialogView.findViewById<TextView>(R.id.textRenameTitle)
        val editRename = dialogView.findViewById<EditText>(R.id.editRename)
        textRenameTitle.text = "$typeText 이름 변경"
        editRename.setText(file.name)

        val dotIndex = file.name.lastIndexOf('.')
        if (!file.isDirectory && dotIndex > 0) {
            editRename.setSelection(0, dotIndex)
        } else {
            editRename.selectAll()
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("변경", null)
            .setNegativeButton("취소", null)
            .create()

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.WHITE)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.WHITE)

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val newName = editRename.text.toString().trim()
            if (newName.isEmpty()) {
                Toast.makeText(requireContext(), "이름을 입력해 주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (newName == file.name) {
                dialog.dismiss()
                return@setOnClickListener
            }
            if (newName.contains("/") || newName.contains("\\")) {
                Toast.makeText(requireContext(), "이름에 / 또는 \\를 사용할 수 없습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newFile = File(file.parentFile, newName)
            if (newFile.exists()) {
                Toast.makeText(requireContext(), "이미 존재하는 이름입니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (file.renameTo(newFile)) {
                loadFiles()
                updateFileCount()
                Toast.makeText(requireContext(), "이름이 변경되었습니다.", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            } else {
                Toast.makeText(requireContext(), "이름 변경에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showDeleteConfirmDialog(file: File, position: Int) {
        val typeText = if (file.isDirectory) "폴더" else "파일"

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_delete_member, null)

        val textDeleteTitle = dialogView.findViewById<TextView>(R.id.textDeleteTitle)
        val textDeleteMessage = dialogView.findViewById<TextView>(R.id.textDeleteMessage)
        val btnCancelDelete = dialogView.findViewById<TextView>(R.id.btnCancelDelete)
        val btnConfirmDelete = dialogView.findViewById<TextView>(R.id.btnConfirmDelete)

        textDeleteTitle.text = "$typeText 삭제"
        textDeleteMessage.text = "'${file.name}' ${typeText}을(를) 삭제하시겠습니까?"

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancelDelete.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirmDelete.setOnClickListener {
            val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
            if (deleted) {
                fileList.removeAt(position)
                adapter.notifyItemRemoved(position)
                adapter.notifyItemRangeChanged(position, fileList.size)
                updateEmptyState()
                updateFileCount()
                Toast.makeText(requireContext(), "${typeText}이(가) 삭제되었습니다.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "${typeText} 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
    }

    private class FileListAdapter(
        private val files: MutableList<File>,
        private val onItemClick: (File, Int) -> Unit,
        private val onLongClick: (File, Int) -> Unit,
        private val onMenuClick: (File, Int) -> Unit
    ) : RecyclerView.Adapter<FileListAdapter.ViewHolder>() {

        private var selectionMode = false
        private val selectedPositions = mutableSetOf<Int>()

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val cbSelect: CheckBox = view.findViewById(R.id.cbSelect)
            val ivFileIcon: ImageView = view.findViewById(R.id.ivFileIcon)
            val tvFileName: TextView = view.findViewById(R.id.tvFileName)
            val tvFileInfo: TextView = view.findViewById(R.id.tvFileInfo)
            val btnItemMenu: ImageButton = view.findViewById(R.id.btnItemMenu)
            val itemRoot: LinearLayout = view.findViewById(R.id.itemRoot)
        }

        fun setSelectionMode(enabled: Boolean) {
            selectionMode = enabled
            if (!enabled) selectedPositions.clear()
            notifyDataSetChanged()
        }

        fun toggleSelection(position: Int) {
            if (selectedPositions.contains(position)) {
                selectedPositions.remove(position)
            } else {
                selectedPositions.add(position)
            }
            notifyItemChanged(position)
        }

        fun getSelectedCount(): Int = selectedPositions.size

        fun getSelectedFiles(): List<File> {
            return selectedPositions
                .filter { it < files.size }
                .map { files[it] }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_manage_file, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = files[position]
            holder.tvFileName.text = file.name

            if (file.isDirectory) {
                holder.ivFileIcon.setImageResource(R.drawable.outline_files_24)
                holder.ivFileIcon.setColorFilter(
                    holder.itemView.context.getColor(R.color.accent_orange)
                )
                val childCount = file.listFiles()?.size ?: 0
                holder.tvFileInfo.text = "폴더 · ${childCount}개 항목"
            } else {
                holder.ivFileIcon.setImageResource(R.drawable.outline_files_24)
                holder.ivFileIcon.setColorFilter(
                    holder.itemView.context.getColor(R.color.accent_teal)
                )
                val size = formatFileSize(file.length())
                val date = formatDate(file.lastModified())
                holder.tvFileInfo.text = "$size · $date"
            }

            if (selectionMode) {
                holder.cbSelect.visibility = View.VISIBLE
                holder.cbSelect.isChecked = selectedPositions.contains(position)
                holder.btnItemMenu.visibility = View.GONE
                if (selectedPositions.contains(position)) {
                    holder.itemRoot.setBackgroundColor(
                        holder.itemView.context.getColor(R.color.background_secondary)
                    )
                } else {
                    holder.itemRoot.background = null
                }
            } else {
                holder.cbSelect.visibility = View.GONE
                holder.btnItemMenu.visibility = View.VISIBLE
                holder.itemRoot.background = null
            }

            holder.itemView.setOnClickListener {
                onItemClick(file, holder.adapterPosition)
            }

            holder.itemView.setOnLongClickListener {
                onLongClick(file, holder.adapterPosition)
                true
            }

            holder.btnItemMenu.setOnClickListener {
                if (!selectionMode) {
                    onMenuClick(file, holder.adapterPosition)
                }
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

        private fun formatDate(timestamp: Long): String {
            val sdf = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    }
}