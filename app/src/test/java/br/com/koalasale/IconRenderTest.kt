package br.com.koalasale

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconRenderTest {
    private fun render(resource: Int, size: Int): Bitmap {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val drawable = context.getDrawable(resource)!!
        if (resource == R.drawable.ic_whatsapp) drawable.setTint(Color.rgb(37,211,102))
        if (resource == R.drawable.ic_purchase) drawable.setTint(Color.rgb(46,125,50))
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }
    private fun save(bitmap: Bitmap, name: String) {
        val directory = File(System.getProperty("java.io.tmpdir"), "KoalaStore-0.6.0-qa").apply { mkdirs() }
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("Icon QA: " + File(directory, name).absolutePath)
    }
    @Test fun whatsappOutlineIsCircularWithTransparentInteriorAndCorners() {
        val bitmap = render(R.drawable.ic_whatsapp, 192)
        save(bitmap, "whatsapp.png")
        assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
        assertEquals(0, Color.alpha(bitmap.getPixel(96, 40)))
        listOf(96 to 5, 186 to 96, 5 to 96, 96 to 185).forEach { (x,y) ->
            assertTrue("Contorno ausente em $x,$y", Color.alpha(bitmap.getPixel(x,y)) > 200)
        }
    }
    @Test fun automaticHasWhiteSunAndMoonSeparatedByDarkLine() {
        val bitmap = render(R.drawable.ic_sun_moon, 192)
        save(bitmap, "automatic.png")
        assertTrue(Color.red(bitmap.getPixel(72, 96)) > 240)
        assertTrue(Color.red(bitmap.getPixel(120, 138)) > 240)
        assertTrue(Color.red(bitmap.getPixel(96, 96)) < 70)
        val preview = Bitmap.createBitmap(512, 256, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(preview)
        canvas.drawColor(Color.rgb(20,24,28))
        val paint = android.graphics.Paint().apply { color = Color.WHITE }
        canvas.drawRect(256f, 0f, 512f, 256f, paint)
        for (offset in listOf(0f, 256f)) {
            canvas.drawBitmap(render(R.drawable.ic_purchase, 64), offset + 24, 28f, null)
            canvas.drawBitmap(render(R.drawable.ic_sun_moon, 64), offset + 144, 28f, null)
            canvas.drawBitmap(render(R.drawable.ic_purchase, 32), offset + 40, 156f, null)
            canvas.drawBitmap(render(R.drawable.ic_sun_moon, 32), offset + 160, 156f, null)
        }
        save(preview, "icons-light-dark.png")
    }
}
