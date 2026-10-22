package br.com.koalasale

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.Instant

object InputMasks {
    fun digits(text: String, limit: Int) = text.filter { it in '0'..'9' }.take(limit)
    fun dateText(raw: String): String = buildString {
        raw.take(8).forEachIndexed { index, digit ->
            append(digit)
            if (index == 1 || index == 3) append('/')
        }
    }
    fun date(raw: String, required: Boolean = false): String? {
        require(!required || raw.isNotBlank()) { "Informe a data da venda." }
        if (raw.isBlank()) return null
        require(raw.length == 8) { "Informe a data completa no formato DD/MM/AAAA." }
        return Rules.date(dateText(raw)).also { require(LocalDate.parse(it).year in 1..9999) { "Informe um ano entre 0001 e 9999." } }
    }
    fun moneyText(raw: String): String {
        if (raw.isEmpty()) return ""
        val padded = raw.padStart(3, '0')
        val whole = padded.dropLast(2).reversed().chunked(3).joinToString(".").reversed()
        return whole + "," + padded.takeLast(2)
    }
    fun cents(raw: String): Long? = raw.toLongOrNull()?.takeIf { it in 0..Rules.MAX_PRICE }
    fun phoneRaw(text: String): String {
        val international = text.trimStart().startsWith('+')
        val limit = if (!international) 11 else if (text.startsWith("+55")) 13 else 15
        return (if (international) "+" else "") + digits(text, limit)
    }
    fun phoneText(raw: String): String {
        val brazilPrefix = raw.startsWith("+55")
        if (raw.startsWith('+') && !brazilPrefix) return raw
        val prefix = if (brazilPrefix) "+55 " else ""
        val number = if (brazilPrefix) raw.drop(3) else raw
        if (number.isEmpty()) return prefix.trimEnd()
        val area = number.take(2)
        if (number.length < 2) return "$prefix($area"
        val local = number.drop(2)
        val split = if (local.length > 8) 5 else 4
        return "$prefix($area) " + local.take(split) +
            (if (local.length > split) "-" + local.drop(split) else "")
    }
    fun dateMillis(iso: String): Long = LocalDate.parse(iso).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun dateFromMillis(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()

    val dateMask = Mask(::dateText)
    val moneyMask = Mask(::moneyText)
    val phoneMask = Mask(::phoneText)

    // Map editable characters from right to left, accounting for generated zeroes in cents.
    // Keeps cursor movement, insertion and deletion valid at every separator.
    class Mask(private val format: (String) -> String) : VisualTransformation {
        override fun filter(text: AnnotatedString): TransformedText {
            val display = format(text.text)
            val positions = IntArray(text.length)
            var cursor = display.lastIndex
            for (index in text.text.indices.reversed()) {
                while (cursor >= 0 && display[cursor] != text[index]) cursor--
                positions[index] = cursor.coerceAtLeast(0)
                cursor--
            }
            return TransformedText(AnnotatedString(display), object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int =
                    if (offset <= 0) positions.firstOrNull() ?: 0 else
                        (positions[offset.coerceAtMost(positions.size) - 1] + 1).coerceAtMost(display.length)
                override fun transformedToOriginal(offset: Int): Int = positions.count { it < offset }
            })
        }
    }
}

object MonthOptions {
    fun available(dates: List<String?>, today: LocalDate): List<YearMonth> {
        val current = YearMonth.from(today)
        val earliest = dates.mapNotNull { date -> date?.let { LocalDate.parse(it).year } }
            .filter { it <= today.year }.minOrNull()?.coerceAtMost(today.year) ?: today.year
        val years = today.year downTo earliest
        return years.flatMap { year -> (12 downTo 1).map { YearMonth.of(year, it) } }.filter { it <= current }
    }
}
