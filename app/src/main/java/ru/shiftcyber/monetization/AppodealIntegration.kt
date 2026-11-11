package ru.shiftcyber.monetization

import android.content.Context
import android.util.Log
import com.appodeal.ads.Appodeal
import com.appodeal.ads.utils.Log.LogLevel
import ru.shiftcyber.BuildConfig

/**
 * Интеграция с Appodeal Mediation SDK 3.5.x (Яндекс РСЯ, VK Реклама / myTarget, Start.io).
 * Отвечает за безопасную инициализацию SDK, передачу согласия 152-ФЗ и отправку кастомных параметров (setExtraData).
 */
open class AppodealIntegration(
    private val consentManager: ConsentManager,
    private val yooKassaManager: YooKassaManager,
    private val configuredAppKey: String = BuildConfig.APPODEAL_APP_KEY
) {
    companion object {
        const val TAG = "Appodeal"
        // Тестовый ключ демо-интеграции, используемый только если боевой APPODEAL_APP_KEY ещё не задан
        private const val DEMO_TEST_APP_KEY = "fee50c333ff3825fd6ad6d38cff78154de3025546d47a84f"
    }

    @Volatile
    private var initialized: Boolean = false

    val isUsingTestMode: Boolean
        get() = BuildConfig.DEBUG || configuredAppKey.isBlank()

    fun isInitialized(): Boolean = initialized

    /**
     * Передаёт статус согласия на обработку ПДн (152-ФЗ) в Appodeal SDK.
     */
    open fun setTrackingConsent(hasConsent: Boolean) {
        Log.d(TAG, "setTrackingConsent(hasConsent=$hasConsent) [152-ФЗ]")
        runCatching {
            Appodeal.setChildDirectedTreatment(!hasConsent)
            Appodeal.setExtraData("consent_152_fz", hasConsent)
            Appodeal.setExtraData("personalized_ads", hasConsent)
        }.onFailure { err ->
            Log.d(TAG, "setTrackingConsent SDK hook skipped/failed: ${err.message}")
        }
    }

    /**
     * Инициализирует Appodeal SDK строго при выполнении двух условий:
     * 1) пользователь НЕ отключил рекламу поддержкой через ЮKassa (!yooKassaManager.isAdsDisabled()),
     * 2) получено согласие пользователя на обработку данных по 152-ФЗ (consentManager.hasConsent()).
     */
    open fun initializeIfNeeded(
        context: Context?,
        isTesting: Boolean = isUsingTestMode,
        onInitialized: (() -> Unit)? = null
    ): Boolean {
        if (yooKassaManager.isAdsDisabled()) {
            Log.d(TAG, "initializeIfNeeded aborted: adsDisabled=true (supporter)")
            return false
        }
        if (!consentManager.hasConsent()) {
            Log.d(TAG, "initializeIfNeeded aborted: 152-FZ consent not granted")
            return false
        }
        if (initialized) {
            Log.d(TAG, "Appodeal already initialized")
            onInitialized?.invoke()
            return true
        }
        if (context == null) {
            Log.d(TAG, "initializeIfNeeded skipped: context is null")
            return false
        }

        val effectiveKey = configuredAppKey.ifBlank { DEMO_TEST_APP_KEY }
        Log.d(TAG, "Initializing Appodeal SDK (adTypes=BANNER|MREC, isTesting=$isTesting)")

        return runCatching {
            setTrackingConsent(true)
            Appodeal.setTesting(isTesting)
            Appodeal.setLogLevel(if (isTesting) LogLevel.verbose else LogLevel.none)
            Appodeal.setSmartBanners(true)
            Appodeal.set728x90Banners(false)

            val adTypes = Appodeal.BANNER or Appodeal.MREC
            Appodeal.initialize(context, effectiveKey, adTypes) { errors ->
                initialized = true
                if (errors.isNullOrEmpty()) {
                    Log.d(TAG, "Appodeal initialization finished successfully")
                } else {
                    Log.d(TAG, "Appodeal initialized with non-fatal warnings: ${errors.joinToString { it.message ?: it.toString() }}")
                }
                onInitialized?.invoke()
            }
            initialized = true
            true
        }.onFailure { err ->
            Log.d(TAG, "Appodeal initialization failed safely: ${err.message}")
        }.getOrDefault(false)
    }

    /**
     * Передаёт собранные данные пользователя и контекст экрана в Appodeal через setExtraData / setCustomFilter.
     */
    open fun sendCustomData(userData: UserData) {
        if (yooKassaManager.isAdsDisabled()) {
            Log.d(TAG, "sendCustomData skipped: adsDisabled=true")
            return
        }
        Log.d(
            TAG,
            "Sending custom extraData to Appodeal: screen_type=${userData.screenType}, page=${userData.currentPage}, category=${userData.userCategory}"
        )
        runCatching {
            Appodeal.setUserId(userData.userId)
            Appodeal.setExtraData("screen_type", userData.screenType)
            Appodeal.setExtraData("session_duration", userData.sessionDuration.toInt())
            Appodeal.setExtraData("app_version", userData.appVersion)
            Appodeal.setExtraData("current_page", userData.currentPage)
            Appodeal.setExtraData("user_category", userData.userCategory)
            Appodeal.setExtraData("ads_disabled", userData.adsDisabled)
            if (userData.interests.isNotEmpty()) {
                Appodeal.setExtraData("interests", userData.interests.joinToString(","))
            }
            userData.age?.let { Appodeal.setExtraData("age", it) }
            userData.gender?.let { Appodeal.setExtraData("gender", it) }

            Appodeal.setCustomFilter("user_category", userData.userCategory)
            Appodeal.setCustomFilter("screen_type", userData.screenType)
        }.onFailure { err ->
            Log.d(TAG, "sendCustomData failed safely: ${err.message}")
        }
    }
}
