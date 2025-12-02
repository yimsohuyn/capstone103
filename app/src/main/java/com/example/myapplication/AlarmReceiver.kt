package com.example.myapplication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 1. Intent에서 데이터 가져오기

        // 🔥 일정 제목을 가져옵니다. 제목이 없을 경우 "일정 알림"을 기본값으로 사용합니다.
        val title = intent.getStringExtra("title") ?: "일정 알림"

        // 일정 내용을 가져옵니다. 내용이 없을 경우 기본 메시지를 사용합니다.
        val content = intent.getStringExtra("content") ?: "알림이 도착했습니다."

        // Logcat 확인용
        Log.d("AlarmReceiver", "알람 수신됨: $title - $content")

        // 현재 시간을 알림 ID로 사용하여 알림이 겹쳐도 이전 알림이 사라지지 않게 합니다.
        val notificationId = System.currentTimeMillis().toInt()
        val channelId = "schedule_alarm_channel" // 채널 ID

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 2. 알림 채널 생성 (Android O 이상)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "일정 알림", // 사용자에게 표시되는 채널 이름
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "사용자가 설정한 일정 알림 채널입니다." // 채널 설명 추가
                enableVibration(true) // 진동 활성화
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 3. 알림 클릭 시 실행될 인텐트 (앱의 메인 화면으로 이동)
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0, // requestCode는 0으로 둡니다.
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE // FLAG_IMMUTABLE 필수
        )

        // 4. 알림 빌드
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.outline_alarm_24)
            .setContentTitle(title) // 🔥 [수정] 전달받은 일정 제목을 사용합니다.
            .setContentText(content) // 🔥 [수정] 전달받은 일정 내용을 사용합니다.
            .setPriority(NotificationCompat.PRIORITY_HIGH) // 중요도 높게 설정
            .setContentIntent(pendingIntent) // 클릭 시 실행될 인텐트 설정
            .setAutoCancel(true) // 클릭하면 알림이 자동으로 사라지게 설정
            .build()

        notificationManager.notify(notificationId, notification)
    }
}