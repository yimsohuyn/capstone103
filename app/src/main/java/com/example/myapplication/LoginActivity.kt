package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)


        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnSignup = findViewById<Button>(R.id.btnSignup) // 회원가입 버튼 연결

        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null) {
            // 이미 로그인됨 → 바로 메인으로 이동
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            return
        }

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString()
            val password = etPassword.text.toString()


            if (email == "1234" && password == "1234") {
                // 로그인 성공 → 메인페이지로 이동
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish() // 로그인화면은 닫기
            } else {
                Toast.makeText(this, "이메일 또는 비밀번호가 올바르지 않습니다", Toast.LENGTH_SHORT).show()
            }
        }
        btnLogin.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                true
            } else {
                false
            }
        }


        // 회원가입 버튼 클릭 시 이동
        btnSignup.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }
}