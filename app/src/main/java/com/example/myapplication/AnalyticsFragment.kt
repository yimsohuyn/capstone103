package com.example.myapplication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.fragment.app.Fragment              // ✅ 여기!
import androidx.navigation.fragment.findNavController
import com.google.android.material.tabs.TabLayout

class AnalyticsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_analytics, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnBack = view.findViewById<ImageButton>(R.id.btnBack)
        val tabLayout = view.findViewById<TabLayout>(R.id.tabLayoutQuiz)

        btnBack.setOnClickListener {
            findNavController().navigateUp()
            // 또는: requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        // 기본 탭: 퀴즈 생성
        replaceQuizFragment(QuizCreateFragment())

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> replaceQuizFragment(QuizCreateFragment())
                    1 -> replaceQuizFragment(QuizLearnedFragment())
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    // 🔽 여기의 Fragment 타입이 androidx.fragment.app.Fragment 이어야 함
    private fun replaceQuizFragment(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.quizContentContainer, fragment)
            .commit()
    }
}
