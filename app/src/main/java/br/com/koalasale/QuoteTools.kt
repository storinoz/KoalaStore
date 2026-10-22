package br.com.koalasale

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Collator
import java.util.Locale

enum class AdjustmentType(val code: Int, val label: String) {
    NONE(0, "Sem ajuste"), DISCOUNT(1, "Desconto"), FEE(2, "Taxa")
}

data class QuoteAmounts(val subtotal: Long, val adjustment: Long, val total: Long)

object QuotePricing {
    // A percentage uses hundredths of one percent: 1250 = 12.50%.
    // Monetary adjustments use integer cents, exactly like product prices.
    fun calculate(subtotal: Long, type: Int = 0, percent: Boolean = false, value: Long = 0): QuoteAmounts {
        require(subtotal >= 0) { "Subtotal inválido." }
        require(type in 0..2) { "Selecione um ajuste válido." }
        if (type == 0) {
            require(value == 0L && !percent) { "Remova o valor do ajuste desativado." }
            return QuoteAmounts(subtotal, 0, subtotal)
        }
        require(value >= 0) { "O desconto ou a taxa não pode ser negativo." }
        val amount = if (percent) {
            require(value <= 10_000) { "O percentual deve estar entre 0% e 100%." }
            BigDecimal.valueOf(subtotal).multiply(BigDecimal.valueOf(value))
                .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP).longValueExact()
        } else {
            require(value <= Rules.MAX_PRICE) { "O valor do ajuste deve ser de até R$ 1.000.000,00." }
            value
        }
        require(type != 1 || amount <= subtotal) { "O desconto não pode superar o subtotal da compra." }
        val signed = if (type == 1) -amount else amount
        val total = runCatching { Math.addExact(subtotal, signed) }.getOrElse {
            throw IllegalArgumentException("O total ultrapassa o limite permitido.")
        }
        return QuoteAmounts(subtotal, signed, total)
    }

    fun label(quote: Quote): String = AdjustmentType.entries.first { it.code == quote.adjustmentType }.label +
        if (quote.adjustmentIsPercent) " (${Rules.priceInput(quote.adjustmentValue)}%)" else ""
}

enum class QuoteOrder(val group: Int, val label: String, val shortLabel: String) {
    DATE_DESC(0, "Data: mais recente primeiro", "Data ↓"),
    DATE_ASC(0, "Data: mais antiga primeiro", "Data ↑"),
    VALUE_DESC(1, "Valor: maior primeiro", "Valor ↓"),
    VALUE_ASC(1, "Valor: menor primeiro", "Valor ↑"),
    CLIENT_ASC(2, "Cliente: A–Z", "Cliente A–Z"),
    CLIENT_DESC(2, "Cliente: Z–A", "Cliente Z–A");

    fun toggle(): QuoteOrder = when(this) {
        DATE_DESC -> DATE_ASC; DATE_ASC -> DATE_DESC
        VALUE_DESC -> VALUE_ASC; VALUE_ASC -> VALUE_DESC
        CLIENT_ASC -> CLIENT_DESC; CLIENT_DESC -> CLIENT_ASC
    }
}

object QuoteListing {
    fun apply(quotes: List<QuoteWithItems>, customers: List<Customer>, month: Long, search: String,
        order: QuoteOrder = QuoteOrder.DATE_DESC, status: Int? = null): List<QuoteWithItems> {
        val names = customers.associate { it.id to it.name }
        val query = search.trim()
        // Month is the outer scope; neither search nor ordering can add records outside it.
        val filtered = quotes.filter { Rules.inMonth(it.quote.saleDate, month) }
            .filter { status == null || it.quote.status == status }.filter { budget ->
            query.isEmpty() || names[budget.quote.customerId]?.contains(query, true) == true ||
                budget.items.any { it.productName.contains(query, true) } || budget.quote.id.toString() == query
        }
        fun dates(a: QuoteWithItems, b: QuoteWithItems, ascending: Boolean = false): Int {
            val first = a.quote.saleDate
            val second = b.quote.saleDate
            return when {
                first == null && second == null -> 0
                first == null -> 1
                second == null -> -1
                ascending -> first.compareTo(second)
                else -> second.compareTo(first)
            }
        }
        val collator = Collator.getInstance(Locale.forLanguageTag("pt-BR")).apply { strength = Collator.PRIMARY }
        return filtered.sortedWith { a, b ->
            val primary = when(order) {
                QuoteOrder.DATE_DESC -> dates(a, b)
                QuoteOrder.DATE_ASC -> dates(a, b, true)
                QuoteOrder.VALUE_DESC -> b.total.compareTo(a.total)
                QuoteOrder.VALUE_ASC -> a.total.compareTo(b.total)
                QuoteOrder.CLIENT_ASC -> collator.compare(names[a.quote.customerId].orEmpty(), names[b.quote.customerId].orEmpty())
                QuoteOrder.CLIENT_DESC -> collator.compare(names[b.quote.customerId].orEmpty(), names[a.quote.customerId].orEmpty())
            }
            if (primary != 0) primary else dates(a, b).takeIf { it != 0 } ?: b.quote.id.compareTo(a.quote.id)
        }
    }
}
