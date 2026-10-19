package com.mustafakoceerr.tilepdf.core.pdf

import java.time.DateTimeException
import java.time.OffsetDateTime
import java.time.ZoneOffset

private val PDF_DATE = Regex("""^(?:D:)?(\d{4})(\d{2})?(\d{2})?(\d{2})?(\d{2})?(\d{2})?(?:([Zz+\-])(\d{2})?'?(\d{2})?'?)?$""")

/**
 * A PDF date, `D:YYYYMMDDHHmmSSOHH'mm'`, as epoch milliseconds. Every part
 * after the year may be left out: the month and day then default to 1, the
 * rest to 0, and the offset to UTC. Null if [text] isn't such a date.
 */
fun pdfDate(text: String): Long? {
    val match = PDF_DATE.matchEntire(text.trim()) ?: return null
    val parts = match.groupValues
    fun number(index: Int, default: Int) = parts[index].ifEmpty { null }?.toInt() ?: default
    return try {
        val sign = if (parts[7] == "-") -1 else 1
        val offset = if (parts[7].isEmpty() || parts[7].equals("Z", ignoreCase = true)) {
            ZoneOffset.UTC
        } else {
            ZoneOffset.ofHoursMinutes(sign * number(8, 0), sign * number(9, 0))
        }
        OffsetDateTime.of(
            number(1, 0), number(2, 1), number(3, 1), number(4, 0), number(5, 0), number(6, 0), 0, offset,
        ).toInstant().toEpochMilli()
    } catch (_: DateTimeException) {
        null
    }
}
