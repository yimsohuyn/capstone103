package com.example.myapplication

import android.os.Bundle
import android.widget.ImageButton
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

class NotesFragment : Fragment(R.layout.fragment_notes) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // fragment_notes.xml 안의 뒤로가기 버튼
        val backButton: ImageButton = view.findViewById(R.id.btnBack)

        backButton.setOnClickListener {
            // 지금 NotesFragment를 닫고 바로 이전 화면(아래에 있던 Fragment)으로 돌아감
            parentFragmentManager.popBackStack()
        }
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
