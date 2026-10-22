package br.com.koalasale

import org.junit.Assert.*
import org.junit.Test

class RulesTest {
    @Test fun moneyUsesExactCentsAndBrazilianInput() {
        assertEquals(10L, Rules.cents("0,10"))
        assertEquals(1290L, Rules.cents("12.90"))
        assertEquals(0L, Rules.cents("0"))
        assertNull(Rules.cents("1,234"))
        assertNull(Rules.cents("-1"))
        assertNull(Rules.cents("NaN"))
        assertNull(Rules.cents("1.234,50"))
        assertNull(Rules.cents("1000001"))
        assertEquals(130L, Rules.total(listOf(DraftLine(1, "Resistor", 3, 10), DraftLine(2, "CI", 2, 50))))
    }
    @Test fun optionalDatesAreStrictAndHandleLeapYears() {
        assertNull(Rules.date(""))
        assertEquals("2024-02-29", Rules.date("29/02/2024"))
        assertEquals("03/10/2026", Rules.dateLabel("2026-10-03"))
        assertThrows(java.time.format.DateTimeParseException::class.java) { Rules.date("31/02/2026") }
    }
    @Test fun whatsappNormalizesLocalAndInternationalNumbers() {
        assertEquals("5511912345678", Rules.phone("(11) 91234-5678"))
        assertEquals("5511912345678", Rules.phone("+55 (11) 91234-5678"))
        assertEquals("14155552671", Rules.phone("+1 415 555 2671"))
        assertNull(Rules.phone("91234-5678"))
        assertNull(Rules.phone("texto1234567890"))
    }
    @Test fun rejectsZeroQuantityAndOversizedNumbers() {
        assertThrows(IllegalArgumentException::class.java) { Rules.total(listOf(DraftLine(1, "CI", 0, 100))) }
        assertThrows(IllegalArgumentException::class.java) { Rules.total(listOf(DraftLine(1, "CI", 1, -1))) }
    }
}
