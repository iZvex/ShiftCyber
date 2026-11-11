package ru.shiftcyber.domain

import ru.shiftcyber.data.AppSettings
import ru.shiftcyber.data.ShiftEntity
import ru.shiftcyber.data.ShiftExtraItem
import ru.shiftcyber.data.ShiftPayType

/**
 * Компактный JSON-кодек без внешних зависимостей (одинаково работает на Android и в JVM unit-тестах)
 * для хранения дополнительных сдельных позиций дневной смены и облачной синхронизации с Google Диском.
 */
object ShiftJsonCodec {

    data class SyncPayload(
        val schemaVersion: Int,
        val exportedAtMillis: Long,
        val settings: AppSettings,
        val shifts: List<ShiftEntity>
    )

    fun encodeExtras(items: List<ShiftExtraItem>): String {
        if (items.isEmpty()) return "[]"
        val sb = StringBuilder("[")
        items.forEachIndexed { index, item ->
            if (index > 0) sb.append(',')
            sb.append('{')
            sb.append("\"comment\":").append(quoteString(item.comment)).append(',')
            sb.append("\"quantity\":").append(formatNumber(item.quantity)).append(',')
            sb.append("\"unitPrice\":").append(formatNumber(item.unitPrice))
            sb.append('}')
        }
        sb.append(']')
        return sb.toString()
    }

    fun decodeExtras(json: String?): List<ShiftExtraItem> {
        if (json.isNullOrBlank()) return emptyList()
        val parsed = runCatching { MiniJsonParser(json).parseValue() }.getOrNull() as? List<*>
            ?: return emptyList()
        return parsed.mapNotNull { entry ->
            val obj = entry as? Map<*, *> ?: return@mapNotNull null
            val comment = (obj["comment"] as? String)?.trim().orEmpty()
            val quantity = (obj["quantity"] as? Number)?.toDouble() ?: return@mapNotNull null
            val unitPrice = (obj["unitPrice"] as? Number)?.toDouble() ?: return@mapNotNull null
            if (!quantity.isFinite() || !unitPrice.isFinite() || quantity < 0.0 || unitPrice < 0.0) {
                null
            } else {
                ShiftExtraItem(comment = comment, quantity = quantity, unitPrice = unitPrice)
            }
        }
    }

    fun encodeSyncPayload(
        shifts: List<ShiftEntity>,
        settings: AppSettings,
        exportedAtMillis: Long = System.currentTimeMillis()
    ): String {
        val sb = StringBuilder("{")
        sb.append("\"schemaVersion\":2,")
        sb.append("\"exportedAtMillis\":").append(exportedAtMillis).append(',')
        sb.append("\"settings\":{")
        sb.append("\"hourlyRate\":").append(formatNumber(settings.hourlyRate)).append(',')
        sb.append("\"dailyRate\":").append(formatNumber(settings.dailyRate)).append(',')
        sb.append("\"darkTheme\":").append(settings.darkTheme).append(',')
        sb.append("\"notifyDayBefore\":").append(settings.notifyDayBefore).append(',')
        sb.append("\"notifySameDay\":").append(settings.notifySameDay).append(',')
        sb.append("\"widgetTransparency\":").append(settings.widgetTransparency)
        sb.append("},")
        sb.append("\"shifts\":[")
        shifts.forEachIndexed { index, shift ->
            if (index > 0) sb.append(',')
            sb.append('{')
            sb.append("\"startMillis\":").append(shift.startMillis).append(',')
            sb.append("\"endMillis\":").append(shift.endMillis).append(',')
            sb.append("\"title\":").append(quoteString(shift.title)).append(',')
            sb.append("\"hourlyRate\":").append(formatNumber(shift.hourlyRate)).append(',')
            sb.append("\"breakMinutes\":").append(shift.breakMinutes).append(',')
            sb.append("\"colorKey\":").append(quoteString(shift.colorKey)).append(',')
            sb.append("\"isManual\":").append(shift.isManual).append(',')
            sb.append("\"payType\":").append(quoteString(shift.payType)).append(',')
            sb.append("\"dayRate\":").append(formatNumber(shift.dayRate)).append(',')
            sb.append("\"extrasJson\":").append(quoteString(shift.extrasJson))
            sb.append('}')
        }
        sb.append("]}")
        return sb.toString()
    }

    fun decodeSyncPayload(
        json: String,
        fallbackSettings: AppSettings = AppSettings()
    ): SyncPayload {
        val root = MiniJsonParser(json).parseValue() as? Map<*, *>
            ?: throw IllegalArgumentException("Некорректный формат файла синхронизации")
        val schemaVersion = (root["schemaVersion"] as? Number)?.toInt() ?: 1
        val exportedAt = (root["exportedAtMillis"] as? Number)?.toLong() ?: 0L

        val settingsMap = root["settings"] as? Map<*, *>
        val restoredSettings = if (settingsMap != null) {
            fallbackSettings.copy(
                hourlyRate = (settingsMap["hourlyRate"] as? Number)?.toDouble() ?: fallbackSettings.hourlyRate,
                dailyRate = (settingsMap["dailyRate"] as? Number)?.toDouble() ?: fallbackSettings.dailyRate,
                darkTheme = (settingsMap["darkTheme"] as? Boolean) ?: fallbackSettings.darkTheme,
                notifyDayBefore = (settingsMap["notifyDayBefore"] as? Boolean) ?: fallbackSettings.notifyDayBefore,
                notifySameDay = (settingsMap["notifySameDay"] as? Boolean) ?: fallbackSettings.notifySameDay,
                widgetTransparency = (settingsMap["widgetTransparency"] as? Number)?.toInt()
                    ?: fallbackSettings.widgetTransparency
            )
        } else {
            fallbackSettings
        }

        val rawShifts = root["shifts"] as? List<*> ?: emptyList<Any?>()
        val shifts = rawShifts.mapNotNull { item ->
            val obj = item as? Map<*, *> ?: return@mapNotNull null
            val start = (obj["startMillis"] as? Number)?.toLong() ?: return@mapNotNull null
            val end = (obj["endMillis"] as? Number)?.toLong() ?: return@mapNotNull null
            if (end <= start) return@mapNotNull null
            val rawPayType = (obj["payType"] as? String) ?: ShiftPayType.HOURLY
            val payType = if (rawPayType == ShiftPayType.DAILY) ShiftPayType.DAILY else ShiftPayType.HOURLY
            ShiftEntity(
                id = 0L,
                startMillis = start,
                endMillis = end,
                title = (obj["title"] as? String)?.ifBlank { "Рабочая смена" } ?: "Рабочая смена",
                hourlyRate = ((obj["hourlyRate"] as? Number)?.toDouble() ?: restoredSettings.hourlyRate).coerceAtLeast(0.0),
                breakMinutes = (obj["breakMinutes"] as? Number)?.toInt() ?: 0,
                colorKey = (obj["colorKey"] as? String) ?: "violet",
                isManual = (obj["isManual"] as? Boolean) ?: false,
                payType = payType,
                dayRate = ((obj["dayRate"] as? Number)?.toDouble() ?: 0.0).coerceAtLeast(0.0),
                extrasJson = (obj["extrasJson"] as? String) ?: "[]"
            )
        }

        return SyncPayload(
            schemaVersion = schemaVersion,
            exportedAtMillis = exportedAt,
            settings = restoredSettings,
            shifts = shifts
        )
    }

    /**
     * Извлекает id первого файла из ответа Google Drive API files.list.
     */
    fun extractFirstDriveFileId(json: String): String? {
        val root = runCatching { MiniJsonParser(json).parseValue() }.getOrNull() as? Map<*, *>
            ?: return null
        val files = root["files"] as? List<*> ?: return null
        val first = files.firstOrNull() as? Map<*, *> ?: return null
        return first["id"] as? String
    }

    fun extractDriveFileId(json: String): String? {
        val root = runCatching { MiniJsonParser(json).parseValue() }.getOrNull() as? Map<*, *>
            ?: return null
        return root["id"] as? String
    }

    private fun formatNumber(value: Double): String =
        if (value.isFinite()) {
            val asLong = value.toLong()
            if (value == asLong.toDouble()) asLong.toString() else value.toString()
        } else {
            "0"
        }

    fun quoteString(value: String): String {
        val sb = StringBuilder(value.length + 2)
        sb.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        sb.append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }

    private class MiniJsonParser(private val text: String) {
        private var pos = 0

        fun parseValue(): Any? {
            skipWhitespace()
            if (pos >= text.length) throw IllegalArgumentException("Неожиданный конец JSON")
            return when (val ch = text[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't', 'f' -> parseBoolean()
                'n' -> parseNull()
                '-', in '0'..'9' -> parseNumber()
                else -> throw IllegalArgumentException("Неожиданный символ '$ch' на позиции $pos")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            pos++ // '{'
            skipWhitespace()
            val map = LinkedHashMap<String, Any?>()
            if (pos < text.length && text[pos] == '}') {
                pos++
                return map
            }
            while (true) {
                skipWhitespace()
                if (pos >= text.length || text[pos] != '"') {
                    throw IllegalArgumentException("Ожидался ключ объекта на позиции $pos")
                }
                val key = parseString()
                skipWhitespace()
                if (pos >= text.length || text[pos] != ':') {
                    throw IllegalArgumentException("Ожидалось ':' на позиции $pos")
                }
                pos++
                val value = parseValue()
                map[key] = value
                skipWhitespace()
                if (pos >= text.length) throw IllegalArgumentException("Незакрытый объект")
                when (text[pos]) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return map
                    }
                    else -> throw IllegalArgumentException("Ожидалась ',' или '}' на позиции $pos")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            pos++ // '['
            skipWhitespace()
            val list = ArrayList<Any?>()
            if (pos < text.length && text[pos] == ']') {
                pos++
                return list
            }
            while (true) {
                list += parseValue()
                skipWhitespace()
                if (pos >= text.length) throw IllegalArgumentException("Незакрытый массив")
                when (text[pos]) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return list
                    }
                    else -> throw IllegalArgumentException("Ожидалась ',' или ']' на позиции $pos")
                }
            }
        }

        private fun parseString(): String {
            pos++ // '"'
            val sb = StringBuilder()
            while (pos < text.length) {
                val ch = text[pos++]
                when (ch) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= text.length) throw IllegalArgumentException("Незавершённый escape")
                        when (val esc = text[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > text.length) throw IllegalArgumentException("Некорректный \\u")
                                val hex = text.substring(pos, pos + 4)
                                sb.append(hex.toInt(16).toChar())
                                pos += 4
                            }
                            else -> sb.append(esc)
                        }
                    }
                    else -> sb.append(ch)
                }
            }
            throw IllegalArgumentException("Незакрытая строка")
        }

        private fun parseBoolean(): Boolean = when {
            text.startsWith("true", pos) -> {
                pos += 4
                true
            }
            text.startsWith("false", pos) -> {
                pos += 5
                false
            }
            else -> throw IllegalArgumentException("Некорректный boolean на позиции $pos")
        }

        private fun parseNull(): Any? {
            if (text.startsWith("null", pos)) {
                pos += 4
                return null
            }
            throw IllegalArgumentException("Некорректный null на позиции $pos")
        }

        private fun parseNumber(): Number {
            val start = pos
            if (text[pos] == '-') pos++
            while (pos < text.length && text[pos] in '0'..'9') pos++
            var isFloat = false
            if (pos < text.length && text[pos] == '.') {
                isFloat = true
                pos++
                while (pos < text.length && text[pos] in '0'..'9') pos++
            }
            if (pos < text.length && (text[pos] == 'e' || text[pos] == 'E')) {
                isFloat = true
                pos++
                if (pos < text.length && (text[pos] == '+' || text[pos] == '-')) pos++
                while (pos < text.length && text[pos] in '0'..'9') pos++
            }
            val raw = text.substring(start, pos)
            return if (isFloat) raw.toDouble() else raw.toLongOrNull() ?: raw.toDouble()
        }

        private fun skipWhitespace() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }
    }
}
