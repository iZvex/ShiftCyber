package ru.shiftcyber.monetization

import android.app.Activity
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.appodeal.ads.Appodeal
import com.appodeal.ads.BannerCallbacks
import com.appodeal.ads.MrecCallbacks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * Менеджер отображения/скрытия рекламных баннеров Appodeal (Smart Banner и MREC 300x250).
 * Встраивает баннер строго во внутренний контейнер страницы поддержки (не перекрывая системные элементы — требование RuStore)
 * и безопасно скрывает контейнер (View.GONE) при ошибках загрузки или при отключённой рекламе.
 */
open class AdDisplayManager(
    private val yooKassaManager: YooKassaManager
) {
    companion object {
        const val TAG = "Appodeal"
    }

    data class BannerUiState(
        val isLoaded: Boolean = false,
        val isShowing: Boolean = false,
        val failedToLoad: Boolean = false,
        val bannerHeightDp: Int = 0
    )

    private val containers = ConcurrentHashMap<Int, WeakReference<FrameLayout>>()
    private var activityRef: WeakReference<Activity>? = null
    private var callbacksRegistered = false

    @Volatile
    private var activeContainerId: Int? = null

    private val _bannerUiState = MutableStateFlow(BannerUiState())
    val bannerUiState: StateFlow<BannerUiState> = _bannerUiState.asStateFlow()

    fun attachActivity(activity: Activity) {
        activityRef = WeakReference(activity)
        registerCallbacksIfNeeded()
    }

    fun registerContainer(containerId: Int, container: FrameLayout) {
        container.id = containerId
        containers[containerId] = WeakReference(container)
        if (yooKassaManager.isAdsDisabled()) {
            container.removeAllViews()
            container.visibility = View.GONE
        }
    }

    /**
     * Показывает баннер (Smart Banner или MREC 300x250) внутри контейнера containerId.
     * Перед вызовом Appodeal.show() повторно проверяет yooKassaManager.isAdsDisabled().
     */
    open fun showBanner(
        containerId: Int,
        format: AdBannerFormat = AdBannerFormat.SMART_BANNER
    ) {
        if (yooKassaManager.isAdsDisabled()) {
            Log.d(TAG, "AdDisplayManager.showBanner blocked: adsDisabled=true")
            hideBanner(containerId)
            return
        }

        val activity = activityRef?.get()
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            Log.d(TAG, "AdDisplayManager.showBanner skipped: Activity unavailable")
            return
        }

        registerCallbacksIfNeeded()
        activeContainerId = containerId

        val container = containers[containerId]?.get() ?: activity.findViewById(containerId)
        if (container == null) {
            Log.d(TAG, "AdDisplayManager.showBanner: container $containerId not attached yet")
            return
        }

        runCatching {
            val adView: View = when (format) {
                AdBannerFormat.MREC_300X250 -> Appodeal.getMrecView(activity)
                AdBannerFormat.SMART_BANNER -> Appodeal.getBannerView(activity)
            }

            (adView.parent as? ViewGroup)?.removeView(adView)
            container.removeAllViews()
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL
            )
            container.addView(adView, lp)

            // Обязательная финальная проверка статуса оплаты ПЕРЕД Appodeal.show()
            if (yooKassaManager.isAdsDisabled()) {
                container.removeAllViews()
                container.visibility = View.GONE
                return
            }

            val adTypeToShow = when (format) {
                AdBannerFormat.MREC_300X250 -> Appodeal.MREC
                AdBannerFormat.SMART_BANNER -> Appodeal.BANNER_VIEW
            }
            val showStarted = Appodeal.show(activity, adTypeToShow)
            Log.d(TAG, "Appodeal.show(containerId=$containerId, format=$format) -> $showStarted")
            if (showStarted) {
                container.visibility = View.VISIBLE
            }
        }.onFailure { err ->
            Log.d(TAG, "AdDisplayManager.showBanner fallback on error: ${err.message}")
            container.removeAllViews()
            container.visibility = View.GONE
            _bannerUiState.value = BannerUiState(isLoaded = false, isShowing = false, failedToLoad = true)
        }
    }

    /**
     * Скрывает баннер и переводит контейнер в View.GONE, чтобы он не занимал место в интерфейсе.
     */
    open fun hideBanner(containerId: Int) {
        Log.d(TAG, "AdDisplayManager.hideBanner(containerId=$containerId) -> View.GONE")
        val activity = activityRef?.get()
        runCatching {
            if (activity != null) {
                Appodeal.hide(activity, Appodeal.BANNER_VIEW)
                Appodeal.hide(activity, Appodeal.BANNER)
                Appodeal.hide(activity, Appodeal.MREC)
            }
        }
        val container = containers[containerId]?.get()
            ?: activity?.findViewById(containerId)
        container?.let {
            it.removeAllViews()
            it.visibility = View.GONE
        }
        _bannerUiState.value = BannerUiState(isLoaded = false, isShowing = false, failedToLoad = false)
    }

    open fun destroyAllBanners() {
        Log.d(TAG, "AdDisplayManager.destroyAllBanners()")
        containers.keys().toList().forEach { id -> hideBanner(id) }
        runCatching {
            Appodeal.destroy(Appodeal.BANNER)
            Appodeal.destroy(Appodeal.MREC)
        }
    }

    private fun registerCallbacksIfNeeded() {
        if (callbacksRegistered) return
        callbacksRegistered = true

        runCatching {
            Appodeal.setBannerCallbacks(object : BannerCallbacks {
                override fun onBannerLoaded(height: Int, isPrecache: Boolean) {
                    Log.d(TAG, "onBannerLoaded(height=$height, isPrecache=$isPrecache)")
                    if (yooKassaManager.isAdsDisabled()) {
                        activeContainerId?.let { hideBanner(it) }
                        return
                    }
                    activeContainerId?.let { id ->
                        containers[id]?.get()?.visibility = View.VISIBLE
                    }
                    _bannerUiState.value = BannerUiState(
                        isLoaded = true,
                        isShowing = true,
                        failedToLoad = false,
                        bannerHeightDp = height.coerceAtLeast(50)
                    )
                }

                override fun onBannerFailedToLoad() {
                    Log.d(TAG, "onBannerFailedToLoad -> hiding container (View.GONE)")
                    activeContainerId?.let { id ->
                        containers[id]?.get()?.visibility = View.GONE
                    }
                    _bannerUiState.value = BannerUiState(
                        isLoaded = false,
                        isShowing = false,
                        failedToLoad = true
                    )
                }

                override fun onBannerShown() {
                    Log.d(TAG, "onBannerShown")
                    _bannerUiState.value = _bannerUiState.value.copy(isShowing = true, failedToLoad = false)
                }

                override fun onBannerShowFailed() {
                    Log.d(TAG, "onBannerShowFailed -> hiding container (View.GONE)")
                    activeContainerId?.let { id ->
                        containers[id]?.get()?.visibility = View.GONE
                    }
                    _bannerUiState.value = BannerUiState(
                        isLoaded = false,
                        isShowing = false,
                        failedToLoad = true
                    )
                }

                override fun onBannerClicked() {
                    Log.d(TAG, "onBannerClicked")
                }

                override fun onBannerExpired() {
                    Log.d(TAG, "onBannerExpired")
                }
            })

            Appodeal.setMrecCallbacks(object : MrecCallbacks {
                override fun onMrecLoaded(isPrecache: Boolean) {
                    Log.d(TAG, "onMrecLoaded(isPrecache=$isPrecache)")
                    if (yooKassaManager.isAdsDisabled()) {
                        activeContainerId?.let { hideBanner(it) }
                        return
                    }
                    activeContainerId?.let { id ->
                        containers[id]?.get()?.visibility = View.VISIBLE
                    }
                    _bannerUiState.value = BannerUiState(
                        isLoaded = true,
                        isShowing = true,
                        failedToLoad = false,
                        bannerHeightDp = 250
                    )
                }

                override fun onMrecFailedToLoad() {
                    Log.d(TAG, "onMrecFailedToLoad -> hiding container (View.GONE)")
                    activeContainerId?.let { id ->
                        containers[id]?.get()?.visibility = View.GONE
                    }
                    _bannerUiState.value = BannerUiState(
                        isLoaded = false,
                        isShowing = false,
                        failedToLoad = true
                    )
                }

                override fun onMrecShown() {
                    Log.d(TAG, "onMrecShown")
                }

                override fun onMrecShowFailed() {
                    Log.d(TAG, "onMrecShowFailed -> hiding container (View.GONE)")
                    activeContainerId?.let { id ->
                        containers[id]?.get()?.visibility = View.GONE
                    }
                    _bannerUiState.value = BannerUiState(
                        isLoaded = false,
                        isShowing = false,
                        failedToLoad = true
                    )
                }

                override fun onMrecClicked() {
                    Log.d(TAG, "onMrecClicked")
                }

                override fun onMrecExpired() {
                    Log.d(TAG, "onMrecExpired")
                }
            })
        }.onFailure { err ->
            Log.d(TAG, "Callback registration skipped: ${err.message}")
        }
    }
}
