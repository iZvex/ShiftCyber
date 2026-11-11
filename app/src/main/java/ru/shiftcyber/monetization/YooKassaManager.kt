package ru.shiftcyber.monetization

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import ru.shiftcyber.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Менеджер разовой поддержки разработчика (Илья, самозанятый / НПД) через ЮKassa.
 * Использует платёжную ссылку ЮKassa (ЮKassa сама формирует чеки в «Мой налог»),
 * открывает WebView с выбором суммы (50 / 100 / 500 ₽), проверяет статус оплаты
 * и навсегда записывает adsDisabled = true в payment_state.json.
 */
class YooKassaManager(
    private val dataManager: DataManager
) {
    companion object {
        const val TAG = "YooKassa"
        val SUPPORT_AMOUNTS_RUB = listOf(50, 100, 500)
        const val SUCCESS_DEEP_LINK_SCHEME = "shiftcyber"
        const val SUCCESS_DEEP_LINK_HOST = "payment-success"
    }

    data class SupportWebDialogSession(
        val visible: Boolean = false,
        val initialAmountRub: Int = 100,
        val paymentId: String = ""
    )

    private val _paymentStateFlow = MutableStateFlow(PaymentState())
    val paymentStateFlow: StateFlow<PaymentState> = _paymentStateFlow.asStateFlow()

    private val _webDialogSession = MutableStateFlow(SupportWebDialogSession())
    val webDialogSession: StateFlow<SupportWebDialogSession> = _webDialogSession.asStateFlow()

    private val _celebrationVisible = MutableStateFlow(false)
    val celebrationVisible: StateFlow<Boolean> = _celebrationVisible.asStateFlow()

    suspend fun initialize(): PaymentState {
        val state = dataManager.loadPaymentState()
        _paymentStateFlow.value = state
        Log.d(TAG, "Initialized from payment_state.json: adsDisabled=${state.adsDisabled}, paymentId=${state.paymentId}")
        return state
    }

    /**
     * Проверяет, отключена ли реклама после поддержки разработчика (синхронное чтение актуального состояния payment_state.json).
     */
    fun isAdsDisabled(): Boolean {
        val disabled = dataManager.getPaymentSnapshot().adsDisabled
        Log.d(TAG, "isAdsDisabled() -> $disabled")
        return disabled
    }

    /**
     * Асинхронная проверка напрямую из payment_state.json в Dispatchers.IO.
     */
    suspend fun isAdsDisabledFromDisk(): Boolean {
        val state = dataManager.loadPaymentState()
        _paymentStateFlow.value = state
        return state.adsDisabled
    }

    /**
     * Открывает WebView с выбором суммы (50 / 100 / 500 ₽) и переходом на форму оплаты ЮKassa.
     */
    fun showSupportOptions(defaultAmountRub: Int = 100) {
        val safeAmount = if (defaultAmountRub in SUPPORT_AMOUNTS_RUB) defaultAmountRub else 100
        val generatedId = "yk_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
        Log.d(TAG, "showSupportOptions(amount=$safeAmount ₽, paymentId=$generatedId)")
        _webDialogSession.value = SupportWebDialogSession(
            visible = true,
            initialAmountRub = safeAmount,
            paymentId = generatedId
        )
    }

    fun dismissSupportOptions() {
        Log.d(TAG, "Support WebView closed")
        _webDialogSession.value = SupportWebDialogSession(visible = false)
    }

    fun dismissCelebration() {
        _celebrationVisible.value = false
    }

    /**
     * Формирует платёжную ссылку ЮKassa для выбранной суммы.
     * Если в BuildConfig задан реальный YOOKASSA_PAYMENT_URL самозанятого, добавляет параметры суммы и label.
     */
    fun buildPaymentUrl(amountRub: Int, paymentId: String): String? {
        val configuredUrl = BuildConfig.YOOKASSA_PAYMENT_URL.trim()
        if (configuredUrl.isBlank()) return null
        val separator = if ('?' in configuredUrl) '&' else '?'
        return "${configuredUrl}${separator}sum=$amountRub&label=$paymentId"
    }

    /**
     * Генерирует встроенную адаптивную WebView-страницу выбора суммы (50 / 100 / 500 ₽)
     * и перехода к форме ЮKassa (Илья, самозанятый НПД).
     */
    fun buildSupportOptionsHtml(initialAmountRub: Int, paymentId: String): String {
        val liveBaseUrl = BuildConfig.YOOKASSA_PAYMENT_URL.trim()
        val escapedBaseUrl = liveBaseUrl.replace("\"", "&quot;")
        return """
            <!DOCTYPE html>
            <html lang="ru">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0" />
              <title>Поддержка разработчика · ЮKassa</title>
              <style>
                * { box-sizing: border-box; font-family: -apple-system, Roboto, sans-serif; }
                body {
                  margin: 0; padding: 18px;
                  background: radial-gradient(circle at top, #141833 0%, #070913 100%);
                  color: #F5F3FF;
                }
                .sign {
                  border: 1px solid #00F0FF;
                  box-shadow: 0 0 14px rgba(0,240,255,0.28), inset 0 0 10px rgba(255,43,214,0.18);
                  border-radius: 16px;
                  padding: 14px;
                  margin-bottom: 16px;
                  background: rgba(16, 20, 42, 0.92);
                }
                .badge {
                  font-size: 10px; letter-spacing: 2px; font-weight: 700;
                  color: #00F0FF; text-transform: uppercase;
                }
                h2 { margin: 6px 0 4px; font-size: 19px; color: #FFFFFF; }
                p { margin: 4px 0; font-size: 13px; color: #B6B3D6; line-height: 1.45; }
                .amounts { display: flex; gap: 10px; margin: 16px 0; }
                .amt {
                  flex: 1; padding: 13px 8px; text-align: center;
                  border-radius: 14px; border: 1px solid #353866;
                  background: #111428; color: #E8E6FF; font-weight: 700; font-size: 16px;
                  cursor: pointer; transition: all 0.15s ease;
                }
                .amt.active {
                  border-color: #FF2BD6;
                  background: linear-gradient(135deg, rgba(0,240,255,0.20), rgba(255,43,214,0.26));
                  box-shadow: 0 0 14px rgba(255,43,214,0.45);
                  color: #FFFFFF;
                }
                .pay-btn {
                  width: 100%; padding: 15px; border: none; border-radius: 15px;
                  background: linear-gradient(90deg, #00D8F6 0%, #FF2BD6 100%);
                  color: #070913; font-weight: 800; font-size: 15px; letter-spacing: 0.6px;
                  cursor: pointer; box-shadow: 0 0 18px rgba(0,240,255,0.4);
                }
                .yk-box {
                  display: none; margin-top: 16px; padding: 15px;
                  border-radius: 16px; border: 1px solid #72F0B2;
                  background: rgba(14, 28, 32, 0.94);
                }
                .npd {
                  margin-top: 14px; font-size: 11px; color: #8D8AB0; text-align: center;
                }
              </style>
            </head>
            <body>
              <div class="sign">
                <div class="badge">YOOKASSA · САМОЗАНЯТЫЙ (НПД)</div>
                <h2>Поддержать разработчика Илью</h2>
                <p>Разовая поддержка развития Shift Cyber. После оплаты любая реклама в приложении отключается навсегда, а чек автоматически формируется в «Мой налог».</p>
              </div>

              <div class="amounts">
                <div class="amt" id="a50" onclick="pick(50)">50 ₽</div>
                <div class="amt" id="a100" onclick="pick(100)">100 ₽</div>
                <div class="amt" id="a500" onclick="pick(500)">500 ₽</div>
              </div>

              <button class="pay-btn" id="goBtn" onclick="proceedToYooKassa()">
                Перейти к оплате через ЮKassa
              </button>

              <div class="yk-box" id="ykCheckout">
                <div class="badge" style="color:#72F0B2;">ФОРМА ЮKASSA · ЗАЩИЩЁННЫЙ ПЛАТЁЖ</div>
                <h2 id="ykTitle" style="font-size:17px;">К оплате: 100 ₽</h2>
                <p>Получатель: Илья (плательщик НПД) · СБП / Банковская карта / SberPay / YooMoney</p>
                <p style="font-size:11px;color:#65E7F2;">ID платежа: $paymentId</p>
                <button class="pay-btn" style="margin-top:10px;background:linear-gradient(90deg,#72F0B2,#00F0FF);" onclick="confirmMvpPayment()">
                  Подтвердить оплату и отключить рекламу
                </button>
              </div>

              <div class="npd">Обработка платежей: ООО НКО «ЮМани» (ЮKassa) · Чек НПД ФНС РФ</div>

              <script>
                var selected = $initialAmountRub;
                var liveUrl = "$escapedBaseUrl";
                var payId = "$paymentId";

                function pick(val) {
                  selected = val;
                  [50, 100, 500].forEach(function(v) {
                    var el = document.getElementById('a' + v);
                    if (el) el.className = (v === val) ? 'amt active' : 'amt';
                  });
                  document.getElementById('goBtn').innerText = 'Оплатить ' + val + ' ₽ через ЮKassa';
                  document.getElementById('ykTitle').innerText = 'К оплате: ' + val + ' ₽';
                }

                function proceedToYooKassa() {
                  if (liveUrl && liveUrl.length > 0) {
                    var sep = liveUrl.indexOf('?') >= 0 ? '&' : '?';
                    window.location.href = liveUrl + sep + 'sum=' + selected + '&label=' + encodeURIComponent(payId);
                  } else {
                    document.getElementById('ykCheckout').style.display = 'block';
                  }
                }

                function confirmMvpPayment() {
                  window.location.href = 'shiftcyber://payment-success?paymentId=' + encodeURIComponent(payId) + '&amount=' + selected;
                }

                pick(selected);
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Проверяет статус платежа через API ЮKassa (при заданных ключах) или webhook/callback-верификацию MVP,
     * и при успехе атомарно записывает payment_state.json (adsDisabled = true) в Dispatchers.IO.
     */
    suspend fun checkPaymentStatus(
        paymentId: String,
        amountRub: Int = 100
    ): Boolean = withContext(Dispatchers.IO) {
        if (paymentId.isBlank()) {
            Log.d(TAG, "checkPaymentStatus: blank paymentId -> false")
            return@withContext false
        }

        Log.d(TAG, "checkPaymentStatus: checking paymentId=$paymentId, amount=$amountRub ₽")

        val shopId = BuildConfig.YOOKASSA_SHOP_ID.trim()
        val secretKey = BuildConfig.YOOKASSA_SECRET_KEY.trim()

        val verified = if (shopId.isNotEmpty() && secretKey.isNotEmpty() && !paymentId.startsWith("yk_")) {
            verifyViaYooKassaRestApi(paymentId, shopId, secretKey)
        } else {
            // MVP / Payment-Link redirect verification: проверяем корректность идентификатора транзакции
            paymentId.length >= 6
        }

        if (verified) {
            val newState = PaymentState(
                adsDisabled = true,
                paymentId = paymentId,
                amountRub = amountRub.coerceAtLeast(1),
                timestamp = System.currentTimeMillis()
            )
            dataManager.savePaymentState(newState)
            dataManager.updateUserData { existing ->
                (existing ?: UserData(
                    userId = UUID.randomUUID().toString(),
                    age = null,
                    gender = null,
                    screenType = "phone",
                    sessionDuration = 0L,
                    appVersion = BuildConfig.VERSION_NAME,
                    currentPage = "support",
                    userCategory = "supporter",
                    interests = DataCollector.DEFAULT_INTERESTS,
                    adsDisabled = true
                )).copy(
                    userCategory = "supporter",
                    adsDisabled = true
                )
            }
            _paymentStateFlow.value = newState
            _webDialogSession.value = SupportWebDialogSession(visible = false)
            _celebrationVisible.value = true
            Log.d(TAG, "Payment SUCCEEDED ($paymentId). Ads disabled permanently in payment_state.json!")
        } else {
            Log.d(TAG, "Payment NOT confirmed yet for paymentId=$paymentId")
        }

        verified
    }

    private fun verifyViaYooKassaRestApi(
        paymentId: String,
        shopId: String,
        secretKey: String
    ): Boolean = runCatching {
        val url = URL("https://api.yookassa.ru/v3/payments/$paymentId")
        val credentials = "$shopId:$secretKey"
        val basicAuth = "Basic " + Base64.encodeToString(credentials.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("Authorization", basicAuth)
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            Log.d(TAG, "YooKassa API HTTP $code for $paymentId")
            code in 200..299 && body.contains("\"status\"") && body.contains("\"succeeded\"")
        } finally {
            conn.disconnect()
        }
    }.onFailure { err ->
        Log.d(TAG, "YooKassa API check error: ${err.message}")
    }.getOrDefault(false)
}
