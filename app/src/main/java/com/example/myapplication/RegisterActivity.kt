package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.common.SignInButton
import com.google.android.gms.tasks.Task
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException



class RegisterActivity : AppCompatActivity() {
    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("GoogleLogin", "signInLauncher callback triggered") // ← 이 로그가 찍혀야 합니다.
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            handleSignInResult(task)
        } else {
            Log.e("GoogleLogin", "Sign-in canceled or failed")
        }
    }

    private val RC_SIGN_IN = 9001

        private lateinit var mGoogleSignInClient: GoogleSignInClient

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_register)

            // Google Sign-In 버튼 찾기
            val signInButton: SignInButton = findViewById(R.id.GoogleSignButton)

            // 버튼 클릭 리스너 설정
            signInButton.setOnClickListener {
                Log.d("GoogleLogin", "Sign-in button clicked")
                signIn() // 로그인 처리 함수 호출
            }

            // GoogleSignInClient 초기화 (로그인 준비)
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()

            mGoogleSignInClient = GoogleSignIn.getClient(this, gso)
        }

        // 구글 로그인 시작
        private fun signIn() {
            val signInIntent = mGoogleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_SIGN_IN)
        }

        // 로그인 결과 처리
        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            super.onActivityResult(requestCode, resultCode, data)

            if (requestCode == RC_SIGN_IN) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                handleSignInResult(task)
            }
        }

        // 로그인 결과 처리 함수
        private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
            try {
//                val account = completedTask.getResult(ApiException::class.java)
                navigateToMainPage()
                finish()
            } catch (e: ApiException) {
                Toast.makeText(this, "로그인실패", Toast.LENGTH_SHORT).show()
            }
        }

        private fun navigateToMainPage() {
            val intent = Intent(this, com.example.myapplication.MainActivity::class.java)
            startActivity(intent)
            finish() // 로그인화면은 닫기
        }
}


//
//
//    private lateinit var mGoogleSignInClient: GoogleSignInClient
//    private val RC_SIGN_IN = 9001
//
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_register)
//
//        // GoogleSignInOptions 설정
//        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
//            .requestEmail()
//            .build()
//
//        // GoogleSignInClient 초기화
////        mGoogleSignInClient = GoogleSignIn.getClient(this, gso)
//
//    }
//    private fun signIn() {
//        val signInIntent = mGoogleSignInClient.signInIntent
//        startActivityForResult(signInIntent, RC_SIGN_IN) // 로그인 화면으로 이동
//    }
//    // 로그인 결과를 처리
//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//
//        if (requestCode == RC_SIGN_IN) {
//            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
//            handleSignInResult(task) // 로그인 결과 처리
//        }
//    }
//    val SignInButton: SignInButton = findViewById(R.id.GoogleSignButton)
//    GoogleSignButton.setOnClickListener {
//        signIn()
//    }
//
//    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
//        try {
//            val account = completedTask.getResult(ApiException::class.java)
//            // 로그인 성공 후 메인 페이지로 이동
//            navigateToMainPage()
//        } catch (e: ApiException) {
//            navigateToMainPage()
//            Toast.makeText(this, "이메일 또는 비밀번호가 올바르지 않습니다", Toast.LENGTH_SHORT).show()
//        }
//        val account = GoogleSignIn.getLastSignedInAccount(this)
//        if (account != null) {
//            navigateToMainPage()
//        }
//
//    }
//    private fun navigateToMainPage() {
//        val intent = Intent(this, MainActivity::class.java)
//        startActivity(intent)
//        finish()  // 현재 로그인 화면 종료
//    }
//}

