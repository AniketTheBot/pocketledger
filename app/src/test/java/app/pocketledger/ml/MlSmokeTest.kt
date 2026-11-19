package app.pocketledger.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fast smoke coverage for the bundled model + local learning (complements ClassifierTest). */
class MlSmokeTest {

    @Test
    fun clearDebitIsClassifiedAsDebit() {
        val p = BundledClassifier().classify(
            "Rs.1,234.50 debited from a/c XX1234 on 19-11-26 to UPI/ANIKET/YESB. Avl Bal Rs.45,678.90"
        )
        assertEquals(SmsClass.DEBIT, p.label)
        assertTrue("pTxn=${p.pTxn}", p.pTxn > 0.8f)
    }

    @Test
    fun clearCreditIsClassifiedAsCredit() {
        val p = BundledClassifier().classify(
            "Rs.25,000.00 credited to a/c XX5678 towards NEFT CR-ACME CORP Ref 045123ABC"
        )
        assertEquals(SmsClass.CREDIT, p.label)
        assertTrue("pTxn=${p.pTxn}", p.pTxn > 0.8f)
    }

    @Test
    fun otpIsNotATransaction() {
        val p = BundledClassifier().classify("123456 is your OTP for HDFC netbanking. Do not share.")
        assertTrue("label=${p.label} pTxn=${p.pTxn}", p.label == SmsClass.NOT_TXN || p.pTxn < 0.5f)
    }

    @Test
    fun deterministicPredictions() {
        val c = BundledClassifier()
        val a = c.classify("You spent £12.40 at Tesco. Balance: £1,234.56")
        val b = c.classify("You spent £12.40 at Tesco. Balance: £1,234.56")
        assertEquals(a.pNotTxn.toRawBits(), b.pNotTxn.toRawBits())
        assertEquals(a.pDebit.toRawBits(), b.pDebit.toRawBits())
        assertEquals(a.pCredit.toRawBits(), b.pCredit.toRawBits())
    }

    @Test
    fun localLearnerShiftsTowardsLabel() {
        val learner = LocalLearner(BundledModel.WEIGHTS, BundledModel.BIAS)
        // Neutral text: the bundled model has no reason to favour DEBIT yet.
        val body = "Dinner plans are set for eight tonight"
        val before = learner.predictProb(body)[1]
        repeat(30) { learner.learn(body, SmsClass.DEBIT, lr = 0.1f) }
        val after = learner.predictProb(body)[1]
        assertTrue("before=$before after=$after", after > before + 0.01f)
    }
}
