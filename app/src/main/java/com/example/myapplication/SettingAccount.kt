package com.example.myapplication

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

class SettingAccount : AppCompatActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient

    private lateinit var layoutAccountInfo: LinearLayout
    private lateinit var layoutLogout: LinearLayout
    private lateinit var accountEmailText: TextView

    companion object {
        private const val RC_SIGN_IN = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.account_activity)

        layoutAccountInfo = findViewById(R.id.layout_account_info)
        layoutLogout = findViewById(R.id.layout_logout)
        accountEmailText = findViewById(R.id.text_account_email)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val currentAccount = GoogleSignIn.getLastSignedInAccount(this)
        updateUiWithAccount(currentAccount)

        // 계정 영역 클릭: 로그인/계정 변경 (signOut 없이)
        layoutAccountInfo.setOnClickListener {
            startSignIn()
        }

        // 로그아웃 메뉴: 진짜 로그아웃
        layoutLogout.setOnClickListener {
            googleSignInClient.signOut()
                .addOnCompleteListener(this) {
                    updateUiWithAccount(null)

                    setResult(Activity.RESULT_OK)
                    finish()
                }
        }
    }

    // 구글 로그인 시작
    private fun startSignIn() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    // 로그인 결과 처리
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                try {
                    val account = task.getResult(ApiException::class.java)
                    updateUiWithAccount(account)
                    setResult(Activity.RESULT_OK)
                    finish()
                } catch (e: ApiException) {
                    Toast.makeText(this, "로그인에 실패했습니다. (${e.statusCode})", Toast.LENGTH_SHORT).show()
                }
            } else {

            }
        }
    }

    // 계정에 따라 화면 상태 변경
    private fun updateUiWithAccount(account: GoogleSignInAccount?) {
        if (account != null) {
            val email = account.email ?: "이메일 정보 없음"
            accountEmailText.text = email
            layoutLogout.visibility = View.VISIBLE
        } else {
            accountEmailText.text = "로그인된 계정이 없습니다."
            layoutLogout.visibility = View.GONE
        }
    }
}
