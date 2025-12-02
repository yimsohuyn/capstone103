package com.example.myapplication

import android.content.Intent
import androidx.navigation.ui.setupWithNavController
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.navigation.fragment.NavHostFragment
import com.example.myapplication.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.main_nav_host) as NavHostFragment
        val navController = navHostFragment.navController

        // BottomNavigationView 기본 연결
        binding.bottomNav.setupWithNavController(navController)

        // BottomNavigationView 아이템 클릭 리스너
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
                R.id.tab_settings -> {
                    // SettingActivity를 열 때 홈 이동 신호 포함
                    val intent = Intent(this, SettingActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }
            // 앱 시작 시 Intent 확인 (SettingActivity에서 홈으로 돌아올 때)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
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

