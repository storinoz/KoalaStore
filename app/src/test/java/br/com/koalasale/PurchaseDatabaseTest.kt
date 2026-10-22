package br.com.koalasale

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PurchaseDatabaseTest {
    private lateinit var context: Context
    private lateinit var db: SaleDatabase
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("purchases-test.db")
        db = SaleDatabase.create(context, "purchases-test.db")
        runBlocking { db.dao().insertCategory(Category(name = "Buzzer")) }
    }
    @After fun cleanup() { db.close(); context.deleteDatabase("purchases-test.db") }

    @Test fun priceReviewOnlyOpensForNewOrChangedPurchases() = runBlocking {
        val dao = db.dao()
        val id = dao.insertProduct(Product(name = "Produto teste", categoryId = dao.categories().first().first().id, priceCents = 500))
        val line = DraftLine(id, "Produto teste", 2, 100)
        val purchase = Purchase(company = "Fornecedor", date = "2026-10-21")
        val savedId = dao.savePurchaseForPriceReview(purchase, listOf(line))!!
        val saved = dao.purchase(savedId)!!
        assertNull(dao.savePurchaseForPriceReview(saved.purchase, saved.draftLines()))
        // Any purchase data change triggers the review, not only changes to prices.
        for (changed in listOf(saved.purchase.copy(company = "Outro fornecedor"),
            saved.purchase.copy(date = "2026-10-20"), saved.purchase.copy(receivedDate = "2026-10-22"),
            saved.purchase.copy(shippingCents = 50), saved.purchase.copy(feesCents = 25))) {
            assertEquals(savedId, dao.savePurchaseForPriceReview(changed, listOf(line)))
            assertNull(dao.savePurchaseForPriceReview(changed, listOf(line)))
        }
        assertEquals(savedId, dao.savePurchaseForPriceReview(saved.purchase, listOf(line.copy(quantity = 3))))
        assertEquals(savedId, dao.savePurchaseForPriceReview(saved.purchase, listOf(line.copy(priceCents = 200))))
        assertEquals(500L, dao.products().first().single().priceCents)
    }

    @Test fun priceReviewUpdatesOnlyPurchasedProductPricesAndMarginsAtomically() = runBlocking {
        val dao = db.dao()
        val category = dao.categories().first().first().id
        val first = Product(name = "Primeiro", categoryId = category, priceCents = 999, stock = 8, legacySourceId = "local-1")
        val second = Product(name = "Segundo", categoryId = category, priceCents = 555, stock = 3)
        val firstId = dao.insertProduct(first)
        val secondId = dao.insertProduct(second)
        val purchaseId = dao.savePurchase(Purchase(date = "2026-10-21", company = "Fornecedor"),
            listOf(DraftLine(firstId, first.name, 2, 100), DraftLine(secondId, second.name, 1, 200)))
        val purchase = dao.purchase(purchaseId)
        val client = dao.insertCustomer(Customer(name = "Cliente teste", phone = "5511912345678"))
        val quoteId = dao.saveQuote(Quote(customerId = client, saleDate = "2026-10-21"), listOf(DraftLine(firstId, first.name, 1, 999)))
        val quote = dao.quote(quoteId)
        val valid = ProductPriceUpdate(firstId, 5000, 1200)
        try {
            dao.updatePurchaseProductPrices(purchaseId, listOf(valid, ProductPriceUpdate(secondId, 10000, 600)))
            fail("Margem inválida deve impedir qualquer atualização")
        } catch (_: IllegalArgumentException) { }
        assertEquals(999L, dao.products().first().first { it.id == firstId }.priceCents)
        try {
            dao.updatePurchaseProductPrices(purchaseId, listOf(valid))
            fail("Revisão deve corresponder aos produtos da compra")
        } catch (_: IllegalArgumentException) { }
        dao.updatePurchaseProductPrices(purchaseId, listOf(valid, ProductPriceUpdate(secondId, 0, 0)))
        db.close(); db = SaleDatabase.create(context, "purchases-test.db")
        assertEquals(first.copy(id = firstId, priceCents = 1200, marginBasisPoints = 5000), db.dao().products().first().first { it.id == firstId })
        assertEquals(second.copy(id = secondId, priceCents = 0, marginBasisPoints = 0), db.dao().products().first().first { it.id == secondId })
        assertEquals(purchase, db.dao().purchase(purchaseId))
        assertEquals(quote, db.dao().quote(quoteId))
    }

    @Test fun savesEditsReopensAndDeletesPurchasesWithoutChangingSalePriceOrStock() = runBlocking {
        val dao = db.dao()
        val category = dao.categories().first().first().id
        val product = Product(name = "CI", categoryId = category, priceCents = 999, stock = 7, marginBasisPoints = 5000)
        val id = dao.insertProduct(product)
        val purchaseId = dao.savePurchase(Purchase(company = "Fornecedor teste", date = "2026-10-03", shippingCents = 200, feesCents = 100), listOf(DraftLine(id,"CI",3,100)))
        assertEquals(400L, PurchasePricing.averageCosts(dao.purchases().first()).getValue(id).recommended(5000))
        val saved = dao.purchase(purchaseId)!!
        dao.savePurchase(saved.purchase.copy(shippingCents = 300, company = "Outra empresa", receivedDate = "2026-10-05"), listOf(DraftLine(id,"CI",2,200)))
        db.close(); db = SaleDatabase.create(context, "purchases-test.db")
        assertEquals(800L, db.dao().purchase(purchaseId)!!.total)
        assertEquals("Outra empresa", db.dao().purchase(purchaseId)!!.purchase.company)
        assertEquals("2026-10-05", db.dao().purchase(purchaseId)!!.purchase.receivedDate)
        assertEquals(product.copy(id = id), db.dao().products().first().single())
        assertEquals(800L, PurchasePricing.averageCosts(db.dao().purchases().first()).getValue(id).recommended(5000))
        db.dao().deletePurchase(db.dao().purchase(purchaseId)!!.purchase)
        assertTrue(db.dao().purchases().first().isEmpty())
        assertTrue(PurchasePricing.averageCosts(db.dao().purchases().first()).isEmpty())
        assertEquals(product.copy(id = id), db.dao().products().first().single())
        db.dao().deleteProduct(product.copy(id = id))
    }

    @Test fun onlyExistingProductsCanBePurchasedAndFailedEditRollsBackWholeTransaction() = runBlocking {
        val dao = db.dao()
        val id = dao.insertProduct(Product(name = "CI", categoryId = dao.categories().first().first().id, priceCents = 500))
        val purchaseId = dao.savePurchase(Purchase(company = "Fornecedor teste", date = "2026-10-03"), listOf(DraftLine(id,"CI",1,100)))
        val original = dao.purchase(purchaseId)!!
        try { dao.savePurchase(original.purchase.copy(feesCents = 300), listOf(DraftLine(999,"Inexistente",1,100))); fail("Produto inexistente") }
        catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals(original, dao.purchase(purchaseId))
        try { dao.deleteProduct(dao.products().first().single()); fail("Produto usado em compra") }
        catch (_: android.database.sqlite.SQLiteConstraintException) { }
        try { dao.savePurchase(Purchase(company = "Fornecedor teste", date = "2026-10-04"), listOf(DraftLine(999,"Inexistente",1,100))); fail("Produto inexistente") }
        catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals(listOf(original), dao.purchases().first())
    }

    @Test fun incompleteOrInvalidPurchasesNeverReplaceExistingData() = runBlocking {
        val dao = db.dao()
        val id = dao.insertProduct(Product(name = "CI", categoryId = dao.categories().first().first().id, priceCents = 500))
        val line = DraftLine(id,"CI",1,100)
        val purchaseId = dao.savePurchase(Purchase(company = "Fornecedor teste", date = "2026-10-03"), listOf(line))
        val original = dao.purchase(purchaseId)!!
        for (lines in listOf(emptyList(), listOf(line, line), listOf(line.copy(quantity = 0)), listOf(line.copy(priceCents = -1)))) {
            try { dao.savePurchase(original.purchase, lines); fail("Compra inválida") } catch (_: IllegalArgumentException) { }
        }
        try { dao.savePurchase(original.purchase.copy(shippingCents = -1), listOf(line)); fail("Frete negativo") }
        catch (_: IllegalArgumentException) { }
        try { dao.savePurchase(original.purchase.copy(date = "2026-02-30"), listOf(line)); fail("Data inválida") }
        catch (_: java.time.format.DateTimeParseException) { }
        try { dao.savePurchase(original.purchase.copy(company = "   "), listOf(line)); fail("Empresa vazia") }
        catch (_: IllegalArgumentException) { }
        try { dao.savePurchase(original.purchase.copy(receivedDate = "2026-02-30"), listOf(line)); fail("Recebimento inválido") }
        catch (_: java.time.format.DateTimeParseException) { }
        assertNull(original.purchase.receivedDate)
        assertEquals(original, dao.purchase(purchaseId))
    }
    @Test fun multipleProductsPersistSeparateCostsAndMargins() = runBlocking {
        val dao = db.dao()
        val category = dao.categories().first().first().id
        val first = dao.insertProduct(Product(name = "CI", categoryId = category, priceCents = 1000))
        val second = dao.insertProduct(Product(name = "Pino", categoryId = category, priceCents = 200, marginBasisPoints = 5000))
        val id = dao.savePurchase(Purchase(company = "Fornecedor teste", date = "2026-10-03", shippingCents = 200, feesCents = 100),
            listOf(DraftLine(first,"CI",2,300), DraftLine(second,"Pino",4,100)))
        db.close(); db = SaleDatabase.create(context, "purchases-test.db")
        val purchase = db.dao().purchase(id)!!
        assertEquals(2, purchase.items.size)
        assertEquals(listOf(first, second), purchase.draftLines().map { it.productId })
        val costs = PurchasePricing.averageCosts(listOf(purchase))
        assertEquals(390L, costs.getValue(first).unitCostCents)
        assertEquals(650L, costs.getValue(first).recommended(4000))
        assertEquals(130L, costs.getValue(second).unitCostCents)
        assertEquals(260L, costs.getValue(second).recommended(5000))
        assertEquals(listOf(1000L, 200L), db.dao().products().first().map { it.priceCents })
    }

}
