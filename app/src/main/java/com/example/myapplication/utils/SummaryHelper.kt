package com.example.myapplication.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OCR 및 AI 요약을 위한 유틸리티 클래스
 */
object SummaryHelper {

    // ───────── 로컬 파인튜닝 모델 설정 ─────────
    /** ngrok 터널을 통한 로컬 모델 서버 URL */
    private const val LOCAL_MODEL_URL = "https://elin-nonaphoristic-julian.ngrok-free.dev/v1/chat/completions"
    /** swift deploy의 served_model_name */
    private const val LOCAL_MODEL_NAME = "qwen35-sum"

    /**
     * URI로부터 Bitmap을 얻어온다.
     */
    fun getBitmapFromUri(context: Context, uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source).copy(Bitmap.Config.ARGB_8888, true)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri).copy(Bitmap.Config.ARGB_8888, true)
        }
    }

    /**
     * ML Kit을 이용하여 Bitmap에서 텍스트를 추출한다 (한국어 지원).
     */
    suspend fun extractTextFromBitmap(bitmap: Bitmap): String {
        return suspendCancellableCoroutine { continuation ->
            val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            val image = InputImage.fromBitmap(bitmap, 0)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    continuation.resume(visionText.text)
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
        }
    }

    /**
     * 텍스트 파일(txt 등)의 내용을 읽어온다.
     */
    fun readTextFromUri(context: Context, uri: Uri): String {
        val stringBuilder = StringBuilder()
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var line: String? = reader.readLine()
                while (line != null) {
                    stringBuilder.appendLine(line)
                    line = reader.readLine()
                }
            }
        }
        return stringBuilder.toString()
    }

    /**
     * 파일의 MIME 타입을 확인하여 이미지인지 판별한다.
     */
    fun isImageFile(context: Context, uri: Uri): Boolean {
        val mimeType = context.contentResolver.getType(uri)
        return mimeType?.startsWith("image/") == true
    }

    /**
     * 파일의 MIME 타입을 확인하여 텍스트 파일인지 판별한다.
     */
    fun isTextFile(context: Context, uri: Uri): Boolean {
        val mimeType = context.contentResolver.getType(uri)
        return mimeType?.startsWith("text/") == true
    }

    /**
     * userPrompt 유무에 따라 적절한 AI 모델로 요약을 수행한다.
     * - userPrompt가 비어있으면 → 로컬 파인튜닝 모델 (Qwen3.5-2B) 사용
     * - userPrompt가 있으면 → Gemini API 사용
     * @param context Android Context (assets 프롬프트 파일 읽기용)
     * @param extractedText OCR 또는 파일에서 추출된 원본 텍스트
     * @param userPrompt 사용자가 추가로 입력한 요약 방향 (비어있을 수 있음)
     * @return AI가 생성한 요약 텍스트
     */
    suspend fun summarize(context: Context, extractedText: String, userPrompt: String): String {
        return if (userPrompt.isBlank()) {
            summarizeWithLocalModel(extractedText)
        } else {
            summarizeWithGemini(context, extractedText, userPrompt)
        }
    }

    /**
     * 로컬 파인튜닝 모델(Qwen3.5-2B)을 사용하여 텍스트를 요약한다.
     * OpenAI 호환 API (/v1/chat/completions) 형식으로 호출한다.
     * @param extractedText OCR 또는 파일에서 추출된 원본 텍스트
     * @return 로컬 AI가 생성한 요약 텍스트
     */
    suspend fun summarizeWithLocalModel(extractedText: String): String {
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS) // 로컬 모델은 응답 시간이 길 수 있음
            .build()

        // OpenAI 호환 요청 본문 구성
        val requestBody = org.json.JSONObject().apply {
            put("model", LOCAL_MODEL_NAME)
            put("messages", org.json.JSONArray().apply {
                put(org.json.JSONObject().apply {
                    put("role", "user")
                    put("content", extractedText)
                })
            })
        }

        val jsonMediaType = "application/json; charset=utf-8".toMediaType()

        val request = okhttp3.Request.Builder()
            .url(LOCAL_MODEL_URL)
            .post(requestBody.toString().toRequestBody(jsonMediaType))
            .addHeader("ngrok-skip-browser-warning", "true") // ngrok 무료 브라우저 경고 우회
            .build()

        android.util.Log.d("SummaryHelper", "로컬 모델 요청: $LOCAL_MODEL_URL")

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()
            ?: throw Exception("로컬 모델 응답이 비어있습니다.")

        android.util.Log.d("SummaryHelper", "로컬 모델 응답 코드: ${response.code}")
        android.util.Log.d("SummaryHelper", "로컬 모델 응답 본문: $responseBody")

        if (!response.isSuccessful) {
            throw Exception("로컬 모델 API 오류 (${response.code}): $responseBody")
        }

        // OpenAI 호환 응답에서 텍스트 추출
        val jsonResponse = org.json.JSONObject(responseBody)
        val choices = jsonResponse.optJSONArray("choices")
            ?: throw Exception("로컬 모델 응답에 choices가 없습니다: $responseBody")

        val message = choices.getJSONObject(0)
            .optJSONObject("message")
            ?: throw Exception("로컬 모델 응답에 message가 없습니다.")

        return message.optString("content", "요약 결과를 생성할 수 없습니다.")
    }

    /**
     * assets/prom.txt에서 시스템 프롬프트를 읽어온다.
     * @param context Android Context
     * @return 프롬프트 문자열 (파일이 없으면 기본 프롬프트 반환)
     */
    private fun loadSystemPrompt(context: Context): String {
        return try {
            context.assets.open("prom.txt").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            android.util.Log.w("SummaryHelper", "prom.txt 로드 실패, 기본 프롬프트 사용: ${e.message}")
            "다음 텍스트를 한국어로 요약해 주세요."
        }
    }

    /**
     * Gemini AI를 사용하여 텍스트를 요약한다.
     * assets/prom.txt의 시스템 프롬프트를 기반으로 요약을 수행한다.
     * OkHttp를 사용하여 Gemini REST API를 직접 호출한다.
     * 429 에러 시 자동 재시도 (최대 3회, 지수 백오프).
     * @param context Android Context (프롬프트 파일 읽기용)
     * @param extractedText OCR 또는 파일에서 추출된 원본 텍스트
     * @param userPrompt 사용자가 추가로 입력한 요약 방향 (비어있을 수 있음)
     * @return AI가 생성한 요약 텍스트
     */
    suspend fun summarizeWithGemini(context: Context, extractedText: String, userPrompt: String): String {
        val apiKey = com.example.myapplication.BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            throw IllegalStateException("GEMINI_API_KEY가 설정되지 않았습니다. local.properties를 확인하세요.")
        }

        // assets/prom.txt에서 시스템 프롬프트 로드
        val systemPrompt = loadSystemPrompt(context)

        // 프롬프트 구성: 시스템 프롬프트 + 사용자 지시 + 원본 텍스트
        val prompt = buildString {
            appendLine(systemPrompt)
            appendLine()
            if (userPrompt.isNotBlank()) {
                appendLine("userPrompt: $userPrompt")
                appendLine()
            }
            appendLine("--- 원본 텍스트 ---")
            appendLine(extractedText)
        }

        // Gemini REST API 요청 본문 구성
        val requestBody = org.json.JSONObject().apply {
            put("contents", org.json.JSONArray().apply {
                put(org.json.JSONObject().apply {
                    put("parts", org.json.JSONArray().apply {
                        put(org.json.JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", org.json.JSONObject().apply {
                put("temperature", 1.0)
            })
        }
 val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=$apiKey"

        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val jsonMediaType = "application/json; charset=utf-8".toMediaType()

        // 최대 3번 재시도 (429 에러 시)
        val maxRetries = 3
        val retryDelays = longArrayOf(5000, 10000, 20000) // 5초, 10초, 20초

        for (attempt in 0..maxRetries) {
            val request = okhttp3.Request.Builder()
                .url(url)
                .post(requestBody.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
                ?: throw Exception("응답이 비어있습니다.")

            android.util.Log.d("SummaryHelper", "Gemini API 응답 코드: ${response.code}")
            android.util.Log.d("SummaryHelper", "Gemini API 응답 본문: $responseBody")

            if (response.code == 429 && attempt < maxRetries) {
                // 429 에러: 재시도 대기
                val errorMessage = parseErrorMessage(responseBody)
                android.util.Log.w("SummaryHelper", "429 오류 (시도 ${attempt + 1}/$maxRetries): $errorMessage. ${retryDelays[attempt] / 1000}초 후 재시도...")
                kotlinx.coroutines.delay(retryDelays[attempt])
                continue
            }

            if (!response.isSuccessful) {
                val errorMessage = parseErrorMessage(responseBody)
                throw Exception("API 오류 (${response.code}): $errorMessage")
            }

            // 성공: 응답에서 텍스트 추출
            val jsonResponse = org.json.JSONObject(responseBody)
            val candidates = jsonResponse.optJSONArray("candidates")
                ?: throw Exception("응답에 candidates가 없습니다: $responseBody")

            val content = candidates.getJSONObject(0)
                .optJSONObject("content")
                ?: throw Exception("응답에 content가 없습니다.")

            val parts = content.optJSONArray("parts")
                ?: throw Exception("응답에 parts가 없습니다.")

            return parts.getJSONObject(0).optString("text", "요약 결과를 생성할 수 없습니다.")
        }

        throw Exception("API 요청이 여러 번 실패했습니다. 잠시 후 다시 시도해 주세요.")
    }

    /**
     * Gemini API 에러 응답에서 사용자 친화적 메시지를 추출한다.
     */
    private fun parseErrorMessage(responseBody: String): String {
        return try {
            val json = org.json.JSONObject(responseBody)
            val error = json.optJSONObject("error")
            if (error != null) {
                val message = error.optString("message", "")
                val status = error.optString("status", "")
                if (message.isNotBlank()) "$status: $message" else responseBody
            } else {
                responseBody
            }
        } catch (e: Exception) {
            responseBody
        }
    }
}