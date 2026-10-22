package br.com.koalasale

import org.junit.Assert.*
import org.junit.Test

class QuoteToolsTest {
    @Test fun fixedAndPercentAdjustmentsUseSubtotalAndRoundToCents() {
        assertEquals(8750L, QuotePricing.calculate(10000, 1, false, 1250).total)
        assertEquals(11250L, QuotePricing.calculate(10000, 2, false, 1250).total)
        assertEquals(8750L, QuotePricing.calculate(10000, 1, true, 1250).total)
        assertEquals(11250L, QuotePricing.calculate(10000, 2, true, 1250).total)
        assertEquals(2L, QuotePricing.calculate(101, 2, true, 150).adjustment)
        assertEquals(0L, QuotePricing.calculate(101, 1, true, 10000).total)
        assertEquals(101L, QuotePricing.calculate(101).total)
        assertEquals(0L, QuotePricing.calculate(0, 1, true, 10000).total)
    }

    @Test fun invalidAdjustmentsCannotProduceNegativeTotalsOrOverflow() {
        val invalid = listOf<() -> Any>(
            { QuotePricing.calculate(100, 1, false, 101) },
            { QuotePricing.calculate(100, 1, true, 10001) },
            { QuotePricing.calculate(100, 2, true, 10001) },
            { QuotePricing.calculate(100, 2, false, -1) },
            { QuotePricing.calculate(100, 2, false, Rules.MAX_PRICE + 1) },
            { QuotePricing.calculate(100, 8, false, 0) },
            { QuotePricing.calculate(100, 0, false, 20) },
            { QuotePricing.calculate(Long.MAX_VALUE, 2, false, 1) })
        invalid.forEach { assertTrue(runCatching(it).exceptionOrNull() is IllegalArgumentException) }
        assertEquals(Long.MAX_VALUE, QuotePricing.calculate(Long.MAX_VALUE, 2, true, 0).total)
    }

    private val customers = listOf(Customer(1, "Álvaro", "5511912345678"), Customer(2, "Bruno", "5511912345678"), Customer(3, "ana", "5511912345678"))
    private fun quote(id: Long, customer: Long, date: String?, price: Long, status: Int = 0, discount: Long = 0) =
        QuoteWithItems(Quote(id, customer, date, status = status, adjustmentType = if (discount > 0) 1 else 0, adjustmentValue = discount),
            listOf(QuoteItem(id, id, 1, "Componente", 1, price, 0)))
    private val quotes = listOf(quote(1, 2, "2026-10-03", 300, 0), quote(2, 1, "2026-10-01", 600, 1, 550),
        quote(3, 3, "2026-10-02", 200, 2), quote(4, 2, "2026-09-30", 900, 2), quote(5, 1, null, 100, 0))

    @Test fun sortingUsesSaleDateFinalValueAndBrazilianAlphabetWithUndatedLast() {
        fun ids(order: QuoteOrder, month: Long = 202610) = QuoteListing.apply(quotes, customers, month, "", order).map { it.quote.id }
        assertEquals(listOf(1L, 3L, 2L), ids(QuoteOrder.DATE_DESC))
        assertEquals(listOf(2L, 3L, 1L), ids(QuoteOrder.DATE_ASC))
        assertEquals(listOf(1L, 3L, 2L), ids(QuoteOrder.VALUE_DESC))
        assertEquals(listOf(2L, 3L, 1L), ids(QuoteOrder.VALUE_ASC))
        assertEquals(listOf(2L, 3L, 1L), ids(QuoteOrder.CLIENT_ASC))
        assertEquals(listOf(1L, 3L, 2L), ids(QuoteOrder.CLIENT_DESC))
        assertEquals(5L, ids(QuoteOrder.DATE_ASC, 0).last())
        assertEquals(5L, ids(QuoteOrder.DATE_DESC, 0).last())
    }

    @Test fun monthBoundsStatusAndSearchBeforeAnySortIsApplied() {
        for (order in QuoteOrder.entries) {
            assertEquals(listOf(3L), QuoteListing.apply(quotes, customers, 202610, "Componente", order, 2).map { it.quote.id })
            assertTrue(QuoteListing.apply(quotes, customers, 202610, "Bruno", order, 2).isEmpty())
            assertTrue(QuoteListing.apply(quotes, customers, 202610, "4", order).isEmpty())
            assertEquals(listOf(4L), QuoteListing.apply(quotes, customers, 202609, "Bruno", order, 2).map { it.quote.id })
            assertEquals(listOf(5L), QuoteListing.apply(quotes, customers, -1, "", order, 0).map { it.quote.id })
        }
    }
}
