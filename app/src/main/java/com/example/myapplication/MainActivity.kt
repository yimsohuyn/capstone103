package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.myapplication.databinding.ActivityMainBinding
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var pendingInviteTeamId: String? = null
    private var pendingInviteAssignmentRemoteId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.main_nav_host) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNav.setupWithNavController(navController)
        binding.bottomNav.setOnItemReselectedListener {
            // 같은 탭 다시 눌렀을 때 아무 동작 안 함
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val targetMenuId = when (destination.id) {
                R.id.homeFragment -> R.id.homeFragment
                R.id.assignmentFragment,
                R.id.teamProjectFragment -> R.id.assignmentFragment

                R.id.notesFragment,
                R.id.manageFilesFragment -> R.id.notesFragment

                R.id.analyticsFragment,
                R.id.quizLearnedFragment -> R.id.analytics_graph

                else -> binding.bottomNav.selectedItemId
            }

            if (binding.bottomNav.selectedItemId != targetMenuId) {
                binding.bottomNav.menu.findItem(targetMenuId)?.isChecked = true
            }
        }

        handleInviteIntent(intent)
        handleIntent(intent, navController)

        // 🔥 학습분석 탭 열기
        val openTab =
            intent.getStringExtra("open_tab")

        if (openTab == "learning_analysis") {

            binding.bottomNav.selectedItemId =
                R.id.analytics_graph
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.main_nav_host) as NavHostFragment
        val navController = navHostFragment.navController

        intent?.let {
            handleInviteIntent(it)
            handleIntent(it, navController)
        }
    }

    private fun handleInviteIntent(intent: Intent) {
        val data: Uri = intent.data ?: return

        val isInviteLink =
            data.scheme == "noteplan" &&
                    data.host == "invite"

        if (!isInviteLink) return

        val teamId = data.getQueryParameter("teamId")
        val assignmentRemoteId = data.getQueryParameter("assignmentRemoteId")

        if (teamId.isNullOrBlank() || assignmentRemoteId.isNullOrBlank()) {
            Toast.makeText(this, "유효하지 않은 초대 링크입니다.", Toast.LENGTH_SHORT).show()
            return
        }

        if (pendingInviteTeamId == teamId &&
            pendingInviteAssignmentRemoteId == assignmentRemoteId
        ) return

        pendingInviteTeamId = teamId
        pendingInviteAssignmentRemoteId = assignmentRemoteId

        showInviteAcceptDialog(teamId, assignmentRemoteId)
    }

    private fun showInviteAcceptDialog(teamId: String, assignmentRemoteId: String) {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_team_invite_confirm, null)

        val btnCancel = dialogView.findViewById<Button>(R.id.btnInviteCancel)
        val btnAccept = dialogView.findViewById<Button>(R.id.btnInviteAccept)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel.setOnClickListener {
            pendingInviteTeamId = null
            pendingInviteAssignmentRemoteId = null
            dialog.dismiss()
        }

        btnAccept.setOnClickListener {
            joinProject(teamId, assignmentRemoteId)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun joinProject(teamId: String, assignmentRemoteId: String) {
        val firebaseUser = Firebase.auth.currentUser
        val googleAccount = com.google.android.gms.auth.api.signin.GoogleSignIn
            .getLastSignedInAccount(this)

        val uid = firebaseUser?.uid ?: googleAccount?.id
        val displayName = firebaseUser?.displayName ?: googleAccount?.displayName ?: "이름없음"
        val email = firebaseUser?.email ?: googleAccount?.email ?: ""

        if (uid.isNullOrBlank()) {
            Toast.makeText(this, "로그인 후 프로젝트에 참가할 수 있습니다.", Toast.LENGTH_SHORT).show()
            pendingInviteTeamId = null
            pendingInviteAssignmentRemoteId = null
            return
        }

        val memberData = mapOf(
            "name" to displayName,
            "email" to email,
            "uid" to uid,
            "joinedAt" to FieldValue.serverTimestamp()
        )

        Firebase.firestore.collection("teams")
            .document(teamId)
            .collection("assignments")
            .document(assignmentRemoteId)
            .collection("members")
            .document(uid)
            .set(memberData, SetOptions.merge())
            .addOnSuccessListener {
                pendingInviteTeamId = null
                pendingInviteAssignmentRemoteId = null
                Toast.makeText(this, "프로젝트 참가가 완료되었습니다.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                pendingInviteTeamId = null
                pendingInviteAssignmentRemoteId = null

                AlertDialog.Builder(this)
                    .setTitle("프로젝트 참가 실패")
                    .setMessage("오류 내용: ${e.message}")
                    .setPositiveButton("확인", null)
                    .show()
            }
    }

    private fun handleIntent(intent: Intent, navController: NavController) {
        val goToHome = intent.getBooleanExtra("go_to_home", false)
        if (goToHome) {
            navController.navigate(R.id.homeFragment)
            binding.bottomNav.selectedItemId = R.id.homeFragment
        }
    }
}