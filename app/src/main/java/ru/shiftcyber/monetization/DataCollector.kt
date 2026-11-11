package ru.shiftcyber.monetization

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import ru.shiftcyber.BuildConfig
import java.util.UUID

/**
 * Сборщик неперсонифицированных поведенческих и технических параметров сессии
 * для формирования UserData и таргетинга рекламного баннера.
 */
class DataCollector(
    private val context: Context?,
    private val dataManager: DataManager,
    private val sessionStartProvider: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        private const val TAG = "AdController"
        val DEFAULT_INTERESTS = listOf(
            "shift_work",
            "productivity",
            "personal_finance",
            "calendar_planning"
        )
    }

    private val sessionStartMillis: Long = sessionStartProvider()

    @Volatile
    private var currentPage: String = "home"

    @Volatile
    private var optionalAge: Int? = null

    @Volatile
    private var optionalGender: String? = null

    private val customInterests = LinkedHashSet<String>().apply {
        addAll(DEFAULT_INTERESTS)
    }

    fun onPageChanged(pageName: String) {
        currentPage = pageName
        Log.d(TAG, "DataCollector: currentPage=$pageName")
    }

    fun setOptionalDemographics(age: Int?, gender: String?) {
        optionalAge = age?.takeIf { it in 14..100 }
        optionalGender = gender?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun registerInterest(tag: String) {
        val normalized = tag.trim().lowercase()
        if (normalized.isNotEmpty()) {
            synchronized(customInterests) {
                customInterests.add(normalized)
            }
        }
    }

    /**
     * Синхронно собирает актуальный снимок UserData, используя кэш DataManager.
     */
    fun collectUserData(adsDisabledOverride: Boolean? = null): UserData {
        val previous = dataManager.getCachedUserData()
        val adsDisabled = adsDisabledOverride ?: dataManager.getPaymentSnapshot().adsDisabled
        val userId = previous?.userId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val durationSeconds = ((now - sessionStartMillis).coerceAtLeast(0L)) / 1000L
        val screenType = detectScreenType()
        val appVersion = detectAppVersion()
        val interestsSnapshot = synchronized(customInterests) {
            if (previous != null && previous.interests.isNotEmpty()) {
                (previous.interests + customInterests).distinct()
            } else {
                customInterests.toList()
            }
        }

        return UserData(
            userId = userId,
            age = optionalAge ?: previous?.age,
            gender = optionalGender ?: previous?.gender,
            screenType = screenType,
            sessionDuration = durationSeconds,
            appVersion = appVersion,
            currentPage = currentPage,
            userCategory = if (adsDisabled) "supporter" else "free",
            interests = interestsSnapshot,
            adsDisabled = adsDisabled
        )
    }

    /**
     * Асинхронный сбор данных с предварительным чтением user_data.json в Dispatchers.IO.
     */
    suspend fun collectAndPersistUserData(adsDisabledOverride: Boolean? = null): UserData {
        dataManager.loadUserData()
        val collected = collectUserData(adsDisabledOverride)
        dataManager.saveUserData(collected)
        return collected
    }

    private fun detectScreenType(): String {
        val cfg = context?.resources?.configuration ?: return "phone"
        val sw = cfg.smallestScreenWidthDp
        val mask = cfg.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
        return when {
            sw >= 600 || mask >= Configuration.SCREENLAYOUT_SIZE_LARGE -> "tablet"
            sw in 1..359 -> "phone_compact"
            else -> "phone"
        }
    }

    private fun detectAppVersion(): String {
        val buildConfigVer = BuildConfig.VERSION_NAME
        if (buildConfigVer.isNotBlank()) return buildConfigVer
        val ctx = context ?: return "1.4.0"
        return runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.4.0"
        }.getOrDefault("1.4.0")
    }
}
