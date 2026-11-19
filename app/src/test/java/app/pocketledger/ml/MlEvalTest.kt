package app.pocketledger.ml

import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream

/**
 * JVM-side evaluation of the bundled model against the trainer's held-out
 * fixtures (eval_holdout.jsonl / eval_adversarial.jsonl, also under
 * app/src/test/resources/). Metrics mirror tools/train_classifier.mjs:
 * confusion[actual][predicted] with argmax = trainer's tie rule, then
 * binary txn P/R and direction accuracy over the same cells.
 *
 * Gates are the same ones the trainer enforces:
 *   holdout txn precision >= 0.99, recall >= 0.90, adversarial precision >= 0.95.
 */
class MlEvalTest {

    private val classifier = BundledClassifier()

    private fun loadJsonl(name: String): List<Pair<String, SmsClass>> {
        val stream: InputStream? = javaClass.classLoader?.getResourceAsStream(name)
            ?: error("fixture $name not found on test classpath")
        return stream!!.bufferedReader().readLines()
            .filter { it.isNotBlank() }
            .map { line ->
                val o = JSONObject(line)
                o.getString("body") to SmsClass.valueOf(o.getString("label"))
            }
    }

    private fun evaluate(rows: List<Pair<String, SmsClass>>): Metrics {
        val matrix = Array(3) { IntArray(3) }
        val misses = mutableListOf<String>()
        for ((body, actual) in rows) {
            val a = actual.ordinal
            val p = classifier.classify(body).label.ordinal
            matrix[a][p]++
            if (a != p) {
                misses += "$actual->" + SmsClass.values()[p] + " :: " + body.take(90)
            }
        }
        return Metrics(matrix, misses)
    }

    class Metrics(val matrix: Array<IntArray>, val misses: List<String>) {
        fun binaryTxn(): Triple<Double, Double, Double> {
            val tp = matrix[1][1] + matrix[1][2] + matrix[2][1] + matrix[2][2]
            val fp = matrix[0][1] + matrix[0][2]
            val fn = matrix[1][0] + matrix[2][0]
            val precision = if (tp + fp == 0) 0.0 else tp.toDouble() / (tp + fp)
            val recall = if (tp + fn == 0) 0.0 else tp.toDouble() / (tp + fn)
            val f1 = if (precision + recall == 0.0) 0.0 else 2 * precision * recall / (precision + recall)
            return Triple(precision, recall, f1)
        }

        fun directionAccuracy(): Double {
            val correct = matrix[1][1] + matrix[2][2]
            val txn = matrix[1][1] + matrix[1][2] + matrix[2][1] + matrix[2][2]
            return if (txn == 0) 0.0 else correct.toDouble() / txn
        }

        fun perClassF1(): List<Double> = (0..2).map { c ->
            val tp = matrix[c][c]
            var fp = 0
            var fn = 0
            for (i in 0..2) {
                if (i != c) {
                    fp += matrix[i][c]
                    fn += matrix[c][i]
                }
            }
            val p = if (tp + fp == 0) 0.0 else tp.toDouble() / (tp + fp)
            val r = if (tp + fn == 0) 0.0 else tp.toDouble() / (tp + fn)
            if (p + r == 0.0) 0.0 else 2 * p * r / (p + r)
        }
    }

    @Test
    fun holdoutGates() {
        val rows = loadJsonl("eval_holdout.jsonl")
        val m = evaluate(rows)
        val (p, r, f1) = m.binaryTxn()
        println(
            "HOLDOUT n=${rows.size} txn P=$p R=$r F1=$f1 " +
                "dirAcc=${m.directionAccuracy()} perClassF1=${m.perClassF1()} " +
                "matrix=${m.matrix.joinToString { it.joinToString(",") }} " +
                "misses=${m.misses.take(10)}"
        )
        assertTrue("holdout txn precision $p < 0.99", p >= 0.99)
        assertTrue("holdout txn recall $r < 0.90", r >= 0.90)
    }

    @Test
    fun adversarialGate() {
        val rows = loadJsonl("eval_adversarial.jsonl")
        val m = evaluate(rows)
        val (p, r, f1) = m.binaryTxn()
        println(
            "ADVERSARIAL n=${rows.size} txn P=$p R=$r F1=$f1 " +
                "dirAcc=${m.directionAccuracy()} " +
                "matrix=${m.matrix.joinToString { it.joinToString(",") }} " +
                "misses=${m.misses}"
        )
        assertTrue("adversarial txn precision $p < 0.95", p >= 0.95)
    }
}
