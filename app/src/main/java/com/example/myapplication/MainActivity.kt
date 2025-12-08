package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.myapplication.databinding.ActivityMainBinding
import com.google.firebase.auth.ktx.auth
import com.google.firebase.dynamiclinks.ktx.dynamicLinks
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // NavController 가져오기
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.main_nav_host) as NavHostFragment
        val navController = navHostFragment.navController

        val bottomNav = binding.bottomNav

        // 🔗 BottomNavigationView 와 NavController 연결
        bottomNav.setupWithNavController(navController)

        // 같은 탭 다시 눌렀을 때 재네비게이션 방지
        bottomNav.setOnItemReselectedListener { /* 아무 것도 안 함 */ }

        // ✅ 현재 화면에 따라 어떤 탭이 선택될지 직접 지정
        navController.addOnDestinationChangedListener { _, destination, _ ->

            // destination.id(현재 프래그먼트) → 선택해야 할 메뉴 id
            val targetMenuId = when (destination.id) {

                // 1) 일정(홈) 화면일 때
                R.id.homeFragment -> R.id.homeFragment

                // 2) 필기요약 화면일 때
                R.id.notesFragment -> R.id.notesFragment

                // 3) 학습 분석 화면일 때
                R.id.analyticsFragment -> R.id.analyticsFragment

                // 4) 그 외 나머지 화면 전부 → 과제 탭으로 간주
                //    (과제 목록, 과제 등록, 팀 프로젝트 등 전부 여기)
                else -> R.id.assignmentFragment
            }

            // 실제 매핑이 있을 때만 체크 변경
            if (targetMenuId != null && bottomNav.selectedItemId != targetMenuId) {
                bottomNav.menu.findItem(targetMenuId).isChecked = true
            }
        }

        // 딥링크 & 인텐트 처리
        handleDeepLink(intent)
        handleIntent(intent, navController)
    }

    // ---------------------------------------------------------
    // 🔥 초대 링크 Deep Link 처리
    // ---------------------------------------------------------
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.main_nav_host) as NavHostFragment
        val navController = navHostFragment.navController

        handleDeepLink(intent)
        intent?.let { handleIntent(it, navController) }
    }

    private fun handleDeepLink(intent: Intent?) {
        if (intent == null) return

        Firebase.dynamicLinks
            .getDynamicLink(intent)
            .addOnSuccessListener { pendingDynamicLinkData ->
                val deepLink = pendingDynamicLinkData?.link ?: return@addOnSuccessListener

                val teamId = deepLink.getQueryParameter("teamId")

                if (teamId != null) {
                    joinTeam(teamId)
                }
            }
    }

    // ---------------------------------------------------------
    // 🔥 팀 참여 기능: Firestore에 팀 멤버 등록
    // ---------------------------------------------------------
    private fun joinTeam(teamId: String) {
        val user = Firebase.auth.currentUser ?: return

        val memberData = mapOf(
            "name" to (user.displayName ?: "이름없음"),
            "joinedAt" to FieldValue.serverTimestamp()
        )

        Firebase.firestore.collection("teams")
            .document(teamId)
            .collection("members")
            .document(user.uid)
            .set(memberData)
    }

    // ---------------------------------------------------------
    // 🔥 다른 Activity에서 돌아올 때 홈으로 가는 처리
    // ---------------------------------------------------------
    private fun handleIntent(intent: Intent, navController: NavController) {
        val goToHome = intent.getBooleanExtra("go_to_home", false)
        if (goToHome) {
            // 프래그먼트는 homeFragment 로 이동
            navController.navigate(R.id.homeFragment)
            // ✅ 네비게이션바 탭도 "일정" 에 맞추기 (메뉴 id = homeFragment)
            binding.bottomNav.selectedItemId = R.id.homeFragment
        }
    }
}
