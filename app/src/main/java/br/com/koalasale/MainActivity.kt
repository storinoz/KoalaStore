package br.com.koalasale

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.net.toUri
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { KoalaStoreTheme { mode, setMode -> KoalaStore(mode, setMode) } }
    }
}

private sealed interface Editor {
    data class Client(val value: Customer? = null) : Editor
    data class Group(val value: Category? = null) : Editor
    data class Item(val value: Product? = null) : Editor
    data class Budget(val value: QuoteWithItems? = null) : Editor
    data class Restock(val value: PurchaseWithItems? = null) : Editor
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KoalaStore(mode: ThemeMode, setMode: (ThemeMode) -> Unit, vm: SaleViewModel = viewModel()) {
    val data by vm.data.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(3) }
    var search by rememberSaveable { mutableStateOf("") }
    var month by rememberSaveable { mutableLongStateOf(0L) }
    var order by rememberSaveable { mutableStateOf(QuoteOrder.DATE_DESC) }
    var statusFilter by rememberSaveable { mutableStateOf<Int?>(null) }
    var productCategory by rememberSaveable { mutableLongStateOf(0L) }
    LaunchedEffect(data.loaded, data.categories, productCategory) {
        if (data.loaded && productCategory != 0L && data.categories.none { it.id == productCategory }) productCategory = 0L
    }
    var completeId by rememberSaveable { mutableLongStateOf(0) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) { while (true) { today = LocalDate.now(); delay(60_000) } }
    var themeDialog by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(tab, month, search, order, statusFilter, productCategory) { listState.scrollToItem(0) }
    var shareId by rememberSaveable { mutableLongStateOf(0) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importCatalog(uri)
    }
    BackHandler(enabled = themeDialog) { themeDialog = false }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collectLatest { snackbar.showSnackbar(it) } }
    var editorType by rememberSaveable { mutableIntStateOf(0) }
    var editorId by rememberSaveable { mutableLongStateOf(0) }
    var priceReviewId by rememberSaveable { mutableLongStateOf(0) }
    fun edit(value: Editor?) {
        editorType = when(value) { is Editor.Client -> 1; is Editor.Group -> 2; is Editor.Item -> 3; is Editor.Budget -> 4; is Editor.Restock -> 5; null -> 0 }
        editorId = when(value) { is Editor.Client -> value.value?.id; is Editor.Group -> value.value?.id;
            is Editor.Item -> value.value?.id; is Editor.Budget -> value.value?.quote?.id; is Editor.Restock -> value.value?.purchase?.id; null -> null } ?: 0
    }
    val editor = if (!data.loaded) null else when(editorType) {
        1 -> Editor.Client(data.customers.find { it.id == editorId })
        2 -> Editor.Group(data.categories.find { it.id == editorId })
        3 -> Editor.Item(data.products.find { it.id == editorId })
        4 -> Editor.Budget(data.quotes.find { it.quote.id == editorId })
        5 -> Editor.Restock(data.purchases.find { it.purchase.id == editorId })
        else -> null
    }
    var delete by remember { mutableStateOf<Editor?>(null) }
    BackHandler(enabled = tab == 1 && editorType == 0 && !themeDialog && delete == null) { tab = 2; search = "" }
    val labels = listOf("Clientes", "Categorias", "Produtos", "Orçamentos", "Compras")
    val icons = listOf(Icons.Default.Person, Icons.AutoMirrored.Filled.List, Icons.Default.ShoppingCart, Icons.Default.DateRange)
    val context = LocalContext.current
    val close = { edit(null) }
    val createLabel = when(tab) { 0 -> "Novo cliente"; 1 -> "Nova categoria"; 2 -> "Novo produto"; 4 -> "Nova compra"; else -> "Novo orçamento" }
    val quotes = QuoteListing.apply(data.quotes, data.customers, month, search, order, statusFilter)

    val costs = remember(data.purchases) { PurchasePricing.averageCosts(data.purchases) }
    fun newEditor(): Editor = when (tab) { 0 -> Editor.Client(); 1 -> Editor.Group(); 2 -> Editor.Item(); 4 -> Editor.Restock(); else -> Editor.Budget() }
    Scaffold(
        topBar = { TopAppBar(title = { Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.koalastore_logo), "Logo KoalaStore", Modifier.size(44.dp))
            Spacer(Modifier.width(10.dp))
            Text(if (themeDialog) "Configurações" else labels[tab], fontWeight = FontWeight.Bold)
        } }, actions = {
            if (themeDialog) IconButton(onClick = { themeDialog = false }) { Icon(painterResource(R.drawable.ic_back), "Voltar às vendas") }
            else IconButton(onClick = { themeDialog = true }) { Icon(Icons.Default.Settings, "Configurações") }
        }) },
        snackbarHost = { SnackbarHost(snackbar) { notice ->
            Snackbar(dismissAction = {
                IconButton(onClick = { notice.dismiss() }) { Icon(Icons.Default.Close, "Fechar aviso") }
            }) { Text(notice.visuals.message) }
        } },
        bottomBar = { if (!themeDialog) NavigationBar {
            listOf(3, 0, 2, 4).forEach { index -> NavigationBarItem(selected = tab == index || (index == 2 && tab == 1),
                onClick = { tab = index; search = "" }, icon = {
                    if (index == 4) Icon(painterResource(R.drawable.ic_purchase), labels[index]) else Icon(icons[index], labels[index])
                },
                label = { Text(labels[index], maxLines = 1, style = MaterialTheme.typography.labelSmall) }) }
        } },
        floatingActionButton = { if (data.loaded && !themeDialog) TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = { PlainTooltip { Text(createLabel) } }, state = rememberTooltipState()) {
            FloatingActionButton(onClick = { edit(newEditor()) }, modifier = Modifier.semantics { contentDescription = createLabel }) {
                Icon(Icons.Default.Add, null)
            }
        } }
    ) { padding ->
      if (themeDialog) {
          Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
          Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
              verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Text("Aparência", style = MaterialTheme.typography.titleLarge)
              ThemeMode.entries.forEach { option ->
                  Row(Modifier.fillMaxWidth().clickable { setMode(option) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                      ThemeIcon(option, null)
                      Text(option.label, Modifier.weight(1f).padding(horizontal = 12.dp))
                      RadioButton(mode == option, onClick = { setMode(option) })
                  }
              }
              HorizontalDivider()
              Text("Dados locais", style = MaterialTheme.typography.titleMedium)
              Text("Os cadastros ficam neste dispositivo. Instalações novas começam sem dados.", style = MaterialTheme.typography.bodySmall)
              OutlinedButton(enabled = !busy, onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }) { Text("Importar catálogo local") }
              Text("Selecione um catálogo JSON do seu dispositivo. Os cadastros existentes e seus preços são preservados.", style = MaterialTheme.typography.bodySmall)
          }
              Spacer(Modifier.height(12.dp))
              HorizontalDivider()
              Text("Versão ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp))
          }
      } else Box(Modifier.fillMaxSize().padding(padding)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            if (tab == 1) IconButton(onClick = { tab = 2; search = "" }) { Icon(painterResource(R.drawable.ic_back), "Voltar aos produtos") }
            if (tab == 2) FilledTonalButton(onClick = { tab = 1; search = "" }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("Categorias") }
            if (tab == 3) {
                MonthFilter(month, data.quotes, today) { month = it }
                Spacer(Modifier.height(4.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
              OutlinedTextField(value = search, onValueChange = { search = it }, singleLine = true,
                placeholder = { Text(if (tab == 3) "Buscar cliente ou produto" else "Buscar ${labels[tab].lowercase()}") },
                leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(18.dp),
                modifier = Modifier.weight(1f))
              if (tab == 3) {
                  Spacer(Modifier.width(8.dp))
                  StatusFilter(statusFilter) { statusFilter = it }
              }
            }
            Spacer(Modifier.height(4.dp))
            if (tab == 2) Choice("Filtrar por categoria", productCategory,
                listOf(0L to "Todas as categorias") + data.categories.map { it.id to it.name },
                compact = true, compactPrefix = "Categoria: ") { productCategory = it }
            if (tab == 3) {
                SortControls(order) { order = it }
                Spacer(Modifier.height(4.dp))
            }
            if (!data.loaded) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                val clients = data.customers.filter { it.name.contains(search, true) || it.phone.contains(search) }
                val groups = data.categories.filter { it.name.contains(search, true) }
                val products = data.products.filter {
                    (productCategory == 0L || it.categoryId == productCategory) && (it.name.contains(search, true) ||
                        data.categories.find { group -> group.id == it.categoryId }?.name?.contains(search, true) == true)
                }
                val purchases = data.purchases.filter { purchase -> search.isBlank() || purchase.items.any { it.productName.contains(search, true) } ||
                    purchase.purchase.company.contains(search, true) || Rules.dateLabel(purchase.purchase.date).contains(search) || purchase.purchase.id.toString() == search }
                val empty = when(tab) { 0 -> clients.isEmpty(); 1 -> groups.isEmpty(); 2 -> products.isEmpty(); 4 -> purchases.isEmpty(); else -> quotes.isEmpty() }
                if (empty) EmptyState(if (search.isNotBlank() || (tab == 2 && productCategory != 0L) || (tab == 3 && (month != 0L || statusFilter != null))) "Nenhum resultado" else "Tudo começa com um cadastro",
                    if (search.isNotBlank()) "Tente outro nome ou código."
                    else if (tab == 2 && productCategory != 0L) "Não há produtos para esta categoria. Selecione outra categoria ou cadastre um produto."
                    else if (tab == 3 && (month != 0L || statusFilter != null)) "Não há orçamentos para os filtros selecionados. Confira o mês e o status."
                    else when(tab) {
                        0 -> "Cadastre seus clientes para montar orçamentos."
                        1 -> "Organize os componentes por categoria."
                        2 -> "Cadastre um componente, sua categoria e preço."
                        4 -> "Registre uma reposição para calcular o custo e o preço recomendado dos seus produtos."
                        else -> "Escolha um cliente e adicione os produtos desejados."
                    })
                else LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
                    when(tab) {
                        0 -> items(clients, key = { it.id }) { client ->
                            RecordCard(client.name, InputMasks.phoneText("+${client.phone}"), onEdit = { edit(Editor.Client(client)) },
                                onDelete = { delete = Editor.Client(client) }, headerAction = {
                                IconButton(onClick = {
                                    try { context.startActivity(Intent(Intent.ACTION_VIEW, "https://wa.me/${client.phone}".toUri())) }
                                    catch (_: ActivityNotFoundException) { Toast.makeText(context, "Instale o WhatsApp ou um navegador para abrir o contato.", Toast.LENGTH_LONG).show() }
                                }) { Icon(painterResource(R.drawable.ic_whatsapp), "Abrir WhatsApp de ${client.name}",
                                    tint = androidx.compose.ui.graphics.Color(0xFF25D366), modifier = Modifier.size(24.dp)) }
                            })
                        }
                        1 -> items(groups, key = { it.id }) { group ->
                            RecordCard(group.name, "${data.products.count { it.categoryId == group.id }} produto(s)",
                                onEdit = { edit(Editor.Group(group)) }, onDelete = { delete = Editor.Group(group) })
                        }
                        2 -> items(products, key = { it.id }) { product ->
                            RecordCard(product.name, data.categories.find { it.id == product.categoryId }?.name.orEmpty(),
                                onEdit = { edit(Editor.Item(product)) }, onDelete = { delete = Editor.Item(product) }) {
                                Text(Rules.money(product.priceCents), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                        4 -> items(purchases, key = { it.purchase.id }) { purchase ->
                            PurchaseCard(purchase, onEdit = { edit(Editor.Restock(purchase)) }, onDelete = { delete = Editor.Restock(purchase) })
                        }
                        else -> items(quotes, key = { it.quote.id }) { budget ->
                            val client = data.customers.find { it.id == budget.quote.customerId }
                            BudgetCard(budget, client?.name ?: "Cliente", onEdit = { edit(Editor.Budget(budget)) },
                                onDelete = { delete = Editor.Budget(budget) }, onComplete = { completeId = budget.quote.id }, onShare = { shareId = budget.quote.id })
                        }
                    }
                }
            }
        }
      }
    }
    when(val current = editor) {
        is Editor.Client -> ClientEditor(current.value, busy, close) { value -> vm.save(close, "Cliente salvo.") {
            if (value.id == 0L) insertCustomer(value) else updateCustomer(value)
        } }
        is Editor.Group -> CategoryEditor(current.value, busy, close) { value -> vm.save(close, "Categoria salva.") {
            if (value.id == 0L) insertCategory(value) else updateCategory(value)
        } }
        is Editor.Item -> ProductEditor(current.value, data.categories, costs[current.value?.id], busy, close) { value -> vm.save(close, "Produto salvo.") {
            val stored = value.copy(legacySourceId = current.value?.legacySourceId)
            if (value.id == 0L) insertProduct(stored) else updateProduct(stored)
        } }
        is Editor.Budget -> QuoteEditor(current.value, data, busy, close,
            onCreateCustomer = { customer, done ->
                vm.save({}, "Cliente salvo.") { done(insertCustomer(customer)) }
            },
            onCreateProduct = { product, done ->
                vm.save({}, "Produto salvo.") { done(insertProduct(product)) }
            }
        ) { value, lines -> vm.save(close, "Orçamento salvo.") { saveQuote(value, lines) } }
        is Editor.Restock -> PurchaseEditor(current.value, data, busy, close) { value, lines ->
            vm.save(close, "Compra salva. Custos recalculados.") {
                priceReviewId = savePurchaseForPriceReview(value, lines) ?: 0L
            }
        }
        null -> Unit
    }
    data.purchases.find { it.purchase.id == priceReviewId }?.let { purchase ->
        key(priceReviewId) {
            PurchasePriceEditor(purchase, data.products, busy, { priceReviewId = 0 }) { updates ->
                vm.save({ priceReviewId = 0 }, "Preços e margens atualizados.") {
                    updatePurchaseProductPrices(purchase.purchase.id, updates)
                }
            }
        }
    }
    delete?.let { target ->
        AlertDialog(onDismissRequest = { if (!busy) delete = null }, title = { Text("Excluir cadastro?") },
            text = { Text("Esta ação remove o registro. Cadastros usados em outros registros só podem ser excluídos após remover as referências.") },
            confirmButton = { TextButton(enabled = !busy, onClick = { vm.save({ delete = null }, "Registro excluído.") {
                when(target) {
                    is Editor.Client -> deleteCustomer(target.value!!)
                    is Editor.Group -> deleteCategory(target.value!!)
                    is Editor.Item -> deleteProduct(target.value!!)
                    is Editor.Budget -> deleteQuote(target.value!!.quote)
                    is Editor.Restock -> deletePurchase(target.value!!.purchase)
                }
            } }) { Text("Excluir", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(enabled = !busy, onClick = { delete = null }) { Text("Cancelar") } })
    }
    error?.let { AlertDialog(onDismissRequest = { vm.error.value = null }, title = { Text("Confira o cadastro") },
        text = { Text(it) }, confirmButton = { TextButton(onClick = { vm.error.value = null }) { Text("Entendi") } }) }
    if (completeId != 0L) AlertDialog(onDismissRequest = { if (!busy) completeId = 0 },
        title = { Text("Confirmar conclusão?") }, text = { Text("Marcar o orçamento #$completeId como pago e entregue?") },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            val id = completeId
            vm.save({ completeId = 0 }, "Orçamento marcado como pago e entregue.") {
                require(completeQuote(id) == 1) { "Orçamento não encontrado." }
            }
        }) { Text("Sim") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { completeId = 0 }) { Text("Não") } })
    if (shareId != 0L) {
        val budget = data.quotes.find { it.quote.id == shareId }
        val client = data.customers.find { it.id == budget?.quote?.customerId }
        AlertDialog(onDismissRequest = { shareId = 0 }, title = { Text("Enviar orçamento") },
            text = { Text("Escolha o formato para enviar a ${client?.name ?: "cliente"} pelo WhatsApp. Revise o destinatário antes de enviar.") },
            confirmButton = { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TextButton(onClick = {
                        if (budget != null && client != null) QuoteSharing.send(context, budget, client, true)
                        else Toast.makeText(context, "Não foi possível localizar o orçamento ou o cliente.", Toast.LENGTH_LONG).show()
                        shareId = 0
                    }) { Text("Imagem") }
                    TextButton(onClick = {
                        if (budget != null && client != null) QuoteSharing.send(context, budget, client, false)
                        else Toast.makeText(context, "Não foi possível localizar o orçamento ou o cliente.", Toast.LENGTH_LONG).show()
                        shareId = 0
                    }) { Text("Texto") }
                }
                TextButton(onClick = { shareId = 0 }) { Text("Cancelar") }
            } })
    }
}

@Composable
private fun MonthFilter(selected: Long, quotes: List<QuoteWithItems>, today: LocalDate, onChoose: (Long) -> Unit) {
    val formatter = DateTimeFormatter.ofPattern("MMMM 'de' uuuu", Locale.forLanguageTag("pt-BR"))
    val options = listOf(0L to "Todos os meses", -1L to "Sem data da venda") +
        MonthOptions.available(quotes.map { it.quote.saleDate }, today).map { (it.year * 100L + it.monthValue) to it.format(formatter) }
    Choice("Filtrar por mês da venda", selected, options, compact = true, onChoose = onChoose)
}

@Composable
private fun EmptyState(title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(top = 64.dp, start = 16.dp, end = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.AutoMirrored.Filled.List, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(text, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun RecordCard(title: String, subtitle: String, onEdit: () -> Unit, onDelete: () -> Unit,
    headerAction: @Composable () -> Unit = {},
    body: @Composable ColumnScope.() -> Unit = {}) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    body()
                }
                headerAction()
                IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Edit, "Editar $title", modifier = Modifier.size(24.dp)) }
                IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Delete, "Excluir $title", modifier = Modifier.size(24.dp)) }
            }

        }
    }
}

@Composable
internal fun EditorFrame(title: String, busy: Boolean, onClose: () -> Unit, onSave: () -> Unit,
    fullHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = { if (!busy) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val sizing = if (fullHeight) Modifier.fillMaxHeight(0.94f) else Modifier.heightIn(max = 760.dp)
        Surface(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().padding(12.dp).imePadding().then(sizing)) {
            Column(Modifier.padding(20.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Column(Modifier.weight(1f, fill = fullHeight).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(enabled = !busy, onClick = onClose) { Text("Cancelar") }
                    Button(enabled = !busy, onClick = onSave, modifier = Modifier.semantics { contentDescription = "Salvar $title" }) { Text(if (busy) "Salvando…" else "Salvar") }
                }
            }
        }
    }
}

@Composable
internal fun Field(label: String, value: String, onChange: (String) -> Unit, type: KeyboardType = KeyboardType.Text,
    hint: String? = null, mask: VisualTransformation = VisualTransformation.None,
    prefix: String? = null, info: Boolean = false, modifier: Modifier = Modifier.fillMaxWidth()) {
    Box(modifier) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type), modifier = Modifier.fillMaxWidth(),
        visualTransformation = mask, prefix = prefix?.let { { Text(it) } },
        supportingText = if (info) null else hint?.let { { Text(it) } })
    if (info && hint != null) HelpTip(label, hint, Modifier.align(Alignment.TopEnd))
    }
}

@Composable internal fun ErrorText(value: String?) { value?.let { Text(it, color = MaterialTheme.colorScheme.error) } }

@Composable
private fun ClientEditor(value: Customer?, busy: Boolean, onClose: () -> Unit, onSave: (Customer) -> Unit) {
    var name by rememberSaveable { mutableStateOf(value?.name.orEmpty()) }
    var phone by rememberSaveable { mutableStateOf(value?.phone?.let { "+$it" }.orEmpty()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    EditorFrame(if (value == null) "Novo cliente" else "Editar cliente", busy, onClose, {
        val normalized = Rules.phone(phone)
        error = when { name.isBlank() -> "Informe o nome do cliente."
            normalized == null -> "Informe o telefone com DDD. Para outro país, use + e o código do país."
            else -> null }
        if (error == null) onSave(Customer(value?.id ?: 0, name.trim(), normalized!!))
    }) {
        Field("Nome", name, { name = it })
        Field("Telefone / WhatsApp", phone, { phone = InputMasks.phoneRaw(it) }, KeyboardType.Phone,
            "Ex.: (11) 91234-5678. Para outro país, comece com +.", InputMasks.phoneMask)
        ErrorText(error)
    }
}

@Composable
private fun CategoryEditor(value: Category?, busy: Boolean, onClose: () -> Unit, onSave: (Category) -> Unit) {
    var name by rememberSaveable { mutableStateOf(value?.name.orEmpty()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    EditorFrame(if (value == null) "Nova categoria" else "Editar categoria", busy, onClose, {
        error = if (name.isBlank()) "Informe o nome da categoria." else null
        if (error == null) onSave(Category(value?.id ?: 0, name.trim()))
    }) { Field("Nome da categoria", name, { name = it }); ErrorText(error) }
}

@Composable
private fun Choice(label: String, selected: Long?, options: List<Pair<Long, String>>, compact: Boolean = false, compactPrefix: String = "Mês: ",
    onCreate: (() -> Unit)? = null, itemVerticalPadding: androidx.compose.ui.unit.Dp = 14.dp, onChoose: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    Column {
        if (!compact) Text(label, style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { query = ""; open = true }, modifier = Modifier.fillMaxWidth()) {
            Text((if (compact) compactPrefix else "") + (options.find { it.first == selected }?.second ?: "Selecionar"), modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, null)
        }
    }
    if (open) Dialog(onDismissRequest = { open = false }) {
        Surface(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(18.dp).height(420.dp)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Field("Buscar", query, { query = it })
                if (options.isEmpty()) Text("Não há opções cadastradas. Faça o cadastro na aba correspondente.", Modifier.padding(vertical = 14.dp))
                else if (options.none { it.second.contains(query, true) }) Text("Nenhum resultado para esta busca.", Modifier.padding(vertical = 14.dp))
                LazyColumn(Modifier.weight(1f)) {
                    items(options.filter { it.second.contains(query, true) }, key = { it.first }) { item ->
                        Row(Modifier.fillMaxWidth().then(if (onCreate != null)
                            Modifier.selectable(selected = selected == item.first, role = androidx.compose.ui.semantics.Role.RadioButton,
                                onClick = { onChoose(item.first); open = false }).heightIn(min = 40.dp).padding(horizontal = 8.dp)
                            else Modifier.clickable { onChoose(item.first); open = false }.padding(vertical = itemVerticalPadding)),
                            verticalAlignment = Alignment.CenterVertically) {
                            if (onCreate == null) RadioButton(selected == item.first, onClick = { onChoose(item.first); open = false })
                            Text(item.second, modifier = Modifier.weight(1f))
                        }
                    }
                }
                if (onCreate != null) OutlinedButton(onClick = { open = false; onCreate() }) { Text("Cadastrar cliente") }
                TextButton(onClick = { open = false }, modifier = Modifier.align(Alignment.End)) { Text("Fechar") }
            }
        }
    }
}

@Composable
private fun ProductEditor(value: Product?, categories: List<Category>, cost: ProductCost?, busy: Boolean, onClose: () -> Unit, onSave: (Product) -> Unit) {
    var name by rememberSaveable { mutableStateOf(value?.name.orEmpty()) }
    var category by rememberSaveable { mutableStateOf(value?.categoryId) }
    var price by rememberSaveable { mutableStateOf(value?.priceCents?.toString().orEmpty()) }
    var stock by rememberSaveable { mutableStateOf(value?.stock?.toString().orEmpty()) }
    var margin by rememberSaveable { mutableStateOf((value?.marginBasisPoints ?: 4000).toString()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    EditorFrame(if (value == null) "Novo produto" else "Editar produto", busy, onClose, {
        val cents = InputMasks.cents(price)
        val stockNumber = stock.toIntOrNull()
        error = when {
            name.isBlank() -> "Informe o nome do produto."
            categories.none { it.id == category } -> "Selecione uma categoria cadastrada."
            cents == null -> "Informe um preço válido, como 2,50 (máximo R$ 1.000.000,00)."
            margin.toIntOrNull()?.let { it !in 0..9999 } != false -> "Informe uma margem entre 0% e 99,99%."
            stock.isNotBlank() && (stockNumber == null || stockNumber !in 0..Rules.MAX_QUANTITY) -> "O estoque deve ser inteiro, entre 0 e 1.000.000."
            else -> null
        }
        if (error == null) onSave(Product(value?.id ?: 0, name.trim(), category!!, cents!!, stockNumber, marginBasisPoints = margin.toInt()))
    }, fullHeight = true) {
        Field("Nome do produto", name, { name = it })
        Choice("Categoria", category, categories.map { it.id to it.name }) { category = it }
        if (categories.isEmpty()) Text("Abra Categorias na tela Produtos para cadastrar uma categoria.")
        Field("Preço unitário de venda", price, { price = InputMasks.digits(it, 9) }, KeyboardType.Number,
            "Digite 250 para R$ 2,50; os últimos dois dígitos são centavos.", InputMasks.moneyMask, "R$ ", info = true)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { ProductCostSummary(cost, margin.toIntOrNull()?.coerceIn(0, 9999) ?: 4000) }
            Field("Margem (%)", margin, { margin = InputMasks.digits(it, 5) }, KeyboardType.Number,
                "Margem de lucro desejada: ${Rules.priceInput((margin.toLongOrNull() ?: 4000))}%. Padrão 40%; digite 4000 para 40,00%. Inclui frete e taxas no custo. Margem sobre a venda, menor que 100%.",
                InputMasks.moneyMask, info = true, modifier = Modifier.width(130.dp))
        }
        Field("Quantidade em estoque (opcional)", stock, { stock = it }, KeyboardType.Number,
            "Atualização manual; orçamentos não alteram o estoque.", info = true)
        ErrorText(error)
    }
}

@Composable
private fun QuoteEditor(value: QuoteWithItems?, data: AppData, busy: Boolean, onClose: () -> Unit,
    onCreateCustomer: (Customer, (Long) -> Unit) -> Unit,
    onCreateProduct: (Product, (Long) -> Unit) -> Unit,
    onSave: (Quote, List<DraftLine>) -> Unit) {
    var createCustomer by rememberSaveable { mutableStateOf(false) }
    var customer by rememberSaveable { mutableStateOf(value?.quote?.customerId) }
    var saleDate by rememberSaveable {
        val initialDate = value?.quote?.saleDate ?: LocalDate.now().toString()
        mutableStateOf(InputMasks.digits(Rules.dateLabel(initialDate), 8))
    }
    var paymentDate by rememberSaveable { mutableStateOf(InputMasks.digits(Rules.dateLabel(value?.quote?.paymentDate), 8)) }
    var status by rememberSaveable { mutableIntStateOf(value?.quote?.status ?: 0) }
    var adjustmentType by rememberSaveable { mutableIntStateOf(value?.quote?.adjustmentType ?: 0) }
    var adjustmentIsPercent by rememberSaveable { mutableStateOf(value?.quote?.adjustmentIsPercent ?: false) }
    var adjustmentValue by rememberSaveable { mutableStateOf(value?.quote?.adjustmentValue?.toString().orEmpty()) }
    val lineSaver = listSaver<List<DraftLine>, Any>(
        save = { list -> list.flatMap { listOf(it.productId, it.name, it.quantity, it.priceCents) } },
        restore = { list -> list.chunked(4).map { DraftLine(it[0] as Long, it[1] as String, it[2] as Int, it[3] as Long) } }
    )
    var lines by rememberSaveable(stateSaver = lineSaver) { mutableStateOf(value?.draftLines().orEmpty()) }
    val quantitySaver = listSaver<Map<Long, String>, Any>(
        save = { map -> map.flatMap { listOf(it.key, it.value) } },
        restore = { list -> list.chunked(2).associate { (it[0] as Long) to (it[1] as String) } })
    var quantities by rememberSaveable(stateSaver = quantitySaver) { mutableStateOf(emptyMap<Long, String>()) }
    var picker by rememberSaveable { mutableStateOf(false) }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    EditorFrame(if (value == null) "Novo orçamento" else "Orçamento #${value.quote.id}", busy, onClose, {
        try {
            require(data.customers.any { it.id == customer }) { "Selecione um cliente cadastrado." }
            require(lines.isNotEmpty()) { "Adicione pelo menos um produto." }
            require(lines.all { line -> quantities[line.productId]?.let { it.toIntOrNull()?.let { n -> n in 1..Rules.MAX_QUANTITY } ?: false } ?: true }) {
                "As quantidades devem ser inteiras, entre 1 e 1.000.000."
            }
            val sale = InputMasks.date(saleDate, required = true)
            val payment = InputMasks.date(paymentDate)
            val adjustment = if (adjustmentType == 0) 0L else InputMasks.cents(adjustmentValue)
            require(adjustment != null) { "Informe o valor ou percentual do desconto/taxa." }
            QuotePricing.calculate(Rules.total(lines), adjustmentType, adjustmentType != 0 && adjustmentIsPercent, adjustment)
            error = null
            onSave(Quote(value?.quote?.id ?: 0, customer!!, sale, payment, status,
                adjustmentType, adjustmentType != 0 && adjustmentIsPercent, adjustment), lines)
        } catch (_: java.time.format.DateTimeParseException) { error = "Use uma data válida no formato DD/MM/AAAA." }
        catch (e: IllegalArgumentException) { error = e.message }
    }, fullHeight = true) {
        Choice("Cliente", customer, data.customers.map { it.id to it.name }, onCreate = { createCustomer = true }, itemVerticalPadding = 4.dp) { customer = it }
        if (data.customers.isEmpty()) Text("Abra Selecionar para cadastrar um cliente.")
        DateField("Data da venda", saleDate, { saleDate = it }, required = true)
        DateField("Data do pagamento", paymentDate, { paymentDate = it })
        StatusSelector(status) { status = it }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Produtos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                if (data.products.isEmpty()) notice = "Não há produtos a adicionar. Cadastre um componente na aba Produtos."
                else picker = true
            }) { Text("Adicionar vários") }
        }
        if (data.products.isEmpty()) Text("Cadastre os componentes na aba Produtos.")
        else if (lines.isEmpty()) Text("Toque em Adicionar vários para escolher os componentes e as quantidades.")
        lines.forEach { line ->
            key(line.productId) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(line.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            IconButton(onClick = { lines = lines.filterNot { it.productId == line.productId } }) { Icon(Icons.Default.Close, "Remover ${line.name}") }
                        }
                        Text("${Rules.money(line.priceCents)} por unidade", style = MaterialTheme.typography.bodySmall)
                        QuantityStepper(line.name, quantities[line.productId] ?: line.quantity.toString(), {
                            quantities = quantities + (line.productId to it)
                            it.toIntOrNull()?.takeIf { count -> count in 1..Rules.MAX_QUANTITY }?.let { count ->
                                lines = lines.map { if (it.productId == line.productId) it.copy(quantity = count) else it }
                            }
                        })
                        Text("Subtotal: ${Rules.money(line.priceCents * line.quantity)}", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        AdjustmentBox(Rules.total(lines), adjustmentType, adjustmentIsPercent, adjustmentValue,
            onType = { adjustmentType = it }, onPercent = { adjustmentIsPercent = it }, onValue = { adjustmentValue = it })
        PricingSummary(Rules.total(lines), adjustmentType, adjustmentIsPercent, adjustmentValue)
        ErrorText(error)
    }
    if (createCustomer) ClientEditor(null, busy, { if (!busy) createCustomer = false }) { newCustomer ->
        onCreateCustomer(newCustomer) { id -> customer = id; createCustomer = false }
    }
    if (picker) ProductPicker(data.products, data.categories, lines, busy = busy, onCreateProduct = onCreateProduct, onClose = { picker = false }) {
        lines = it; quantities = emptyMap(); picker = false
    }
    notice?.let { AlertDialog(onDismissRequest = { notice = null }, title = { Text("Nenhum produto disponível") },
        text = { Text(it) }, confirmButton = { TextButton(onClick = { notice = null }) { Text("Entendi") } }) }
}

@Composable
internal fun ProductPicker(products: List<Product>, categories: List<Category>, current: List<DraftLine>, purchase: Boolean = false,
    busy: Boolean = false, onCreateProduct: ((Product, (Long) -> Unit) -> Unit)? = null,
    onClose: () -> Unit, onAccept: (List<DraftLine>) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    val quantitySaver = listSaver<androidx.compose.runtime.snapshots.SnapshotStateMap<Long, String>, Any>(
        save = { map -> map.flatMap { listOf(it.key, it.value) } },
        restore = { list -> mutableStateMapOf<Long, String>().apply { list.chunked(2).forEach { put(it[0] as Long, it[1] as String) } } }
    )
    val quantities = rememberSaveable(saver = quantitySaver) { mutableStateMapOf<Long, String>().apply { current.forEach { put(it.productId, it.quantity.toString()) } } }
    var error by remember { mutableStateOf<String?>(null) }
    var createProduct by rememberSaveable { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().padding(12.dp).imePadding().heightIn(max = 720.dp)) {
            Column(Modifier.padding(18.dp)) {
                Text("Selecionar produtos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Marque vários e informe as quantidades.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                Field("Buscar produto ou categoria", search, { search = it })
                Spacer(Modifier.height(12.dp))
                val filtered = products.filter { product -> product.name.contains(search, true) || categories.find { it.id == product.categoryId }?.name?.contains(search, true) == true }
                if (filtered.isEmpty()) Text("Não há produtos para esta busca. Tente outro nome ou categoria.", Modifier.padding(vertical = 12.dp))
                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(filtered, key = { it.id }) { product ->
                        Column(Modifier.fillMaxWidth()) {
                          Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = quantities.containsKey(product.id), onCheckedChange = {
                                if (it) quantities[product.id] = "1" else quantities.remove(product.id)
                            })
                            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(product.name, fontWeight = FontWeight.SemiBold,
                                    maxLines = if (quantities.containsKey(product.id)) 2 else Int.MAX_VALUE,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text(categories.find { it.id == product.categoryId }?.name.orEmpty(), style = MaterialTheme.typography.bodySmall)
                                val storedPrice = current.find { it.productId == product.id }?.priceCents ?: product.priceCents
                                if (!purchase) Text(Rules.money(storedPrice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                            if (quantities.containsKey(product.id)) QuantityStepper(product.name, quantities[product.id].orEmpty(),
                                { quantities[product.id] = it }, compact = true)
                          }
                        }
                    }
                }
                ErrorText(error)
                if (onCreateProduct != null) TextButton(onClick = { createProduct = true }) { Text("Cadastrar produto") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClose) { Text("Cancelar") }
                    Button(onClick = {
                        val invalid = quantities.any { (_, quantity) -> quantity.toIntOrNull()?.let { it !in 1..Rules.MAX_QUANTITY } ?: true }
                        if (invalid) error = "As quantidades devem ser inteiras, entre 1 e 1.000.000."
                        else {
                            val selected = products.filter { quantities.containsKey(it.id) }.map { product ->
                                val old = current.find { it.productId == product.id }
                                DraftLine(product.id, old?.name ?: product.name, quantities.getValue(product.id).toInt(), old?.priceCents ?: if (purchase) 0L else product.priceCents)
                            }
                            onAccept(selected)
                        }
                    }) { Text("Adicionar (${quantities.size})") }
                }
            }
        }
    }
    if (createProduct && onCreateProduct != null) ProductEditor(null, categories, null, busy, { if (!busy) createProduct = false }) { product ->
        onCreateProduct(product) { id ->
            quantities[id] = "1"
            createProduct = false
        }
    }
}
