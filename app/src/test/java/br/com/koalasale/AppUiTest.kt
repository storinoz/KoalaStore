package br.com.koalasale

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.json.JSONObject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class AppUiTest {
    private var fixtureCase = ""
    @OptIn(ExperimentalCoroutinesApi::class)
    @get:Rule(order = 0) val mainDispatcher = object : TestWatcher() {
        override fun starting(description: Description) {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            fixtureCase = description.methodName
        }
        override fun finished(description: Description) { Dispatchers.resetMain() }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    @Before fun seedThroughTheActivityDatabase() = runBlocking {
        // Seed the actual activity's Room connection. Robolectric on Windows can
        // retain a different application context than ApplicationProvider between scenarios.
        check(compose.activity.getDatabasePath("koalasale.db").path.contains("robolectric"))
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[SaleViewModel::class.java]
        val dao = vm.dao
        dao.purchases().first().forEach { dao.deletePurchase(it.purchase) }
        dao.quotes().first().forEach { dao.deleteQuote(it.quote) }
        dao.products().first().forEach { dao.deleteProduct(it) }
        dao.categories().first().forEach { dao.deleteCategory(it) }
        dao.customers().first().forEach { dao.deleteCustomer(it) }
        val category = dao.insertCategory(Category(name = "Teste"))
        val product = dao.insertProduct(Product(name = "Componente fictício", categoryId = category, priceCents = 1000))
        val databaseField = SaleViewModel::class.java.getDeclaredField("database").apply { isAccessible = true }
        val database = databaseField.get(vm) as SaleDatabase
        database.openHelper.writableDatabase.execSQL("DELETE FROM sqlite_sequence WHERE name IN ('quotes', 'purchases')")
        if (fixtureCase == "changingFiltersReturnsTheListToItsFirstQuote") {
            val customer = dao.insertCustomer(Customer(name = "Cliente fictício", phone = "5511912345678"))
            (1..20).forEach { dao.saveQuote(Quote(customerId = customer, saleDate = LocalDate.now().toString()), listOf(DraftLine(product, "Componente fictício", 1, 1000))) }
        }
        kotlinx.coroutines.withTimeout(15000) { vm.data.first { it.loaded && it.products.size == 1 && it.customers.size == (if (fixtureCase == "changingFiltersReturnsTheListToItsFirstQuote") 1 else 0) } }
        Unit
    }
    @After fun clearActivityViewModels() { compose.activity.viewModelStore.clear() }

    @Test fun homeIsQuotesAndThemeCanBeChangedFromSettings() {
        compose.waitUntil(timeoutMillis = 15000) { compose.onAllNodesWithContentDescription("Novo orçamento").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("By KoalaBoy").assertDoesNotExist()
        compose.onNodeWithText("Mês: Todos os meses").assertExists()
        compose.onNodeWithContentDescription("Novo orçamento").assertIsDisplayed()
        compose.onNodeWithContentDescription("Configurações").performClick()
        compose.onNodeWithText("Claro").performClick()
        assertEquals(ThemeMode.LIGHT, ThemePreferences.read(compose.activity))
        compose.onNodeWithText("Escuro").performClick()
        assertEquals(ThemeMode.DARK, ThemePreferences.read(compose.activity))
        compose.onNodeWithText("Versão ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
    }

    @Test fun quoteFlowMasksValidatesDatesAndQuantitiesAndShowsCollapsedStatusCard() {
        val saleLabel = Rules.dateLabel(LocalDate.now().withDayOfMonth(3).toString())
        val paymentLabel = Rules.dateLabel(LocalDate.now().withDayOfMonth(4).toString())
        compose.waitUntil(timeoutMillis = 15000) { compose.onAllNodesWithContentDescription("Novo orçamento").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Clientes", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("Novo cliente").performClick()
        compose.onNode(hasSetTextAction() and hasText("Nome")).performTextInput("Cliente interface")
        compose.onNode(hasSetTextAction() and hasText("Telefone / WhatsApp")).performTextInput("11912345678")
        compose.onNodeWithText("(11) 91234-5678").assertExists()
        compose.onNodeWithText("Salvar").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.waitUntil(timeoutMillis = 15000) { compose.onAllNodesWithContentDescription("Abrir WhatsApp de Cliente interface").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Abrir WhatsApp").assertDoesNotExist()
        compose.onNodeWithContentDescription("Orçamentos", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("Novo orçamento").performClick()
        compose.onNode(hasSetTextAction() and hasText("Data da venda *")).assertTextContains(Rules.dateLabel(LocalDate.now().toString()))
        compose.onNodeWithText("Selecionar").performClick()
        compose.onNodeWithText("Cliente interface").performClick()
        compose.onNodeWithText("Adicionar vários").performScrollTo().performClick()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "Componente fictício"
        compose.onNode(hasSetTextAction() and hasText("Buscar produto ou categoria")).performTextInput(name)
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        compose.onNodeWithContentDescription("Quantidade de $name").performTextReplacement("12")
        compose.onNodeWithContentDescription("Aumentar quantidade de $name").performClick()
        compose.onNodeWithText("13").assertExists()
        compose.onNodeWithContentDescription("Diminuir quantidade de $name").performClick()
        compose.onNodeWithContentDescription("Quantidade de $name").performTextReplacement("0")
        compose.onNodeWithText("Adicionar (1)").performClick()
        compose.onNodeWithText("As quantidades devem ser inteiras, entre 1 e 1.000.000.").assertExists()
        compose.onNodeWithContentDescription("Quantidade de $name").performTextReplacement("12")
        compose.onNodeWithText("Adicionar (1)").performClick()
        compose.onNode(hasSetTextAction() and hasText("Data da venda *")).performScrollTo().performTextReplacement(InputMasks.digits(saleLabel, 8))
        compose.onNodeWithText(saleLabel).assertExists()
        compose.onNode(hasSetTextAction() and hasText("Data do pagamento (opcional)")).performTextInput("31042026")
        compose.onNodeWithText("Salvar").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("Use uma data válida no formato DD/MM/AAAA.").assertExists()
        compose.onNode(hasSetTextAction() and hasText("Data do pagamento (opcional)")).performScrollTo().performTextReplacement(InputMasks.digits(paymentLabel, 8))
        compose.onNodeWithContentDescription("Calendário: Data da venda").performClick()
        compose.onNodeWithText("Confirmar").performClick()
        compose.onNodeWithContentDescription("Pago e não entregue").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Pago e não entregue").assertIsSelected()
        compose.onNodeWithText("Desconto").performScrollTo().performClick()
        compose.onNodeWithText("%").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("Percentual")).performScrollTo().performTextInput("1250")
        val base = 12 * 1000L
        val total = QuotePricing.calculate(base, 1, true, 1250).total
        compose.onNodeWithContentDescription("Total final do orçamento").assertTextEquals(Rules.money(total))
        compose.onNodeWithText("Salvar").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.waitUntil(timeoutMillis = 15000) { compose.onAllNodesWithContentDescription("Expandir produtos do orçamento #1").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(timeoutMillis = 15000) { compose.onAllNodesWithText("Orçamento salvo.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Fechar aviso").performClick()
        compose.onNodeWithContentDescription("Expandir produtos do orçamento #1").performScrollTo()
        compose.onNodeWithContentDescription("Pago e não entregue").assertExists()
        compose.onNodeWithText(Rules.money(total)).assertExists()
        compose.onNodeWithText("1 produto(s) · 12 unidade(s)").assertDoesNotExist()
        compose.onNodeWithText("Venda: $saleLabel").assertExists()
        compose.onNodeWithText("Pagamento: $paymentLabel").assertDoesNotExist()
        compose.onNodeWithText("12 × $name").assertDoesNotExist()
        val expandBounds = compose.onNodeWithContentDescription("Expandir produtos do orçamento #1").fetchSemanticsNode().boundsInRoot
        val statusBounds = compose.onNodeWithContentDescription("Pago e não entregue").fetchSemanticsNode().boundsInRoot
        val completeBounds = compose.onNodeWithContentDescription("Marcar orçamento #1 como pago e entregue").fetchSemanticsNode().boundsInRoot
        assertTrue("Status e conclusão devem ficar à esquerda da expansão", statusBounds.right <= expandBounds.left && completeBounds.right <= expandBounds.left)
        val saleBounds = compose.onNodeWithText("Venda: $saleLabel").fetchSemanticsNode().boundsInRoot
        val priceBounds = compose.onNodeWithText(Rules.money(total)).fetchSemanticsNode().boundsInRoot
        assertTrue("Data da venda deve aparecer antes do preço", saleBounds.bottom <= priceBounds.top)
        // The add button now floats over the list. Scroll the card above it before tapping.
        compose.onNode(hasScrollToNodeAction()).performTouchInput { swipeUp() }
        compose.onNodeWithContentDescription("Expandir produtos do orçamento #1").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Recolher produtos do orçamento #1").assertExists()
        compose.onNodeWithText("12 × $name").assertExists()
        compose.onNodeWithContentDescription("Recolher produtos do orçamento #1").performScrollTo().performClick()
        compose.onNodeWithText("12 × $name").assertDoesNotExist()
        compose.onNodeWithContentDescription("Pago e não entregue").performScrollTo().performClick()
        compose.onNodeWithText("Status do orçamento").assertExists()
        compose.onNodeWithText("Pago e não entregue").assertExists()
        compose.onNodeWithText("Entendi").performClick()
        compose.onNodeWithContentDescription("Marcar orçamento #1 como pago e entregue").performScrollTo().performClick()
        compose.onNodeWithText("Confirmar conclusão?").assertExists()
        compose.onNodeWithText("Não").performClick()
        compose.onNodeWithContentDescription("Pago e não entregue").assertExists()
        compose.onNodeWithContentDescription("Marcar orçamento #1 como pago e entregue").performScrollTo().performClick()
        compose.onNodeWithText("Sim").performClick()
        compose.waitUntil(timeoutMillis = 15000) { compose.onAllNodesWithContentDescription("Pago e entregue").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(Rules.money(total)).assertExists()
        compose.onNodeWithContentDescription("Marcar orçamento #1 como pago e entregue").assertDoesNotExist()
        compose.onNodeWithContentDescription("Filtrar por status").performClick()
        compose.onNodeWithText("Pago e não entregue").performClick()
        compose.onNodeWithContentDescription("Expandir produtos do orçamento #1").assertDoesNotExist()
        compose.onNodeWithContentDescription("Filtrar por status").performClick()
        compose.onNodeWithText("Pago e entregue").performClick()
        compose.onNodeWithContentDescription("Expandir produtos do orçamento #1").assertExists()
        compose.onNodeWithContentDescription("Ordenar por valor").performClick()
        compose.onNodeWithText("Valor ↓").assertExists()
        compose.onNodeWithContentDescription("Ordenar por valor").performClick()
        compose.onNodeWithText("Valor ↑").assertExists()
        compose.onNodeWithContentDescription("Ordenar por cliente").performClick()
        compose.onNodeWithText("Cliente A–Z").assertExists()
        compose.onNodeWithContentDescription("Pago e entregue").assertExists()
    }

    @Test fun registeringProductInsideQuoteReturnsToPickerWithItSelected() {
        compose.waitUntil(timeoutMillis = 15000) { compose.onAllNodesWithContentDescription("Novo orçamento").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Novo orçamento").performClick()
        compose.onNodeWithText("Adicionar vários").performScrollTo().performClick()
        compose.onNodeWithText("Cadastrar produto").performClick()
        compose.onNode(hasSetTextAction() and hasText("Nome do produto")).performTextInput("Produto criado no orçamento")
        compose.onAllNodesWithText("Selecionar").onLast().performClick()
        compose.onNode(hasText("Teste") and hasClickAction()).performClick()
        compose.onNode(hasSetTextAction() and hasText("Preço unitário de venda")).performTextInput("250")
        compose.onNodeWithContentDescription("Salvar Novo produto").performClick()
        compose.waitUntil(timeoutMillis = 15000) {
            compose.onAllNodesWithText("Produto criado no orçamento").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Cadastrar produto").assertExists()
        compose.onNodeWithText("Adicionar (1)").assertExists()
    }

    @Test fun purchasesAndPerProductMarginCalculateRecommendationWithoutOverwritingSalePrice() {
        compose.waitUntil(timeoutMillis = 15000) { compose.onAllNodesWithContentDescription("Novo orçamento").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Categorias", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Produtos", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Categorias").performClick()
        compose.onNodeWithContentDescription("Nova categoria").performClick()
        compose.onNode(hasSetTextAction() and hasText("Nome da categoria")).performTextInput("Reposição especial")
        compose.onNodeWithText("Salvar").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.waitUntil(timeoutMillis = 15000) { compose.onAllNodesWithText("Categoria salva.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Fechar aviso").performClick()
        compose.onNodeWithContentDescription("Voltar aos produtos").performClick()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "Componente fictício"
        compose.onNode(hasSetTextAction() and hasText("Buscar produtos")).performTextInput(name)
        compose.onNodeWithContentDescription("Editar $name").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("Margem (%)")).performScrollTo().performTextReplacement("10000")
        compose.onNodeWithText("Salvar").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("Informe uma margem entre 0% e 99,99%.").assertExists()
        compose.onNode(hasSetTextAction() and hasText("Margem (%)")).performScrollTo().performTextReplacement("5000")
        compose.onNodeWithText("Salvar").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.waitUntil(timeoutMillis = 15000) { compose.onAllNodesWithText("Produto salvo.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Fechar aviso").performClick()
        compose.onNodeWithContentDescription("Compras", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("Nova compra").performClick()
        val dateFieldBounds = compose.onNode(hasSetTextAction() and hasText("Data da compra *")).fetchSemanticsNode().boundsInRoot
        val dateHelpBounds = compose.onNodeWithContentDescription("Dica: Data da compra", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Dica da data deve ficar no canto superior direito", dateHelpBounds.center.x > dateFieldBounds.center.x && dateHelpBounds.center.y < dateFieldBounds.center.y)
        compose.onNodeWithText("Salvar").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("Adicione pelo menos um produto cadastrado.").assertExists()
        compose.onNode(hasSetTextAction() and hasText("Empresa")).performScrollTo().performTextInput("Fornecedor interface")
        compose.onNodeWithText("Adicionar vários").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("Buscar produto ou categoria")).performTextInput(name)
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        compose.onNodeWithContentDescription("Quantidade de $name").performTextReplacement("2")
        compose.onNodeWithText("Adicionar (1)").performClick()
        compose.onNodeWithText("Salvar").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("Informe o preço unitário de compra de $name.").assertExists()
        compose.onNode(hasSetTextAction() and hasText("Preço unitário de compra")).performScrollTo().performTextInput("1000")
        compose.onNode(hasSetTextAction() and hasText("Data de recebimento (opcional)")).performScrollTo().performTextInput("31042026")
        compose.onNodeWithText("Salvar").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("Use uma data válida no formato DD/MM/AAAA.").assertExists()
        val receipt = Rules.dateLabel(LocalDate.now().plusDays(2).toString())
        compose.onNode(hasSetTextAction() and hasText("Data de recebimento (opcional)")).performScrollTo().performTextReplacement(InputMasks.digits(receipt, 8))
        compose.onNode(hasSetTextAction() and hasText("Frete")).performScrollTo().performTextReplacement("600")
        compose.onNode(hasSetTextAction() and hasText("Outras taxas")).performScrollTo().performTextReplacement("200")
        compose.onNodeWithContentDescription("Total da compra").assertTextEquals("Total da compra: " + Rules.money(2800))
        compose.onNodeWithText("Salvar").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.waitUntil(timeoutMillis = 15000) { compose.onAllNodesWithText("Atualizar preços").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Custo desta compra / un.: ${Rules.money(1400)}").assertExists()
        compose.onNodeWithText("Cancelar").performClick()
        compose.waitUntil(timeoutMillis = 15000) { compose.onAllNodesWithContentDescription("Expandir produtos da compra #1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("1 produto(s) · 2 unidade(s)").assertDoesNotExist()
        compose.onNodeWithText("Fornecedor interface").assertExists()
        compose.onNodeWithText("Recebimento: $receipt").assertDoesNotExist()
        compose.onNodeWithText("Data: ${Rules.dateLabel(LocalDate.now().toString())}").assertExists()
        compose.onNodeWithContentDescription("Fechar aviso").performClick()
        compose.onNodeWithContentDescription("Expandir produtos da compra #1").performScrollTo().performClick()
        compose.onNodeWithText("2 × $name").assertExists()
        compose.onNodeWithText("Valor dos produtos: ${Rules.money(2000)}").assertExists()
        compose.onNodeWithContentDescription("Produtos", useUnmergedTree = true).performClick()
        compose.onNode(hasSetTextAction() and hasText("Buscar produtos")).performTextInput(name)
        compose.onNodeWithText("Custo mínimo / un.: " + Rules.money(1400)).assertDoesNotExist()
        compose.onNodeWithText("Valor de venda recomendado: " + Rules.money(2800)).assertDoesNotExist()
        compose.onNodeWithText(Rules.money(1000L)).assertExists()
        compose.onNodeWithContentDescription("Editar $name").performScrollTo().performClick()
        compose.onNodeWithText("Custo mínimo / un.: " + Rules.money(1400)).assertExists()
        compose.onNodeWithText("Valor de venda recomendado: " + Rules.money(2800)).assertExists()
        // Disk persistence and reopening are covered by PurchaseDatabaseTest and CatalogMigrationTest.

    }

    @Test fun registeringCustomerInsideQuotePreservesDraftAndSharingOffersBothFormats() {
        compose.waitUntil(timeoutMillis = 15000) { compose.onAllNodesWithContentDescription("Novo orçamento").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Novo orçamento").performClick()
        val date = InputMasks.digits(Rules.dateLabel(LocalDate.now().toString()), 8)
        compose.onNode(hasSetTextAction() and hasText("Data da venda *")).performScrollTo().performTextInput(date)
        compose.onNodeWithText("Adicionar vários").performScrollTo().performClick()
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        compose.onNodeWithContentDescription("Quantidade de Componente fictício").performTextReplacement("3")
        compose.onNodeWithText("Adicionar (1)").performClick()
        compose.onNodeWithText("Cadastrar cliente").assertDoesNotExist()
        compose.onNodeWithText("Selecionar").performScrollTo().performClick()
        compose.onNodeWithText("Cadastrar cliente").performClick()
        compose.onNode(hasSetTextAction() and hasText("Nome")).performTextInput("Cliente do fluxo paralelo")
        compose.onNode(hasSetTextAction() and hasText("Telefone / WhatsApp")).performTextInput("11912345678")
        compose.onNodeWithContentDescription("Salvar Novo cliente").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.waitUntil(timeoutMillis = 15000) { compose.onAllNodesWithText("Cliente salvo.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Fechar aviso").performClick()
        compose.onNodeWithText("Cliente do fluxo paralelo").assertExists()
        compose.onNodeWithContentDescription("Quantidade de Componente fictício").performScrollTo().assertTextEquals("3")
        compose.onNodeWithText("Salvar").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.waitUntil(timeoutMillis = 15000) { compose.onAllNodesWithText("Orçamento salvo.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Fechar aviso").performClick()
        compose.waitUntil(timeoutMillis = 15000) { compose.onAllNodesWithContentDescription("Enviar orçamento #1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Expandir produtos do orçamento #1").performScrollTo()
        compose.onNodeWithText(Rules.money(3000)).assertExists()
        compose.onNodeWithContentDescription("Enviar orçamento #1").performClick()
        compose.onNodeWithText("Texto").assertExists()
        compose.onNodeWithText("Imagem").assertExists()
        val imageBounds = compose.onNodeWithText("Imagem").fetchSemanticsNode().boundsInRoot
        val textBounds = compose.onNodeWithText("Texto").fetchSemanticsNode().boundsInRoot
        val cancelBounds = compose.onNodeWithText("Cancelar").fetchSemanticsNode().boundsInRoot
        assertTrue("Imagem deve vir antes de Texto na mesma linha", imageBounds.right < textBounds.left && imageBounds.top == textBounds.top)
        assertTrue("Cancelar deve ficar abaixo dos formatos", cancelBounds.top >= imageBounds.bottom && cancelBounds.top >= textBounds.bottom)
        compose.onNodeWithText("Cancelar").performClick()
    }

    @Test fun purchasePriceReviewSavesEditsAndSkipsUnchangedPurchase() = runBlocking {
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[SaleViewModel::class.java]
        val dao = vm.dao
        val product = dao.products().first().single()
        val purchaseId = dao.savePurchase(Purchase(date = LocalDate.now().toString(), company = "Fornecedor teste"),
            listOf(DraftLine(product.id, product.name, 2, 300)))
        kotlinx.coroutines.withTimeout(15000) { vm.data.first { it.purchases.any { p -> p.purchase.id == purchaseId } } }
        compose.onNodeWithContentDescription("Compras", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("Editar compra #$purchaseId").performClick()
        compose.onNodeWithContentDescription("Salvar Compra #$purchaseId").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithContentDescription("Salvar Compra #$purchaseId").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Atualizar preços").assertDoesNotExist()
        compose.onNodeWithContentDescription("Editar compra #$purchaseId").performClick()
        compose.onNode(hasSetTextAction() and hasText("Empresa")).performTextReplacement("Fornecedor alterado")
        compose.onNodeWithContentDescription("Salvar Compra #$purchaseId").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("Atualizar preços").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Custo desta compra / un.: ${Rules.money(300)}").assertExists()
        compose.onNode(hasSetTextAction() and hasText("Empresa")).assertDoesNotExist()
        compose.onNode(hasSetTextAction() and hasText("Margem (%) de ${product.name}")).performScrollTo().performTextReplacement("10000")
        compose.onNodeWithContentDescription("Salvar Atualizar preços").performClick()
        compose.onNodeWithText("Informe uma margem entre 0% e 99,99% para ${product.name}.").performScrollTo().assertExists()
        compose.onNode(hasSetTextAction() and hasText("Margem (%) de ${product.name}")).performScrollTo().performTextReplacement("5000")
        compose.onNode(hasSetTextAction() and hasText("Preço de venda de ${product.name}")).performScrollTo().performTextReplacement("950")
        compose.onNodeWithContentDescription("Salvar Atualizar preços").performClick()
        kotlinx.coroutines.withTimeout(15000) { vm.data.first { it.products.single().priceCents == 950L } }
        val updated = dao.products().first().single()
        assertEquals(5000, updated.marginBasisPoints)
        assertEquals("Fornecedor alterado", dao.purchase(purchaseId)!!.purchase.company)
        compose.waitUntil(15000) { compose.onAllNodesWithText("Atualizar preços").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun customerRowsAreCompactAndProductQuantityStaysOnTheRight() = runBlocking {
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[SaleViewModel::class.java]
        val dao = vm.dao
        dao.insertCustomer(Customer(name = "Cliente Alfa", phone = "5511912345678"))
        dao.insertCustomer(Customer(name = "Cliente Beta", phone = "5511912345679"))
        val product = dao.products().first().single()
        val name = "Produto fictício com um nome bastante comprido para testar a quebra de linhas"
        dao.updateProduct(product.copy(name = name))
        kotlinx.coroutines.withTimeout(15000) { vm.data.first { it.customers.size == 2 && it.products.single().name == name } }
        compose.onNodeWithContentDescription("Novo orçamento").performClick()
        compose.onNodeWithText("Cadastrar cliente").assertDoesNotExist()
        compose.onNodeWithText("Selecionar").performClick()
        val first = compose.onNodeWithText("Cliente Alfa").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithText("Cliente Beta").fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        assertTrue("Linhas devem ficar a no máximo 40 dp de distância", (second.top - first.top) / density <= 40.5f)
        compose.onNodeWithText("Cadastrar cliente").assertHasClickAction()
        compose.onNodeWithText("Cliente Alfa").performClick()
        compose.onNodeWithText("Adicionar vários").performScrollTo().performClick()
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        val title = compose.onNodeWithText(name).fetchSemanticsNode().boundsInRoot
        val quantity = compose.onNodeWithContentDescription("Quantidade de $name").fetchSemanticsNode().boundsInRoot
        assertTrue("Quantidade deve ficar à direita do nome e na mesma linha", quantity.left >= title.right && quantity.center.y >= title.top && quantity.center.y <= title.bottom + 60 * density)
        compose.onNodeWithContentDescription("Quantidade de $name").performTextReplacement("1000000")
        compose.onNodeWithContentDescription("Aumentar quantidade de $name").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Diminuir quantidade de $name").performClick()
        compose.onNodeWithContentDescription("Quantidade de $name").assertTextEquals("999999")
        Unit
    }

    @Test fun productCategoryFilterCombinesWithSearchAndRecoversAfterCategoryDeletion() = runBlocking {
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[SaleViewModel::class.java]
        val dao = vm.dao
        val category = dao.insertCategory(Category(name = "Outra categoria"))
        val product = Product(name = "Sensor fictício", categoryId = category, priceCents = 2000)
        val productId = dao.insertProduct(product)
        kotlinx.coroutines.withTimeout(15000) { vm.data.first { it.products.size == 2 } }
        compose.onNodeWithContentDescription("Produtos", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Categoria: Todas as categorias").performClick()
        compose.onNode(hasText("Outra categoria") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("Sensor fictício").assertExists()
        compose.onNodeWithText("Componente fictício").assertDoesNotExist()
        compose.onNode(hasSetTextAction() and hasText("Buscar produtos")).assertExists()
        val searchField = compose.onNode(hasSetTextAction())
        searchField.performTextInput("Componente")
        compose.onNodeWithText("Nenhum resultado").assertExists()
        compose.onNodeWithText("Componente fictício").assertDoesNotExist()
        searchField.performTextReplacement("Sensor")
        compose.onNodeWithText("Sensor fictício").assertExists()
        searchField.performTextClearance()
        compose.onNodeWithText("Categoria: Outra categoria").performClick()
        compose.onNodeWithText("Todas as categorias").performClick()
        compose.onNodeWithText("Componente fictício").assertExists()
        compose.onNodeWithText("Categoria: Todas as categorias").performClick()
        compose.onNode(hasText("Outra categoria") and hasAnyAncestor(isDialog())).performClick()
        dao.deleteProduct(product.copy(id = productId))
        dao.deleteCategory(Category(id = category, name = "Outra categoria"))
        kotlinx.coroutines.withTimeout(15000) { vm.data.first { it.categories.none { group -> group.id == category } } }
        compose.waitUntil(timeoutMillis = 15000) { compose.onAllNodesWithText("Categoria: Todas as categorias").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Componente fictício").assertExists()
        compose.onNodeWithContentDescription("Novo produto").performClick()
        compose.onNode(hasSetTextAction() and hasText("Preço unitário de venda")).performScrollTo()
        val fieldBounds = compose.onNode(hasSetTextAction() and hasText("Preço unitário de venda")).fetchSemanticsNode().boundsInRoot
        val tipBounds = compose.onNodeWithContentDescription("Dica: Preço unitário de venda", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Dica do preço deve ficar no canto superior direito", tipBounds.center.x > fieldBounds.center.x && tipBounds.center.y < fieldBounds.center.y)
        Unit
    }

    @Test fun changingFiltersReturnsTheListToItsFirstQuote() {
        compose.waitUntil(timeoutMillis = 15000) { compose.onAllNodesWithContentDescription("Enviar orçamento #20").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasContentDescription("Enviar orçamento #1"))
        compose.onNodeWithContentDescription("Enviar orçamento #1").assertExists()
        compose.onNodeWithContentDescription("Filtrar por status").performClick()
        compose.onNodeWithText("Não pago e não entregue").performClick()
        compose.onNodeWithContentDescription("Enviar orçamento #20").assertExists()
        compose.onNodeWithContentDescription("Enviar orçamento #1").assertDoesNotExist()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasContentDescription("Enviar orçamento #1"))
        compose.onNodeWithContentDescription("Ordenar por valor").performClick()
        compose.onNodeWithContentDescription("Enviar orçamento #20").assertExists()
    }

}
