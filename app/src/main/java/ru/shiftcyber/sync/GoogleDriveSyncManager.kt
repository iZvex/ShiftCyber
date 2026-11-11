@file:Suppress("DEPRECATION")

package ru.shiftcyber.sync

import android.accounts.Account
import android.annotation.SuppressLint
import android.app.backup.BackupManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import ru.shiftcyber.data.AppSettings
import ru.shiftcyber.data.ShiftDatabase
import ru.shiftcyber.domain.ShiftJsonCodec
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

class ConsentRequiredException(val consentIntent: Intent) : Exception("Требуется подтверждение доступа к Google Диску")

object GoogleDriveSyncManager {
    private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    private const val OAUTH_SCOPE_TOKEN = "oauth2:$DRIVE_APPDATA_SCOPE"
    private const val BACKUP_FILE_NAME = "shift_cyber_sync_v2.json"

    fun getSignInClient(context: Context): GoogleSignInClient {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DRIVE_APPDATA_SCOPE))
            .build()
        return GoogleSignIn.getClient(context.applicationContext, options)
    }

    fun getSignedInAccount(context: Context): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context.applicationContext)

    suspend fun signOut(context: Context) = withContext(Dispatchers.IO) {
        runCatching {
            suspendCancellableCoroutine { cont ->
                getSignInClient(context).signOut().addOnCompleteListener {
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        }
    }

    fun notifySystemBackup(context: Context) {
        runCatching { BackupManager.dataChanged(context.packageName) }
    }

    suspend fun uploadToGoogleDrive(context: Context): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val appContext = context.applicationContext
            val signedIn = getSignedInAccount(appContext)
                ?: throw IllegalStateException("Сначала войди в аккаунт Google")
            val account: Account = signedIn.account
                ?: throw IllegalStateException("Не удалось получить аккаунт Google на устройстве")

            val token = acquireAccessToken(appContext, account)
            val db = ShiftDatabase.get(appContext)
            val shifts = db.shifts().getAll()
            val settings = db.settings().getOnce() ?: AppSettings()
            val payloadJson = ShiftJsonCodec.encodeSyncPayload(shifts, settings)

            val existingFileId = findBackupFileId(token)
            val targetFileId = existingFileId ?: createEmptyAppDataFile(token)
            uploadMediaContent(token, targetFileId, payloadJson)

            val now = System.currentTimeMillis()
            db.settings().save(
                settings.copy(
                    lastSyncMillis = now,
                    googleAccountEmail = signedIn.email.orEmpty()
                )
            )
            notifySystemBackup(appContext)
            shifts.size
        }
    }

    suspend fun downloadFromGoogleDrive(context: Context): Result<ShiftJsonCodec.SyncPayload> =
        withContext(Dispatchers.IO) {
            runCatching {
                val appContext = context.applicationContext
                val signedIn = getSignedInAccount(appContext)
                    ?: throw IllegalStateException("Сначала войди в аккаунт Google")
                val account: Account = signedIn.account
                    ?: throw IllegalStateException("Не удалось получить аккаунт Google на устройстве")

                val token = acquireAccessToken(appContext, account)
                val fileId = findBackupFileId(token)
                    ?: throw IllegalStateException("В облаке Google пока нет сохранённой копии графика")
                val rawJson = downloadMediaContent(token, fileId)
                val currentSettings = ShiftDatabase.get(appContext).settings().getOnce() ?: AppSettings()
                ShiftJsonCodec.decodeSyncPayload(rawJson, currentSettings)
            }
        }

    suspend fun exportToUri(context: Context, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val appContext = context.applicationContext
            val db = ShiftDatabase.get(appContext)
            val shifts = db.shifts().getAll()
            val settings = db.settings().getOnce() ?: AppSettings()
            val json = ShiftJsonCodec.encodeSyncPayload(shifts, settings)

            appContext.contentResolver.openOutputStream(uri)?.use { out ->
                OutputStreamWriter(out, Charsets.UTF_8).use { writer ->
                    writer.write(json)
                    writer.flush()
                }
            } ?: throw IllegalStateException("Не удалось открыть файл в Google Диске для записи")

            db.settings().save(settings.copy(lastSyncMillis = System.currentTimeMillis()))
            notifySystemBackup(appContext)
            shifts.size
        }
    }

    suspend fun importFromUri(context: Context, uri: Uri): Result<ShiftJsonCodec.SyncPayload> =
        withContext(Dispatchers.IO) {
            runCatching {
                val appContext = context.applicationContext
                val rawJson = appContext.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    ?: throw IllegalStateException("Не удалось прочитать выбранный файл из Google Диска")
                val currentSettings = ShiftDatabase.get(appContext).settings().getOnce() ?: AppSettings()
                ShiftJsonCodec.decodeSyncPayload(rawJson, currentSettings)
            }
        }

    @SuppressLint("PackageManagerGetSignatures")
    fun getAppSigningSha1(context: Context): String = runCatching {
        val pm = context.packageManager
        val certBytes: ByteArray? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signingInfo = info.signingInfo
            val signatures = if (signingInfo?.hasMultipleSigners() == true) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo?.signingCertificateHistory
            }
            signatures?.firstOrNull()?.toByteArray()
        } else {
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            info.signatures?.firstOrNull()?.toByteArray()
        }
        if (certBytes == null) return@runCatching "недоступен"
        val digest = MessageDigest.getInstance("SHA-1").digest(certBytes)
        digest.joinToString(":") { "%02X".format(it) }
    }.getOrDefault("недоступен")

    private fun acquireAccessToken(context: Context, account: Account): String {
        try {
            return GoogleAuthUtil.getToken(context, account, OAUTH_SCOPE_TOKEN)
        } catch (recoverable: UserRecoverableAuthException) {
            val intent = recoverable.intent
            if (intent != null) throw ConsentRequiredException(intent)
            throw recoverable
        }
    }

    private fun findBackupFileId(token: String): String? {
        val query = URLEncoder.encode("name='$BACKUP_FILE_NAME' and trashed=false", "UTF-8")
        val endpoint = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=$query&fields=files(id,name,modifiedTime)&pageSize=1"
        val response = executeHttp(endpoint, method = "GET", token = token)
        return ShiftJsonCodec.extractFirstDriveFileId(response)
    }

    private fun createEmptyAppDataFile(token: String): String {
        val metadata = """{"name":"$BACKUP_FILE_NAME","parents":["appDataFolder"],"mimeType":"application/json"}"""
        val response = executeHttp(
            url = "https://www.googleapis.com/drive/v3/files?fields=id",
            method = "POST",
            token = token,
            body = metadata
        )
        return ShiftJsonCodec.extractDriveFileId(response)
            ?: throw IllegalStateException("Google Drive не вернул идентификатор файла синхронизации")
    }

    private fun uploadMediaContent(token: String, fileId: String, jsonBody: String) {
        executeHttp(
            url = "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media",
            method = "PATCH",
            token = token,
            body = jsonBody
        )
    }

    private fun downloadMediaContent(token: String, fileId: String): String =
        executeHttp(
            url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media",
            method = "GET",
            token = token
        )

    private fun executeHttp(
        url: String,
        method: String,
        token: String,
        body: String? = null
    ): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            requestMethod = if (method == "PATCH") "POST" else method
            if (method == "PATCH") {
                setRequestProperty("X-HTTP-Method-Override", "PATCH")
            }
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
        }
        try {
            if (body != null) {
                conn.outputStream.use { out ->
                    out.write(body.toByteArray(Charsets.UTF_8))
                }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("Ошибка Google Drive API ($code): ${responseText.take(180)}")
            }
            return responseText
        } finally {
            conn.disconnect()
        }
    }
}
