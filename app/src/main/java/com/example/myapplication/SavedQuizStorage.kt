package com.example.myapplication

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object SavedQuizStorage {

    private const val PREF_NAME = "saved_quiz_pref"
    private const val KEY_SAVED_QUIZ_LIST = "saved_quiz_list"
    private const val WRONG_QUIZ_PREFIX = "WrongQuiz_"

    fun saveQuizText(context: Context, fileName: String, content: String): SavedQuizItem? {
        return try {
            val trimmedContent = content.trim()
            if (trimmedContent.isBlank()) return null

            val saveDirectory = File(context.filesDir, "saved_quizzes")
            if (!saveDirectory.exists()) {
                saveDirectory.mkdirs()
            }

            val safeBaseName = sanitizeFileName(fileName)
            if (safeBaseName.isBlank()) return null

            val finalFileName = ensureUniqueFileName(
                directory = saveDirectory,
                fileName = ensureTxtExtension(safeBaseName)
            )

            val file = File(saveDirectory, finalFileName)
            file.writeText(trimmedContent)

            val item = SavedQuizItem(
                fileName = finalFileName,
                filePath = file.absolutePath,
                savedAt = System.currentTimeMillis()
            )

            val currentList = getSavedQuizList(context).toMutableList()
            currentList.removeAll { it.filePath == item.filePath }
            currentList.add(item)

            saveQuizList(context, sortSavedQuizList(currentList))
            item
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getSavedQuizList(context: Context): List<SavedQuizItem> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_SAVED_QUIZ_LIST, "[]") ?: "[]"

        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<SavedQuizItem>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val item = SavedQuizItem(
                    fileName = obj.getString("fileName"),
                    filePath = obj.getString("filePath"),
                    savedAt = obj.getLong("savedAt")
                )

                if (File(item.filePath).exists()) {
                    list.add(item)
                }
            }

            sortSavedQuizList(list)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun readQuizText(item: SavedQuizItem): String? {
        return try {
            val file = File(item.filePath)
            if (!file.exists()) return null
            file.readText()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteQuiz(context: Context, item: SavedQuizItem) {
        try {
            File(item.filePath).delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val updatedList = getSavedQuizList(context)
            .filter { it.filePath != item.filePath }

        saveQuizList(context, sortSavedQuizList(updatedList))
    }

    fun renameQuiz(context: Context, item: SavedQuizItem, newFileName: String): SavedQuizItem? {
        return try {
            val safeFileName = sanitizeFileName(newFileName)
            if (safeFileName.isBlank()) return null

            val finalFileName = ensureTxtExtension(safeFileName)

            val oldFile = File(item.filePath)
            if (!oldFile.exists()) return null

            val parentDir = oldFile.parentFile ?: return null
            val newFile = File(parentDir, finalFileName)

            if (newFile.exists() && newFile.absolutePath != oldFile.absolutePath) {
                return null
            }

            val renamed = if (oldFile.absolutePath == newFile.absolutePath) {
                true
            } else {
                oldFile.renameTo(newFile)
            }

            if (!renamed) return null

            val renamedItem = item.copy(
                fileName = finalFileName,
                filePath = newFile.absolutePath
            )

            val currentList = getSavedQuizList(context).toMutableList()
            val index = currentList.indexOfFirst { it.filePath == item.filePath }

            if (index != -1) {
                currentList[index] = renamedItem
                saveQuizList(context, sortSavedQuizList(currentList))
                renamedItem
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun saveQuizList(context: Context, list: List<SavedQuizItem>) {
        val jsonArray = JSONArray()

        list.forEach { item ->
            val obj = JSONObject().apply {
                put("fileName", item.fileName)
                put("filePath", item.filePath)
                put("savedAt", item.savedAt)
            }
            jsonArray.put(obj)
        }

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SAVED_QUIZ_LIST, jsonArray.toString()).apply()
    }

    private fun sortSavedQuizList(list: List<SavedQuizItem>): List<SavedQuizItem> {
        return list.sortedWith(
            compareByDescending<SavedQuizItem> {
                it.fileName.startsWith(WRONG_QUIZ_PREFIX, ignoreCase = true)
            }.thenByDescending {
                it.savedAt
            }
        )
    }

    private fun ensureTxtExtension(fileName: String): String {
        return if (fileName.lowercase().endsWith(".txt")) {
            fileName
        } else {
            "$fileName.txt"
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name
            .replace(Regex("""[\\/:*?"<>|]"""), "")
            .replace("\n", "")
            .replace("\r", "")
            .trim()
    }

    private fun ensureUniqueFileName(directory: File, fileName: String): String {
        val dotIndex = fileName.lastIndexOf('.')
        val baseName = if (dotIndex >= 0) fileName.substring(0, dotIndex) else fileName
        val extension = if (dotIndex >= 0) fileName.substring(dotIndex) else ""

        var candidate = fileName
        var count = 1

        while (File(directory, candidate).exists()) {
            candidate = "${baseName}_$count$extension"
            count++
        }

        return candidate
    }
}