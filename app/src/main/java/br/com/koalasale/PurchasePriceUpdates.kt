package br.com.koalasale

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

data class ProductPriceUpdate(val productId: Long, val marginBasisPoints: Int, val priceCents: Long)

@Composable
internal fun PurchasePriceEditor(purchase: PurchaseWithItems, products: List<Product>, busy: Boolean,
    onClose: () -> Unit, onSave: (List<ProductPriceUpdate>) -> Unit) {
    val costs = remember(purchase) { PurchasePricing.averageCosts(listOf(purchase)) }
    val mapSaver = listSaver<Map<Long, String>, Any>(
        save = { map -> map.flatMap { listOf(it.key, it.value) } },
        restore = { list -> list.chunked(2).associate { it[0] as Long to it[1] as String } })
    var margins by rememberSaveable(purchase.purchase.id, stateSaver = mapSaver) {
        mutableStateOf(purchase.items.associate { item -> item.productId to
            products.first { it.id == item.productId }.marginBasisPoints.toString() })
    }
    var prices by rememberSaveable(purchase.purchase.id, stateSaver = mapSaver) {
        mutableStateOf(purchase.items.associate { item -> item.productId to
            products.first { it.id == item.productId }.priceCents.toString() })
    }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    EditorFrame("Atualizar preços", busy, onClose, {
        try {
            val updates = purchase.items.map { item ->
                val margin = margins[item.productId]?.toIntOrNull()
                val price = InputMasks.cents(prices[item.productId].orEmpty())
                require(margin != null && margin in 0..9999) { "Informe uma margem entre 0% e 99,99% para ${item.productName}." }
                require(price != null) { "Informe um preço de venda válido para ${item.productName}." }
                ProductPriceUpdate(item.productId, margin, price)
            }
            error = null
            onSave(updates)
        } catch (e: IllegalArgumentException) { error = e.message }
    }, fullHeight = true) {
        Text("Compra #${purchase.purchase.id} salva. Confira a margem e o preço de venda dos produtos comprados.")
        Text("O custo é desta compra, incluindo o rateio de frete e taxas. Salvar atualiza os produtos; cancelar mantém a compra salva e os preços anteriores.",
            style = MaterialTheme.typography.bodySmall)
        purchase.items.sortedBy { it.position }.forEach { item -> key(item.productId) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.productName, fontWeight = FontWeight.SemiBold)
                    val cost = costs.getValue(item.productId)
                    Text("Custo desta compra / un.: ${Rules.money(cost.unitCostCents)}", style = MaterialTheme.typography.bodyMedium)
                    Field("Margem (%) de ${item.productName}", margins[item.productId].orEmpty(),
                        { margins = margins + (item.productId to InputMasks.digits(it, 5)) }, KeyboardType.Number,
                        "Digite 4000 para 40,00%. Margem sobre o preço de venda.", InputMasks.moneyMask)
                    Field("Preço de venda de ${item.productName}", prices[item.productId].orEmpty(),
                        { prices = prices + (item.productId to InputMasks.digits(it, 9)) }, KeyboardType.Number,
                        mask = InputMasks.moneyMask, prefix = "R$ ")
                    margins[item.productId]?.toIntOrNull()?.takeIf { it in 0..9999 }?.let { margin ->
                        Text("Recomendado nesta compra: ${Rules.money(cost.recommended(margin))}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } }
        ErrorText(error)
    }
}
