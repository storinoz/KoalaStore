package br.com.koalasale

import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object QuoteSharing {
    fun text(budget: QuoteWithItems, customer: Customer): String = buildString {
        appendLine("KoalaStore · Orçamento #${budget.quote.id}")
        appendLine("Cliente: ${customer.name}")
        appendLine("Venda: ${Rules.dateLabel(budget.quote.saleDate)}")
        appendLine("Pagamento: ${Rules.dateLabel(budget.quote.paymentDate).ifBlank { "Não informado" }}")
        appendLine("Status: ${BudgetStatus.from(budget.quote.status).description}")
        appendLine()
        budget.items.sortedBy { it.position }.forEach {
            appendLine("${it.quantity} × ${it.productName}")
            appendLine("${Rules.money(it.unitPriceCents)} / un. · ${Rules.money(it.unitPriceCents * it.quantity)}")
        }
        appendLine()
        appendLine("Subtotal: ${Rules.money(budget.subtotal)}")
        if (budget.quote.adjustmentType != 0) appendLine("${QuotePricing.label(budget.quote)}: ${if (budget.quote.adjustmentType == 1) "−" else "+"}${Rules.money(kotlin.math.abs(budget.amounts.adjustment))}")
        append("TOTAL: ${Rules.money(budget.total)}")
    }

    fun textIntent(phone: String, message: String, packageName: String): Intent {
        require(phone.matches(Regex("[0-9]{8,15}"))) { "O cliente não tem um telefone válido para WhatsApp." }
        val uri = Uri.Builder().scheme("https").authority("wa.me").appendPath(phone).appendQueryParameter("text", message).build()
        return Intent(Intent.ACTION_VIEW, uri).setPackage(packageName)
    }

    /** Bound each bitmap height; long quotations are shared as numbered pages. */
    fun renderImages(context: Context, message: String, id: Long): List<File> {
        val paint = TextPaint().apply { color = Color.rgb(35, 45, 39); textSize = 28f; isAntiAlias = true }
        val layout = StaticLayout.Builder.obtain(message, 0, message.length, paint, 840)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(8f, 1f).build()
        val folder = File(context.cacheDir, "quotes").apply { mkdirs() }
        // Only disposable exports created by this feature, never the device database.
        folder.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 7 * 24 * 60 * 60 * 1000L }?.forEach { it.delete() }
        val files = arrayListOf<File>()
        var startLine = 0
        while (startLine < layout.lineCount) {
            val top = layout.getLineTop(startLine)
            var endLine = startLine + 1
            while (endLine < layout.lineCount && layout.getLineBottom(endLine) - top <= 1440) endLine++
            val bottom = layout.getLineBottom(endLine - 1)
            val bitmap = Bitmap.createBitmap(960, bottom - top + 120, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap); canvas.drawColor(Color.WHITE)
            canvas.save(); canvas.clipRect(60, 60, 900, bottom - top + 60); canvas.translate(60f, 60f - top)
            layout.draw(canvas); canvas.restore()
            val file = File(folder, "orcamento-${id}-${System.nanoTime()}-${files.size + 1}.png")
            try { file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
            finally { bitmap.recycle() }
            files.add(file)
            startLine = endLine
        }
        return files
    }

    fun images(context: Context, message: String, id: Long): ArrayList<Uri> =
        ArrayList(renderImages(context, message, id).map { FileProvider.getUriForFile(context, "${context.packageName}.files", it) })

    fun send(context: Context, budget: QuoteWithItems, customer: Customer, image: Boolean) {
        try {
            require(customer.phone.matches(Regex("[0-9]{8,15}"))) { "O cliente não tem um telefone válido para WhatsApp." }
            val message = text(budget, customer)
            val packages = listOf("com.whatsapp", "com.whatsapp.w4b")
            val target = packages.firstOrNull { name ->
                @Suppress("DEPRECATION")
                context.packageManager.queryIntentActivities(textIntent(customer.phone, "", name), 0).isNotEmpty()
            } ?: throw IllegalArgumentException("Instale o WhatsApp ou WhatsApp Business para enviar o orçamento.")
            val intent = if (!image) textIntent(customer.phone, message, target) else {
                val uris = images(context, message, budget.quote.id)
                Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "image/png"; setPackage(target)
                    if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris.single()) else putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    putExtra(Intent.EXTRA_TEXT, "Orçamento #${budget.quote.id} · ${customer.name} · ${Rules.money(budget.total)}")
                    // WhatsApp may ignore this routing hint depending on its version.
                    putExtra("jid", "${customer.phone}@s.whatsapp.net")
                    clipData = ClipData.newUri(context.contentResolver, "Orçamento", uris.first()).apply {
                        uris.drop(1).forEach { addItem(ClipData.Item(it)) }
                    }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            context.startActivity(intent)
            if (image) Toast.makeText(context, "Se o WhatsApp pedir um destinatário, selecione ${customer.name} (+${customer.phone}).", Toast.LENGTH_LONG).show()
        } catch (e: IllegalArgumentException) { Toast.makeText(context, e.message ?: "Confira o orçamento e o contato.", Toast.LENGTH_LONG).show() }
        catch (_: Exception) { Toast.makeText(context, "Não foi possível abrir o WhatsApp ou preparar o orçamento. Tente novamente.", Toast.LENGTH_LONG).show() }
    }
}
