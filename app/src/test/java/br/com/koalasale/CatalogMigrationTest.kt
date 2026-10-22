package br.com.koalasale

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONArray
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CatalogMigrationTest {
    private lateinit var context: Context
    private var db: SaleDatabase? = null
    @Before fun setup() { context = ApplicationProvider.getApplicationContext(); context.deleteDatabase("upgrade.db") }
    @After fun cleanup() { db?.close(); context.deleteDatabase("upgrade.db") }

    private val syntheticCatalog = """{"catalogId":"synthetic-fixture","products":[{"sourceId":"fixture-1","name":"Componente fictício","category":"Teste","priceCents":100}]}"""
    @Test fun freshInstallationHasNoBundledBusinessDataAndOptionalLocalImportIsOnceOnly() = runBlocking {
        db = SaleDatabase.create(context, "upgrade.db")
        val dao = db!!.dao()
        assertTrue(dao.products().first().isEmpty())
        assertTrue(dao.categories().first().isEmpty())
        assertTrue(dao.customers().first().isEmpty())
        assertTrue(dao.quotes().first().isEmpty())
        assertTrue(dao.purchases().first().isEmpty())
        assertFalse(context.assets.list("")!!.contains("legacy_catalog.json"))
        CatalogImporter.importOnce(db!!.openHelper.writableDatabase, syntheticCatalog)
        assertEquals(1, dao.products().first().size)
        val product = dao.products().first().single()
        dao.updateProduct(product.copy(priceCents = 321))
        CatalogImporter.importOnce(db!!.openHelper.writableDatabase, syntheticCatalog)
        assertEquals(321L, dao.products().first().single().priceCents)
        db!!.close(); db = SaleDatabase.create(context, "upgrade.db")
        assertEquals(321L, db!!.dao().products().first().single().priceCents)
        db!!.dao().deleteProduct(db!!.dao().products().first().single())
        CatalogImporter.importOnce(db!!.openHelper.writableDatabase, syntheticCatalog)
        assertTrue(db!!.dao().products().first().isEmpty())
    }

    @Test fun version1UpgradePreservesCustomersQuoteAndUserEditedPrice() = runBlocking {
        val catalog = JSONObject(syntheticCatalog)
        val first = catalog.getJSONArray("products").getJSONObject(0)
        val category = first.getString("category")
        val name = first.getString("name")
        val schemaStream = javaClass.classLoader!!.getResourceAsStream("br.com.koalasale.SaleDatabase/1.json")!!
        val schema = JSONObject(schemaStream.bufferedReader().use { it.readText() }).getJSONObject("database")
        val file = context.getDatabasePath("upgrade.db")
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: JSONArray()
                for (i in 0 until indices.length()) old.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) old.execSQL(setup.getString(index))
            old.execSQL("INSERT INTO categories (id,name,nameKey) VALUES (1,?,?)", arrayOf(category, category.lowercase(java.util.Locale.ROOT)))
            old.execSQL("INSERT INTO customers (id,name,phone) VALUES (1,'Cliente preservado','5511912345678')")
            old.execSQL("INSERT INTO products (id,name,categoryId,priceCents,stock) VALUES (1,?,1,99900,7)", arrayOf(name))
            old.execSQL("INSERT INTO quotes (id,customerId,saleDate,paymentDate) VALUES (1,1,'2026-10-03',NULL)")
            old.execSQL("INSERT INTO quote_items (id,quoteId,productId,productName,quantity,unitPriceCents,position) VALUES (1,1,1,?,2,99900,0)", arrayOf(name))
            old.version = 1
        }
        db = SaleDatabase.create(context, "upgrade.db")
        val dao = db!!.dao()
        val products = dao.products().first()
        assertEquals(1, products.size)
        val preserved = products.first { it.id == 1L }
        assertEquals(99900L, preserved.priceCents)
        assertEquals(7, preserved.stock)
        assertNull(preserved.legacySourceId)
        assertEquals("Cliente preservado", dao.customers().first().single().name)
        assertEquals(199800L, dao.quote(1)!!.total)
        assertEquals("2026-10-03", dao.quote(1)!!.quote.saleDate)
        assertEquals(0, dao.quote(1)!!.quote.status)
    }

    @Test fun version2UpgradePreservesUndatedQuotesAndInfersPaidWithoutClaimingDelivery() = runBlocking {
        val schema = JSONObject(javaClass.classLoader!!.getResourceAsStream("br.com.koalasale.SaleDatabase/2.json")!!
            .bufferedReader().use { it.readText() }).getJSONObject("database")
        val file = context.getDatabasePath("upgrade.db")
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: JSONArray()
                for (i in 0 until indices.length()) old.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) old.execSQL(setup.getString(index))
            old.execSQL("INSERT INTO categories (id,name,nameKey) VALUES (1,'CI','ci')")
            old.execSQL("INSERT INTO customers (id,name,phone) VALUES (1,'Cliente preservado','5511912345678')")
            old.execSQL("INSERT INTO products (id,name,categoryId,priceCents,stock,legacySourceId) VALUES (1,'CI',1,350,8,'antigo-1')")
            old.execSQL("INSERT INTO quotes (id,customerId,saleDate,paymentDate) VALUES (1,1,NULL,NULL),(2,1,'2026-10-03','2026-10-04')")
            old.execSQL("INSERT INTO quote_items (id,quoteId,productId,productName,quantity,unitPriceCents,position) VALUES (1,1,1,'CI',2,350,0),(2,2,1,'CI',3,350,0)")
            old.version = 2
        }
        db = SaleDatabase.create(context, "upgrade.db")
        val dao = db!!.dao()
        val undated = dao.quote(1)!!
        assertNull(undated.quote.saleDate)
        assertEquals(0, undated.quote.status)
        assertEquals(700L, undated.total)
        val paid = dao.quote(2)!!
        assertEquals(1, paid.quote.status)
        assertEquals("2026-10-04", paid.quote.paymentDate)
        assertEquals(1050L, paid.total)
        dao.saveQuote(paid.quote.copy(status = 2), paid.draftLines())
        db!!.close()
        db = SaleDatabase.create(context, "upgrade.db")
        assertEquals(2, db!!.dao().quote(2)!!.quote.status)
        assertEquals(8, db!!.dao().products().first().single().stock)
        assertNull(db!!.dao().quote(1)!!.quote.saleDate)
    }

    @Test fun version3UpgradeKeepsStatusAndPricesAndPersistsAllFourAdjustmentModes() = runBlocking {
        val schema = JSONObject(javaClass.classLoader!!.getResourceAsStream("br.com.koalasale.SaleDatabase/3.json")!!
            .bufferedReader().use { it.readText() }).getJSONObject("database")
        val file = context.getDatabasePath("upgrade.db")
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: JSONArray()
                for (i in 0 until indices.length()) old.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) old.execSQL(setup.getString(index))
            old.execSQL("INSERT INTO categories (id,name,nameKey) VALUES (1,'CI','ci')")
            old.execSQL("INSERT INTO customers (id,name,phone) VALUES (1,'Cliente preservado','5511912345678')")
            old.execSQL("INSERT INTO products (id,name,categoryId,priceCents,stock,legacySourceId) VALUES (1,'CI',1,1001,8,'antigo-1')")
            old.execSQL("INSERT INTO quotes (id,customerId,saleDate,paymentDate,status) VALUES (1,1,'2026-10-03','2026-10-04',2),(2,1,NULL,NULL,0)")
            old.execSQL("INSERT INTO quote_items (id,quoteId,productId,productName,quantity,unitPriceCents,position) VALUES (1,1,1,'CI',3,1001,0),(2,2,1,'CI',1,1001,0)")
            old.version = 3
        }
        db = SaleDatabase.create(context, "upgrade.db")
        val migrated = db!!.dao().quote(1)!!
        assertEquals(2, migrated.quote.status)
        assertEquals(0, migrated.quote.adjustmentType)
        assertFalse(migrated.quote.adjustmentIsPercent)
        assertEquals(0L, migrated.quote.adjustmentValue)
        assertEquals(3003L, migrated.total)
        assertEquals("2026-10-04", migrated.quote.paymentDate)
        assertEquals(1, db!!.dao().completeQuote(2))
        assertNull(db!!.dao().quote(2)!!.quote.saleDate)
        assertEquals(2, db!!.dao().quote(2)!!.quote.status)
        for ((type, percent, value, total) in listOf(
            listOf(1L, 0L, 250L, 2753L), listOf(2L, 0L, 250L, 3253L),
            listOf(1L, 1L, 1250L, 2628L), listOf(2L, 1L, 1250L, 3378L))) {
            db!!.dao().saveQuote(migrated.quote.copy(adjustmentType = type.toInt(), adjustmentIsPercent = percent == 1L,
                adjustmentValue = value), migrated.draftLines())
            db!!.close()
            db = SaleDatabase.create(context, "upgrade.db")
            val saved = db!!.dao().quote(1)!!
            assertEquals(total, saved.total)
            assertEquals(3003L, saved.subtotal)
            assertEquals(type.toInt(), saved.quote.adjustmentType)
            assertEquals(percent == 1L, saved.quote.adjustmentIsPercent)
            assertEquals(value, saved.quote.adjustmentValue)
            assertEquals(2, saved.quote.status)
            assertEquals(8, db!!.dao().products().first().single().stock)
        }
        val stored = db!!.dao().quote(1)!!
        try {
            db!!.dao().saveQuote(stored.quote.copy(adjustmentType = 1, adjustmentIsPercent = false, adjustmentValue = 99999), stored.draftLines())
            fail("Desconto superior ao subtotal deve falhar.")
        } catch (_: IllegalArgumentException) { }
        assertEquals(stored.quote, db!!.dao().quote(1)!!.quote)
        assertEquals(0, db!!.dao().completeQuote(999))
    }
    @Test fun version4UpgradePreservesFullQuoteAndPricesAndAddsDefaultMarginAndEmptyPurchases() = runBlocking {
        val schema = JSONObject(javaClass.classLoader!!.getResourceAsStream("br.com.koalasale.SaleDatabase/4.json")!!
            .bufferedReader().use { it.readText() }).getJSONObject("database")
        val file = context.getDatabasePath("upgrade.db")
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: JSONArray()
                for (i in 0 until indices.length()) old.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) old.execSQL(setup.getString(index))
            old.execSQL("INSERT INTO categories (id,name,nameKey) VALUES (1,'CI','ci')")
            old.execSQL("INSERT INTO customers (id,name,phone) VALUES (1,'Cliente preservado','5511912345678')")
            old.execSQL("INSERT INTO products (id,name,categoryId,priceCents,stock,legacySourceId) VALUES (1,'CI',1,999,8,'antigo-1')")
            old.execSQL("INSERT INTO quotes (id,customerId,saleDate,paymentDate,status,adjustmentType,adjustmentIsPercent,adjustmentValue) VALUES (1,1,'2026-10-03','2026-10-04',2,1,1,1000)")
            old.execSQL("INSERT INTO quote_items (id,quoteId,productId,productName,quantity,unitPriceCents,position) VALUES (1,1,1,'CI histórico',2,500,0)")
            old.version = 4
        }
        db = SaleDatabase.create(context, "upgrade.db")
        val dao = db!!.dao()
        val product = dao.products().first().single()
        assertEquals(4000, product.marginBasisPoints)
        assertEquals(999L, product.priceCents)
        assertEquals(8, product.stock)
        assertEquals("antigo-1", product.legacySourceId)
        val quote = dao.quote(1)!!
        assertEquals(900L, quote.total)
        assertEquals(2, quote.quote.status)
        assertEquals("CI histórico", quote.items.single().productName)
        assertEquals("2026-10-04", quote.quote.paymentDate)
        assertTrue(dao.purchases().first().isEmpty())
        assertTrue(PurchasePricing.averageCosts(dao.purchases().first()).isEmpty())
        dao.savePurchase(Purchase(company = "Fornecedor teste", date = "2026-10-03", shippingCents = 200, feesCents = 100), listOf(DraftLine(product.id, product.name, 3, 500)))
        dao.updateProduct(product.copy(marginBasisPoints = 5000))
        db!!.close(); db = SaleDatabase.create(context, "upgrade.db")
        assertEquals(1200L, PurchasePricing.averageCosts(db!!.dao().purchases().first()).getValue(product.id).recommended(5000))
        assertEquals(5000, db!!.dao().products().first().single().marginBasisPoints)
        assertEquals(999L, db!!.dao().products().first().single().priceCents)
        assertEquals(quote, db!!.dao().quote(1))
    }

    @Test fun version5UpgradeKeepsPurchasesAndPricingWithoutInventingCompanyOrReceipt() = runBlocking {
        val schema = JSONObject(javaClass.classLoader!!.getResourceAsStream("br.com.koalasale.SaleDatabase/5.json")!!
            .bufferedReader().use { it.readText() }).getJSONObject("database")
        val file = context.getDatabasePath("upgrade.db")
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: JSONArray()
                for (i in 0 until indices.length()) old.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) old.execSQL(setup.getString(index))
            old.execSQL("INSERT INTO categories (id,name,nameKey) VALUES (1,'CI','ci')")
            old.execSQL("INSERT INTO customers (id,name,phone) VALUES (1,'Cliente preservado','5511912345678')")
            old.execSQL("INSERT INTO products (id,name,categoryId,priceCents,stock,legacySourceId,marginBasisPoints) VALUES (1,'CI',1,999,8,'antigo-1',5000)")
            old.execSQL("INSERT INTO quotes (id,customerId,saleDate,paymentDate,status,adjustmentType,adjustmentIsPercent,adjustmentValue) VALUES (1,1,'2026-10-03','2026-10-04',2,1,1,1000)")
            old.execSQL("INSERT INTO quote_items (id,quoteId,productId,productName,quantity,unitPriceCents,position) VALUES (1,1,1,'CI histórico',2,500,0)")
            old.execSQL("INSERT INTO purchases (id,date,shippingCents,feesCents) VALUES (1,'2026-10-03',100,100)")
            old.execSQL("INSERT INTO purchase_items (id,purchaseId,productId,productName,quantity,unitPriceCents,position) VALUES (5,1,1,'CI reposição',2,350,0)")
            old.version = 5
        }
        db = SaleDatabase.create(context, "upgrade.db")
        val dao = db!!.dao()
        val product = dao.products().first().single()
        assertEquals(999L, product.priceCents)
        assertEquals(5000, product.marginBasisPoints)
        assertEquals(8, product.stock)
        val purchase = dao.purchase(1)!!
        assertEquals("", purchase.purchase.company)
        assertNull(purchase.purchase.receivedDate)
        assertEquals(5L, purchase.items.single().id)
        assertEquals("CI reposição", purchase.items.single().productName)
        assertEquals(900L, purchase.total)
        assertEquals(900L, PurchasePricing.averageCosts(listOf(purchase)).getValue(1).recommended(product.marginBasisPoints))
        val quote = dao.quote(1)!!
        assertEquals(900L, quote.total)
        assertEquals(2, quote.quote.status)
        assertEquals("CI histórico", quote.items.single().productName)
        dao.savePurchase(purchase.purchase.copy(company = "Fornecedor atualizado", receivedDate = "2026-10-05"), purchase.draftLines())
        db!!.close(); db = SaleDatabase.create(context, "upgrade.db")
        val updated = db!!.dao().purchase(1)!!
        assertEquals("Fornecedor atualizado", updated.purchase.company)
        assertEquals("2026-10-05", updated.purchase.receivedDate)
        assertEquals(purchase.total, updated.total)
        assertEquals(purchase.draftLines(), updated.draftLines())
        assertEquals(product, db!!.dao().products().first().single())
        assertEquals(quote, db!!.dao().quote(1))
    }

}
