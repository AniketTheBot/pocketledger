package app.pocketledger.ml

import android.content.Context
import kotlin.math.exp

enum class SmsClass { NOT_TXN, DEBIT, CREDIT }

data class MlPrediction(
    val pNotTxn: Float,
    val pDebit: Float,
    val pCredit: Float,
) {
    val pTxn: Float get() = (pDebit + pCredit).coerceIn(0f, 1f)
    val label: SmsClass
        get() = when {
            pDebit >= pCredit && pDebit >= pNotTxn -> SmsClass.DEBIT
            pCredit >= pDebit && pCredit >= pNotTxn -> SmsClass.CREDIT
            else -> SmsClass.NOT_TXN
        }
}

interface SmsClassifier {
    fun classify(body: String): MlPrediction
}

class SoftmaxModel(val weights: Array<FloatArray>, val bias: FloatArray) {
    init {
        require(weights.size == 3) { "3 classes required" }
        require(weights.all { it.size == BundledModel.BUCKETS }) { "bucket width mismatch" }
        require(bias.size == 3) { "bias width mismatch" }
    }

    fun predict(buckets: IntArray): FloatArray {
        val logits = FloatArray(3)
        for (c in 0 until 3) {
            var sum = bias[c]
            val row = weights[c]
            for (b in buckets) sum += row[b]
            logits[c] = sum
        }
        val max = logits.max()
        var denom = 0f
        for (c in 0 until 3) {
            logits[c] = exp((logits[c] - max).toDouble()).toFloat()
            denom += logits[c]
        }
        for (c in 0 until 3) logits[c] /= denom
        return logits
    }

    fun predictProb(body: String): FloatArray = predict(TextFeatures.buckets(body))
    fun copyWeights(): Array<FloatArray> = Array(3) { weights[it].copyOf() }
    fun copyBias(): FloatArray = bias.copyOf()
}

private fun FloatArray.toPrediction() = MlPrediction(this[0], this[1], this[2])

class BundledClassifier : SmsClassifier {
    private val model = SoftmaxModel(BundledModel.WEIGHTS, BundledModel.BIAS)
    fun predictProb(body: String): FloatArray = model.predictProb(body)
    override fun classify(body: String): MlPrediction = model.predictProb(body).toPrediction()
}

/**
 * On-device SGD fine-tune of the bundled softmax model from Review labels.
 * One cross-entropy step per label; cheap enough to run on every Confirm/Discard.
 */
class LocalLearner(initialWeights: Array<FloatArray>, initialBias: FloatArray) {
    private val weights = Array(3) { initialWeights[it].copyOf() }
    private val bias = initialBias.copyOf()

    fun predictProb(body: String): FloatArray =
        SoftmaxModel(weights, bias).predictProb(body)

    @Synchronized
    fun learn(body: String, label: SmsClass, lr: Float = 0.05f) {
        val buckets = TextFeatures.buckets(body)
        val probs = predictProb(body)
        val labelIndex = when (label) {
            SmsClass.NOT_TXN -> 0
            SmsClass.DEBIT -> 1
            SmsClass.CREDIT -> 2
        }
        val grad = FloatArray(3) { c -> probs[c] - if (c == labelIndex) 1f else 0f }
        for (c in 0 until 3) {
            bias[c] -= lr * grad[c]
            val row = weights[c]
            for (b in buckets) row[b] -= lr * grad[c]
        }
    }

    fun copyWeights(): Array<FloatArray> = Array(3) { weights[it].copyOf() }
    fun copyBias(): FloatArray = bias.copyOf()
}

/**
 * Bundled model + locally learned adjustments.
 * - labels agree  -> local probabilities are authoritative
 * - labels differ -> averaged probabilities (deterministic, never silently confident)
 * Weights persist to filesDir so learning survives restarts. No network, ever.
 */
class HybridClassifier(private val context: Context? = null) : SmsClassifier {

    private val bundled = SoftmaxModel(BundledModel.WEIGHTS, BundledModel.BIAS)
    private val local: LocalLearner = loadLearner() ?: LocalLearner(BundledModel.WEIGHTS, BundledModel.BIAS)

    @Synchronized
    override fun classify(body: String): MlPrediction {
        val pb = bundled.predictProb(body)
        val pl = local.predictProb(body)
        val merged = if (pb.toPrediction().label == pl.toPrediction().label) {
            pl
        } else {
            FloatArray(3) { (pb[it] + pl[it]) / 2f }
        }
        return merged.toPrediction()
    }

    @Synchronized
    fun learn(body: String, label: SmsClass) {
        local.learn(body, label)
        persist()
    }

    private fun loadLearner(): LocalLearner? {
        val file = modelFile() ?: return null
        if (!file.exists()) return null
        return runCatching {
            val lines = file.readLines()
            if (lines.size < 5 || !lines[0].startsWith("PocketLedgerLearner")) return null
            val weights = Array(3) { c ->
                val values = lines[1 + c].trim().split(" ").map { it.toFloat() }
                require(values.size == BundledModel.BUCKETS)
                values.toFloatArray()
            }
            val bias = lines[4].trim().split(" ").map { it.toFloat() }.toFloatArray()
            require(bias.size == 3)
            LocalLearner(weights, bias)
        }.getOrNull()
    }

    private fun persist() {
        val file = modelFile() ?: return
        runCatching {
            val sb = StringBuilder("PocketLedgerLearner v1\n")
            local.copyWeights().forEach { row ->
                sb.append(row.joinToString(" ") { it.toString() })
                sb.append('\n')
            }
            sb.append(local.copyBias().joinToString(" ") { it.toString() })
            file.writeText(sb.toString())
        }
    }

    private fun modelFile() = context?.filesDir?.resolve("classifier_local.txt")
}
