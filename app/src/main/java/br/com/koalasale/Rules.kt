package br.com.koalasale

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

object Rules {
    const val MAX_PRICE = 100_000_000L
    const val MAX_QUANTITY = 1_000_000
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)

    fun cents(text: String): Long? = runCatching {
        val raw = text.trim().removePrefix("R$").trim()
        require(raw.matches(Regex("[0-9]+([,.][0-9]{1,2})?")))
        BigDecimal(raw.replace(',', '.')).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY)
            .longValueExact().also { require(it in 0..MAX_PRICE) }
    }.getOrNull()

    fun money(cents: Long): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
        .format(BigDecimal.valueOf(cents, 2))

    fun priceInput(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString().replace('.', ',')

    fun date(text: String): String? = if (text.isBlank()) null else LocalDate.parse(text, dateFormat).toString()
    fun dateLabel(iso: String?): String = iso?.let { LocalDate.parse(it).format(dateFormat) } ?: ""
    fun inMonth(saleDate: String?, selection: Long): Boolean = when(selection) {
        0L -> true
        -1L -> saleDate == null
        else -> saleDate?.let { LocalDate.parse(it).let { date -> date.year * 100L + date.monthValue == selection } } ?: false
    }

    // Brazilian local numbers receive +55. An explicit + preserves international numbers.
    fun phone(text: String): String? {
        val raw = text.trim()
        if (!raw.matches(Regex("\\+?[0-9 ()-]+"))) return null
        var digits = raw.filter(Char::isDigit)
        if (!raw.startsWith("+") && digits.length in 10..11) digits = "55$digits"
        if (digits.length !in 10..15 || digits.firstOrNull() == '0') return null
        return digits
    }

    fun total(lines: List<DraftLine>): Long = lines.fold(0L) { sum, line ->
        require(line.quantity in 1..MAX_QUANTITY)
        require(line.priceCents in 0..MAX_PRICE)
        Math.addExact(sum, Math.multiplyExact(line.priceCents, line.quantity.toLong()))
    }
}

data class DraftLine(val productId: Long, val name: String, val quantity: Int, val priceCents: Long)
