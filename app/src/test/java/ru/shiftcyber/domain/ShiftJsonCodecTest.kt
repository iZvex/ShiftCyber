package ru.shiftcyber.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.shiftcyber.data.AppSettings
import ru.shiftcyber.data.ShiftEntity
import ru.shiftcyber.data.ShiftExtraItem
import ru.shiftcyber.data.ShiftPayType

class ShiftJsonCodecTest {

    @Test
    fun `sync payload roundtrip preserves hourly and daily shifts extras and settings`() {
        val extras = listOf(
            ShiftExtraItem(comment = "Заказы по району", quantity = 12.0, unitPrice = 180.0),
            ShiftExtraItem(comment = "Тяжёлый груз", quantity = 2.0, unitPrice = 300.0)
        )
        val shifts = listOf(
            ShiftEntity(
                id = 1L,
                startMillis = 1_762_848_000_000L,
                endMillis = 1_762_891_200_000L,
                title = "Почасовая смена",
                hourlyRate = 450.0,
                payType = ShiftPayType.HOURLY,
                colorKey = "cyan",
                isManual = true
            ),
            ShiftEntity(
                id = 2L,
                startMillis = 1_762_934_400_000L,
                endMillis = 1_763_020_800_000L,
                title = "Дневная смена",
                payType = ShiftPayType.DAILY,
                dayRate = 3200.0,
                extrasJson = ShiftJsonCodec.encodeExtras(extras),
                colorKey = "green",
                isManual = true
            )
        )
        val settings = AppSettings(
            hourlyRate = 450.0,
            dailyRate = 3200.0,
            notifyDayBefore = true,
            notifySameDay = false,
            darkTheme = true,
            autoGoogleSync = true
        )

        val encoded = ShiftJsonCodec.encodeSyncPayload(
            shifts = shifts,
            settings = settings,
            exportedAtMillis = 1_763_000_000_000L
        )

        val decoded = ShiftJsonCodec.decodeSyncPayload(encoded)
        assertEquals(2, decoded.shifts.size)
        assertEquals(ShiftPayType.HOURLY, decoded.shifts[0].payType)
        assertEquals(450.0, decoded.shifts[0].hourlyRate, 1e-9)

        assertEquals(ShiftPayType.DAILY, decoded.shifts[1].payType)
        assertEquals(3200.0, decoded.shifts[1].dayRate, 1e-9)
        val decodedExtras = ShiftJsonCodec.decodeExtras(decoded.shifts[1].extrasJson)
        assertEquals(2, decodedExtras.size)
        assertEquals("Заказы по району", decodedExtras[0].comment)
        assertEquals(2160.0, decodedExtras[0].total, 1e-9)
        assertEquals(5960.0, ShiftMath.earnings(decoded.shifts[1]), 1e-9)

        assertEquals(3200.0, decoded.settings.dailyRate, 1e-9)
        assertEquals(false, decoded.settings.notifySameDay)
    }
}
