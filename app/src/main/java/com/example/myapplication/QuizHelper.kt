package com.example.myapplication.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import com.example.myapplication.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object QuizHelper {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(110, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build()

    fun isPdfFile(context: Context, uri: Uri): Boolean {
        val mimeType = context.contentResolver.getType(uri).orEmpty()
        val fileName = getFileName(context, uri).lowercase()
        return mimeType == "application/pdf" || fileName.endsWith(".pdf")
    }

    fun isTxtFile(context: Context, uri: Uri): Boolean {
        val mimeType = context.contentResolver.getType(uri).orEmpty()
        val fileName = getFileName(context, uri).lowercase()
        return mimeType == "text/plain" || fileName.endsWith(".txt")
    }

    fun isImageFile(context: Context, uri: Uri): Boolean {
        val mimeType = context.contentResolver.getType(uri).orEmpty()
        val fileName = getFileName(context, uri).lowercase()

        return mimeType.startsWith("image/") ||
                fileName.endsWith(".jpg") ||
                fileName.endsWith(".jpeg") ||
                fileName.endsWith(".png") ||
                fileName.endsWith(".webp")
    }

    fun isJpgFile(context: Context, uri: Uri): Boolean {
        return isImageFile(context, uri)
    }

    suspend fun extractTextFromTxt(context: Context, uri: Uri): String =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream)).use { reader ->
                        reader.readText()
                            .replace(Regex("\\s+"), " ")
                            .take(6000)
                    }
                }.orEmpty()
            } catch (e: Exception) {
                Log.e("QUIZ_TXT_ERROR", "txt 추출 실패", e)
                ""
            }
        }

    suspend fun generateQuizWithGemini(
        extractedText: String,
        userPrompt: String
    ): String =
        withContext(Dispatchers.IO) {
            generateQuizWithGeminiInternal(
                extractedText = extractedText,
                imageBase64List = emptyList(),
                userPrompt = userPrompt
            )
        }

    suspend fun generateQuizWithGeminiFromFiles(
        context: Context,
        fileUris: List<Uri>,
        userPrompt: String
    ): String =
        withContext(Dispatchers.IO) {
            val textBuilder = StringBuilder()
            val imageBase64List = mutableListOf<Pair<String, String>>()

            fileUris.take(2).forEach { uri ->
                when {
                    isTxtFile(context, uri) -> {
                        val text = extractTextFromTxt(context, uri)
                        if (text.isNotBlank()) {
                            textBuilder.appendLine(text.take(5000))
                        }
                    }

                    isImageFile(context, uri) -> {
                        val mimeType = context.contentResolver.getType(uri)
                            ?: "image/jpeg"

                        val base64 = uriToBase64(context, uri)
                        if (base64.isNotBlank()) {
                            imageBase64List.add(mimeType to base64)
                        }
                    }

                    isPdfFile(context, uri) -> {
                        val bitmaps = pdfToBitmaps(context, uri)

                        bitmaps.take(1).forEach { bitmap ->
                            try {
                                val base64 = bitmapToBase64(bitmap)
                                if (base64.isNotBlank()) {
                                    imageBase64List.add("image/jpeg" to base64)
                                }
                            } finally {
                                if (!bitmap.isRecycled) bitmap.recycle()
                            }
                        }
                    }
                }
            }

            generateQuizWithGeminiInternal(
                extractedText = textBuilder.toString(),
                imageBase64List = imageBase64List,
                userPrompt = userPrompt
            )
        }

    private fun generateQuizWithGeminiInternal(
        extractedText: String,
        imageBase64List: List<Pair<String, String>>,
        userPrompt: String
    ): String {
        val apiKey = BuildConfig.GEMINI_API_KEY.trim()

        if (apiKey.isBlank()) {
            return "퀴즈 생성 실패: GEMINI_API_KEY가 비어 있습니다."
        }

        val trimmedText = extractedText
            .replace(Regex("\\s+"), " ")
            .take(6000)

        if (trimmedText.isBlank() && imageBase64List.isEmpty()) {
            return "퀴즈 생성 실패: 사용할 수 있는 텍스트나 이미지가 없습니다."
        }

        val prompt = buildString {
            appendLine(userPrompt.ifBlank { "학습 자료를 바탕으로 퀴즈를 만들어줘." })
            appendLine()
            appendLine("[출력 규칙]")
            appendLine("반드시 JSON 배열만 출력해.")
            appendLine("마크다운, 설명문, 코드블록은 절대 쓰지 마.")
            appendLine("객관식 4지선다 문제로 만들어.")
            appendLine()
            appendLine("[JSON 형식]")
            appendLine("""[{"id":1,"question":"문제","options":["보기1","보기2","보기3","보기4"],"answerIndex":0,"explanation":"해설"}]""")
            appendLine()
            appendLine("[필수 조건]")
            appendLine("- question은 비우지 말 것")
            appendLine("- options는 반드시 4개")
            appendLine("- answerIndex는 0,1,2,3 중 하나")
            appendLine("- explanation은 짧게")
            appendLine()
            appendLine("[학습 자료]")
            appendLine(trimmedText.ifBlank { "첨부된 이미지를 분석해서 문제를 만들어줘." })
        }

        val parts = JSONArray().apply {
            put(JSONObject().put("text", prompt))

            imageBase64List.forEach { pair ->
                put(
                    JSONObject().put(
                        "inline_data",
                        JSONObject()
                            .put("mime_type", pair.first)
                            .put("data", pair.second)
                    )
                )
            }
        }

        val requestJson = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put("parts", parts)
                )
            )

            put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0.1)
                    .put("maxOutputTokens", 2000)
            )
        }

        val url =
            "https://generativelanguage.googleapis.com/v1/models/gemini-2.0-flash:generateContent?key=$apiKey"

        val requestBody = requestJson.toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    Log.e("GEMINI_HTTP_ERROR", responseBody)

                    return when (response.code) {
                        400 -> "퀴즈 생성 실패: 요청 형식이 올바르지 않습니다.\n${responseBody.take(500)}"
                        401 -> "퀴즈 생성 실패: API 키가 올바르지 않습니다."
                        403 -> "퀴즈 생성 실패: API 접근 권한이 없습니다."
                        404 -> "퀴즈 생성 실패: Gemini 모델을 찾을 수 없습니다."
                        429 -> "퀴즈 생성 실패: 요청이 너무 많거나 사용 한도를 초과했습니다."
                        500, 502, 503, 504 -> "퀴즈 생성 실패: Gemini 서버가 불안정합니다."
                        else -> "퀴즈 생성 실패\nHTTP ${response.code}\n${responseBody.take(500)}"
                    }
                }

                if (responseBody.isBlank()) {
                    return "퀴즈 생성 실패: 응답 본문이 비어 있습니다."
                }

                val json = JSONObject(responseBody)
                val candidates = json.optJSONArray("candidates")
                    ?: return "퀴즈 생성 실패: 생성된 퀴즈가 없습니다."

                val rawText = buildString {
                    for (i in 0 until candidates.length()) {
                        val candidate = candidates.optJSONObject(i) ?: continue
                        val content = candidate.optJSONObject("content") ?: continue
                        val responseParts = content.optJSONArray("parts") ?: continue

                        for (j in 0 until responseParts.length()) {
                            val text = responseParts.optJSONObject(j)
                                ?.optString("text")
                                .orEmpty()

                            if (text.isNotBlank()) {
                                append(text.trim())
                            }
                        }
                    }
                }.trim()

                Log.e("GEMINI_DEBUG", "Gemini 응답 받음")

                Log.e("GEMINI_RAW_RESPONSE", rawText)

                if (rawText.isBlank()) {
                    return "퀴즈 생성 실패: 퀴즈 생성 결과가 없습니다."
                }

                extractJsonArrayText(rawText)
            }
        } catch (e: Exception) {
            Log.e("QUIZ_API_ERROR", "Gemini 호출 실패", e)
            "퀴즈 생성 실패: ${e.message ?: "알 수 없는 오류"}"
        }
    }

    private fun extractJsonArrayText(rawText: String): String {
        val text = rawText
            .trim()
            .replace("```json", "")
            .replace("```JSON", "")
            .replace("```", "")
            .trim()

        val start = text.indexOf("[")
        val end = text.lastIndexOf("]")

        return if (start >= 0 && end >= start) {
            text.substring(start, end + 1).trim()
        } else {
            text
        }
    }

    fun parseQuizItems(jsonText: String): List<QuizItem> {
        return try {
            val quizJson = extractJsonArrayText(jsonText)

            if (quizJson.isBlank() || !quizJson.startsWith("[")) {
                return emptyList()
            }

            val jsonArray = JSONArray(quizJson)
            val result = mutableListOf<QuizItem>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue

                val question = obj.optString("question").trim()
                val explanation = obj.optString("explanation", "").trim()

                val optionsArray = obj.optJSONArray("options") ?: JSONArray()
                val options = mutableListOf<String>()

                for (j in 0 until optionsArray.length()) {
                    val option = optionsArray.optString(j).trim()
                    if (option.isNotBlank()) {
                        options.add(option)
                    }
                }

                if (question.isBlank()) continue
                if (options.size < 2) continue

                val fixedOptions = options.toMutableList()

                while (fixedOptions.size < 4) {
                    fixedOptions.add("선택지 ${fixedOptions.size + 1}")
                }

                if (fixedOptions.size > 4) {
                    fixedOptions.subList(4, fixedOptions.size).clear()
                }

                var answerIndex = obj.optInt("answerIndex", -1)

                if (answerIndex !in 0..3) {
                    answerIndex = 0
                }

                result.add(
                    QuizItem(
                        id = obj.optInt("id", i + 1),
                        question = question,
                        options = fixedOptions,
                        answerIndex = answerIndex,
                        explanation = explanation,
                        type = "choice"
                    )
                )
            }

            result
        } catch (e: Exception) {
            Log.e("QUIZ_PARSE_ERROR", "JSON 파싱 실패", e)
            emptyList()
        }
    }

    fun isQuizJsonValid(jsonText: String): Boolean {
        return parseQuizItems(jsonText).isNotEmpty()
    }

    private fun getFileName(context: Context, uri: Uri): String {
        val cursor = context.contentResolver.query(uri, null, null, null, null)

        cursor?.use {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)

            if (it.moveToFirst() && nameIndex >= 0) {
                return it.getString(nameIndex) ?: "unknown"
            }
        }

        return uri.lastPathSegment ?: "unknown"
    }

    private fun uriToBase64(context: Context, uri: Uri): String {
        return try {
            val bitmap = loadBitmapFromUri(context, uri) ?: return ""
            try {
                bitmapToBase64(bitmap)
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        } catch (e: Exception) {
            Log.e("QUIZ_IMAGE_BASE64_ERROR", "이미지 base64 변환 실패", e)
            ""
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val resized = resizeBitmap(bitmap, 600)

        val outputStream = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)

        if (resized != bitmap && !resized.isRecycled) {
            resized.recycle()
        }

        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private fun resizeBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxSize && height <= maxSize) {
            return bitmap
        }

        val ratio = minOf(
            maxSize.toFloat() / width.toFloat(),
            maxSize.toFloat() / height.toFloat()
        )

        val newWidth = (width * ratio).toInt().coerceAtLeast(1)
        val newHeight = (height * ratio).toInt().coerceAtLeast(1)

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun pdfToBitmaps(context: Context, uri: Uri): List<Bitmap> {
        val bitmaps = mutableListOf<Bitmap>()

        try {
            val fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
                ?: return emptyList()

            val renderer = PdfRenderer(fileDescriptor)

            try {
                val pageCount = minOf(renderer.pageCount, 1)

                for (i in 0 until pageCount) {
                    val page = renderer.openPage(i)

                    try {
                        val scale = 1
                        val bitmap = Bitmap.createBitmap(
                            page.width * scale,
                            page.height * scale,
                            Bitmap.Config.ARGB_8888
                        )

                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmaps.add(bitmap)
                    } finally {
                        page.close()
                    }
                }
            } finally {
                renderer.close()
                fileDescriptor.close()
            }
        } catch (e: Exception) {
            Log.e("QUIZ_PDF_IMAGE_ERROR", "PDF 이미지 변환 실패", e)
        }

        return bitmaps
    }

    private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)

                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }.copy(Bitmap.Config.ARGB_8888, true)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    .copy(Bitmap.Config.ARGB_8888, true)
            }
        } catch (e: Exception) {
            Log.e("QUIZ_BITMAP_ERROR", "이미지 로드 실패", e)
            null
        }
    }
}