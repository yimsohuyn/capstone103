package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import android.app.Activity
import android.view.View
import android.widget.Toast
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import androidx.activity.result.contract.ActivityResultContracts

class SettingActivity : AppCompatActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient

    companion object {
        private const val RC_SIGN_IN = 1001
    }

    private var currentAccount: GoogleSignInAccount? = null
    private lateinit var layoutAccountInfo: LinearLayout
    private lateinit var layoutLogout: LinearLayout
    private lateinit var accountEmailText: TextView


    private val accountLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                setResult(Activity.RESULT_OK)

            }
        }
    private fun startSignIn() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, SettingActivity.Companion.RC_SIGN_IN)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setting)

        currentAccount = GoogleSignIn.getLastSignedInAccount(this)

        layoutLogout = findViewById(R.id.settingLogout)
        accountEmailText = findViewById(R.id.tvGoogleEmail)
        layoutAccountInfo = findViewById(R.id.userBox)



        // 구글 로그인 클라이언트 생성
        googleSignInClient = GoogleSignIn.getClient(
            this,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build()
        )

        // 현재 로그인된 계정 이메일 표시
        findViewById<TextView>(R.id.tvGoogleEmail).apply {
            val account = GoogleSignIn.getLastSignedInAccount(this@SettingActivity)
            text = account?.email ?: "로그인 정보 없음"
        }

        // 정보 메뉴 클릭 → AppInfoActivity 실행
        findViewById<LinearLayout>(R.id.settingInfo2).setOnClickListener {
            startActivity(Intent(this, AppInfoActivity::class.java))
        }

        // 로그아웃 메뉴 클릭
        findViewById<LinearLayout>(R.id.settingLogout).setOnClickListener {
            logout()
        }

        // 뒤로가기 버튼 클릭 → MainActivity 홈 화면으로 이동
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            intent.putExtra("go_to_home", true) // 홈 화면 이동 신호
            startActivity(intent)
            finish()
        }

        val accountMenuLayout: LinearLayout = findViewById(R.id.userBox)
        accountMenuLayout.setOnClickListener {
            val intent = Intent(this, SettingAccount::class.java)
            accountLauncher.launch(intent)
        }
        layoutAccountInfo.setOnClickListener {
            startSignIn()
        }
    }
    private fun logout() {
        googleSignInClient.signOut().addOnCompleteListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
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
                    Toast.makeText(this, "로그인에 실패했습니다. (${e.statusCode})", Toast.LENGTH_SHORT)
                        .show()
                }
            } else {
            }
        }
    }
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

