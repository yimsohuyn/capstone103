package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.myapplication.ui.theme.MyApplicationTheme
import android.content.Intent
import android.widget.ImageView
import android.app.AlertDialog

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val searchIcon = findViewById<ImageView>(R.id.searchIcon)
        val bellIcon = findViewById<ImageView>(R.id.bellIcon)
        val settingIcon = findViewById<ImageView>(R.id.settingIcon)

        searchIcon.setOnClickListener { openSearch() }
        bellIcon.setOnClickListener { openAlert() }
        settingIcon.setOnClickListener { openSettings() }

    }


    private fun openSearch() {
        val intent = Intent(this, SearchActivity::class.java)
        startActivity(intent)
    }

    private fun openAlert() {
        AlertDialog.Builder(this)
            .setTitle("알림")
            .setMessage("알림 화면 준비 중입니다.")
            .setPositiveButton("확인", null)
            .show()
    }

    private fun openSettings() {
        val intent = Intent(this, SettingActivity::class.java)
        startActivity(intent)
    }
}



@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme {
        Greeting("Android")
    }
}


//val settingIcon = findViewById<ImageView>(R.id.settingIcon)
//settingIcon.setOnClickListener {
//    // 여기에 기능 작성
//    // 예: 새 화면 열기, 다이얼로그 띄우기 등
//}
