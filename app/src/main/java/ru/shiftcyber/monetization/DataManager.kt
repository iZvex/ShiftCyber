package ru.shiftcyber.monetization

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Менеджер атомарного чтения и записи JSON-файлов (user_data.json, consent.json, payment_state.json).
 * Не использует Room/SQLite или SharedPreferences — все состояния хранятся строго в JSON-файлах в context.filesDir.
 * Все файловые операции выполняются в Dispatchers.IO с атомарной заменой (временный файл -> переименование).
 */
class DataManager(private val baseDir: File) {

    constructor(context: Context) : this(context.applicationContext.filesDir)

    companion object {
        private const val TAG = "AdController"
        const val USER_DATA_FILE = "user_data.json"
        const val CONSENT_FILE = "consent.json"
        const val PAYMENT_STATE_FILE = "payment_state.json"
    }

    private val gson: Gson = GsonBuilder()
        .serializeNulls()
        .setPrettyPrinting()
        .create()

    private val fileMutex = Mutex()

    @Volatile
    private var cachedUserData: UserData? = null

    @Volatile
    private var cachedConsentState: ConsentState? = null

    @Volatile
    private var cachedPaymentState: PaymentState? = null

    /**
     * Предварительно прогревает кэш из JSON-файлов в Dispatchers.IO при старте приложения.
     */
    suspend fun preloadAll() = withContext(Dispatchers.IO) {
        loadPaymentState()
        loadConsentState()
        loadUserData()
    }

    suspend fun saveUserData(data: UserData) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            writeJsonAtomically(USER_DATA_FILE, gson.toJson(data))
            cachedUserData = data
            Log.d(TAG, "DataManager: saved $USER_DATA_FILE (category=${data.userCategory}, adsDisabled=${data.adsDisabled})")
        }
    }

    suspend fun loadUserData(): UserData? = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            val loaded = readJsonOrNull(USER_DATA_FILE, UserData::class.java)
            cachedUserData = loaded
            loaded
        }
    }

    suspend fun updateUserData(transform: (UserData?) -> UserData): UserData = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            val current = cachedUserData ?: readJsonOrNull(USER_DATA_FILE, UserData::class.java)
            val updated = transform(current)
            writeJsonAtomically(USER_DATA_FILE, gson.toJson(updated))
            cachedUserData = updated
            Log.d(TAG, "DataManager: updated $USER_DATA_FILE (page=${updated.currentPage}, adsDisabled=${updated.adsDisabled})")
            updated
        }
    }

    suspend fun saveConsentState(state: ConsentState) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            writeJsonAtomically(CONSENT_FILE, gson.toJson(state))
            cachedConsentState = state
            Log.d(TAG, "DataManager: saved $CONSENT_FILE (decided=${state.decided}, hasConsent=${state.hasConsent})")
        }
    }

    suspend fun loadConsentState(): ConsentState = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            val loaded = readJsonOrNull(CONSENT_FILE, ConsentState::class.java) ?: ConsentState()
            cachedConsentState = loaded
            loaded
        }
    }

    suspend fun savePaymentState(state: PaymentState) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            writeJsonAtomically(PAYMENT_STATE_FILE, gson.toJson(state))
            cachedPaymentState = state
            Log.d(TAG, "DataManager: saved $PAYMENT_STATE_FILE (adsDisabled=${state.adsDisabled}, paymentId=${state.paymentId})")
        }
    }

    suspend fun loadPaymentState(): PaymentState = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            val loaded = readJsonOrNull(PAYMENT_STATE_FILE, PaymentState::class.java) ?: PaymentState()
            cachedPaymentState = loaded
            loaded
        }
    }

    fun getCachedUserData(): UserData? = cachedUserData

    fun getConsentSnapshot(): ConsentState = cachedConsentState ?: ConsentState()

    fun getPaymentSnapshot(): PaymentState = cachedPaymentState ?: PaymentState()

    private fun <T> readJsonOrNull(fileName: String, clazz: Class<T>): T? {
        val target = File(baseDir, fileName)
        if (!target.exists() || !target.isFile) return null
        return runCatching {
            val raw = target.readText(Charsets.UTF_8)
            if (raw.isBlank()) null else gson.fromJson(raw, clazz)
        }.onFailure { err ->
            Log.d(TAG, "DataManager: failed reading $fileName (${err.message}), using safe fallback")
        }.getOrNull()
    }

    private fun writeJsonAtomically(fileName: String, jsonContent: String) {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        val targetFile = File(baseDir, fileName)
        val tempFile = File(baseDir, "$fileName.tmp")

        FileOutputStream(tempFile).use { fos ->
            OutputStreamWriter(fos, Charsets.UTF_8).use { writer ->
                writer.write(jsonContent)
                writer.flush()
            }
            runCatching { fos.fd.sync() }
        }

        try {
            Files.move(
                tempFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Exception) {
            if (!tempFile.renameTo(targetFile)) {
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        }
    }
}
