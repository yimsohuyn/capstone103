package com.example.myapplication

import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.view.LayoutInflater
import android.view.ViewGroup
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
    private val fileList = mutableListOf<File>()
    private lateinit var adapter: FileListAdapter
    private lateinit var breadcrumbContainer: LinearLayout
    private lateinit var breadcrumbScrollView: HorizontalScrollView

    /** 현재 탐색 중인 디렉토리 */
    private lateinit var currentDirectory: File

    /** 최상위 루트 디렉토리 (saved_notes) */
    private lateinit var rootDirectory: File

    // ───── 선택 모드 관련 ─────
    /** 선택 모드 활성화 여부 */
    private var isSelectionMode = false

    /** 선택 모드 UI 요소 */
    private lateinit var normalTopBar: View
    private lateinit var selectionActionBar: LinearLayout
    private lateinit var tvSelectionCount: TextView
    private lateinit var fabNewFolder: com.google.android.material.floatingactionbutton.FloatingActionButton

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rootDirectory = File(requireContext().filesDir, "saved_notes")
        if (!rootDirectory.exists()) rootDirectory.mkdirs()
        currentDirectory = rootDirectory

        // 일반 상단바
        normalTopBar = view.findViewById(R.id.manageFilesTopBar)

        // 선택 모드 액션바
        selectionActionBar = view.findViewById(R.id.selectionActionBar)
        tvSelectionCount = view.findViewById(R.id.tvSelectionCount)

        // 뒤로가기 버튼: 선택 모드면 해제, 상위 폴더 또는 이전 화면으로
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

        // Breadcrumb 초기화
        breadcrumbContainer = view.findViewById(R.id.breadcrumbContainer)
        breadcrumbScrollView = view.findViewById(R.id.breadcrumbScrollView)

        recyclerView = view.findViewById(R.id.rvManagedFiles)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = FileListAdapter(
            fileList,
            onItemClick = { file, position ->
                if (isSelectionMode) {
                    // 선택 모드: 선택 토글
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
                // 롱클릭 → 선택 모드 진입 + 해당 항목 선택
                if (!isSelectionMode) {
                    enterSelectionMode()
                }
                adapter.toggleSelection(position)
                updateSelectionCount()
            },
            onMenuClick = { file, position ->
                // 3-dot 메뉴 → 개별 항목 컨텍스트 메뉴
                showContextMenu(file, position)
            }
        )
        recyclerView.adapter = adapter

        // 선택 모드 액션바 버튼
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
        updateBreadcrumb()

        // FAB: 새 폴더 생성
        fabNewFolder = view.findViewById(R.id.fabNewFolder)
        fabNewFolder.setOnClickListener {
            showNewFolderDialog()
        }
    }

    // ───── 선택 모드 진입/해제 ─────

    /** 선택 모드로 진입 */
    private fun enterSelectionMode() {
        isSelectionMode = true
        adapter.setSelectionMode(true)
        normalTopBar.visibility = View.GONE
        selectionActionBar.visibility = View.VISIBLE
        fabNewFolder.visibility = View.GONE
        updateSelectionCount()
    }

    /** 선택 모드 해제 */
    private fun exitSelectionMode() {
        isSelectionMode = false
        adapter.setSelectionMode(false)
        normalTopBar.visibility = View.VISIBLE
        selectionActionBar.visibility = View.GONE
        fabNewFolder.visibility = View.VISIBLE
    }

    /** 선택 개수 갱신 */
    private fun updateSelectionCount() {
        val count = adapter.getSelectedCount()
        tvSelectionCount.text = "${count}개 선택"
    }

    // 선택 항목 일괄 이동
    private fun moveSelectedFiles() {
        val selectedFiles = adapter.getSelectedFiles()
        if (selectedFiles.isEmpty()) {
            Toast.makeText(requireContext(), "항목을 선택해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        // 이동 대상 폴더 (이미 선택되어있는 폴더 제외)
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
        }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("${selectedFiles.size}개 항목 이동")
            .setItems(folderLabels) { _, which ->
                val targetFolder = allFolders[which]
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
            }
            .setNegativeButton("취소", null)
            .show()
    }

    // ───── 선택된 항목 일괄 삭제 ─────
    private fun deleteSelectedFiles() {
        val selectedFiles = adapter.getSelectedFiles()
        if (selectedFiles.isEmpty()) {
            Toast.makeText(requireContext(), "항목을 선택해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle("${selectedFiles.size}개 항목 삭제")
            .setMessage("선택된 ${selectedFiles.size}개 항목을 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                var successCount = 0
                for (file in selectedFiles) {
                    val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
                    if (deleted) successCount++
                }

                exitSelectionMode()
                loadFiles()
                Toast.makeText(requireContext(), "${successCount}개 삭제 완료", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    /** 새 폴더 생성 다이얼로그 */
    private fun showNewFolderDialog() {
        val editText = android.widget.EditText(requireContext()).apply {
            hint = "폴더 이름 입력"
            setPadding(48, 32, 48, 32)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }

        AlertDialog.Builder(requireContext())
            .setTitle("새 폴더 만들기")
            .setView(editText)
            .setPositiveButton("생성") { _, _ ->
                val folderName = editText.text.toString().trim()
                if (folderName.isEmpty()) {
                    Toast.makeText(requireContext(), "폴더 이름을 입력해 주세요.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (folderName.contains("/") || folderName.contains("\\")) {
                    Toast.makeText(requireContext(), "폴더 이름에 / 또는 \\를 사용할 수 없습니다.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val newFolder = File(currentDirectory, folderName)
                if (newFolder.exists()) {
                    Toast.makeText(requireContext(), "이미 존재하는 이름입니다.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (newFolder.mkdirs()) {
                    loadFiles()
                    Toast.makeText(requireContext(), "'$folderName' 폴더가 생성되었습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "폴더 생성에 실패했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    /** 지정된 디렉토리로 이동하고 목록을 새로고침 */
    private fun navigateTo(directory: File) {
        // 폴더 이동 시 선택 모드 해제
        if (isSelectionMode) exitSelectionMode()
        currentDirectory = directory
        loadFiles()
        updateTitle()
        updateBreadcrumb()
    }

    /** Breadcrumb 경로 바를 현재 디렉토리에 맞게 갱신 */
    private fun updateBreadcrumb() {
        breadcrumbContainer.removeAllViews()

        // 루트부터 현재 디렉토리까지의 경로 리스트 구성
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
            // 구분자 추가 (첫 번째 이후)
            if (index > 0) {
                val separator = TextView(context).apply {
                    text = "  ›  "
                    textSize = 13f
                    setTextColor(context.getColor(R.color.text_hint))
                }
                breadcrumbContainer.addView(separator)
            }

            // 경로 버튼
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

        // 자동 스크롤을 오른쪽 끝으로
        breadcrumbScrollView.post {
            breadcrumbScrollView.fullScroll(HorizontalScrollView.FOCUS_RIGHT)
        }
    }

    /** 타이틀을 현재 경로에 맞게 갱신 */
    private fun updateTitle() {
        val titleTextView: TextView? = view?.findViewById(R.id.manageFilesTitleText)
        if (currentDirectory == rootDirectory) {
            titleTextView?.text = "요약 파일 관리"
        } else {
            titleTextView?.text = currentDirectory.name
        }
    }

    /** 현재 디렉토리의 파일 목록을 로드 (폴더 우선 정렬) */
    private fun loadFiles() {
        fileList.clear()
        if (currentDirectory.exists() && currentDirectory.isDirectory) {
            val entries = currentDirectory.listFiles()
            if (entries != null) {
                // 폴더를 먼저, 그 뒤 파일. 각각 이름순 정렬
                val sorted = entries.sortedWith(
                    compareByDescending<File> { it.isDirectory }
                        .thenBy { it.name.lowercase() }
                )
                fileList.addAll(sorted)
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

    /** 3-dot 메뉴에서 호출하는 컨텍스트 메뉴 (이름 변경 / 이동 / 삭제) */
    private fun showContextMenu(file: File, position: Int) {
        val options = arrayOf("이름 변경", "이동", "삭제")

        AlertDialog.Builder(requireContext())
            .setTitle(file.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenameDialog(file, position)
                    1 -> showMoveDialog(file, position)
                    2 -> showDeleteConfirmDialog(file, position)
                }
            }
            .show()
    }

    /** 파일 내용 보기 다이얼로그 */
    private fun showFileContentDialog(file: File) {
        val content = try {
            file.readText()
        } catch (e: Exception) {
            "파일을 읽을 수 없습니다: ${e.message}"
        }

        val scrollView = android.widget.ScrollView(requireContext())
        val textView = TextView(requireContext()).apply {
            text = content
            setPadding(48, 32, 48, 32)
            textSize = 14f
            setTextColor(requireContext().getColor(R.color.text_primary))
            setTextIsSelectable(true)
        }
        scrollView.addView(textView)

        AlertDialog.Builder(requireContext())
            .setTitle(file.name)
            .setView(scrollView)
            .setPositiveButton("닫기", null)
            .show()
    }

    /** 파일/폴더 이동 다이얼로그 (개별) — 이동할 대상 폴더 선택 */
    private fun showMoveDialog(file: File, position: Int) {
        val typeText = if (file.isDirectory) "폴더" else "파일"

        // saved_notes 하위의 모든 폴더를 수집 (자기 자신 제외)
        val allFolders = mutableListOf<File>()
        collectFolders(rootDirectory, allFolders, excludeDir = if (file.isDirectory) file else null)

        if (allFolders.isEmpty()) {
            Toast.makeText(requireContext(), "이동할 수 있는 폴더가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        // 폴더 경로를 사용자 친화적으로 표시 (루트 기준 상대 경로)
        val folderLabels = allFolders.map { folder ->
            val relative = folder.toRelativeString(rootDirectory)
            if (relative.isEmpty()) "홈 (최상위)" else relative
        }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("'${file.name}' $typeText 이동")
            .setItems(folderLabels) { _, which ->
                val targetFolder = allFolders[which]
                val targetFile = File(targetFolder, file.name)

                if (targetFolder == file.parentFile) {
                    Toast.makeText(requireContext(), "현재 위치와 같은 폴더입니다.", Toast.LENGTH_SHORT).show()
                    return@setItems
                }
                if (targetFile.exists()) {
                    Toast.makeText(requireContext(), "대상 폴더에 같은 이름이 이미 있습니다.", Toast.LENGTH_SHORT).show()
                    return@setItems
                }
                if (file.renameTo(targetFile)) {
                    fileList.removeAt(position)
                    adapter.notifyItemRemoved(position)
                    adapter.notifyItemRangeChanged(position, fileList.size)
                    updateEmptyState()
                    Toast.makeText(requireContext(), "${typeText}이(가) 이동되었습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "${typeText} 이동에 실패했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    /** rootDir 하위의 모든 폴더를 재귀적으로 수집 (excludeDir 제외) */
    private fun collectFolders(dir: File, result: MutableList<File>, excludeDir: File?) {
        result.add(dir)
        val children = dir.listFiles()?.filter { it.isDirectory } ?: return
        for (child in children.sortedBy { it.name.lowercase() }) {
            if (child == excludeDir) continue
            collectFolders(child, result, excludeDir)
        }
    }

    /** rootDir 하위의 모든 폴더를 재귀적으로 수집 (여러 폴더 제외) — 일괄 이동용 */
    private fun collectFoldersExcluding(dir: File, result: MutableList<File>, excludeDirs: Set<File>) {
        result.add(dir)
        val children = dir.listFiles()?.filter { it.isDirectory } ?: return
        for (child in children.sortedBy { it.name.lowercase() }) {
            if (child in excludeDirs) continue
            collectFoldersExcluding(child, result, excludeDirs)
        }
    }

    /** 이름 변경 다이얼로그 */
    private fun showRenameDialog(file: File, position: Int) {
        val typeText = if (file.isDirectory) "폴더" else "파일"
        val editText = android.widget.EditText(requireContext()).apply {
            setText(file.name)
            setPadding(48, 32, 48, 32)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            // 확장자 앞까지 선택
            val dotIndex = file.name.lastIndexOf('.')
            if (!file.isDirectory && dotIndex > 0) {
                setSelection(0, dotIndex)
            } else {
                selectAll()
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle("$typeText 이름 변경")
            .setView(editText)
            .setPositiveButton("변경") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isEmpty()) {
                    Toast.makeText(requireContext(), "이름을 입력해 주세요.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (newName == file.name) return@setPositiveButton
                if (newName.contains("/") || newName.contains("\\")) {
                    Toast.makeText(requireContext(), "이름에 / 또는 \\를 사용할 수 없습니다.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val newFile = File(file.parentFile, newName)
                if (newFile.exists()) {
                    Toast.makeText(requireContext(), "이미 존재하는 이름입니다.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (file.renameTo(newFile)) {
                    fileList[position] = newFile
                    adapter.notifyItemChanged(position)
                    Toast.makeText(requireContext(), "이름이 변경되었습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "이름 변경에 실패했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    /** 개별 삭제 확인 다이얼로그 */
    private fun showDeleteConfirmDialog(file: File, position: Int) {
        val typeText = if (file.isDirectory) "폴더" else "파일"
        AlertDialog.Builder(requireContext())
            .setTitle("$typeText 삭제")
            .setMessage("'${file.name}' ${typeText}을(를) 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
                if (deleted) {
                    fileList.removeAt(position)
                    adapter.notifyItemRemoved(position)
                    adapter.notifyItemRangeChanged(position, fileList.size)
                    updateEmptyState()
                    Toast.makeText(requireContext(), "${typeText}이(가) 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "${typeText} 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    // ───────── RecyclerView 어댑터 ─────────
    private class FileListAdapter(
        private val files: MutableList<File>,
        private val onItemClick: (File, Int) -> Unit,
        private val onLongClick: (File, Int) -> Unit,
        private val onMenuClick: (File, Int) -> Unit
    ) : RecyclerView.Adapter<FileListAdapter.ViewHolder>() {

        /** 선택 모드 여부 */
        private var selectionMode = false

        /** 선택된 위치 셋 */
        private val selectedPositions = mutableSetOf<Int>()

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val cbSelect: CheckBox = view.findViewById(R.id.cbSelect)
            val ivFileIcon: ImageView = view.findViewById(R.id.ivFileIcon)
            val tvFileName: TextView = view.findViewById(R.id.tvFileName)
            val tvFileInfo: TextView = view.findViewById(R.id.tvFileInfo)
            val btnItemMenu: ImageButton = view.findViewById(R.id.btnItemMenu)
        }

        /** 선택 모드 설정 */
        fun setSelectionMode(enabled: Boolean) {
            selectionMode = enabled
            if (!enabled) selectedPositions.clear()
            notifyDataSetChanged()
        }

        /** 선택 토글 */
        fun toggleSelection(position: Int) {
            if (selectedPositions.contains(position)) {
                selectedPositions.remove(position)
            } else {
                selectedPositions.add(position)
            }
            notifyItemChanged(position)
        }

        /** 선택된 파일 수 */
        fun getSelectedCount(): Int = selectedPositions.size

        /** 선택된 파일 목록 */
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
                // 폴더 아이콘 + 항목 수 표시
                holder.ivFileIcon.setImageResource(R.drawable.outline_files_24)
                holder.ivFileIcon.setColorFilter(
                    holder.itemView.context.getColor(R.color.accent_orange)
                )
                val childCount = file.listFiles()?.size ?: 0
                holder.tvFileInfo.text = "폴더 · ${childCount}개 항목"
            } else {
                // 파일 아이콘 + 크기 + 수정일 표시
                holder.ivFileIcon.setImageResource(R.drawable.outline_files_24)
                holder.ivFileIcon.setColorFilter(
                    holder.itemView.context.getColor(R.color.accent_teal)
                )
                val size = formatFileSize(file.length())
                val date = formatDate(file.lastModified())
                holder.tvFileInfo.text = "$size · $date"
            }

            // ── 선택 모드 시 체크박스 표시 ──
            if (selectionMode) {
                holder.cbSelect.visibility = View.VISIBLE
                holder.cbSelect.isChecked = selectedPositions.contains(position)
                holder.btnItemMenu.visibility = View.GONE
                // 선택된 항목 배경 하이라이트
                if (selectedPositions.contains(position)) {
                    holder.itemView.setBackgroundColor(
                        holder.itemView.context.getColor(R.color.background_secondary)
                    )
                } else {
                    holder.itemView.background = null
                }
            } else {
                holder.cbSelect.visibility = View.GONE
                holder.btnItemMenu.visibility = View.VISIBLE
                holder.itemView.background = null
            }

            // 항목 클릭
            holder.itemView.setOnClickListener {
                onItemClick(file, holder.adapterPosition)
            }

            // 항목 롱프레스 → 선택 모드 진입 + 선택
            holder.itemView.setOnLongClickListener {
                onLongClick(file, holder.adapterPosition)
                true
            }

            // 3-dot 메뉴 버튼 (선택 모드가 아닐 때만 동작)
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