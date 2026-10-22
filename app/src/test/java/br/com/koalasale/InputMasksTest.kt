package br.com.koalasale

import androidx.compose.ui.text.AnnotatedString
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate
import java.time.YearMonth

class InputMasksTest {
    @Test fun datesAreMaskedAndStrictEvenWhenOptional() {
        assertEquals("03/10/2026", InputMasks.dateText("03102026"))
        assertEquals("03/1", InputMasks.dateText("031"))
        assertEquals("2024-02-29", InputMasks.date("29022024", true))
        assertNull(InputMasks.date(""))
        for (raw in listOf("", "0310", "31042026", "29022025", "00002026", "01010000")) {
            assertTrue("$raw deve ser recusado", runCatching { InputMasks.date(raw, true) }.isFailure)
        }
        assertTrue(runCatching { InputMasks.date("31042026") }.isFailure)
    }
    @Test fun moneyAndPhoneAreMaskedWithoutChangingTheirStoredValues() {
        assertEquals("0,01", InputMasks.moneyText("1"))
        assertEquals("2,50", InputMasks.moneyText("250"))
        assertEquals("1.234,56", InputMasks.moneyText("123456"))
        assertEquals(123456L, InputMasks.cents("123456"))
        assertNull(InputMasks.cents("100000001"))
        assertEquals("(11) 91234-5678", InputMasks.phoneText("11912345678"))
        assertEquals("+55 (11) 91234-5678", InputMasks.phoneText("+5511912345678"))
        assertEquals("(11) 1234-5678", InputMasks.phoneText("1112345678"))
        assertEquals("5511912345678", Rules.phone(InputMasks.phoneText("11912345678")))
        assertEquals("+441234567890", InputMasks.phoneRaw("+44 1234 567890"))
    }
    @Test fun cursorMappingsRemainMonotonicAndBoundedForEverySeparatorAndLeadingZero() {
        val cases = listOf(InputMasks.dateMask to "03102026", InputMasks.dateMask to "03", InputMasks.dateMask to "",
            InputMasks.moneyMask to "1", InputMasks.moneyMask to "000000000", InputMasks.moneyMask to "123456789",
            InputMasks.phoneMask to "11912345678", InputMasks.phoneMask to "+55", InputMasks.phoneMask to "+5511912345678")
        for ((mask, raw) in cases) {
            val transformed = mask.filter(AnnotatedString(raw))
            val forward = (0..raw.length).map { transformed.offsetMapping.originalToTransformed(it) }
            val backward = (0..transformed.text.length).map { transformed.offsetMapping.transformedToOriginal(it) }
            assertTrue(forward.all { it in 0..transformed.text.length })
            assertTrue(backward.all { it in 0..raw.length })
            assertEquals(forward.sorted(), forward)
            assertEquals(backward.sorted(), backward)
            assertEquals(raw.length, backward.last())
        }
    }
    @Test fun calendarConversionsUseUtcWithoutShiftingTheDateInBrazil() {
        for (date in listOf("2026-10-03", "2024-02-29", "1900-01-01", "2099-12-31")) {
            assertEquals(date, InputMasks.dateFromMillis(InputMasks.dateMillis(date)))
        }
    }
    @Test fun filterExcludesFutureMonthsAndYearsAndGrowsWhenTheClockAdvances() {
        val dates = listOf("2025-05-01", "2028-01-01", null)
        val october = MonthOptions.available(dates, LocalDate.of(2026, 10, 3))
        assertEquals(YearMonth.of(2026, 10), october.first())
        assertFalse(YearMonth.of(2026, 11) in october)
        assertFalse(october.any { it.year > 2026 })
        val january = MonthOptions.available(dates, LocalDate.of(2027, 1, 1))
        assertEquals(YearMonth.of(2027, 1), january.first())
        assertFalse(YearMonth.of(2027, 2) in january)
        assertEquals(12, january.count { it.year == 2026 })
    }
}
