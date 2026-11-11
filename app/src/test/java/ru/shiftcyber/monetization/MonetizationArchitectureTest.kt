package ru.shiftcyber.monetization

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MonetizationArchitectureTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `DataManager saves updates and loads user_data consent and payment_state atomically in JSON`() = runBlocking {
        val baseDir = tempFolder.newFolder("json_store")
        val dm = DataManager(baseDir)

        val initial = UserData(
            userId = "u-101",
            age = 27,
            gender = "male",
            screenType = "phone",
            sessionDuration = 45L,
            appVersion = "1.4.0",
            currentPage = "support",
            userCategory = "free",
            interests = listOf("shift_work", "finance"),
            adsDisabled = false
        )
        dm.saveUserData(initial)

        val userFile = File(baseDir, DataManager.USER_DATA_FILE)
        val tempFile = File(baseDir, "${DataManager.USER_DATA_FILE}.tmp")
        assertTrue(userFile.exists())
        assertFalse(tempFile.exists())

        val loaded = dm.loadUserData()
        assertNotNull(loaded)
        assertEquals("u-101", loaded!!.userId)
        assertEquals("free", loaded.userCategory)
        assertFalse(loaded.adsDisabled)

        val updated = dm.updateUserData { current ->
            current!!.copy(userCategory = "supporter", adsDisabled = true, sessionDuration = 120L)
        }
        assertEquals("supporter", updated.userCategory)
        assertTrue(updated.adsDisabled)
        assertEquals(120L, dm.loadUserData()!!.sessionDuration)
    }

    @Test
    fun `ConsentManager persists 152-FZ choice in consent_json and notifies tracking callback`() = runBlocking {
        val baseDir = tempFolder.newFolder("consent_store")
        val dm = DataManager(baseDir)
        var trackedFlag: Boolean? = null
        val cm = ConsentManager(dm) { granted -> trackedFlag = granted }

        cm.initialize()
        assertTrue(cm.shouldShowConsentDialog())
        assertFalse(cm.hasConsent())

        cm.saveConsentDecision(true)
        assertFalse(cm.shouldShowConsentDialog())
        assertTrue(cm.hasConsent())
        assertEquals(true, trackedFlag)
        assertTrue(File(baseDir, DataManager.CONSENT_FILE).exists())

        // Reload in a fresh instance to verify pure JSON persistence
        val dm2 = DataManager(baseDir)
        val cm2 = ConsentManager(dm2)
        val restored = cm2.initialize()
        assertTrue(restored.decided)
        assertTrue(restored.hasConsent)
    }

    @Test
    fun `YooKassaManager disables ads forever in payment_state_json after successful checkPaymentStatus`() = runBlocking {
        val baseDir = tempFolder.newFolder("yookassa_store")
        val dm = DataManager(baseDir)
        val yk = YooKassaManager(dm)

        yk.initialize()
        assertFalse(yk.isAdsDisabled())

        val success = yk.checkPaymentStatus(paymentId = "yk_1762860000_ab12cd34", amountRub = 100)
        assertTrue(success)
        assertTrue(yk.isAdsDisabled())

        val paymentJsonFile = File(baseDir, DataManager.PAYMENT_STATE_FILE)
        assertTrue(paymentJsonFile.exists())
        val rawJson = paymentJsonFile.readText(Charsets.UTF_8)
        assertTrue(rawJson.contains("\"adsDisabled\": true"))
        assertTrue(rawJson.contains("\"paymentId\": \"yk_1762860000_ab12cd34\""))

        val updatedUser = dm.loadUserData()
        assertNotNull(updatedUser)
        assertEquals("supporter", updatedUser!!.userCategory)
        assertTrue(updatedUser.adsDisabled)
    }

    @Test
    fun `AdController blocks all banner shows when YooKassa support is paid or 152-FZ consent declined`() = runBlocking {
        val baseDir = tempFolder.newFolder("controller_store")
        val dm = DataManager(baseDir)
        val yk = YooKassaManager(dm)

        var initCount = 0
        var customDataCount = 0
        var showBannerCount = 0
        var hideBannerCount = 0

        lateinit var fakeAppodeal: AppodealIntegration
        val cm = ConsentManager(dm) { granted -> fakeAppodeal.setTrackingConsent(granted) }

        fakeAppodeal = object : AppodealIntegration(cm, yk, "test_key") {
            override fun setTrackingConsent(hasConsent: Boolean) {}
            override fun initializeIfNeeded(
                context: Context?,
                isTesting: Boolean,
                onInitialized: (() -> Unit)?
            ): Boolean {
                if (yk.isAdsDisabled() || !cm.hasConsent()) return false
                initCount++
                onInitialized?.invoke()
                return true
            }

            override fun sendCustomData(userData: UserData) {
                customDataCount++
            }
        }

        val fakeDisplay = object : AdDisplayManager(yk) {
            override fun showBanner(containerId: Int, format: AdBannerFormat) {
                if (!yk.isAdsDisabled()) {
                    showBannerCount++
                }
            }

            override fun hideBanner(containerId: Int) {
                hideBannerCount++
            }

            override fun destroyAllBanners() {
                hideBannerCount++
            }
        }

        val collector = DataCollector(context = null, dataManager = dm)

        val controller = AdController(
            appContext = null,
            dataManager = dm,
            dataCollector = collector,
            consentManager = cm,
            yooKassaManager = yk,
            appodealIntegration = fakeAppodeal,
            adDisplayManager = fakeDisplay
        )

        controller.bootstrap()

        // 1. Без согласия 152-ФЗ реклама НЕ показывается
        controller.showSupportPageBanner(containerId = 1001)
        assertEquals(0, initCount)
        assertEquals(0, showBannerCount)
        assertTrue(hideBannerCount >= 1)

        // 2. Пользователь даёт согласие по 152-ФЗ -> баннер инициализируется и показывается
        cm.saveConsentDecision(true)
        controller.showSupportPageBanner(containerId = 1001)
        assertEquals(1, initCount)
        assertEquals(1, customDataCount)
        assertEquals(1, showBannerCount)

        // 3. Пользователь поддерживает разработчика через ЮKassa -> реклама отключается навсегда
        val paid = controller.handlePaymentCallback("yk_supporter_500", 500, containerIdToHide = 1001)
        assertTrue(paid)
        assertTrue(yk.isAdsDisabled())

        // 4. Повторный заход на страницу поддержки НИКОГДА не вызывает showBanner
        controller.showSupportPageBanner(containerId = 1001)
        assertEquals(1, showBannerCount)
    }
}
