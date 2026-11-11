package ru.shiftcyber.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import ru.shiftcyber.R
import ru.shiftcyber.domain.ShiftMath
import ru.shiftcyber.monetization.AdBannerFormat
import ru.shiftcyber.monetization.AdController
import ru.shiftcyber.monetization.ConsentState
import ru.shiftcyber.monetization.YooKassaManager

private val NeonCyan = Color(0xFF00F0FF)
private val NeonMagenta = Color(0xFFFF2BD6)
private val NeonEmerald = Color(0xFF72F0B2)
private val CyberPanelBg = Color(0xFF0A0D1E)

@Composable
fun SupportScreen(
    adController: AdController
) {
    val scope = rememberCoroutineScope()
    val paymentState by adController.yooKassaManager.paymentStateFlow.collectAsState()
    val consentState by adController.consentManager.consentFlow.collectAsState()
    val bannerUiState by adController.adDisplayManager.bannerUiState.collectAsState()

    var selectedPresetRub by rememberSaveable { mutableIntStateOf(100) }
    var bannerFormat by rememberSaveable { mutableStateOf(AdBannerFormat.SMART_BANNER) }

    LaunchedEffect(paymentState.adsDisabled, consentState.hasConsent, bannerFormat) {
        if (paymentState.adsDisabled) {
            adController.hideSupportPageBanner(R.id.support_banner_container)
        } else if (consentState.hasConsent) {
            adController.showSupportPageBanner(R.id.support_banner_container, bannerFormat)
        } else {
            adController.hideSupportPageBanner(R.id.support_banner_container)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            adController.hideSupportPageBanner(R.id.support_banner_container)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Горизонтальная неоновая вывеска (Cyberpunk Horizontal Neon Signboard)
        item {
            CyberpunkNeonSignboard(
                adsDisabled = paymentState.adsDisabled
            )
        }

        // Если оплата уже выполнена — карточка благодарности без какой-либо рекламы
        if (paymentState.adsDisabled) {
            item {
                SupporterForeverCard(
                    paymentId = paymentState.paymentId,
                    amountRub = paymentState.amountRub,
                    timestamp = paymentState.timestamp
                )
            }
        }

        // Карточка разовой поддержки разработчика через ЮKassa (Илья, самозанятый НПД)
        item {
            NeonCard(
                modifier = Modifier.fillMaxWidth(),
                accent = NeonMagenta
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(NeonMagenta.copy(alpha = 0.16f), CircleShape)
                                .border(1.dp, NeonMagenta.copy(alpha = 0.7f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Favorite,
                                contentDescription = null,
                                tint = NeonMagenta,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Разовая поддержка проекта",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                            Text(
                                text = "Разработчик: Илья (самозанятый, НПД) · ЮKassa",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = if (paymentState.adsDisabled) {
                            "Ты уже поддержал разработку Shift Cyber — реклама отключена навсегда. При желании можно отправить дополнительный донат на развитие новых функций."
                        } else {
                            "Поддержи независимую разработку любой комфортной суммой через защищённую форму ЮKassa (чек «Мой налог» формируется автоматически). Сразу после оплаты вся реклама в приложении отключается навсегда!"
                        },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    // Выбор суммы поддержки 50 / 100 / 500 ₽
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        YooKassaManager.SUPPORT_AMOUNTS_RUB.forEach { amount ->
                            val active = selectedPresetRub == amount
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (active) {
                                            Brush.linearGradient(
                                                listOf(
                                                    NeonCyan.copy(alpha = 0.24f),
                                                    NeonMagenta.copy(alpha = 0.28f)
                                                )
                                            )
                                        } else {
                                            Brush.linearGradient(
                                                listOf(
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                                )
                                            )
                                        }
                                    )
                                    .border(
                                        width = if (active) 1.6.dp else 1.dp,
                                        color = if (active) NeonCyan else MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable { selectedPresetRub = amount }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$amount ₽",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = if (active) NeonCyan else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Неоновая кнопка «Поддержать разработчика» с эффектом свечения при нажатии
                    NeonSupportButton(
                        text = "Поддержать разработчика · $selectedPresetRub ₽",
                        onClick = {
                            adController.yooKassaManager.showSupportOptions(selectedPresetRub)
                        }
                    )
                }
            }
        }

        // Контейнер рекламного баннера Appodeal:
        // При adsDisabled == true полностью скрыт (View.GONE и отсутствует в дереве верстки — 0 px).
        if (!paymentState.adsDisabled) {
            item {
                SupportPageAdBlock(
                    adController = adController,
                    consentState = consentState,
                    bannerLoaded = bannerUiState.isLoaded,
                    bannerFailed = bannerUiState.failedToLoad,
                    selectedFormat = bannerFormat,
                    onSelectFormat = { bannerFormat = it },
                    onGrantConsent = {
                        scope.launch {
                            adController.consentManager.saveConsentDecision(true)
                            adController.showSupportPageBanner(R.id.support_banner_container, bannerFormat)
                        }
                    }
                )
            }
        }

        // Управление согласием на обработку ПДн (152-ФЗ)
        item {
            NeonCard(
                modifier = Modifier.fillMaxWidth(),
                accent = NeonCyan
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.PrivacyTip,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Персонализация рекламы (152-ФЗ)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (consentState.hasConsent) {
                                "Согласие предоставлено · хранится локально в consent.json"
                            } else {
                                "Отключено · рекламные SDK не инициализируются"
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = consentState.hasConsent,
                        onCheckedChange = { granted ->
                            scope.launch {
                                adController.consentManager.saveConsentDecision(granted)
                                if (granted && !paymentState.adsDisabled) {
                                    adController.showSupportPageBanner(R.id.support_banner_container, bannerFormat)
                                } else {
                                    adController.hideSupportPageBanner(R.id.support_banner_container)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CyberpunkNeonSignboard(adsDisabled: Boolean) {
    val infinite = rememberInfiniteTransition(label = "neon-sign")
    val glowAlpha by infinite.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sign-glow"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = NeonCyan.copy(alpha = 0.35f),
                spotColor = NeonMagenta.copy(alpha = 0.4f)
            )
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        CyberPanelBg,
                        Color(0xFF15102E),
                        CyberPanelBg
                    )
                )
            )
            .border(
                width = 1.6.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        NeonCyan.copy(alpha = glowAlpha),
                        NeonMagenta.copy(alpha = glowAlpha),
                        NeonCyan.copy(alpha = glowAlpha)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "NEON SUPPORT // ИЛЬЯ · НПД",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.2.sp,
                    color = NeonCyan
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "ПОДДЕРЖКА SHIFT CYBER",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                    color = Color.White
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (adsDisabled) {
                        "СТАТУС: SUPPORTER · РЕКЛАМА ОТКЛЮЧЕНА НАВСЕГДА"
                    } else {
                        "СТАТУС: FREE · РАЗОВЫЙ ДОНАТ УБИРАЕТ РЕКЛАМУ НАВСЕГДА"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (adsDisabled) NeonEmerald else NeonMagenta
                )
            }
            Icon(
                imageVector = if (adsDisabled) Icons.Rounded.Verified else Icons.Rounded.Bolt,
                contentDescription = null,
                tint = if (adsDisabled) NeonEmerald else NeonCyan,
                modifier = Modifier.size(34.dp)
            )
        }
    }
}

@Composable
fun NeonSupportButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val glowElevation by animateDpAsState(
        targetValue = if (isPressed) 26.dp else 12.dp,
        animationSpec = tween(150),
        label = "btn-glow"
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(120),
        label = "btn-scale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .shadow(
                elevation = glowElevation,
                shape = RoundedCornerShape(16.dp),
                ambientColor = if (isPressed) NeonMagenta else NeonCyan.copy(alpha = 0.65f),
                spotColor = if (isPressed) NeonCyan else NeonMagenta.copy(alpha = 0.75f)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    colors = if (isPressed) {
                        listOf(NeonMagenta, NeonCyan)
                    } else {
                        listOf(NeonCyan, NeonMagenta)
                    }
                )
            )
            .border(
                width = if (isPressed) 2.dp else 1.2.dp,
                color = Color.White.copy(alpha = if (isPressed) 0.9f else 0.45f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 15.dp, horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Bolt,
                contentDescription = null,
                tint = Color(0xFF060814),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                color = Color(0xFF060814),
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun SupporterForeverCard(
    paymentId: String,
    amountRub: Int,
    timestamp: Long
) {
    NeonCard(
        modifier = Modifier.fillMaxWidth(),
        accent = NeonEmerald
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.GppGood,
                contentDescription = null,
                tint = NeonEmerald,
                modifier = Modifier.size(34.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Реклама отключена навсегда. Спасибо за поддержку!",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = NeonEmerald
                )
                if (timestamp > 0L) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = buildString {
                            if (amountRub > 0) append("Поддержка: $amountRub ₽ · ")
                            append(ShiftMath.dateTimeLabel(timestamp))
                            if (paymentId.isNotBlank()) append(" · ID: ${paymentId.take(16)}")
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SupportPageAdBlock(
    adController: AdController,
    consentState: ConsentState,
    bannerLoaded: Boolean,
    bannerFailed: Boolean,
    selectedFormat: AdBannerFormat,
    onSelectFormat: (AdBannerFormat) -> Unit,
    onGrantConsent: () -> Unit
) {
    if (!consentState.hasConsent) {
        NeonCard(
            modifier = Modifier.fillMaxWidth(),
            accent = NeonCyan
        ) {
            Text(
                text = "Рекламный блок скрыт (нет согласия 152-ФЗ)",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Рекламный баннер на этой странице помогает оплачивать развитие бесплатной версии Shift Cyber.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onGrantConsent) {
                Text("Разрешить показ баннера", fontSize = 12.sp)
            }
        }
        return
    }

    // Если реклама не загрузилась (onBannerFailedToLoad / onBannerShowFailed), аккуратно сворачиваем блок без поломки UI
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "РЕКЛАМНЫЙ БЛОК · APPODEAL (РСЯ / VK / START.IO)",
                fontSize = 10.sp,
                letterSpacing = 1.1.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = selectedFormat == AdBannerFormat.SMART_BANNER,
                    onClick = { onSelectFormat(AdBannerFormat.SMART_BANNER) },
                    label = { Text("Smart", fontSize = 10.sp) }
                )
                FilterChip(
                    selected = selectedFormat == AdBannerFormat.MREC_300X250,
                    onClick = { onSelectFormat(AdBannerFormat.MREC_300X250) },
                    label = { Text("300×250", fontSize = 10.sp) }
                )
            }
        }

        // Нативный FrameLayout-контейнер внутри скроллируемой страницы (не перекрывает системные бары и кнопки — стандарт RuStore)
        AndroidView(
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    id = R.id.support_banner_container
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    visibility = View.GONE
                    adController.adDisplayManager.registerContainer(R.id.support_banner_container, this)
                }
            },
            update = { frame ->
                if (adController.yooKassaManager.isAdsDisabled() || bannerFailed) {
                    frame.visibility = View.GONE
                } else if (bannerLoaded) {
                    frame.visibility = View.VISIBLE
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Диалог получения согласия на обработку персональных данных по 152-ФЗ при первом запуске.
 */
@Composable
fun Consent152FzDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Требуется явный выбор по 152-ФЗ */ },
        containerColor = CyberPanelBg,
        titleContentColor = Color.White,
        textContentColor = Color(0xFFD6D3F0),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.PrivacyTip,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Согласие на обработку данных (152-ФЗ)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "В соответствии с Федеральным законом № 152-ФЗ «О персональных данных» запрашиваем твоё согласие на сбор и обработку обезличенных технических и поведенческих данных (тип экрана, версия приложения, длительность сессии, рекламный идентификатор).",
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Text(
                    text = "Оператор: разработчик Илья (самозанятый, плательщик НПД). Цель обработки — показ релевантного рекламного баннера на вкладке поддержки через партнёров Appodeal (Яндекс РСЯ, VK Реклама, Start.io).",
                    fontSize = 12.sp,
                    color = Color(0xFFA9A5CC),
                    lineHeight = 17.sp
                )
                Text(
                    text = "Ты можешь отказаться от персонализации прямо сейчас или изменить решение в любой момент на вкладке «Поддержка».",
                    fontSize = 12.sp,
                    color = NeonCyan
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyan,
                    contentColor = Color(0xFF060814)
                )
            ) {
                Text("Согласен", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text("Отказаться", color = NeonMagenta)
            }
        }
    )
}

/**
 * Модальное окно с WebView выбора суммы (50 / 100 / 500 ₽) и переходом на платёжную форму ЮKassa.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YooKassaWebViewDialog(
    initialAmountRub: Int,
    paymentId: String,
    yooKassaManager: YooKassaManager,
    onPaymentCompleted: (paymentId: String, amountRub: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val htmlContent = remember(initialAmountRub, paymentId) {
        yooKassaManager.buildSupportOptionsHtml(initialAmountRub, paymentId)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(min = 420.dp, max = 640.dp)
                .border(1.5.dp, NeonCyan, RoundedCornerShape(22.dp)),
            shape = RoundedCornerShape(22.dp),
            color = CyberPanelBg
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ЮKASSA · ПОДДЕРЖКА РАЗРАБОТЧИКА",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp,
                        color = NeonCyan,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Закрыть",
                            tint = Color.White
                        )
                    }
                }
                HorizontalDivider(color = NeonMagenta.copy(alpha = 0.35f))

                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val uri = request?.url ?: return false
                                    return interceptPaymentSuccessUri(uri, paymentId, initialAmountRub, onPaymentCompleted)
                                }

                                @Deprecated("Deprecated in Android WebViewClient")
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    url: String?
                                ): Boolean {
                                    val uri = url?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return false
                                    return interceptPaymentSuccessUri(uri, paymentId, initialAmountRub, onPaymentCompleted)
                                }
                            }
                            loadDataWithBaseURL(
                                "https://yookassa.shiftcyber.local/",
                                htmlContent,
                                "text/html",
                                "UTF-8",
                                null
                            )
                        }
                    }
                )
            }
        }
    }
}

private fun interceptPaymentSuccessUri(
    uri: Uri,
    fallbackPaymentId: String,
    fallbackAmount: Int,
    onPaymentCompleted: (String, Int) -> Unit
): Boolean {
    val isDeepLink = uri.scheme == YooKassaManager.SUCCESS_DEEP_LINK_SCHEME &&
        uri.host == YooKassaManager.SUCCESS_DEEP_LINK_HOST
    val isYooKassaSuccessRedirect = (uri.host?.contains("yookassa.ru") == true || uri.host?.contains("yoomoney.ru") == true) &&
        (uri.path?.contains("success") == true)

    if (isDeepLink || isYooKassaSuccessRedirect) {
        val resolvedId = uri.getQueryParameter("paymentId")
            ?: uri.getQueryParameter("label")
            ?: fallbackPaymentId
        val resolvedAmount = uri.getQueryParameter("amount")?.toIntOrNull() ?: fallbackAmount
        onPaymentCompleted(resolvedId, resolvedAmount)
        return true
    }
    return false
}

/**
 * Анимированный Cyberpunk-экран успешной оплаты:
 * «Реклама отключена навсегда. Спасибо за поддержку!»
 */
@Composable
fun PaymentSuccessCelebrationDialog(
    visible: Boolean,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(250)),
        exit = fadeOut(tween(200))
    ) {
        val infinite = rememberInfiniteTransition(label = "celebration")
        val pulseScale by infinite.animateFloat(
            initialValue = 0.92f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse-scale"
        )

        Dialog(onDismissRequest = onDismiss) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(26.dp),
                        ambientColor = NeonCyan,
                        spotColor = NeonMagenta
                    )
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0E1330), CyberPanelBg)
                        )
                    )
                    .border(
                        width = 2.dp,
                        brush = Brush.linearGradient(listOf(NeonCyan, NeonMagenta, NeonEmerald)),
                        shape = RoundedCornerShape(26.dp)
                    )
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .scale(pulseScale)
                        .background(NeonEmerald.copy(alpha = 0.16f), CircleShape)
                        .border(2.dp, NeonEmerald, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = NeonEmerald,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "SUPPORTER UNLOCKED",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp,
                    color = NeonCyan
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Реклама отключена навсегда. Спасибо за поддержку!",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    lineHeight = 25.sp
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Статус сохранён локально в payment_state.json. Рекламные модули полностью деактивированы во всём приложении.",
                    fontSize = 12.sp,
                    color = Color(0xFFB8B4D8),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(18.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonEmerald,
                        contentColor = Color(0xFF06130E)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Отлично ⚡", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}
