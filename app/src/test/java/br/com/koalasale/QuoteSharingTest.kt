package br.com.koalasale

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuoteSharingTest {
    private val customer = Customer(1, "Cliente fictício & teste", "5511912345678")
    private val budget = QuoteWithItems(Quote(3, 1, "2026-10-06", status = 1, adjustmentType = 1, adjustmentIsPercent = true, adjustmentValue = 1000),
        listOf(QuoteItem(1, 3, 1, "Componente fictício Ω", 2, 1000, 0)))
    @Test fun sharedTextContainsHistoricalItemsDatesAdjustmentAndFinalTotalAndEscapesUrl() {
        val text = QuoteSharing.text(budget, customer)
        assertTrue(text.contains("2 × Componente fictício Ω"))
        assertTrue(text.contains("Cliente: ${customer.name}"))
        assertTrue(text.contains("Venda: 06/10/2026"))
        assertTrue(text.contains("TOTAL: ${Rules.money(1800)}"))
        val intent = QuoteSharing.textIntent(customer.phone, text, "com.whatsapp")
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(customer.phone, intent.data!!.lastPathSegment)
        assertEquals(text, intent.data!!.getQueryParameter("text"))
        assertEquals("com.whatsapp", intent.`package`)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preview = QuoteSharing.renderImages(context, text, 3).first()
        val output = java.io.File(System.getenv("KOALASTORE_QA_DIR") ?: (System.getProperty("java.io.tmpdir") + "/KoalaStore-0.7.0-qa")).apply { mkdirs() }
        preview.copyTo(java.io.File(output, "quote-preview.png"), overwrite = true)
    }
    @Test fun longImageIsPaginatedAsReadablePngsInsideDedicatedCache() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val files = QuoteSharing.renderImages(context, (1..100).joinToString("\n") { "$it × Componente fictício: ${Rules.money(it * 10L)}" }, 3)
        assertTrue(files.size > 1)
        files.forEach { file ->
            assertEquals(java.io.File(context.cacheDir, "quotes").canonicalFile, file.parentFile!!.canonicalFile)
            val bitmap = file.inputStream().use { BitmapFactory.decodeStream(it) }
            assertEquals(960, bitmap.width)
            assertTrue(bitmap.height <= 1560)
            bitmap.recycle()
        }
    }
    @Test fun missingWhatsAppGivesAnErrorWithoutPretendingToSend() {
        QuoteSharing.send(ApplicationProvider.getApplicationContext(), budget, customer, false)
        assertTrue(ShadowToast.getTextOfLatestToast().contains("Instale o WhatsApp"))
    }
}
