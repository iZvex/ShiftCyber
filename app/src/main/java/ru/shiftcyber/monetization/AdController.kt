package ru.shiftcyber.monetization

import android.app.Activity
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Главный координатор монетизации и рекламы.
 * Проверяет статус оплаты (YooKassaManager.isAdsDisabled()) ПЕРЕД любым показом или инициализацией рекламы,
 * управляет согласием по 152-ФЗ, сбором и сохранением UserData в JSON и отображением баннера на экране поддержки.
 */
class AdController(
    private val appContext: Context?,
    val dataManager: DataManager,
    val dataCollector: DataCollector,
    val consentManager: ConsentManager,
    val yooKassaManager: YooKassaManager,
    val appodealIntegration: AppodealIntegration,
    val adDisplayManager: AdDisplayManager,
    private val ioScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    companion object {
        const val TAG = "AdController"
    }

    /**
     * Загружает все JSON-состояния (payment_state.json, consent.json, user_data.json) при запуске приложения.
     */
    suspend fun bootstrap() {
        Log.d(TAG, "AdController.bootstrap: loading JSON states on Dispatchers.IO")
        dataManager.preloadAll()
        val payment = yooKassaManager.initialize()
        val consent = consentManager.initialize()
        dataCollector.collectAndPersistUserData(adsDisabledOverride = payment.adsDisabled)
        Log.d(
            TAG,
            "AdController.bootstrap ready: adsDisabled=${payment.adsDisabled}, consentDecided=${consent.decided}, hasConsent=${consent.hasConsent}"
        )
    }

    fun attachActivity(activity: Activity) {
        adDisplayManager.attachActivity(activity)
    }

    fun onPageVisited(pageName: String) {
        dataCollector.onPageChanged(pageName)
        ioScope.launch {
            val data = dataCollector.collectUserData()
            dataManager.saveUserData(data)
        }
    }

    /**
     * Главный метод показа рекламного баннера на странице поддержки.
     * Если пользователь поддержал разработчика через ЮKassa (isAdsDisabled() == true),
     * контейнер немедленно скрывается (View.GONE) и никакие рекламные вызовы не выполняются.
     */
    fun showSupportPageBanner(
        containerId: Int,
        format: AdBannerFormat = AdBannerFormat.SMART_BANNER
    ) {
        Log.d(TAG, "showSupportPageBanner(containerId=$containerId, format=$format)")
        if (yooKassaManager.isAdsDisabled()) {
            Log.d(TAG, "Ads disabled permanently (supporter) -> hiding container $containerId")
            adDisplayManager.hideBanner(containerId)
            return // Реклама не показывается, если была оплата
        }

        if (consentManager.hasConsent()) {
            val ready = appodealIntegration.initializeIfNeeded(appContext)
            val data = dataCollector.collectUserData(adsDisabledOverride = false)
            ioScope.launch {
                dataManager.saveUserData(data)
            }
            if (ready && !yooKassaManager.isAdsDisabled()) {
                appodealIntegration.sendCustomData(data)
                adDisplayManager.showBanner(containerId, format)
            }
        } else {
            Log.d(TAG, "No 152-FZ consent -> hiding banner container $containerId")
            adDisplayManager.hideBanner(containerId)
        }
    }

    fun hideSupportPageBanner(containerId: Int) {
        adDisplayManager.hideBanner(containerId)
    }

    /**
     * Обрабатывает подтверждение оплаты из WebView / deep-link ЮKassa:
     * проверяет статус, записывает payment_state.json и навсегда уничтожает все рекламные баннеры.
     */
    suspend fun handlePaymentCallback(
        paymentId: String,
        amountRub: Int,
        containerIdToHide: Int? = null
    ): Boolean {
        Log.d(TAG, "handlePaymentCallback(paymentId=$paymentId, amount=$amountRub ₽)")
        val ok = yooKassaManager.checkPaymentStatus(paymentId, amountRub)
        if (ok) {
            containerIdToHide?.let { adDisplayManager.hideBanner(it) }
            adDisplayManager.destroyAllBanners()
        }
        return ok
    }
}
