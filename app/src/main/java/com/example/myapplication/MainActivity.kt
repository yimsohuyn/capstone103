package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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

        // 🔗 BottomNavigationView 와 NavController 연결 (탭/뒤로가기 자동 처리)
        binding.bottomNav.setupWithNavController(navController)

        // 같은 탭 다시 눌렀을 때 재네비게이션 방지 (선택 사항)
        binding.bottomNav.setOnItemReselectedListener { /* 아무 것도 안 함 */ }

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
    private fun handleIntent(intent: Intent, navController: androidx.navigation.NavController) {
        // SettingActivity에서 뒤로가기 시 홈 Fragment로 이동
        val goToHome = intent.getBooleanExtra("go_to_home", false)
        if (goToHome) {
            navController.navigate(R.id.homeFragment)
            // setupWithNavController가 있어서 이 줄은 사실 없어도 되지만, 확실히 맞춰주고 싶으면 유지
            binding.bottomNav.selectedItemId = R.id.homeFragment
        }
    }
}
