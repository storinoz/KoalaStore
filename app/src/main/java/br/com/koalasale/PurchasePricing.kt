package br.com.koalasale

import java.math.BigInteger

// BigInteger.longValueExact is unavailable on Android 8–11.
internal fun BigInteger.checkedLong(): Long {
    if (bitLength() > 63) throw ArithmeticException("Valor fora do intervalo de Long.")
    return toLong()
}

data class ProductCost(val unitCostCents: Long, val purchasedUnits: BigInteger) {
    fun recommended(marginBasisPoints: Int): Long {
        require(marginBasisPoints in 0..9999) { "A margem deve estar entre 0% e 99,99%." }
        return PurchasePricing.ceil(BigInteger.valueOf(unitCostCents) * BigInteger.valueOf(10_000),
            BigInteger.valueOf((10_000 - marginBasisPoints).toLong())).checkedLong()
    }
}

object PurchasePricing {
    internal fun ceil(numerator: BigInteger, denominator: BigInteger): BigInteger =
        (numerator + denominator - BigInteger.ONE) / denominator

    fun total(subtotal: Long, shipping: Long, fees: Long): Long {
        require(subtotal >= 0)
        require(shipping in 0..Rules.MAX_PRICE && fees in 0..Rules.MAX_PRICE) { "Frete e taxas devem estar entre zero e R$ 1.000.000,00." }
        return Math.addExact(subtotal, Math.addExact(shipping, fees))
    }

    // Largest-remainder allocation preserves every cent of the purchase expenses.
    fun allocatedCosts(purchase: PurchaseWithItems): Map<Long, Long> {
        val lines = purchase.draftLines()
        require(lines.isNotEmpty() && lines.map { it.productId }.distinct().size == lines.size)
        val subtotal = purchase.subtotal
        total(subtotal, purchase.purchase.shippingCents, purchase.purchase.feesCents)
        val expenses = purchase.purchase.shippingCents + purchase.purchase.feesCents
        val weights = lines.map { if (subtotal > 0) it.priceCents * it.quantity else it.quantity.toLong() }
        val denominator = weights.fold(BigInteger.ZERO) { sum, weight -> sum + BigInteger.valueOf(weight) }
        val divisions = weights.map { (BigInteger.valueOf(expenses) * BigInteger.valueOf(it)).divideAndRemainder(denominator) }
        val allocations = divisions.map { it[0].checkedLong() }.toMutableList()
        val remaining = expenses - allocations.sum()
        lines.indices.sortedWith(compareByDescending<Int> { divisions[it][1] }.thenBy { lines[it].productId })
            .take(remaining.toInt()).forEach { allocations[it]++ }
        return lines.mapIndexed { index, line -> line.productId to Math.addExact(line.priceCents * line.quantity, allocations[index]) }.toMap()
    }

    fun averageCosts(purchases: List<PurchaseWithItems>): Map<Long, ProductCost> {
        val amounts = mutableMapOf<Long, BigInteger>()
        val units = mutableMapOf<Long, BigInteger>()
        purchases.forEach { purchase ->
            val costs = allocatedCosts(purchase)
            purchase.items.forEach { item ->
                amounts[item.productId] = (amounts[item.productId] ?: BigInteger.ZERO) + BigInteger.valueOf(costs.getValue(item.productId))
                units[item.productId] = (units[item.productId] ?: BigInteger.ZERO) + BigInteger.valueOf(item.quantity.toLong())
            }
        }
        return amounts.mapValues { (id, amount) -> ProductCost(ceil(amount, units.getValue(id)).checkedLong(), units.getValue(id)) }
    }
}
