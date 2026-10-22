package br.com.koalasale

import org.junit.Test
import org.junit.Assert.*

class PurchasePricingTest {
    private fun purchase(id: Long, shipping: Long, fees: Long, vararg lines: DraftLine) = PurchaseWithItems(
        Purchase(id, "2026-10-03", shipping, fees), lines.mapIndexed { index, line ->
            PurchaseItem(purchaseId = id, productId = line.productId, productName = line.name,
                quantity = line.quantity, unitPriceCents = line.priceCents, position = index)
        })

    @Test fun proportionalAllocationIncludesBothExpensesAndPreservesEveryCent() {
        val purchase = purchase(1, 301, 100, DraftLine(1, "CI", 2, 300), DraftLine(2, "Resistor", 4, 100))
        val costs = PurchasePricing.allocatedCosts(purchase)
        assertEquals(841L, costs[1L])
        assertEquals(560L, costs[2L])
        assertEquals(purchase.total, costs.values.sum())
        val averages = PurchasePricing.averageCosts(listOf(purchase))
        assertEquals(421L, averages.getValue(1).unitCostCents)
        assertEquals(702L, averages.getValue(1).recommended(4000))
        assertEquals(234L, averages.getValue(2).recommended(4000))
    }

    @Test fun freeItemsUseQuantityWhenWholePurchaseSubtotalIsZero() {
        val purchase = purchase(1, 300, 0, DraftLine(1, "CI", 2, 0), DraftLine(2, "Pino", 1, 0))
        assertEquals(mapOf(1L to 200L, 2L to 100L), PurchasePricing.allocatedCosts(purchase))
        assertEquals(167L, PurchasePricing.averageCosts(listOf(purchase)).getValue(1).recommended(4000))
    }

    @Test fun weightedAverageUsesAllUnitsAndMarginIsOnSalePrice() {
        val old = purchase(1, 0, 0, DraftLine(1, "CI", 10, 400))
        val recent = purchase(2, 1000, 0, DraftLine(1, "CI", 10, 700))
        val cost = PurchasePricing.averageCosts(listOf(old, recent)).getValue(1)
        assertEquals(600L, cost.unitCostCents)
        assertEquals(1000L, cost.recommended(4000))
        assertEquals(1200L, cost.recommended(5000))
        assertEquals(600L, cost.recommended(0))
        assertEquals(800L, PurchasePricing.averageCosts(listOf(recent)).getValue(1).unitCostCents)
        assertTrue(PurchasePricing.averageCosts(emptyList()).isEmpty())
    }

    @Test fun fractionalUnitCostAndRecommendedPriceAlwaysRoundUp() {
        val cost = PurchasePricing.averageCosts(listOf(purchase(1, 1, 0, DraftLine(1, "CI", 3, 100)))).getValue(1)
        assertEquals(101L, cost.unitCostCents)
        assertEquals(169L, cost.recommended(4000))
        assertEquals(1010000L, cost.recommended(9999))
    }

    @Test fun largeValuesAllocateWithoutIntermediateLongOverflow() {
        assertEquals(Long.MAX_VALUE, java.math.BigInteger.valueOf(Long.MAX_VALUE).checkedLong())
        assertEquals(Long.MIN_VALUE, java.math.BigInteger.valueOf(Long.MIN_VALUE).checkedLong())
        try { (java.math.BigInteger.valueOf(Long.MAX_VALUE) + java.math.BigInteger.ONE).checkedLong(); fail("Overflow") }
        catch (_: ArithmeticException) { }
        try { (java.math.BigInteger.valueOf(Long.MIN_VALUE) - java.math.BigInteger.ONE).checkedLong(); fail("Overflow") }
        catch (_: ArithmeticException) { }
        val purchase = purchase(1, Rules.MAX_PRICE, Rules.MAX_PRICE,
            DraftLine(1, "CI", Rules.MAX_QUANTITY, Rules.MAX_PRICE),
            DraftLine(2, "Pino", Rules.MAX_QUANTITY, Rules.MAX_PRICE))
        val costs = PurchasePricing.allocatedCosts(purchase)
        assertEquals(purchase.total, costs.values.sum())
        assertEquals(100000100L, PurchasePricing.averageCosts(listOf(purchase)).getValue(1).unitCostCents)
    }

    @Test fun invalidMarginsExpensesAndDuplicateProductsAreRejected() {
        val cost = ProductCost(100, java.math.BigInteger.ONE)
        for (margin in listOf(-1, 10000, 20000)) {
            try { cost.recommended(margin); fail("Margem inválida aceita") } catch (_: IllegalArgumentException) { }
        }
        for ((shipping, fees) in listOf(-1L to 0L, 0L to -1L, Rules.MAX_PRICE + 1 to 0L)) {
            try { PurchasePricing.total(100, shipping, fees); fail("Taxa inválida aceita") } catch (_: IllegalArgumentException) { }
        }
        try { PurchasePricing.allocatedCosts(purchase(1, 0, 0, DraftLine(1,"CI",1,100), DraftLine(1,"CI",1,200))); fail("Duplicado") }
        catch (_: IllegalArgumentException) { }
    }
}
