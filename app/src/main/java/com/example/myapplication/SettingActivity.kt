package com.example.myapplication

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class SettingActivity : AppCompatActivity() {
    private val accountLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                setResult(Activity.RESULT_OK)

            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setting)
        val accountMenuLayout: LinearLayout = findViewById(R.id.layout_menu_account)

        accountMenuLayout.setOnClickListener {
            val intent = Intent(this, SettingAccount::class.java)
            accountLauncher.launch(intent)
        }
    }
}


