package com.example.myapplication

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AI 요약 탭의 메인 Fragment.
 *
 * 파일 소스 선택 바텀시트를 통해 카메라 / 내부저장소 / 구글 드라이브 중
 * 하나를 선택한 뒤, 파일을 가져와 AI 요약 다이얼로그로 전달한다.
 */
class NotesFragment : Fragment(R.layout.fragment_notes),
    FileSourcePickerDialog.OnSourceSelectedListener {

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

    /** 카메라 촬영 이미지가 저장될 임시 URI */
    private var cameraImageUri: Uri? = null

    // ──────────────────────────────────────────────
    // ActivityResultLauncher들
    // ──────────────────────────────────────────────

    /** 카메라 권한 요청 런처 */
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            Toast.makeText(requireContext(), "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    /** 카메라 촬영 런처 */
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraImageUri?.let { uri ->
                showAiSummaryPromptDialog(listOf(uri))
            }
        }
    }

    /**
     * 내부저장소 파일 선택 런처.
     * ACTION_OPEN_DOCUMENT + EXTRA_INITIAL_URI로 기기 내부저장소에서 시작한다.
     * 이미지와 텍스트 파일 모두 선택 가능.
     */
    private val pickInternalStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uris = extractUrisFromResult(result.data)
            if (uris.isNotEmpty()) {
                handleSelectedUris(uris)
            }
        }
    }

    /**
     * 구글 드라이브 파일 선택 런처.
     * ACTION_OPEN_DOCUMENT 기반으로, 구글 드라이브가 초기 위치로 힌트된다.
     */
    private val pickDriveLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uris = extractUrisFromResult(result.data)
            if (uris.isNotEmpty()) {
                handleSelectedUris(uris)
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

        // AI 요약 카드 클릭 → 파일 소스 선택 바텀시트 표시
        val aiSummaryCard: LinearLayout = view.findViewById(R.id.settingInfo1)
        aiSummaryCard.setOnClickListener {
            showFileSourcePicker()
        }

        // 빠른 요약 카드 클릭 → 파일 소스 선택 바텀시트 표시
        quickSummaryCard.setOnClickListener {
            showFileSourcePicker()
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

    // ──────────────────────────────────────────────
    // FileSourcePickerDialog 콜백 구현
    // ──────────────────────────────────────────────

    /**
     * 파일 소스 선택 바텀시트를 표시한다.
     * 기존 openFilePicker()를 대체한다.
     */
    private fun showFileSourcePicker() {
        val picker = FileSourcePickerDialog()
        picker.show(childFragmentManager, FileSourcePickerDialog.TAG)
    }

    /** 카메라 선택 시 호출 */
    override fun onCameraSelected() {
        // 카메라 권한 확인
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    /**
     * 내부저장소 선택 시 호출.
     * ACTION_OPEN_DOCUMENT를 사용하되, 초기 위치를 다운로드 폴더로 지정한다.
     * 이미지(JPG/PNG)와 텍스트(TXT) 파일 모두 선택 가능.
     */
    override fun onInternalStorageSelected() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "text/*"))
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addCategory(Intent.CATEGORY_OPENABLE)

            // 다운로드 폴더를 초기 위치로 설정 (바로 파일 목록 진입)
            val localStorageUri = Uri.parse(
                "content://com.android.externalstorage.documents/document/primary%3ADownload"
            )
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, localStorageUri)
        }
        pickInternalStorageLauncher.launch(intent)
    }

    /** 구글 드라이브 선택 시 호출 — ACTION_OPEN_DOCUMENT + 드라이브 힌트 */
    override fun onGoogleDriveSelected() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "text/*"))
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addCategory(Intent.CATEGORY_OPENABLE)
        }

        // 구글 드라이브의 DocumentProvider URI를 초기 위치로 힌트
        try {
            val driveRootUri = Uri.parse(
                "content://com.google.android.apps.docs.storage/document/root"
            )
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, driveRootUri)
        } catch (_: Exception) {
            // 드라이브 앱이 없는 경우 무시, 기본 SAF 피커가 열림
        }

        pickDriveLauncher.launch(intent)
    }

    // ──────────────────────────────────────────────
    // 카메라 관련
    // ──────────────────────────────────────────────

    /**
     * 카메라를 실행하여 사진을 촬영한다.
     * 촬영된 이미지는 캐시 디렉토리의 임시 파일에 저장된다.
     */
    private fun launchCamera() {
        val imageFile = File(
            File(requireContext().cacheDir, "camera_images").apply { mkdirs() },
            "camera_${System.currentTimeMillis()}.jpg"
        )
        cameraImageUri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            imageFile
        )
        takePictureLauncher.launch(cameraImageUri!!)
    }

    // ──────────────────────────────────────────────
    // 공통 유틸리티
    // ──────────────────────────────────────────────

    /**
     * ActivityResult에서 선택된 URI 목록을 추출한다.
     * 다중 선택(clipData)과 단일 선택(data) 모두 처리한다.
     */
    private fun extractUrisFromResult(data: Intent?): List<Uri> {
        val uris = mutableListOf<Uri>()
        data?.clipData?.let { clipData ->
            for (i in 0 until clipData.itemCount) {
                uris.add(clipData.getItemAt(i).uri)
            }
        } ?: data?.data?.let { uri ->
            uris.add(uri)
        }
        return uris
    }

    /**
     * 선택된 URI 목록을 처리한다.
     * 최대 5개 제한을 적용한 후 AI 요약 다이얼로그를 표시한다.
     */
    private fun handleSelectedUris(uris: List<Uri>) {
        if (uris.size > 5) {
            Toast.makeText(requireContext(), "최대 5개까지만 선택할 수 있습니다.", Toast.LENGTH_SHORT).show()
            val limitedUris = uris.take(5)
            showAiSummaryPromptDialog(limitedUris)
        } else {
            showAiSummaryPromptDialog(uris)
        }
    }

    // ──────────────────────────────────────────────
    // 기존 기능 (변경 없음)
    // ──────────────────────────────────────────────

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