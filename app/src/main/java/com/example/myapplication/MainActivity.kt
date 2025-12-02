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

        // ---- Bottom Navigation 설정 ----
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.main_nav_host) as NavHostFragment

        val navController = navHostFragment.navController
        binding.bottomNav.setupWithNavController(navController)

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.tab_schedule -> {
                    navController.navigate(R.id.homeFragment)
                    true
                }
                R.id.tab_tasks -> {
                    navController.navigate(R.id.assignmentFragment)
                    true
                }
                R.id.tab_notes -> {
                    navController.navigate(R.id.notesFragment)
                    true
                }
                R.id.tab_analytics -> {
                    navController.navigate(R.id.analyticsFragment)
                    true
                }

                else -> false
            }
        }
        // 🔥 앱이 처음 실행될 때 Intent의 딥링크도 처리해야 해서 onNewIntent도 호출
        handleDeepLink(intent)
        handleIntent(intent)
    }

    // ---------------------------------------------------------
    // 🔥 초대 링크 Deep Link 처리
    // ---------------------------------------------------------
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
        intent?.let { handleIntent(it) }
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
    private fun handleIntent(intent: Intent) {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.main_nav_host) as NavHostFragment
        val navController = navHostFragment.navController

        // SettingActivity에서 뒤로가기 시 홈 Fragment로 이동
        val goToHome = intent.getBooleanExtra("go_to_home", false)
        if (goToHome) {
            navController.navigate(R.id.homeFragment)
            binding.bottomNav.selectedItemId = R.id.tab_schedule
        }
    }

}

