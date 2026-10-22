package br.com.koalasale

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
internal fun ProductCostSummary(cost: ProductCost?, margin: Int) {
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("Custo mínimo / un.: ${cost?.let { Rules.money(it.unitCostCents) } ?: "—"}", style = MaterialTheme.typography.bodySmall)
        Text("Valor de venda recomendado: ${cost?.let { Rules.money(it.recommended(margin)) } ?: "—"}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold)

    }
}

@Composable
internal fun PurchaseCard(value: PurchaseWithItems, onEdit: () -> Unit, onDelete: () -> Unit) {
    var expanded by rememberSaveable(value.purchase.id) { mutableStateOf(false) }
    val costs = remember(value) { PurchasePricing.allocatedCosts(value) }
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Compra #${value.purchase.id}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Data: ${Rules.dateLabel(value.purchase.date)}", style = MaterialTheme.typography.bodySmall)
                    Text(value.purchase.company.ifBlank { "Empresa não informada" }, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Editar compra #${value.purchase.id}") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Excluir compra #${value.purchase.id}") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Rules.money(value.total), Modifier.weight(1f), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                ExpandButton(expanded, "produtos da compra #${value.purchase.id}") { expanded = !expanded }
            }
            if (expanded) {
                HorizontalDivider()
                Text("Recebimento: ${Rules.dateLabel(value.purchase.receivedDate).ifEmpty { "Não informado" }}", style = MaterialTheme.typography.bodySmall)
                Text("${value.items.size} produto(s) · ${value.items.sumOf { it.quantity.toLong() }} unidade(s)", style = MaterialTheme.typography.bodySmall)
                Text("Valor dos produtos: ${Rules.money(value.subtotal)}", style = MaterialTheme.typography.bodySmall)
                Text("Frete: ${Rules.money(value.purchase.shippingCents)} · Taxas: ${Rules.money(value.purchase.feesCents)}", style = MaterialTheme.typography.bodySmall)
                value.items.sortedBy { it.position }.forEach { item ->
                    Column(Modifier.padding(top = 10.dp)) {
                        Text("${item.quantity} × ${item.productName}", fontWeight = FontWeight.SemiBold)
                        Text("${Rules.money(item.unitPriceCents)} / un. · ${Rules.money(item.unitPriceCents * item.quantity)}", style = MaterialTheme.typography.bodySmall)
                        val unitCost = PurchasePricing.ceil(java.math.BigInteger.valueOf(costs.getValue(item.productId)),
                            java.math.BigInteger.valueOf(item.quantity.toLong())).checkedLong()
                        Text("Custo com frete e taxas / un.: ${Rules.money(unitCost)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
internal fun PurchaseEditor(value: PurchaseWithItems?, data: AppData, busy: Boolean, onClose: () -> Unit,
    onSave: (Purchase, List<DraftLine>) -> Unit) {
    var date by rememberSaveable { mutableStateOf(InputMasks.digits(Rules.dateLabel(value?.purchase?.date ?: LocalDate.now().toString()), 8)) }
    var receivedDate by rememberSaveable { mutableStateOf(InputMasks.digits(Rules.dateLabel(value?.purchase?.receivedDate), 8)) }
    var company by rememberSaveable { mutableStateOf(value?.purchase?.company.orEmpty()) }
    var shipping by rememberSaveable { mutableStateOf((value?.purchase?.shippingCents ?: 0).toString()) }
    var fees by rememberSaveable { mutableStateOf((value?.purchase?.feesCents ?: 0).toString()) }
    val lineSaver = listSaver<List<DraftLine>, Any>(
        save = { list -> list.flatMap { listOf(it.productId, it.name, it.quantity, it.priceCents) } },
        restore = { list -> list.chunked(4).map { DraftLine(it[0] as Long, it[1] as String, it[2] as Int, it[3] as Long) } })
    val mapSaver = listSaver<Map<Long, String>, Any>(save = { map -> map.flatMap { listOf(it.key, it.value) } },
        restore = { list -> list.chunked(2).associate { it[0] as Long to it[1] as String } })
    var lines by rememberSaveable(stateSaver = lineSaver) { mutableStateOf(value?.draftLines().orEmpty()) }
    var prices by rememberSaveable(stateSaver = mapSaver) { mutableStateOf(value?.items?.associate { it.productId to it.unitPriceCents.toString() }.orEmpty()) }
    var quantities by rememberSaveable(stateSaver = mapSaver) { mutableStateOf(emptyMap<Long, String>()) }
    var picker by rememberSaveable { mutableStateOf(false) }
    var notice by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    fun normalizedLines(): List<DraftLine> = lines.map { line ->
        require(data.products.any { it.id == line.productId }) { "Selecione somente produtos já cadastrados." }
        val quantity = (quantities[line.productId] ?: line.quantity.toString()).toIntOrNull()
        require(quantity != null && quantity in 1..Rules.MAX_QUANTITY) { "Informe uma quantidade válida para ${line.name}." }
        val price = InputMasks.cents(prices[line.productId].orEmpty())
        require(price != null) { "Informe o preço unitário de compra de ${line.name}." }
        line.copy(quantity = quantity, priceCents = price)
    }
    EditorFrame(if (value == null) "Nova compra" else "Compra #${value.purchase.id}", busy, onClose, {
        try {
            require(lines.isNotEmpty()) { "Adicione pelo menos um produto cadastrado." }
            val purchaseDate = InputMasks.date(date, required = true)!!
            val received = InputMasks.date(receivedDate)
            require(company.isNotBlank()) { "Informe a empresa onde a compra foi feita." }
            val freight = InputMasks.cents(shipping)
            val taxes = InputMasks.cents(fees)
            require(freight != null && taxes != null) { "Informe valores válidos para frete e taxas. Use zero quando não houver." }
            val normalized = normalizedLines()
            PurchasePricing.total(Rules.total(normalized), freight, taxes)
            error = null
            onSave(Purchase(value?.purchase?.id ?: 0, purchaseDate, freight, taxes, company.trim(), received), normalized)
        } catch (_: java.time.format.DateTimeParseException) { error = "Use uma data válida no formato DD/MM/AAAA." }
        catch (e: IllegalArgumentException) { error = e.message }
    }, fullHeight = true) {
        DateField("Data da compra", date, { date = it }, required = true, info = true)
        DateField("Data de recebimento", receivedDate, { receivedDate = it }, info = true)
        Field("Empresa", company, { company = it }, hint = "Informe a empresa ou fornecedor da compra. Campo obrigatório.", info = true)
        Text("Selecione os produtos cadastrados e informe quanto você pagou por unidade.", style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Produtos repostos", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { if (data.products.isEmpty()) notice = true else picker = true }) { Text("Adicionar vários") }
        }
        if (lines.isEmpty()) Text("Nenhum produto adicionado.")
        lines.forEach { line -> key(line.productId) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(line.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = {
                            lines = lines.filterNot { it.productId == line.productId }; prices = prices - line.productId; quantities = quantities - line.productId
                        }) { Icon(Icons.Default.Close, "Remover ${line.name}") }
                    }
                    Field("Preço unitário de compra", prices[line.productId].orEmpty(), { raw ->
                        val digits = InputMasks.digits(raw, 9)
                        prices = prices + (line.productId to digits)
                        InputMasks.cents(digits)?.let { price -> lines = lines.map { if (it.productId == line.productId) it.copy(priceCents = price) else it } }
                    }, KeyboardType.Number, "Valor pago por unidade, sem frete ou taxas.", InputMasks.moneyMask, "R$ ")
                    QuantityStepper(line.name, quantities[line.productId] ?: line.quantity.toString(), { raw ->
                        quantities = quantities + (line.productId to raw)
                        raw.toIntOrNull()?.takeIf { it in 1..Rules.MAX_QUANTITY }?.let { quantity ->
                            lines = lines.map { if (it.productId == line.productId) it.copy(quantity = quantity) else it }
                        }
                    })
                }
            }
        } }
        HorizontalDivider()
        Field("Frete", shipping, { shipping = InputMasks.digits(it, 9) }, KeyboardType.Number, "Valor total do frete desta compra.", InputMasks.moneyMask, "R$ ")
        Field("Outras taxas", fees, { fees = InputMasks.digits(it, 9) }, KeyboardType.Number, "Valor total das demais taxas. Use zero se não houver.", InputMasks.moneyMask, "R$ ")
        val subtotal = runCatching { Rules.total(normalizedLines()) }.getOrNull()
        val total = subtotal?.let { base -> runCatching { PurchasePricing.total(base, requireNotNull(InputMasks.cents(shipping)), requireNotNull(InputMasks.cents(fees))) }.getOrNull() }
        if (total != null) {
            Text("Subtotal dos produtos: ${Rules.money(subtotal!!)}")
            Text("Total da compra: ${Rules.money(total)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { contentDescription = "Total da compra" })
        } else Text("Preencha os preços, quantidades, frete e taxas para calcular o total.", style = MaterialTheme.typography.bodySmall)
        Text("Frete e taxas são rateados pelo valor dos itens. Após salvar uma compra nova ou alterada, você pode revisar a margem e o preço de venda dos produtos. O preço só muda ao salvar essa revisão. O estoque continua manual.",
            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Justify, modifier = Modifier.fillMaxWidth())
        ErrorText(error)
    }
    if (picker) ProductPicker(data.products, data.categories, lines, purchase = true, onClose = { picker = false }) { selected ->
        lines = selected; quantities = emptyMap(); prices = prices.filterKeys { id -> selected.any { it.productId == id } }; picker = false
    }
    if (notice) AlertDialog(onDismissRequest = { notice = false }, title = { Text("Nenhum produto disponível") },
        text = { Text("Não há produtos a adicionar. Cadastre primeiro na aba Produtos.") },
        confirmButton = { TextButton(onClick = { notice = false }) { Text("Entendi") } })
}
