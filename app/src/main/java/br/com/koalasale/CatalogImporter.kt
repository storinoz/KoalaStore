package br.com.koalasale

import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONObject
import java.util.Locale

object CatalogImporter {
    private data class Entry(val sourceId: String, val name: String, val category: String, val priceCents: Long)

    fun importOnce(db: SupportSQLiteDatabase, json: String) {
        val catalog = JSONObject(json)
        val catalogId = catalog.getString("catalogId")
        require(catalogId.isNotBlank())
        db.query("SELECT id FROM catalog_imports WHERE id = ?", arrayOf(catalogId)).use { if (it.moveToFirst()) return }
        val products = catalog.getJSONArray("products")
        val entries = (0 until products.length()).map { index ->
            val product = products.getJSONObject(index)
            Entry(product.getString("sourceId"), product.getString("name").trim(), product.getString("category").trim(), product.getLong("priceCents"))
                .also { require(it.sourceId.isNotBlank() && it.name.isNotBlank() && it.category.isNotBlank() && it.priceCents in 0..Rules.MAX_PRICE) }
        }
        require(entries.map { it.sourceId }.distinct().size == entries.size)
        db.beginTransaction()
        try {
            var added = 0
            var matched = 0
            entries.forEach { entry ->
                val categoryKey = entry.category.lowercase(Locale.ROOT)
                db.execSQL("INSERT OR IGNORE INTO categories (name, nameKey) VALUES (?, ?)", arrayOf(entry.category, categoryKey))
                val categoryId = db.query("SELECT id FROM categories WHERE nameKey = ?", arrayOf(categoryKey)).use {
                    check(it.moveToFirst()); it.getLong(0)
                }
                var existing: Long? = null
                db.query("SELECT id, name FROM products WHERE categoryId = ? AND legacySourceId IS NULL ORDER BY id", arrayOf(categoryId)).use { cursor ->
                    while (cursor.moveToNext()) {
                        if (cursor.getString(1).trim().equals(entry.name, ignoreCase = true)) { existing = cursor.getLong(0); break }
                    }
                }
                if (existing != null) {
                    // Keep user-edited prices and stock when a matching product already exists.
                    db.execSQL("UPDATE products SET legacySourceId = ? WHERE id = ?", arrayOf<Any?>(entry.sourceId, existing))
                    matched++
                } else {
                    db.execSQL("INSERT INTO products (name, categoryId, priceCents, stock, legacySourceId) VALUES (?, ?, ?, NULL, ?)",
                        arrayOf<Any>(entry.name, categoryId, entry.priceCents, entry.sourceId))
                    added++
                }
            }
            db.execSQL("INSERT INTO catalog_imports (id, added, matched) VALUES (?, ?, ?)", arrayOf(catalogId, added, matched))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
}
