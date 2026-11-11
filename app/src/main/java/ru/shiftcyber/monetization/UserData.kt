package ru.shiftcyber.monetization

/**
 * Основная модель данных пользователя и контекста сессии для персонализации рекламы (user_data.json).
 */
data class UserData(
    val userId: String,
    val age: Int?,
    val gender: String?,
    val screenType: String,
    val sessionDuration: Long,
    val appVersion: String,
    val currentPage: String,
    val userCategory: String, // "free" или "supporter"
    val interests: List<String>,
    val adsDisabled: Boolean = false
)

/**
 * Состояние согласия пользователя на обработку персональных данных согласно 152-ФЗ (consent.json).
 */
data class ConsentState(
    val decided: Boolean = false,
    val hasConsent: Boolean = false,
    val policyVersion: String = "152-FZ-v1.0",
    val timestamp: Long = 0L
)

/**
 * Состояние разовой поддержки разработчика через ЮKassa (payment_state.json).
 * После успешной оплаты adsDisabled = true навсегда отключает любую рекламу в приложении.
 */
data class PaymentState(
    val adsDisabled: Boolean = false,
    val paymentId: String = "",
    val amountRub: Int = 0,
    val timestamp: Long = 0L
)

enum class AdBannerFormat {
    SMART_BANNER,
    MREC_300X250
}
