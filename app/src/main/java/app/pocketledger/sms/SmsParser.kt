package app.pocketledger.sms

import app.pocketledger.money.Money

/**
 * Deterministic field extraction from a bank SMS.
 *
 * [isTransaction] is only true when the text supports both a direction and a
 * currency-prefixed transaction amount. Junk messages never become
 * transactions, even when they happen to contain a direction word.
 */
data class ParsedSms(
    val isTransaction: Boolean,
    val direction: String?,      // "DEBIT" | "CREDIT" (null if unknown/ambiguous)
    val amountMinor: Long?,      // transaction amount in minor units
    val currency: String?,       // ISO: INR | GBP | USD | EUR (null if unknown)
    val accountTail: String?,    // digits only, e.g. "1234"
    val ref: String?,            // UTR/RRN/UPI reference if present
    val merchant: String?,       // best-effort payee/merchant token
    val reportedBalanceMinor: Long?, // informational only
    val junk: String?,           // OTP | PROMO | BALANCE_ONLY | SERVICE | UNPARSEABLE | null
    val notes: List<String> = emptyList(),
)

/**
 * Pure-Kotlin parser for Indian/UK/US bank SMS. No Android imports, no I/O,
 * no sender whitelist (the pipeline owns that decision).
 *
 * Design rule: never guess. Every returned field must be supported by the
 * body text; when support is missing the field stays null.
 */
object SmsParser {

    private const val JUNK_OTP = "OTP"
    private const val JUNK_PROMO = "PROMO"
    private const val JUNK_BALANCE_ONLY = "BALANCE_ONLY"
    private const val JUNK_SERVICE = "SERVICE"
    private const val JUNK_UNPARSEABLE = "UNPARSEABLE"

    private const val DIRECTION_DEBIT = "DEBIT"
    private const val DIRECTION_CREDIT = "CREDIT"

    // ------------------------------------------------------------------
    // Junk / service detection
    // ------------------------------------------------------------------

    private val OTP_RE = Regex(
        "(?i)\\b(?:otp|one[\\s-]+time[\\s-]+password|verification[\\s-]+code)\\b" +
            "|\\bdo\\s+not\\s+share\\b"
    )

    private val PROMO_RE = Regex(
        "(?i)\\b(?:promo\\w*|pre[\\s-]?approved|personal\\s+loan|cashback\\s+offer" +
            "|click\\s+here|kyc\\s+pending|emi\\s+due|offer(?:ed|s)?|win(?:ner)?)\\b"
    )

    private val SERVICE_RE = Regex(
        "(?i)\\b(?:statement\\s+(?:is\\s+)?ready|blocked|unblocked|limit\\s+increased)\\b"
    )

    /** Any word that suggests the message is reporting a balance. */
    private val BALANCE_RE = Regex("(?i)\\b(?:balance|bal|avl|avail(?:able)?)\\b")

    /**
     * Strong transaction verbs used only for the BALANCE_ONLY veto. Nouns such
     * as "salary", "cashback", "interest" and card-type words such as "debit"
     * / "ATM" deliberately do not count here: "ATM card balance" is still a
     * balance-only message.
     */
    private val TXN_VERB_RE = Regex(
        "(?i)(?:\\b(?:debited|spent|paid|purchase(?:d|s)?|withdraw(?:al|als|n)?" +
            "|charged|deduct(?:ed|ion)?|credited|received|deposit(?:ed|s)?" +
            "|refund(?:ed|s)?|added\\s+to|transfer(?:red)?\\s+to|used\\s+(?:at|for))\\b" +
            "|\\b(?:imps|neft)\\s+cr\\b|\\bcr-(?=[a-z0-9]))"
    )

    // ------------------------------------------------------------------
    // Direction detection
    // ------------------------------------------------------------------

    private val DEBIT_RE = Regex(
        "(?i)\\b(?:debit(?:ed)?|spent|paid|purchases?|withdraw(?:al|als|n)?" +
            "|atm|charged|deduct(?:ed|ion)?|used\\s+(?:at|for)|card\\s+used" +
            "|transfer(?:red)?\\s+to)\\b"
    )

    private val CREDIT_RE = Regex(
        "(?i)(?:\\b(?:credited|received|deposit(?:ed|s)?|refund(?:ed|s)?" +
            "|salary|cashback|interest|added\\s+to|imps\\s+cr|neft\\s+cr)\\b" +
            "|\\bcr-(?=[a-z0-9]))"
    )

    /** Refund / reversal wording always resolves to a credit. */
    private val REVERSAL_RE = Regex(
        "(?i)\\b(?:reversal|reversed|chargeback|refund(?:ed|s)?)\\b"
    )

    // ------------------------------------------------------------------
    // Amount extraction
    // ------------------------------------------------------------------

    /**
     * Matches a currency prefix plus a western, Indian-grouped or European
     * number. Group 1/3 are optional minus signs, group 2 the currency and
     * group 4 the numeric token handed to [Money.parseMinor].
     */
    private val AMOUNT_RE = Regex(
        "(?i)([-−])?\\s*" +
            "(₹|£|\\$|€|(?<![A-Za-z])(?:Rs\\.?|INR|GBP|USD|EUR)(?![A-Za-z]))\\s*" +
            "([-−])?\\s*" +
            "(\\d{1,3}(?:\\.\\d{3})+(?:,\\d{1,2})?" +
            "|\\d{1,3}(?:,\\d{2,3})+(?:\\.\\d{1,2})?" +
            "|\\d+(?:\\.\\d{1,2})?)" +
            "(?![0-9]|\\.[0-9]|,[0-9])"
    )

    /** The last balance word in a segment, used to label the amount after it. */
    private val BALANCE_WORD_RE = Regex("(?i)\\b(?:balance|bal|avl|avail(?:able)?)\\b")

    /** Filler words allowed between a balance label and its amount. */
    private val BALANCE_FILLER = setOf(
        "is", "was", "now", "new", "the", "of", "on", "as", "at",
        "current", "available", "closing", "amount", "date", "your",
        "account", "a/c", "no",
        "jan", "january", "feb", "february", "mar", "march", "apr", "april",
        "may", "jun", "june", "jul", "july", "aug", "august", "sep", "sept",
        "september", "oct", "october", "nov", "november", "dec", "december",
    )

    private val DATE_TOKEN_RE =
        Regex("\\d{1,4}(?:st|nd|rd|th)?(?:[-/.]\\d{1,4}(?:st|nd|rd|th)?)*")

    // ------------------------------------------------------------------
    // Account tail / reference
    // ------------------------------------------------------------------

    private val TAIL_RE = Regex(
        "(?i)(?:\\b(?:a\\s*/\\s*c|account|acct|card)\\b|x{2,4}|\\*{2,6})" +
            "[\\s.:#~-]*(?:no\\.?|number|ending(?:\\s+(?:with|in))?|last)?" +
            "[\\s.:#~-]*(?:x{2,4}|\\*{2,6})?[\\s.:#~-]*(\\d{3,6})(?!\\d)"
    )

    private val REF_RE = Regex(
        "(?i)\\b(?:ref(?:erence)?|txn|utr|rrn|cheque|upi\\s*[/ ]?\\s*ref)" +
            "(?:\\s*(?:no\\.?|number|id))?(?![A-Za-z])[.:\\s#/-]*" +
            "([A-Za-z0-9]{6,22})(?![A-Za-z0-9])"
    )

    // ------------------------------------------------------------------
    // Merchant extraction
    // ------------------------------------------------------------------

    private val UPI_TO_RE = Regex(
        "(?i)\\bto\\s+(UPI/[A-Z0-9][A-Z0-9._-]{1,30})"
    )

    private val AT_RE = Regex(
        "(?i)\\bat\\s+([A-Z0-9][A-Z0-9&'._/-]*(?:\\s+[A-Z0-9&'._/-]+){0,3})"
    )

    private val TO_VIA_UPI_RE = Regex(
        "(?i)\\bto\\s+([A-Z0-9][A-Z0-9&'._/-]*(?:\\s+[A-Z0-9&'._/-]+){0,3})" +
            "\\s+via\\s+UPI\\b"
    )

    private val FROM_RE = Regex(
        "(?i)\\bfrom\\s+([A-Z0-9][A-Z0-9&'._/-]*(?:\\s+[A-Z0-9&'._/-]+){0,3})"
    )

    private val BY_RE = Regex(
        "(?i)\\bcredited\\s+by\\s+([A-Z0-9][A-Z0-9&'._/-]*(?:\\s+[A-Z0-9&'._/-]+){0,3})"
    )

    private val MERCHANT_STOP_RE = Regex(
        "(?i)\\s+(?:via|on|for|with|from|at|towards|using|ref(?:erence)?" +
            "|bal(?:ance)?|avl|avail(?:able)?|dated|the)\\b"
    )

    private val ACCOUNT_MASK_RE = Regex("^(?:x{2,4}|\\*{2,6})\\s*\\d+")

    private val MERCHANT_DENY = setOf(
        "atm", "upi", "pos", "card", "account", "acct", "a/c", "wallet",
        "balance", "bal", "bank", "your", "the", "new",
    )

    /** Payment-rail / reference vocabulary is never a merchant name. */
    private val MERCHANT_NON_NAMES = setOf(
        "neft", "imps", "rtgs", "ach", "sepa", "cheque", "ref", "reference",
        "txn", "utr", "rrn", "id",
    )

    private val BANK_SUFFIXES = setOf("bank", "ltd", "limited", "pvt", "plc", "inc")

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Parses [body] as sent by [senderRaw]. The sender is intentionally not
     * used for field extraction; sender whitelisting belongs to the pipeline.
     */
    @Suppress("UNUSED_PARAMETER")
    fun parse(senderRaw: String, body: String): ParsedSms {
        val text = body.trim()
        if (text.isEmpty()) {
            return ParsedSms(
                isTransaction = false,
                direction = null,
                amountMinor = null,
                currency = null,
                accountTail = null,
                ref = null,
                merchant = null,
                reportedBalanceMinor = null,
                junk = null,
                notes = listOf("no_amount"),
            )
        }

        val debitMatches = DEBIT_RE.findAll(text).toList()
        val creditMatches = CREDIT_RE.findAll(text).toList()
        val amounts = extractAmounts(text)
        val accountTail = TAIL_RE.find(text)?.groupValues?.get(1)
        val ref = REF_RE.find(text)?.groupValues?.get(1)

        val junkClass = detectJunk(text)
        if (junkClass != null) {
            // Junk wins, but cheap fields are still reported for the audit trail.
            val nonBalance = amounts.filter { !it.isBalance }
            val labeledBalances = amounts.filter { it.isBalance }
            val chosen = if (junkClass == JUNK_BALANCE_ONLY) null else nonBalance.firstOrNull()
            // For a balance-only message the amount after the label is the
            // balance; if the label came after the number, fall back to the
            // last amount in the body.
            val reportedHit = if (junkClass == JUNK_BALANCE_ONLY) {
                labeledBalances.lastOrNull() ?: amounts.lastOrNull()
            } else {
                labeledBalances.lastOrNull()
            }
            return ParsedSms(
                isTransaction = false,
                direction = null,
                amountMinor = chosen?.absoluteMinor(),
                currency = chosen?.currency ?: reportedHit?.currency,
                accountTail = accountTail,
                ref = ref,
                merchant = extractMerchant(text, null),
                reportedBalanceMinor = reportedHit?.valueMinor,
                junk = junkClass,
                notes = emptyList(),
            )
        }

        val keywordRanges = debitMatches.map { it.range } + creditMatches.map { it.range }
        val chosen = selectTransactionAmount(amounts, keywordRanges)
        val balanceAmounts = amounts.filter { it.isBalance }
        val reportedBalance = balanceAmounts.lastOrNull()?.valueMinor

        val hasDebit = debitMatches.isNotEmpty()
        val hasCredit = creditMatches.isNotEmpty()
        val forceCredit = REVERSAL_RE.containsMatchIn(text)
        val ambiguous = !forceCredit && hasDebit && hasCredit

        var direction: String? = when {
            forceCredit -> DIRECTION_CREDIT
            ambiguous -> null
            hasDebit -> DIRECTION_DEBIT
            hasCredit -> DIRECTION_CREDIT
            else -> null
        }

        // A signed negative amount flips the stated direction. The ledger keeps
        // the magnitude and the (possibly flipped) direction separately.
        if (!forceCredit && !ambiguous && chosen != null && chosen.negative) {
            direction = when (direction) {
                DIRECTION_DEBIT -> DIRECTION_CREDIT
                DIRECTION_CREDIT -> DIRECTION_DEBIT
                else -> direction
            }
        }

        val notes = mutableListOf<String>()
        var junk: String? = null
        when {
            chosen == null -> notes += "no_amount"
            ambiguous -> notes += "ambiguous_direction"
            direction == null -> {
                junk = JUNK_UNPARSEABLE
                notes += "direction_missing"
            }
        }

        val isTransaction =
            junk == null && chosen != null && (direction != null || ambiguous)

        return ParsedSms(
            isTransaction = isTransaction,
            direction = direction,
            amountMinor = chosen?.absoluteMinor(),
            currency = chosen?.currency ?: balanceAmounts.lastOrNull()?.currency,
            accountTail = accountTail,
            ref = ref,
            merchant = extractMerchant(text, direction),
            reportedBalanceMinor = reportedBalance,
            junk = junk,
            notes = notes,
        )
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private fun detectJunk(text: String): String? {
        if (OTP_RE.containsMatchIn(text)) return JUNK_OTP
        if (PROMO_RE.containsMatchIn(text)) return JUNK_PROMO
        // Service messages are checked before the generic balance-only rule:
        // "your card is blocked, balance Rs.X" is a service message.
        if (SERVICE_RE.containsMatchIn(text)) return JUNK_SERVICE
        if (BALANCE_RE.containsMatchIn(text) && !TXN_VERB_RE.containsMatchIn(text)) {
            return JUNK_BALANCE_ONLY
        }
        return null
    }

    private data class AmountHit(
        val valueMinor: Long,
        val currency: String,
        val start: Int,
        val endExclusive: Int,
        val negative: Boolean,
        val isBalance: Boolean,
    )

    private fun AmountHit.absoluteMinor(): Long = kotlin.math.abs(valueMinor)

    private fun extractAmounts(text: String): List<AmountHit> {
        val result = mutableListOf<AmountHit>()
        var previousEnd = 0
        for (match in AMOUNT_RE.findAll(text)) {
            val currency = currencyFor(match.groupValues[2]) ?: continue
            val number = match.groupValues[4]
            if (number.isEmpty()) continue
            val negative =
                match.groupValues[1].isNotEmpty() || match.groupValues[3].isNotEmpty()
            val minor = Money.parseMinor((if (negative) "-" else "") + number) ?: continue

            val start = match.range.first
            val segment = text.substring(previousEnd.coerceAtMost(start), start)
            result += AmountHit(
                valueMinor = minor,
                currency = currency,
                start = start,
                endExclusive = match.range.last + 1,
                negative = negative,
                isBalance = hasBalanceLabel(segment),
            )
            previousEnd = match.range.last + 1
        }
        return result
    }

    /**
     * A balance label only applies to the amount that immediately follows it.
     * Looking at the segment since the previous amount prevents a balance that
     * appears before the transaction amount from swallowing that amount.
     * The filler check keeps "balance is low. Rs.500 debited" from being
     * mistaken for a balance figure.
     */
    private fun hasBalanceLabel(segment: String): Boolean {
        val label = BALANCE_WORD_RE.findAll(segment).lastOrNull() ?: return false
        val remainder = segment.substring(label.range.last + 1).trim().lowercase()
        if (remainder.isEmpty()) return true

        val tokens = remainder.split(Regex("[\\s:.,;#\\-]+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return true

        return tokens.all { token ->
            token in BALANCE_FILLER || DATE_TOKEN_RE.matches(token)
        }
    }

    private fun selectTransactionAmount(
        amounts: List<AmountHit>,
        keywordRanges: List<IntRange>,
    ): AmountHit? {
        val candidates = amounts.filter { !it.isBalance }
        if (candidates.isEmpty()) return null
        if (keywordRanges.isEmpty()) return candidates.first()

        fun distance(hit: AmountHit): Int = keywordRanges.minOf { range ->
            when {
                hit.endExclusive <= range.first -> range.first - hit.endExclusive
                range.last < hit.start -> hit.start - range.last
                else -> 0
            }
        }

        val closest = candidates.minWithOrNull(
            compareBy<AmountHit>({ distance(it) }, { it.start })
        ) ?: return null

        return if (distance(closest) <= 30) closest else candidates.first()
    }

    private fun currencyFor(raw: String): String? {
        return when (raw.uppercase().replace(".", "").trim()) {
            "₹", "RS", "INR" -> "INR"
            "£", "GBP" -> "GBP"
            "$", "USD" -> "USD"
            "€", "EUR" -> "EUR"
            else -> null
        }
    }

    private fun extractMerchant(text: String, direction: String?): String? {
        // For credits the payer is the useful field; for debits the card/UPI
        // merchant is. Keep the order deterministic.
        if (direction == DIRECTION_CREDIT) {
            FROM_RE.find(text)?.groupValues?.get(1)?.let { raw ->
                cleanMerchant(raw)?.let { return it }
            }
            BY_RE.find(text)?.groupValues?.get(1)?.let { raw ->
                cleanMerchant(raw)?.let { return it }
            }
        }

        UPI_TO_RE.find(text)?.groupValues?.get(1)?.let { raw ->
            cleanMerchant(raw)?.let { return it }
        }
        AT_RE.find(text)?.groupValues?.get(1)?.let { raw ->
            cleanMerchant(raw)?.let { return it }
        }
        TO_VIA_UPI_RE.find(text)?.groupValues?.get(1)?.let { raw ->
            cleanMerchant(raw)?.let { return it }
        }

        if (direction == null) {
            FROM_RE.find(text)?.groupValues?.get(1)?.let { raw ->
                cleanMerchant(raw)?.let { return it }
            }
            BY_RE.find(text)?.groupValues?.get(1)?.let { raw ->
                cleanMerchant(raw)?.let { return it }
            }
        }

        return null
    }

    private fun cleanMerchant(raw: String): String? {
        var value = raw.trim()
        if (value.isEmpty()) return null

        MERCHANT_STOP_RE.find(value)?.let { stop ->
            value = value.substring(0, stop.range.first)
        }

        value = value
            .trim()
            .trimEnd('.', ',', ';', ':', '!', '?', '-', '/', '*')
            .trim()
            .replace(Regex("\\s+"), " ")

        if (value.isEmpty()) return null

        val lower = value.lowercase()
        if (lower.length < 2) return null
        if (lower in MERCHANT_DENY) return null
        if (lower.startsWith("your ") || lower.contains("a/c")) return null
        if (lower.startsWith("account") || lower.startsWith("acct")) return null
        if (ACCOUNT_MASK_RE.containsMatchIn(lower)) return null
        if (lower.startsWith("card ") && lower.any { it.isDigit() }) return null
        if (lower.substringBefore(' ') in MERCHANT_NON_NAMES) return null
        if (lower.endsWith(" account")) return null
        if (lower.first().isDigit()) return null
        if (lower.none { it.isLetter() }) return null

        val tokens = value.split(" ").toMutableList()
        while (tokens.size > 1 &&
            tokens.last().lowercase().trimEnd('.') in BANK_SUFFIXES
        ) {
            tokens.removeAt(tokens.lastIndex)
        }

        value = tokens.joinToString(" ").trim()
        return value.ifEmpty { null }
    }
}
