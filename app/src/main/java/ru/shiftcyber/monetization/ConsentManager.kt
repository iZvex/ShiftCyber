package ru.shiftcyber.monetization

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Менеджер получения и хранения согласия пользователя на обработку персональных данных по 152-ФЗ.
 * Хранит выбор пользователя строго в consent.json через DataManager (Dispatchers.IO)
 * и синхронизирует флаг отслеживания с Appodeal SDK.
 */
class ConsentManager(
    private val dataManager: DataManager,
    private val onTrackingConsentChanged: (Boolean) -> Unit = {}
) {
    companion object {
        private const val TAG = "AdController"
    }

    private val _consentFlow = MutableStateFlow(ConsentState())
    val consentFlow: StateFlow<ConsentState> = _consentFlow.asStateFlow()

    suspend fun initialize(): ConsentState {
        val loaded = dataManager.loadConsentState()
        _consentFlow.value = loaded
        if (loaded.decided) {
            onTrackingConsentChanged(loaded.hasConsent)
        }
        Log.d(TAG, "ConsentManager: initialized from consent.json (decided=${loaded.decided}, hasConsent=${loaded.hasConsent})")
        return loaded
    }

    fun hasConsent(): Boolean = dataManager.getConsentSnapshot().hasConsent

    fun shouldShowConsentDialog(): Boolean = !dataManager.getConsentSnapshot().decided

    suspend fun saveConsentDecision(granted: Boolean): ConsentState {
        val updated = ConsentState(
            decided = true,
            hasConsent = granted,
            policyVersion = "152-FZ-v1.0",
            timestamp = System.currentTimeMillis()
        )
        dataManager.saveConsentState(updated)
        _consentFlow.value = updated
        onTrackingConsentChanged(granted)
        Log.d(TAG, "ConsentManager: 152-FZ decision saved -> hasConsent=$granted")
        return updated
    }
}
