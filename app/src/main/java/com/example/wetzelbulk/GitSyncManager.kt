package com.example.wetzelbulk

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

data class GitConfig(
    val repo: String = "",
    val token: String = "",
    val branch: String = "main",
    val autoSync: Boolean = true,
    val lastSyncTime: Long = 0L,
    val lastSyncStatus: String = ""
) {
    fun isValid(): Boolean = repo.isNotBlank() && repo.contains("/") && token.isNotBlank()
}

object GitSyncManager {
    private const val TAG = "GitSyncManager"
    private const val PREFS_NAME = "WetzelBulkGitPrefs"

    const val DEFAULT_REPO = "wetzeltg/WetzelBulk"
    val DEFAULT_TOKEN: String = BuildConfig.GITHUB_DEFAULT_TOKEN
    const val DEFAULT_BRANCH = "main"

    private const val KEY_REPO = "github_repo"
    private const val KEY_TOKEN = "github_token"
    private const val KEY_BRANCH = "github_branch"
    private const val KEY_AUTO_SYNC = "github_auto_sync"
    private const val KEY_LAST_SYNC_TIME = "github_last_sync_time"
    private const val KEY_LAST_SYNC_STATUS = "github_last_sync_status"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getConfig(context: Context): GitConfig {
        val prefs = getPrefs(context)
        val savedRepo = prefs.getString(KEY_REPO, DEFAULT_REPO)?.takeIf { it.isNotBlank() } ?: DEFAULT_REPO
        val savedToken = prefs.getString(KEY_TOKEN, DEFAULT_TOKEN)?.takeIf { it.isNotBlank() } ?: DEFAULT_TOKEN
        val savedBranch = prefs.getString(KEY_BRANCH, DEFAULT_BRANCH)?.takeIf { it.isNotBlank() } ?: DEFAULT_BRANCH
        return GitConfig(
            repo = savedRepo,
            token = savedToken,
            branch = savedBranch,
            autoSync = prefs.getBoolean(KEY_AUTO_SYNC, true),
            lastSyncTime = prefs.getLong(KEY_LAST_SYNC_TIME, 0L),
            lastSyncStatus = prefs.getString(KEY_LAST_SYNC_STATUS, "") ?: ""
        )
    }

    fun saveConfig(
        context: Context,
        repo: String,
        token: String,
        branch: String = "main",
        autoSync: Boolean = true
    ) {
        val cleanRepo = repo.trim().removePrefix("https://github.com/").removeSuffix(".git").trim('/')
        getPrefs(context).edit()
            .putString(KEY_REPO, cleanRepo)
            .putString(KEY_TOKEN, token.trim())
            .putString(KEY_BRANCH, if (branch.isBlank()) "main" else branch.trim())
            .putBoolean(KEY_AUTO_SYNC, autoSync)
            .apply()
    }

    fun recordSyncResult(context: Context, success: Boolean, message: String) {
        getPrefs(context).edit()
            .putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis())
            .putString(KEY_LAST_SYNC_STATUS, if (success) "SUCCESS: $message" else "FAILED: $message")
            .apply()
    }

    fun isConfigured(context: Context): Boolean {
        return getConfig(context).isValid()
    }

    fun isAutoSyncEnabled(context: Context): Boolean {
        val config = getConfig(context)
        return config.isValid() && config.autoSync
    }

    suspend fun testConnection(repoInput: String, tokenInput: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanRepo = repoInput.trim().removePrefix("https://github.com/").removeSuffix(".git").trim('/')
            if (!cleanRepo.contains("/")) {
                return@withContext Result.failure(Exception("Repository must be in format 'owner/repository'"))
            }
            if (tokenInput.isBlank()) {
                return@withContext Result.failure(Exception("Personal Access Token cannot be blank"))
            }

            val url = URL("https://api.github.com/repos/$cleanRepo")
            val connection = (url.openConnection() as HttpsURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer ${tokenInput.trim()}")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "WetzelBulk-Android")
                connectTimeout = 12000
                readTimeout = 12000
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val responseBody = readStream(connection.inputStream)
                val json = JSONObject(responseBody)
                val fullName = json.optString("full_name", cleanRepo)
                val isPrivate = json.optBoolean("private", false)
                val visibility = if (isPrivate) "private" else "public"
                Result.success("Connected to $fullName ($visibility)")
            } else {
                val errorMsg = readStream(connection.errorStream)
                Result.failure(Exception("HTTP $responseCode: ${parseGitHubError(errorMsg)}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Test connection failed", e)
            Result.failure(Exception("Connection error: ${e.localizedMessage ?: e.message}"))
        }
    }

    suspend fun syncDatabaseToGit(
        context: Context,
        database: WetzelBulkDatabase,
        commitNote: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val config = getConfig(context)
        if (!config.isValid()) {
            val err = "GitHub Sync is not configured. Please enter Repo & Token in Settings."
            recordSyncResult(context, false, err)
            return@withContext Result.failure(Exception(err))
        }

        try {
            // 1. Force SQLite WAL checkpoint to flush uncommitted logs to the primary db file
            try {
                database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
            } catch (e: Exception) {
                Log.w(TAG, "WAL checkpoint notification: ${e.message}")
            }

            // 2. Read SQLite database bytes
            val dbFile = context.getDatabasePath("wetzel_bulk_db")
            if (!dbFile.exists()) {
                val err = "Database file does not exist at ${dbFile.path}"
                recordSyncResult(context, false, err)
                return@withContext Result.failure(Exception(err))
            }
            val dbBytes = dbFile.readBytes()
            val dbBase64 = Base64.encodeToString(dbBytes, Base64.NO_WRAP)

            // 3. Build comprehensive JSON export
            val jsonExport = exportDatabaseToJson(database.workoutDao())
            val jsonBytes = jsonExport.toByteArray(Charsets.UTF_8)
            val jsonBase64 = Base64.encodeToString(jsonBytes, Base64.NO_WRAP)

            val timestampStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val baseMessage = commitNote ?: "Workout log sync ($timestampStr)"

            // 4. Upload SQLite file to data/wetzel_bulk_db.sqlite
            val sqliteUpload = uploadFileToGitHub(
                repo = config.repo,
                branch = config.branch,
                token = config.token,
                path = "data/wetzel_bulk_db.sqlite",
                contentBase64 = dbBase64,
                commitMessage = "$baseMessage [sqlite backup]"
            )
            if (sqliteUpload.isFailure) {
                val err = "Failed to upload wetzel_bulk_db.sqlite: ${sqliteUpload.exceptionOrNull()?.message}"
                recordSyncResult(context, false, err)
                return@withContext Result.failure(Exception(err))
            }

            // 5. Upload JSON file to data/workouts.json
            val jsonUpload = uploadFileToGitHub(
                repo = config.repo,
                branch = config.branch,
                token = config.token,
                path = "data/workouts.json",
                contentBase64 = jsonBase64,
                commitMessage = "$baseMessage [json export]"
            )
            if (jsonUpload.isFailure) {
                val err = "Failed to upload workouts.json: ${jsonUpload.exceptionOrNull()?.message}"
                recordSyncResult(context, false, err)
                return@withContext Result.failure(Exception(err))
            }

            val successMsg = "Successfully uploaded SQLite & JSON to GitHub (${config.repo}) at $timestampStr"
            recordSyncResult(context, true, successMsg)
            Result.success(successMsg)
        } catch (e: Exception) {
            Log.e(TAG, "Sync to Git failed", e)
            val err = "Upload error: ${e.localizedMessage ?: e.message}"
            recordSyncResult(context, false, err)
            Result.failure(Exception(err))
        }
    }

    /** Downloads a text file from the configured repo/branch. Used for coach/notes.json. Read-only. */
    suspend fun fetchTextFile(context: Context, path: String): Result<String> = withContext(Dispatchers.IO) {
        val config = getConfig(context)
        if (!config.isValid()) return@withContext Result.failure(Exception("GitHub not configured"))
        try {
            val url = URL("https://api.github.com/repos/${config.repo}/contents/$path?ref=${config.branch}")
            val connection = (url.openConnection() as HttpsURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer ${config.token}")
                setRequestProperty("Accept", "application/vnd.github.raw+json")
                setRequestProperty("User-Agent", "WetzelBulk-Android")
                useCaches = false
                connectTimeout = 12000
                readTimeout = 12000
            }
            val code = connection.responseCode
            if (code == 200) Result.success(readStream(connection.inputStream))
            else Result.failure(Exception("HTTP $code: ${parseGitHubError(readStream(connection.errorStream))}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun getFileSha(
        repo: String,
        branch: String,
        token: String,
        path: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/$repo/contents/$path?ref=$branch")
            val connection = (url.openConnection() as HttpsURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "WetzelBulk-Android")
                connectTimeout = 12000
                readTimeout = 12000
            }

            if (connection.responseCode == 200) {
                val body = readStream(connection.inputStream)
                val json = JSONObject(body)
                if (json.has("sha")) json.getString("sha") else null
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun uploadFileToGitHub(
        repo: String,
        branch: String,
        token: String,
        path: String,
        contentBase64: String,
        commitMessage: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Check if file already exists to obtain its SHA (required by GitHub API to update existing files)
            val existingSha = getFileSha(repo, branch, token, path)

            val url = URL("https://api.github.com/repos/$repo/contents/$path")
            val connection = (url.openConnection() as HttpsURLConnection).apply {
                requestMethod = "PUT"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("User-Agent", "WetzelBulk-Android")
                doOutput = true
                connectTimeout = 20000
                readTimeout = 25000
            }

            val payload = JSONObject().apply {
                put("message", commitMessage)
                put("content", contentBase64)
                put("branch", branch)
                if (existingSha != null) {
                    put("sha", existingSha)
                }
            }

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                Result.success("File committed successfully: $path")
            } else {
                val errBody = readStream(connection.errorStream)
                Result.failure(Exception("GitHub API $responseCode: ${parseGitHubError(errBody)}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun exportDatabaseToJson(dao: WorkoutDao): String {
        val root = JSONObject()
        root.put("exportVersion", 1)
        root.put("exportedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))

        val allSessions = dao.getAllSessionsDesc()
        root.put("totalSessions", allSessions.size)

        val sessionsArray = JSONArray()
        for (session in allSessions) {
            val sessionObj = JSONObject().apply {
                put("sessionId", session.sessionId)
                put("dateString", session.dateString)
                put("dayName", session.dayName)
                put("startTime", session.startTime)
                put("endTime", session.endTime)
                put("bodyWeight", session.bodyWeight)
                // Added in v2.1: the coach note that was current when this workout was finished
                session.coachNoteId?.let { put("coachNoteId", it) }
            }

            val logs = dao.getSetLogsForSession(session.sessionId)
            val logsArray = JSONArray()
            for (log in logs) {
                val logObj = JSONObject().apply {
                    put("logId", log.logId)
                    put("exerciseBlockTitle", log.exerciseBlockTitle)
                    put("exerciseLabel", log.exerciseLabel)
                    put("setNum", log.setNum)
                    put("weightAchieved", log.weightAchieved)
                    put("repsAchieved", log.repsAchieved)
                    // Added in v2.0; omitted for sets logged before rest was recorded
                    if (log.restSeconds >= 0) put("restSeconds", log.restSeconds)
                }
                logsArray.put(logObj)
            }
            sessionObj.put("sets", logsArray)
            sessionsArray.put(sessionObj)
        }
        root.put("sessions", sessionsArray)

        // Export exercise notes
        val allNotes = dao.getAllNotes()
        val notesArray = JSONArray()
        for (note in allNotes) {
            val noteObj = JSONObject().apply {
                put("blockTitle", note.blockTitle)
                put("notesText", note.notesText)
            }
            notesArray.put(noteObj)
        }
        root.put("exerciseNotes", notesArray)

        return root.toString(2)
    }

    private fun readStream(stream: java.io.InputStream?): String {
        if (stream == null) return ""
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
    }

    private fun parseGitHubError(jsonResponse: String): String {
        return try {
            val json = JSONObject(jsonResponse)
            val msg = json.optString("message", "")
            if (msg.isNotBlank()) msg else jsonResponse
        } catch (_: Exception) {
            jsonResponse
        }
    }
}
