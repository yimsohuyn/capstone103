package com.example.myapplication

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

        // ✅ 기본 연결(그대로 두기)
        binding.bottomNav.setupWithNavController(navController)

        // ✅ 여기 아래에 추가!!
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
                    navController.navigate(R.id.settingFragment)
                    true
                }
                else -> false
            }
        }
    }
}
