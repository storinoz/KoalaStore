package br.com.koalasale

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpTip(label: String, text: String, modifier: Modifier = Modifier) {
    val state = rememberTooltipState()
    val scope = rememberCoroutineScope()
    Box(modifier.size(32.dp)) {
    TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(text) } }, state = state, focusable = false) {
        IconButton(onClick = { scope.launch { state.show() } }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Info, "Dica: $label", Modifier.size(16.dp))
        }
    }
    }
}

@Composable
fun ThemeIcon(mode: ThemeMode, description: String?) {
    Icon(painterResource(when(mode) {
        ThemeMode.LIGHT -> R.drawable.ic_sun
        ThemeMode.DARK -> R.drawable.ic_moon
        ThemeMode.SYSTEM -> R.drawable.ic_sun_moon
    }), description, tint = if (mode == ThemeMode.SYSTEM) Color.Unspecified else LocalContentColor.current,
        modifier = Modifier.size(28.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, raw: String, onChange: (String) -> Unit, required: Boolean = false, info: Boolean = false) {
    var open by rememberSaveable { mutableStateOf(false) }
    val invalid = raw.isNotEmpty() && runCatching { InputMasks.date(raw) }.isFailure
    Box(Modifier.fillMaxWidth()) {
    OutlinedTextField(value = raw, onValueChange = { onChange(InputMasks.digits(it, 8)) },
        label = { Text(if (required) "$label *" else "$label (opcional)") },
        placeholder = { Text("dd/mm/aaaa") }, singleLine = true,
        visualTransformation = InputMasks.dateMask,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = invalid,
        supportingText = if (info && !invalid) null else { { Text(if (invalid) "Informe uma data válida e completa: dd/mm/aaaa."
            else if (required) "Obrigatória · dd/mm/aaaa" else "Se preenchida, deve ser válida · dd/mm/aaaa") } },
        trailingIcon = { IconButton(onClick = { open = true }) { Icon(Icons.Default.DateRange, "Calendário: $label") } },
        modifier = Modifier.fillMaxWidth())
    if (info) HelpTip(label, if (required) "Obrigatória. Digite dd/mm/aaaa ou use o calendário." else "Opcional. Se preenchida, use uma data válida em dd/mm/aaaa.", Modifier.align(Alignment.TopEnd))
    }
    if (open) {
        val iso = runCatching { InputMasks.date(raw) }.getOrNull() ?: LocalDate.now().toString()
        val state = rememberDatePickerState(initialSelectedDateMillis = InputMasks.dateMillis(iso), yearRange = 1..9999)
        DatePickerDialog(onDismissRequest = { open = false },
            confirmButton = { TextButton(enabled = state.selectedDateMillis != null, onClick = {
                state.selectedDateMillis?.let { onChange(InputMasks.digits(Rules.dateLabel(InputMasks.dateFromMillis(it)), 8)) }
                open = false
            }) { Text("Confirmar") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } }) {
            DatePicker(state = state)
        }
    }
}

@Composable
fun QuantityStepper(name: String, raw: String, onChange: (String) -> Unit, compact: Boolean = false) {
    val number = raw.toIntOrNull()
    Surface(shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp,
        if (number == null || number !in 1..Rules.MAX_QUANTITY) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline),
        modifier = Modifier.width(if (compact) 140.dp else 184.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(enabled = number == null || number != 1, onClick = {
                onChange(((number ?: 2) - 1).coerceIn(1, Rules.MAX_QUANTITY).toString())
            }, modifier = if (compact) Modifier.size(40.dp) else Modifier) {
                Icon(painterResource(R.drawable.ic_minus), "Diminuir quantidade de $name", Modifier.size(if (compact) 16.dp else 18.dp))
            }
            BasicTextField(value = raw, onValueChange = { onChange(InputMasks.digits(it, 7)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center),
                modifier = Modifier.weight(1f).semantics { contentDescription = "Quantidade de $name" })
            IconButton(enabled = number == null || number < Rules.MAX_QUANTITY, onClick = {
                onChange(((number ?: 0) + 1).coerceIn(1, Rules.MAX_QUANTITY).toString())
            }, modifier = if (compact) Modifier.size(40.dp) else Modifier) {
                Icon(Icons.Default.Add, "Aumentar quantidade de $name", Modifier.size(if (compact) 16.dp else 18.dp))
            }
        }
    }
}

enum class BudgetStatus(val code: Int, val description: String, val color: Color, val ink: Color, val icon: Int) {
    PENDING(0, "Não pago e não entregue", Color(0xFFB3261E), Color.White, R.drawable.ic_pending),
    PAID(1, "Pago e não entregue", Color(0xFFFFB74D), Color(0xFF202428), R.drawable.ic_paid),
    DELIVERED(2, "Pago e entregue", Color(0xFF2E7D32), Color.White, R.drawable.ic_delivered);
    companion object { fun from(code: Int) = entries.firstOrNull { it.code == code } ?: PENDING }
}

@Composable
fun StatusIndicator(status: Int) {
    val appearance = BudgetStatus.from(status)
    var explain by remember { mutableStateOf(false) }
    Surface(onClick = { explain = true }, color = appearance.color, shape = CircleShape,
        modifier = Modifier.size(48.dp).semantics { contentDescription = appearance.description }) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(appearance.icon), null, tint = appearance.ink, modifier = Modifier.size(22.dp))
        }
    }
    if (explain) AlertDialog(onDismissRequest = { explain = false }, title = { Text("Status do orçamento") },
        text = { Text(appearance.description) },
        confirmButton = { TextButton(onClick = { explain = false }) { Text("Entendi") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusSelector(selected: Int, onSelect: (Int) -> Unit) {
    Column {
        Text("Status", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().semantics { selectableGroup() }, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            BudgetStatus.entries.forEach { status ->
                val tooltip = rememberTooltipState()
                val scope = rememberCoroutineScope()
                TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                    tooltip = { PlainTooltip { Text(status.description) } }, state = tooltip, focusable = false) {
                Surface(onClick = { onSelect(status.code); scope.launch { tooltip.show() } }, color = status.color, shape = CircleShape,
                    border = BorderStroke(if (selected == status.code) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.size(48.dp).semantics {
                        role = Role.RadioButton; this.selected = selected == status.code
                        contentDescription = status.description
                    }) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(painterResource(status.icon), null, tint = status.ink, modifier = Modifier.size(28.dp))
                    }
                }
                }
            }
        }
    }
}

@Composable
fun BudgetCard(budget: QuoteWithItems, customer: String, onEdit: () -> Unit, onDelete: () -> Unit,
    onComplete: () -> Unit, onShare: () -> Unit) {
    var expanded by rememberSaveable(budget.quote.id) { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(customer, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onShare) { Icon(Icons.AutoMirrored.Filled.Send, "Enviar orçamento #${budget.quote.id}") }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Editar orçamento #${budget.quote.id}") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Excluir orçamento #${budget.quote.id}") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Venda: ${Rules.dateLabel(budget.quote.saleDate).ifEmpty { "Não informada" }}", style = MaterialTheme.typography.bodySmall)
                    Text(Rules.money(budget.total), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                StatusIndicator(budget.quote.status)
                if (budget.quote.status != BudgetStatus.DELIVERED.code) IconButton(onClick = onComplete) {
                    Icon(painterResource(R.drawable.ic_delivered), "Marcar orçamento #${budget.quote.id} como pago e entregue", tint = MaterialTheme.colorScheme.primary)
                }
                ExpandButton(expanded, "produtos do orçamento #${budget.quote.id}") { expanded = !expanded }
            }
            if (expanded) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Orçamento #${budget.quote.id}", style = MaterialTheme.typography.bodySmall)
                Text("${budget.items.size} produto(s) · ${budget.items.sumOf { it.quantity.toLong() }} unidade(s)", style = MaterialTheme.typography.bodySmall)
                Text("Pagamento: ${Rules.dateLabel(budget.quote.paymentDate).ifEmpty { "Não informado" }}", style = MaterialTheme.typography.bodySmall)
                if (budget.quote.adjustmentType != 0) {
                    Text("Subtotal: ${Rules.money(budget.subtotal)}", style = MaterialTheme.typography.bodySmall)
                    Text("${QuotePricing.label(budget.quote)}: ${if (budget.quote.adjustmentType == 1) "−" else "+"}${Rules.money(kotlin.math.abs(budget.amounts.adjustment))}", style = MaterialTheme.typography.bodySmall)
                }
                budget.items.sortedBy { it.position }.forEach { item ->
                    Column(Modifier.padding(top = 8.dp)) {
                        Text("${item.quantity} × ${item.productName}", fontWeight = FontWeight.SemiBold)
                        Text("${Rules.money(item.unitPriceCents)} / un. · ${Rules.money(item.unitPriceCents * item.quantity)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun ExpandButton(expanded: Boolean, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.semantics { stateDescription = if (expanded) "Expandido" else "Recolhido" }) {
        Icon(painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more), "${if (expanded) "Recolher" else "Expandir"} $label")
    }
}
