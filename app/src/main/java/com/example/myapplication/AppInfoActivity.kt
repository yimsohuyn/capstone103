package com.example.myapplication

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.databinding.ActivityAppInfoBinding

class AppInfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppInfoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAppInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 뒤로가기 버튼 클릭 시 종료
        binding.btnBack.setOnClickListener {
            finish()
        }

        // 예: 앱 정보 표시
        binding.tvAppName.text = "Study with me"
        binding.tvVersion.text = "버전 1.0.0"
        binding.tvDeveloper.text = "개발자: 103조"
    }
}
