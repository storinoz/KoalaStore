package br.com.koalasale

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DatabaseTest {
    private lateinit var context: Context
    private lateinit var db: SaleDatabase
    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("test.db")
        db = SaleDatabase.create(context, "test.db")
        runBlocking { db.dao().insertCategory(Category(name = "Buzzer")) }
    }
    @After fun tearDown() { db.close(); context.deleteDatabase("test.db") }

    @Test fun multiProductQuoteSurvivesReopenAndKeepsOriginalPrice() = runBlocking {
        val dao = db.dao()
        assertEquals(1, dao.categories().first().size)
        val category = dao.categories().first().first().id
        val customer = dao.insertCustomer(Customer(name = "Cliente de teste", phone = "5511912345678"))
        val first = dao.insertProduct(Product(name = "Resistor 10k", categoryId = category, priceCents = 25, stock = 50))
        val second = dao.insertProduct(Product(name = "Buzzer", categoryId = category, priceCents = 500))
        val quoteId = dao.saveQuote(Quote(customerId = customer, saleDate = "2026-10-03"),
            listOf(DraftLine(first, "Resistor 10k", 10, 25), DraftLine(second, "Buzzer", 2, 500)))
        dao.updateProduct(Product(first, "Resistor renomeado", category, 100, 50))
        db.close()
        db = SaleDatabase.create(context, "test.db")
        val saved = db.dao().quote(quoteId)!!
        assertEquals(1250L, saved.total)
        assertEquals("Resistor 10k", saved.draftLines().first().name)
        assertEquals(25L, saved.draftLines().first().priceCents)
        assertEquals(50, db.dao().products().first().first { it.id == first }.stock)
        assertEquals("2026-10-03", saved.quote.saleDate)
        assertNull(saved.quote.paymentDate)
    }

    @Test fun failedEditRollsBackAllQuoteChanges() = runBlocking {
        val dao = db.dao()
        val category = dao.categories().first().first().id
        val customer = dao.insertCustomer(Customer(name = "Teste", phone = "5511912345678"))
        val product = dao.insertProduct(Product(name = "CI", categoryId = category, priceCents = 100))
        val id = dao.saveQuote(Quote(customerId = customer, saleDate = "2026-10-02"), listOf(DraftLine(product, "CI", 2, 100)))
        try {
            dao.saveQuote(Quote(id, customer, "2026-10-03"), listOf(DraftLine(999999L, "Inexistente", 1, 500)))
            fail("A referência inválida deve impedir a edição.")
        } catch (_: android.database.sqlite.SQLiteConstraintException) { }
        val saved = dao.quote(id)!!
        assertEquals(200L, saved.total)
        assertEquals("2026-10-02", saved.quote.saleDate)
        assertEquals(product, saved.items.single().productId)
    }

    @Test fun deletionProtectsReferencesAndCascadesQuoteItems() = runBlocking {
        val dao = db.dao()
        val category = dao.categories().first().first()
        val customer = Customer(dao.insertCustomer(Customer(name = "Teste", phone = "5511912345678")), "Teste", "5511912345678")
        val product = Product(dao.insertProduct(Product(name = "CI", categoryId = category.id, priceCents = 100)), "CI", category.id, 100)
        val id = dao.saveQuote(Quote(customerId = customer.id, saleDate = "2026-10-03"), listOf(DraftLine(product.id, "CI", 1, 100)))
        try { dao.deleteCustomer(customer); fail("Cliente em uso não deve ser excluído.") }
        catch (_: android.database.sqlite.SQLiteConstraintException) { }
        try { dao.deleteProduct(product); fail("Produto em uso não deve ser excluído.") }
        catch (_: android.database.sqlite.SQLiteConstraintException) { }
        dao.deleteQuote(Quote(id, customer.id))
        dao.deleteProduct(product)
        dao.deleteCategory(category)
        dao.deleteCustomer(customer)
        assertTrue(dao.quotes().first().isEmpty())
    }

    @Test fun categoryRenameIsNotResetOnReopen() = runBlocking {
        val dao = db.dao()
        val category = dao.categories().first().first { it.name == "Buzzer" }
        dao.updateCategory(Category(category.id, "Buzzers"))
        db.close()
        db = SaleDatabase.create(context, "test.db")
        val names = db.dao().categories().first().map { it.name }
        assertTrue("Buzzers" in names)
        assertFalse("Buzzer" in names)
    }

    @Test fun savingWithoutSaleDateOrWithInvalidStatusDoesNotWriteAnything() = runBlocking {
        val dao = db.dao()
        val category = dao.categories().first().first().id
        val customer = dao.insertCustomer(Customer(name = "Teste", phone = "5511912345678"))
        val product = dao.insertProduct(Product(name = "CI", categoryId = category, priceCents = 100))
        val line = listOf(DraftLine(product, "CI", 1, 100))
        for (value in listOf(Quote(customerId = customer), Quote(customerId = customer, saleDate = "2026-10-03", status = 8))) {
            try { dao.saveQuote(value, line); fail("Dados inválidos devem ser recusados.") }
            catch (_: IllegalArgumentException) { }
        }
        assertTrue(dao.quotes().first().isEmpty())
    }
}
