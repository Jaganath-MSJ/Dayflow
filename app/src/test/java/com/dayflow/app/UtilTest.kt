package com.dayflow.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class UtilTest {
    @Test fun tminParsesValidTimes() {
        assertEquals(0, tmin("00:00"))
        assertEquals(570, tmin("09:30"))
        assertEquals(1439, tmin("23:59"))
        assertEquals(570, tmin(" 9:30 "))
    }

    @Test fun tminReturnsNullForBlankOrMalformed() {
        assertNull(tmin(null))
        assertNull(tmin(""))
        assertNull(tmin("9"))          // used to throw IndexOutOfBounds
        assertNull(tmin("9am"))        // used to throw NumberFormatException
        assertNull(tmin("ab:cd"))
        assertNull(tmin("25:00"))
        assertNull(tmin("10:75"))
    }

    @Test fun fmtTFormats12h() {
        assertEquals("12:00 AM", fmtT("00:00"))
        assertEquals("9:05 AM", fmtT("09:05"))
        assertEquals("12:30 PM", fmtT("12:30"))
        assertEquals("11:59 PM", fmtT("23:59"))
        assertEquals("", fmtT(null))
    }

    @Test fun fmtTDoesNotCrashOnMalformed() {
        assertEquals("9am", fmtT("9am"))
    }

    @Test fun fmtMinAndDur() {
        assertEquals("1:05 AM", fmtMin(65))
        assertEquals("7h 05m", fmtDur(425))
    }

    @Test fun dstrPadsAndRoundTrips() {
        val c = Calendar.getInstance().apply { set(2026, Calendar.MARCH, 4) }
        assertEquals("2026-03-04", dstr(c))
        assertEquals("2026-03-04", dstr(calOf("2026-03-04")))
    }

    @Test fun weekKeyIsSundayOfWeek() {
        // 2026-11-14 is a Saturday -> week starts Sunday 2026-11-08
        assertEquals("2026-11-08", weekKey(calOf("2026-11-14")))
        assertEquals("2026-11-08", weekKey(calOf("2026-11-08")))
    }

    @Test fun normStripsPunctuationAndCase() {
        assertEquals("i woke up", norm("  I   woke-up!! "))
        assertEquals("wallet upi", norm("Wallet / UPI"))
    }

    @Test fun csvRoundTrip() {
        assertEquals(listOf("a", "b"), uncsv(csv(listOf("a", "b"))))
        assertEquals(emptyList<String>(), uncsv(""))
        assertEquals(listOf("a", "b"), uncsv("a||b|"))
    }

    @Test fun titleCaseWorks() {
        assertEquals("Airport Lounge", titleCase("airport lounge"))
    }
}
