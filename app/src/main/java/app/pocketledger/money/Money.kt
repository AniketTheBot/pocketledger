package app.pocketledger.money

/**
 * All money in PocketLedger is Long minor units (paise/cents).
 * Parsing and formatting are the only places strings touch money.
 */
object Money {

    private val SYMBOLS = mapOf("INR" to "₹", "GBP" to "£", "USD" to "$", "EUR" to "€")

    fun symbolFor(currency: String): String = SYMBOLS[currency] ?: ""

    /**
     * Parses user input like "24,861.15", "₹24861.1", "1.234,50", "-500".
     * Returns null if unparseable; max 2 decimals.
     */
    fun parseMinor(input: String): Long? {
        var s = input.trim()
        if (s.isEmpty()) return null
        val negative = s.startsWith("-") || s.startsWith("−")
        s = s.replace(Regex("[^0-9.,]"), "")
        if (s.isEmpty() || s == "." || s == ",") return null

        val euFormat = Regex("^\\d{1,3}(\\.\\d{3})+,\\d{1,2}$").matches(s) ||
            Regex("^\\d{1,3}(\\.\\d{3})+$").matches(s)

        val intPart: String
        val fracPart: String
        if (euFormat) {
            val parts = s.split(",")
            intPart = parts[0].replace(".", "")
            fracPart = parts.getOrElse(1) { "" }
        } else {
            if (s.count { it == '.' } > 1) return null
            val parts = s.split(".")
            intPart = parts[0].replace(",", "")
            fracPart = parts.getOrElse(1) { "" }
        }
        val digits = intPart.ifEmpty { "0" }
        if (!digits.all { it.isDigit() }) return null
        if (!fracPart.all { it.isDigit() } || fracPart.length > 2) return null
        val frac = fracPart.padEnd(2, '0')
        val whole = digits.toLongOrNull() ?: return null
        if (whole > 9_999_999_999L) return null
        val value = whole * 100 + (frac.toLongOrNull() ?: 0)
        return if (negative) -value else value
    }

    fun format(minor: Long, currency: String = "INR", withSymbol: Boolean = true): String {
        val negative = minor < 0
        val abs = if (minor == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(minor)
        val whole = abs / 100
        val frac = abs % 100
        val grouped = if (currency == "INR") groupIndian(whole) else groupThousands(whole)
        val body = if (withSymbol) {
            symbolFor(currency) + grouped + "." + frac.toString().padStart(2, '0')
        } else {
            grouped + "." + frac.toString().padStart(2, '0')
        }
        return if (negative) "−$body" else body
    }

    fun formatSigned(minor: Long, currency: String = "INR"): String {
        val sign = if (minor >= 0) "+" else ""
        return sign + format(minor, currency)
    }

    /** Plain decimal for CSV/JSON export: 1234.56 (no grouping, no symbol). */
    fun toDecimal(minor: Long): String {
        val negative = minor < 0
        val abs = kotlin.math.abs(minor)
        val s = (abs / 100).toString() + "." + (abs % 100).toString().padStart(2, '0')
        return if (negative) "-$s" else s
    }

    private fun groupIndian(n: Long): String {
        val s = n.toString()
        if (s.length <= 3) return s
        val last3 = s.takeLast(3)
        var rest = s.dropLast(3)
        val groups = ArrayDeque<String>()
        while (rest.length > 2) {
            groups.addFirst(rest.takeLast(2))
            rest = rest.dropLast(2)
        }
        if (rest.isNotEmpty()) groups.addFirst(rest)
        return groups.joinToString(",") + "," + last3
    }

    private fun groupThousands(n: Long): String {
        val s = n.toString()
        if (s.length <= 3) return s
        val groups = ArrayDeque<String>()
        var rest = s
        while (rest.length > 3) {
            groups.addFirst(rest.takeLast(3))
            rest = rest.dropLast(3)
        }
        if (rest.isNotEmpty()) groups.addFirst(rest)
        return groups.joinToString(",")
    }
}
