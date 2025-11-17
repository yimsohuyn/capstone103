package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException

class RegisterActivity : AppCompatActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient


    private val signInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data ?: return@registerForActivityResult

            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)

                if (account != null) {
                    navigateToMainPage()
                } else {
                    Toast.makeText(this, "로그인 실패(null)", Toast.LENGTH_SHORT).show()
                }

            } catch (e: ApiException) {
                Toast.makeText(this, "로그인 실패: ${e.statusCode}", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        googleSignInClient = GoogleSignIn.getClient(
            this,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build()
        )

        findViewById<SignInButton>(R.id.GoogleSignButton).setOnClickListener {
            signInLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    private fun navigateToMainPage() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
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