package com.example.myapplication

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

class SettingActivity : AppCompatActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient

    // 현재 UI에 표시 중인 계정 (앱 실행 시 불러오거나 변경된 계정)
    private var currentAccount: GoogleSignInAccount? = null

    // 변경 작업을 하다가 '취소'했을 때 되돌아갈 계정 백업용
    private var backupAccount: GoogleSignInAccount? = null

    private lateinit var layoutAccountInfo: LinearLayout
    private lateinit var layoutLogout: LinearLayout
    private lateinit var accountEmailText: TextView

    // 계정 변경 결과 처리 런처
    private val signInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

            // 1. 사용자가 취소하거나 뒤로가기 누른 경우
            if (result.resultCode != Activity.RESULT_OK) {

                // 백업된 이메일 주소를 가져옵니다.
                val originalEmail = backupAccount?.email

                if (originalEmail != null) {
                    // 이메일이 확실히 있을 때만 복구 시도
                    Toast.makeText(this, "취소됨. 기존 계정 복구 중...", Toast.LENGTH_SHORT).show()

                    val recoveryGso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .setAccountName(originalEmail)
                        .build()

                    val recoveryClient = GoogleSignIn.getClient(this, recoveryGso)

                    recoveryClient.silentSignIn().addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            currentAccount = task.result
                            updateUiWithAccount(currentAccount)
                            Toast.makeText(this, "기존 계정이 유지됩니다.", Toast.LENGTH_SHORT).show()
                        } else {
                            // 실패 시 UI만이라도 복구
                            currentAccount = backupAccount
                            updateUiWithAccount(currentAccount)
                            Toast.makeText(this, "세션 복구 실패", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    // 이메일 정보가 없었던 경우 (로그인 안 된 상태였거나 등등) -> 그냥 UI만 복구
                    currentAccount = backupAccount
                    updateUiWithAccount(currentAccount)
                }
                return@registerForActivityResult
            }

            // 2. 정상적으로 계정을 선택한 경우
            val data = result.data
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)

            try {
                val newAccount = task.getResult(ApiException::class.java)
                if (newAccount != null) {
                    currentAccount = newAccount
                    updateUiWithAccount(newAccount)
                    Toast.makeText(this, "계정이 변경되었습니다.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: ApiException) {
                // 에러 발생 시 UI 복구
                currentAccount = backupAccount
                updateUiWithAccount(currentAccount)
            }
        }

    // [보조 함수] 실패/취소 시 기존 세션 복구용
    private fun recoverOriginalSession() {
        googleSignInClient.silentSignIn().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                currentAccount = task.result
                updateUiWithAccount(currentAccount)
            } else {
                currentAccount = backupAccount
                updateUiWithAccount(currentAccount)
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setting)

        initViews()
        setupGoogleSignIn()

        // 앱 진입 시 현재 로그인된 정보 가져오기
        currentAccount = GoogleSignIn.getLastSignedInAccount(this)
        // 백업 변수에도 일단 저장
        backupAccount = currentAccount

        updateUiWithAccount(currentAccount)
    }

    private fun initViews() {
        layoutLogout = findViewById(R.id.settingLogout)
        accountEmailText = findViewById(R.id.tvGoogleEmail)
        layoutAccountInfo = findViewById(R.id.userBox)

        // 1. 정보 메뉴 클릭
        findViewById<LinearLayout>(R.id.settingInfo2).setOnClickListener {
            startActivity(Intent(this, AppInfoActivity::class.java))
        }

        // 2. 뒤로 가기 클릭
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            // 메인으로 돌아갈 때 기존 메인을 재활용 (FLAG_ACTIVITY_CLEAR_TOP 등 상황에 맞게 조절)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            intent.putExtra("go_to_home", true)
            startActivity(intent)
            finish()
        }

        // 3. 로그아웃 클릭
        layoutLogout.setOnClickListener {
            performLogout()
        }

        // 4. [핵심] 유저 박스 클릭 -> 계정 변경
        layoutAccountInfo.setOnClickListener {
            changeAccount()
        }
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    // 계정 변경 로직
    private fun changeAccount() {
        // 변경 시도 직전의 계정 상태를 백업
        backupAccount = currentAccount

        // 구글 로그인 특성상 signOut을 해줘야 계정 선택 팝업(Account Picker)이 뜹니다.
        // signOut 없이 launch하면 구글이 자동으로 이전 계정으로 로그인시켜버릴 수 있습니다.
        googleSignInClient.signOut().addOnCompleteListener {
            // 로그아웃 완료 후 -> 로그인 창 띄우기
            val signInIntent = googleSignInClient.signInIntent
            signInLauncher.launch(signInIntent)
        }
    }

    // UI 업데이트
    private fun updateUiWithAccount(account: GoogleSignInAccount?) {
        if (account != null) {
            val email = account.email ?: "이메일 정보 없음"
            accountEmailText.text = email
            layoutLogout.visibility = View.VISIBLE
        } else {
            // 로그인 된 계정이 없는 상태
            accountEmailText.text = "로그인 정보 없음"
            layoutLogout.visibility = View.GONE
        }
    }

    // 로그아웃 수행
    private fun performLogout() {
        googleSignInClient.signOut().addOnCompleteListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}