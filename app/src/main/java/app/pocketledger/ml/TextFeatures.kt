package app.pocketledger.ml

/**
 * Feature extraction must match tools/train_classifier.mjs exactly:
 * lowercase, trim, collapse whitespace runs to one space; character n-grams
 * n in 2..4; binary presence; FNV-1a 32-bit over UTF-16 code units; % 1024.
 */
object TextFeatures {

    fun buckets(text: String): IntArray {
        val normalized = text.lowercase().trim().replace(Regex("\\s+"), " ")
        val present = BooleanArray(BundledModel.BUCKETS)
        for (n in 2..4) {
            if (normalized.length < n) continue
            for (i in 0..(normalized.length - n)) {
                present[fnvBucket(normalized, i, n)] = true
            }
        }
        var count = 0
        for (b in present) if (b) count++
        val out = IntArray(count)
        var k = 0
        for (b in present.indices) if (present[b]) out[k++] = b
        return out
    }

    private fun fnvBucket(text: String, start: Int, len: Int): Int {
        var h = 2166136261L
        val end = start + len
        for (i in start until end) {
            h = h xor (text[i].code.toLong() and 0xFFFF)
            h = (h * 16777619L) and 0xFFFFFFFFL
        }
        return (h % BundledModel.BUCKETS).toInt()
    }
}
