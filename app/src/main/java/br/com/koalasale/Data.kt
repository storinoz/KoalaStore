package br.com.koalasale

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import kotlinx.coroutines.flow.Flow
import java.util.Locale

@Entity(tableName = "customers")
data class Customer(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val phone: String)

@Entity(tableName = "categories", indices = [Index(value = ["nameKey"], unique = true)])
data class Category(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String,
                    val nameKey: String = name.trim().lowercase(Locale.ROOT))

@Entity(tableName = "products", foreignKeys = [ForeignKey(entity = Category::class,
    parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("categoryId"), Index(value = ["legacySourceId"], unique = true)])
data class Product(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String,
                   val categoryId: Long, val priceCents: Long, val stock: Int? = null,
                   val legacySourceId: String? = null,
                   @ColumnInfo(defaultValue = "4000") val marginBasisPoints: Int = 4000)

@Entity(tableName = "catalog_imports")
data class CatalogImport(@PrimaryKey val id: String, val added: Int, val matched: Int)

@Entity(tableName = "quotes", foreignKeys = [ForeignKey(entity = Customer::class,
    parentColumns = ["id"], childColumns = ["customerId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("customerId")])
data class Quote(@PrimaryKey(autoGenerate = true) val id: Long = 0, val customerId: Long,
                 val saleDate: String? = null, val paymentDate: String? = null,
                 @ColumnInfo(defaultValue = "0") val status: Int = 0,
                 @ColumnInfo(defaultValue = "0") val adjustmentType: Int = 0,
                 @ColumnInfo(defaultValue = "0") val adjustmentIsPercent: Boolean = false,
                 @ColumnInfo(defaultValue = "0") val adjustmentValue: Long = 0)

@Entity(tableName = "quote_items", foreignKeys = [
    ForeignKey(entity = Quote::class, parentColumns = ["id"], childColumns = ["quoteId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = Product::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.RESTRICT)
], indices = [Index("quoteId"), Index("productId")])
data class QuoteItem(@PrimaryKey(autoGenerate = true) val id: Long = 0, val quoteId: Long,
    val productId: Long, val productName: String, val quantity: Int, val unitPriceCents: Long, val position: Int)

data class QuoteWithItems(@Embedded val quote: Quote,
    @Relation(parentColumn = "id", entityColumn = "quoteId") val items: List<QuoteItem>) {
    fun draftLines() = items.sortedBy { it.position }.map { DraftLine(it.productId, it.productName, it.quantity, it.unitPriceCents) }
    val subtotal: Long get() = Rules.total(draftLines())
    val amounts: QuoteAmounts get() = QuotePricing.calculate(subtotal, quote.adjustmentType, quote.adjustmentIsPercent, quote.adjustmentValue)
    val total: Long get() = amounts.total
}

@Entity(tableName = "purchases")
data class Purchase(@PrimaryKey(autoGenerate = true) val id: Long = 0, val date: String,
    val shippingCents: Long = 0, val feesCents: Long = 0,
    @ColumnInfo(defaultValue = "''") val company: String = "", val receivedDate: String? = null)

@Entity(tableName = "purchase_items", foreignKeys = [
    ForeignKey(entity = Purchase::class, parentColumns = ["id"], childColumns = ["purchaseId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = Product::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.RESTRICT)
], indices = [Index("purchaseId"), Index("productId")])
data class PurchaseItem(@PrimaryKey(autoGenerate = true) val id: Long = 0, val purchaseId: Long,
    val productId: Long, val productName: String, val quantity: Int, val unitPriceCents: Long, val position: Int)

data class PurchaseWithItems(@Embedded val purchase: Purchase,
    @Relation(parentColumn = "id", entityColumn = "purchaseId") val items: List<PurchaseItem>) {
    fun draftLines() = items.sortedBy { it.position }.map { DraftLine(it.productId, it.productName, it.quantity, it.unitPriceCents) }
    val subtotal: Long get() = Rules.total(draftLines())
    val total: Long get() = PurchasePricing.total(subtotal, purchase.shippingCents, purchase.feesCents)
}

@Dao
abstract class SaleDao {
    @Query("SELECT * FROM customers ORDER BY name COLLATE NOCASE") abstract fun customers(): Flow<List<Customer>>
    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE") abstract fun categories(): Flow<List<Category>>
    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE") abstract fun products(): Flow<List<Product>>
    @Transaction @Query("SELECT * FROM quotes ORDER BY id DESC") abstract fun quotes(): Flow<List<QuoteWithItems>>
    @Transaction @Query("SELECT * FROM purchases ORDER BY date DESC, id DESC") abstract fun purchases(): Flow<List<PurchaseWithItems>>
    @Transaction @Query("SELECT * FROM purchases WHERE id = :id") abstract suspend fun purchase(id: Long): PurchaseWithItems?
    @Insert abstract suspend fun insertPurchase(value: Purchase): Long
    @Update abstract suspend fun updatePurchase(value: Purchase)
    @Delete abstract suspend fun deletePurchase(value: Purchase)
    @Insert abstract suspend fun insertPurchaseItems(values: List<PurchaseItem>)
    @Query("DELETE FROM purchase_items WHERE purchaseId = :id") abstract suspend fun clearPurchaseItems(id: Long)
    @Insert abstract suspend fun insertCustomer(value: Customer): Long
    @Update abstract suspend fun updateCustomer(value: Customer)
    @Delete abstract suspend fun deleteCustomer(value: Customer)
    @Insert abstract suspend fun insertCategory(value: Category): Long
    @Update abstract suspend fun updateCategory(value: Category)
    @Delete abstract suspend fun deleteCategory(value: Category)
    @Insert abstract suspend fun insertProduct(value: Product): Long
    @Update abstract suspend fun updateProduct(value: Product)
    @Query("UPDATE products SET priceCents = :price, marginBasisPoints = :margin WHERE id = :id")
    abstract suspend fun updateProductPrice(id: Long, price: Long, margin: Int): Int
    @Delete abstract suspend fun deleteProduct(value: Product)
    @Insert abstract suspend fun insertQuote(value: Quote): Long
    @Update abstract suspend fun updateQuote(value: Quote)
    @Delete abstract suspend fun deleteQuote(value: Quote)
    @Query("UPDATE quotes SET status = 2 WHERE id = :id") abstract suspend fun completeQuote(id: Long): Int
    @Insert abstract suspend fun insertItems(values: List<QuoteItem>)
    @Query("DELETE FROM quote_items WHERE quoteId = :id") abstract suspend fun clearItems(id: Long)
    @Transaction @Query("SELECT * FROM quotes WHERE id = :id") abstract suspend fun quote(id: Long): QuoteWithItems?

    @Transaction
    open suspend fun saveQuote(value: Quote, lines: List<DraftLine>): Long {
        require(lines.isNotEmpty()) { "Adicione pelo menos um produto." }
        require(lines.map { it.productId }.distinct().size == lines.size) { "Produto repetido no orçamento." }
        QuotePricing.calculate(Rules.total(lines), value.adjustmentType, value.adjustmentIsPercent, value.adjustmentValue)
        require(!value.saleDate.isNullOrBlank()) { "Informe a data da venda." }
        java.time.LocalDate.parse(value.saleDate)
        value.paymentDate?.let { java.time.LocalDate.parse(it) }
        require(value.status in 0..2) { "Selecione um status válido." }
        val id = if (value.id == 0L) insertQuote(value) else {
            require(quote(value.id) != null) { "Orçamento não encontrado." }
            updateQuote(value)
            clearItems(value.id)
            value.id
        }
        insertItems(lines.mapIndexed { index, line -> QuoteItem(quoteId = id, productId = line.productId,
            productName = line.name, quantity = line.quantity, unitPriceCents = line.priceCents, position = index) })
        return id
    }

    @Transaction
    open suspend fun savePurchaseForPriceReview(value: Purchase, lines: List<DraftLine>): Long? {
        val previous = if (value.id == 0L) null else purchase(value.id)
        val changed = previous == null || previous.purchase != value || previous.draftLines() != lines
        val id = savePurchase(value, lines)
        return if (changed) id else null
    }

    @Transaction
    open suspend fun updatePurchaseProductPrices(purchaseId: Long, updates: List<ProductPriceUpdate>) {
        val saved = requireNotNull(purchase(purchaseId)) { "Compra não encontrada." }
        require(updates.map { it.productId }.toSet() == saved.items.map { it.productId }.toSet() &&
            updates.map { it.productId }.distinct().size == updates.size) { "Confira os produtos da compra." }
        updates.forEach {
            require(it.priceCents in 0..Rules.MAX_PRICE) { "Informe um preço de venda válido." }
            require(it.marginBasisPoints in 0..9999) { "Informe uma margem entre 0% e 99,99%." }
        }
        updates.forEach {
            require(updateProductPrice(it.productId, it.priceCents, it.marginBasisPoints) == 1) { "Produto não encontrado." }
        }
    }

    @Transaction
    open suspend fun savePurchase(value: Purchase, lines: List<DraftLine>): Long {
        require(lines.isNotEmpty()) { "Adicione pelo menos um produto cadastrado." }
        require(lines.map { it.productId }.distinct().size == lines.size) { "Produto repetido na compra." }
        java.time.LocalDate.parse(value.date)
        value.receivedDate?.let { java.time.LocalDate.parse(it) }
        require(value.company.isNotBlank()) { "Informe a empresa onde a compra foi feita." }
        PurchasePricing.total(Rules.total(lines), value.shippingCents, value.feesCents)
        val id = if (value.id == 0L) insertPurchase(value) else {
            require(purchase(value.id) != null) { "Compra não encontrada." }
            updatePurchase(value)
            clearPurchaseItems(value.id)
            value.id
        }
        insertPurchaseItems(lines.mapIndexed { index, line -> PurchaseItem(purchaseId = id, productId = line.productId,
            productName = line.name, quantity = line.quantity, unitPriceCents = line.priceCents, position = index) })
        return id
    }
}

@Database(entities = [Customer::class, Category::class, Product::class, Quote::class, QuoteItem::class, CatalogImport::class,
    Purchase::class, PurchaseItem::class], version = 6)
abstract class SaleDatabase : RoomDatabase() {
    abstract fun dao(): SaleDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN legacySourceId TEXT")
                db.execSQL("CREATE UNIQUE INDEX index_products_legacySourceId ON products (legacySourceId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS catalog_imports (id TEXT NOT NULL PRIMARY KEY, added INTEGER NOT NULL, matched INTEGER NOT NULL)")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE quotes ADD COLUMN status INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE quotes SET status = 1 WHERE paymentDate IS NOT NULL AND paymentDate != ''")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE quotes ADD COLUMN adjustmentType INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE quotes ADD COLUMN adjustmentIsPercent INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE quotes ADD COLUMN adjustmentValue INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN marginBasisPoints INTEGER NOT NULL DEFAULT 4000")
                db.execSQL("CREATE TABLE purchases (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, date TEXT NOT NULL, shippingCents INTEGER NOT NULL, feesCents INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE purchase_items (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, purchaseId INTEGER NOT NULL, productId INTEGER NOT NULL, productName TEXT NOT NULL, quantity INTEGER NOT NULL, unitPriceCents INTEGER NOT NULL, position INTEGER NOT NULL, FOREIGN KEY(purchaseId) REFERENCES purchases(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(productId) REFERENCES products(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX index_purchase_items_purchaseId ON purchase_items(purchaseId)")
                db.execSQL("CREATE INDEX index_purchase_items_productId ON purchase_items(productId)")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE purchases ADD COLUMN company TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE purchases ADD COLUMN receivedDate TEXT")
            }
        }
        fun create(context: Context, name: String = "koalasale.db"): SaleDatabase =
            Room.databaseBuilder(context.applicationContext, SaleDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
    }
}
