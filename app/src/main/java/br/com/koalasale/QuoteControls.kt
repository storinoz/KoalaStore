package br.com.koalasale

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdjustmentBox(subtotal: Long, type: Int, percent: Boolean, raw: String,
    onType: (Int) -> Unit, onPercent: (Boolean) -> Unit, onValue: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Desconto ou taxa", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdjustmentType.entries.forEach { option ->
                    FilterChip(selected = type == option.code, onClick = { onType(option.code) }, label = { Text(option.label) })
                }
            }
            if (type != 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !percent, onClick = { onPercent(false) }, label = { Text("R$") })
                    FilterChip(selected = percent, onClick = { onPercent(true) }, label = { Text("%") })
                }
                val value = InputMasks.cents(raw)
                val issue = if (raw.isNotEmpty()) runCatching {
                    require(value != null) { "Informe um valor válido." }
                    QuotePricing.calculate(subtotal, type, percent, value)
                }.exceptionOrNull()?.message else null
                OutlinedTextField(value = raw, onValueChange = { onValue(InputMasks.digits(it, 9)) },
                    label = { Text(if (percent) "Percentual" else "Valor do ajuste") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = InputMasks.moneyMask,
                    prefix = if (!percent) ({ Text("R$ ") }) else null,
                    suffix = if (percent) ({ Text("%") }) else null,
                    supportingText = { Text(issue ?: if (percent) "Digite 1250 para 12,50%. Base: subtotal dos componentes."
                        else "Digite 250 para R$ 2,50. Base: subtotal dos componentes.") },
                    isError = issue != null, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun PricingSummary(subtotal: Long, type: Int, percent: Boolean, raw: String) {
    val value = if (type == 0) 0L else InputMasks.cents(raw)
    val amounts = value?.let { runCatching { QuotePricing.calculate(subtotal, type, type != 0 && percent, it) }.getOrNull() }
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Subtotal"); Text(Rules.money(subtotal))
            }
            if (amounts == null) Text("Confira o desconto ou a taxa para calcular o total.", color = MaterialTheme.colorScheme.error)
            else {
                if (type != 0) {
                    Text(AdjustmentType.entries.first { it.code == type }.label + if (percent) " (${Rules.priceInput(value!!)}%)" else "")
                    Text((if (type == 1) "−" else "+") + Rules.money(abs(amounts.adjustment)))
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total", fontWeight = FontWeight.Bold)
                    Text(Rules.money(amounts.total), fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.semantics { contentDescription = "Total final do orçamento" })
                }
            }
        }
    }
}

@Composable
fun SortControls(order: QuoteOrder, onOrder: (QuoteOrder) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(QuoteOrder.DATE_DESC, QuoteOrder.VALUE_DESC, QuoteOrder.CLIENT_ASC).forEach { option ->
            val active = order.group == option.group
            FilterChip(selected = active, onClick = { onOrder(if (active) order.toggle() else option) },
                label = { Text(if (active) order.shortLabel else when(option.group) { 0 -> "Data"; 1 -> "Valor"; else -> "Cliente" },
                    style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                modifier = Modifier.weight(1f).semantics {
                    contentDescription = "Ordenar por ${when(option.group) { 0 -> "data"; 1 -> "valor"; else -> "cliente" }}"
                })
        }
    }
}

@Composable
fun StatusFilter(status: Int?, onStatus: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    FilledTonalIconButton(onClick = { open = true }, modifier = Modifier.size(48.dp).semantics {
        contentDescription = "Filtrar por status"
        stateDescription = status?.let { BudgetStatus.from(it).description } ?: "Todos os status"
    }) {
        Icon(painterResource(status?.let { BudgetStatus.from(it).icon } ?: R.drawable.ic_filter), null,
            tint = status?.let { BudgetStatus.from(it).color } ?: MaterialTheme.colorScheme.onSecondaryContainer)
    }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text("Filtrar por status") },
        text = { Column {
            TextButton(onClick = { onStatus(null); open = false }, modifier = Modifier.fillMaxWidth()) { Icon(painterResource(R.drawable.ic_filter), null, tint = MaterialTheme.colorScheme.onSurface); Spacer(Modifier.width(10.dp)); Text("Todos os status", Modifier.weight(1f)) }
            BudgetStatus.entries.forEach { option ->
                TextButton(onClick = { onStatus(option.code); open = false }, modifier = Modifier.fillMaxWidth()) {
                    Icon(painterResource(option.icon), null, tint = option.color)
                    Spacer(Modifier.width(10.dp)); Text(option.description, Modifier.weight(1f),
                        fontWeight = if (status == option.code) FontWeight.Bold else FontWeight.Normal)
                }
            }
        } }, confirmButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } })
}
