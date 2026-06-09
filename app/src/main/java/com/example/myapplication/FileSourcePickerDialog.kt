package com.example.myapplication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * 파일 소스 선택 바텀시트 다이얼로그.
 *
 * 카메라 / 내부저장소 / 구글 드라이브 중 하나를 선택하면
 * [OnSourceSelectedListener] 콜백을 통해 부모 Fragment에 알린다.
 */
class FileSourcePickerDialog : BottomSheetDialogFragment() {

    /**
     * 파일 소스 선택 결과를 전달하는 콜백 인터페이스.
     */
    interface OnSourceSelectedListener {
        /** 카메라 촬영 선택 */
        fun onCameraSelected()
        /** 내부저장소 (갤러리 · 최근 파일) 선택 */
        fun onInternalStorageSelected()
        /** 구글 드라이브 선택 */
        fun onGoogleDriveSelected()
    }

    private var listener: OnSourceSelectedListener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_file_source_picker, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 부모 Fragment에서 콜백 리스너 연결
        listener = parentFragment as? OnSourceSelectedListener
            ?: activity as? OnSourceSelectedListener

        val btnClose: ImageButton = view.findViewById(R.id.btnCloseSourcePicker)
        val optionCamera: LinearLayout = view.findViewById(R.id.optionCamera)
        val optionStorage: LinearLayout = view.findViewById(R.id.optionInternalStorage)
        val optionDrive: LinearLayout = view.findViewById(R.id.optionGoogleDrive)

        btnClose.setOnClickListener { dismiss() }

        optionCamera.setOnClickListener {
            dismiss()
            listener?.onCameraSelected()
        }

        optionStorage.setOnClickListener {
            dismiss()
            listener?.onInternalStorageSelected()
        }

        optionDrive.setOnClickListener {
            dismiss()
            listener?.onGoogleDriveSelected()
        }
    }

    companion object {
        const val TAG = "FileSourcePickerDialog"
    }
}
