package app.pocketledger.ml

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassifierTest {

    /**
     * The trainer exports these vectors in `tools/eval_report.json`
     * (`feature_fixture`). Any change to hashing, normalization or n-gram
     * ranges must be applied to both sides and these vectors regenerated.
     */
    @Test
    fun textFeaturesMatchesTrainerFixture() {
        val fixtures = listOf(
            "Rs.1,234.50 debited from a/c XX1234 on 19-11-26" to intArrayOf(
                4, 5, 16, 25, 31, 38, 46, 47, 51, 58, 64, 91, 101, 111, 117, 118,
                125, 132, 137, 138, 139, 140, 144, 160, 175, 180, 195, 198, 199, 210, 216, 222,
                246, 249, 258, 260, 263, 270, 271, 285, 293, 342, 362, 365, 379, 384, 402, 423,
                431, 452, 454, 460, 475, 492, 500, 506, 507, 522, 523, 529, 547, 548, 555, 558,
                560, 569, 570, 576, 601, 642, 644, 654, 674, 675, 678, 681, 690, 698, 700, 707,
                720, 729, 738, 748, 750, 753, 756, 758, 763, 765, 774, 778, 779, 784, 786, 792,
                795, 812, 823, 831, 834, 862, 868, 870, 886, 889, 897, 900, 904, 905, 930, 953,
                957, 958, 962, 970, 971, 975, 976, 980, 983, 989, 1004, 1012,
            ),
            "  You   spent £12.40 at Tesco.  " to intArrayOf(
                7, 16, 33, 49, 55, 58, 105, 125, 139, 141, 156, 159, 169, 173, 204, 205,
                231, 271, 278, 285, 307, 313, 361, 362, 366, 392, 394, 409, 467, 508, 513, 515,
                522, 524, 543, 552, 557, 564, 574, 592, 593, 601, 606, 651, 654, 690, 702, 704,
                714, 730, 766, 771, 773, 784, 852, 888, 910, 912, 939, 951, 980, 987, 988, 996,
                1008, 1009, 1012, 1014, 1021, 1023,
            ),
            "123456 is your OTP for HDFC netbanking. Do not share." to intArrayOf(
                0, 8, 20, 45, 60, 66, 69, 71, 77, 89, 90, 91, 97, 111, 136, 138,
                139, 156, 164, 182, 186, 204, 210, 234, 248, 253, 259, 260, 262, 267, 268, 271,
                276, 281, 282, 293, 300, 309, 313, 326, 329, 336, 344, 346, 354, 356, 362, 367,
                388, 393, 397, 412, 430, 435, 442, 466, 469, 476, 493, 507, 513, 514, 515, 516,
                518, 522, 524, 528, 530, 560, 564, 566, 567, 569, 574, 582, 586, 590, 591, 592,
                594, 599, 606, 618, 623, 626, 638, 639, 643, 654, 658, 663, 668, 670, 674, 682,
                698, 705, 712, 716, 723, 733, 735, 742, 757, 762, 764, 765, 773, 781, 786, 789,
                795, 809, 811, 822, 825, 828, 862, 866, 886, 894, 897, 912, 918, 926, 930, 933,
                934, 935, 938, 940, 945, 962, 963, 964, 972, 982, 985, 996, 1002, 1009, 1015, 1018,
            ),
            "Your A/c XX1234 balance is Rs.45,678.90 as on 19-11" to intArrayOf(
                5, 9, 16, 25, 30, 40, 46, 51, 58, 61, 63, 64, 67, 75, 78, 87,
                91, 101, 108, 111, 118, 122, 127, 137, 139, 141, 156, 166, 180, 184, 198, 199,
                204, 208, 210, 242, 246, 260, 263, 265, 271, 278, 281, 285, 293, 326, 327, 330,
                332, 342, 354, 362, 365, 375, 376, 383, 401, 402, 416, 423, 434, 452, 453, 458,
                463, 475, 478, 480, 506, 513, 515, 518, 522, 531, 538, 543, 555, 556, 558, 560,
                564, 582, 616, 627, 642, 658, 661, 679, 682, 694, 707, 712, 716, 720, 728, 742,
                747, 750, 751, 756, 758, 765, 778, 784, 786, 789, 795, 812, 818, 824, 825, 831,
                834, 843, 848, 862, 863, 886, 889, 897, 900, 902, 905, 930, 935, 948, 953, 957,
                960, 962, 970, 976, 980, 983, 989, 1009,
            ),
            "₹1,23,456.00 credited to a/c XX5678" to intArrayOf(
                0, 5, 16, 28, 36, 46, 47, 54, 58, 63, 101, 109, 111, 116, 118, 123,
                144, 161, 199, 215, 216, 220, 238, 239, 246, 260, 263, 271, 276, 280, 282, 285,
                330, 336, 342, 346, 353, 354, 365, 392, 393, 401, 407, 423, 460, 462, 469, 479,
                485, 487, 493, 504, 522, 529, 530, 538, 588, 589, 591, 593, 601, 608, 622, 644,
                648, 661, 666, 681, 690, 703, 738, 763, 773, 774, 778, 780, 786, 796, 810, 828,
                849, 870, 886, 902, 906, 914, 922, 925, 953, 976, 979, 1002,
            ),
        )

        for ((text, expected) in fixtures) {
            assertArrayEquals("bucket mismatch for \"$text\"", expected, TextFeatures.buckets(text))
        }
    }

    @Test
    fun textFeaturesNormalizesCaseAndWhitespace() {
        assertArrayEquals(
            TextFeatures.buckets("you spent £12.40 at tesco."),
            TextFeatures.buckets("  You   spent £12.40 at Tesco.  "),
        )
    }

    @Test
    fun bundledClassifierDetectsTransactions() {
        val classifier = BundledClassifier()
        val samples = listOf(
            "Rs.1,234.50 debited from a/c XX1234 on 19-11-26 to UPI/ANIKET/YESB. Avl Bal Rs.45,678.90",
            "You spent £12.40 at Tesco. Balance: £1,234.56",
            "CHASE: $45.20 debit card purchase at STARBUCKS. Avail bal $1,234.56",
            "Rs.25,000.00 credited to a/c XX5678 towards NEFT CR-ACME CORP Ref 045123ABC",
            "Your account ****4321 was debited £50.00 on 19/11/2026. Available balance £890.12",
            "£1,500.00 credited by SALARY CO on 19 Nov. Balance £2,345.67",
            "You have received Rs.299.00 in your a/c XX1234 from RAM via UPI",
            "Paytm: Rs.100 added to your wallet. New balance Rs.340",
            "Card xx4021 used for $25.99 at AMAZON.COM",
            "Aapke account se Rs.950 debit ho gaya. Avl bal Rs.23,450.",
            "INR 750 has been paid to ZOMATO using your ICICI UPI.",
            "$2,000.00 was deposited into your account ending 3344.",
        )

        for (body in samples) {
            val prediction = classifier.classify(body)
            assertNotEquals("expected a transaction: $body", SmsClass.NOT_TXN, prediction.label)
            assertTrue(
                "expected pTxn > 0.8 for: $body (was ${prediction.pTxn})",
                prediction.pTxn > 0.8f,
            )
        }
    }

    @Test
    fun bundledClassifierRejectsJunk() {
        val classifier = BundledClassifier()
        val samples = listOf(
            "123456 is your OTP for HDFC netbanking. Do not share.",
            "Get pre-approved personal loan up to Rs.5,00,000! Apply now.",
            "Your A/c XX1234 balance is Rs.45,678.90 as on 19-11",
            "Your card ending 4321 has been blocked. Call 1800123456.",
            "Can you send me ₹500 on GPay? I will return it tonight.",
            "Your parcel from Amazon is out for delivery. Track it in the app.",
            "Your OTP for a payment of ₹4,999 to FLIPKART is 998877. Never share.",
            "Your EMI of ₹8,750 will be debited on 05 Dec. Keep sufficient balance.",
        )

        for (body in samples) {
            val prediction = classifier.classify(body)
            assertTrue(
                "expected junk: $body (label=${prediction.label}, pTxn=${prediction.pTxn})",
                prediction.label == SmsClass.NOT_TXN || prediction.pTxn < 0.5f,
            )
        }
    }

    @Test
    fun bundledPredictionsAreDeterministic() {
        val body = "Rs.1,234.50 debited from a/c XX1234 on 19-11-26 to UPI/ANIKET/YESB."
        val first = BundledClassifier().classify(body)
        val second = BundledClassifier().classify(body)

        assertEquals(first.pNotTxn.toRawBits(), second.pNotTxn.toRawBits())
        assertEquals(first.pDebit.toRawBits(), second.pDebit.toRawBits())
        assertEquals(first.pCredit.toRawBits(), second.pCredit.toRawBits())
        assertEquals(1.0, (first.pNotTxn + first.pDebit + first.pCredit).toDouble(), 1e-4)
    }

    @Test
    fun localLearnerMovesTowardsTheReviewedLabel() {
        // Clear debit phrasing; the exact wording is not in the corpus.
        val debitBody = "Payment of 42.00 GBP to UBER EATS was taken from your account."
        val debitLearner = LocalLearner(BundledModel.WEIGHTS, BundledModel.BIAS)
        val debitBefore = debitLearner.predictProb(debitBody)[1]
        repeat(30) { debitLearner.learn(debitBody, SmsClass.DEBIT, lr = 0.1f) }
        val debitAfter = debitLearner.predictProb(debitBody)[1]
        assertTrue(
            "pDebit should rise after review labels: $debitBefore -> $debitAfter",
            debitAfter > debitBefore + 0.05f,
        )

        val junkBody = "Your subscription of ₹999 renews on 05 Dec."
        val junkLearner = LocalLearner(BundledModel.WEIGHTS, BundledModel.BIAS)
        val junkBefore = junkLearner.predictProb(junkBody)[0]
        repeat(30) { junkLearner.learn(junkBody, SmsClass.NOT_TXN, lr = 0.1f) }
        val junkAfter = junkLearner.predictProb(junkBody)[0]
        assertTrue(
            "pNotTxn should rise after review labels: $junkBefore -> $junkAfter",
            junkAfter > junkBefore,
        )
    }

    @Test
    fun hybridClassifierMergesDeterministicallyWithoutContext() {
        val classifier = HybridClassifier(null)
        val body = "Rs.1,234.50 debited from a/c XX1234 on 19-11-26 to UPI/ANIKET/YESB."
        val first = classifier.classify(body)
        val second = classifier.classify(body)

        assertEquals(first.pNotTxn.toRawBits(), second.pNotTxn.toRawBits())
        assertEquals(first.pDebit.toRawBits(), second.pDebit.toRawBits())
        assertEquals(first.pCredit.toRawBits(), second.pCredit.toRawBits())
        assertTrue(first.pTxn > 0.8f)
    }

    @Test
    fun hybridClassifierAveragesWhenBundledAndLocalDisagree() {
        val classifier = HybridClassifier(null)
        val otp = "123456 is your OTP for HDFC netbanking. Do not share."
        assertEquals(SmsClass.NOT_TXN, classifier.classify(otp).label)

        // Review says this is a debit. The bundled model cannot change, so the
        // two heads disagree and classify() must average their distributions.
        repeat(50) { classifier.learn(otp, SmsClass.DEBIT) }

        val merged = classifier.classify(otp)
        val repeated = classifier.classify(otp)
        assertEquals(merged.pDebit.toRawBits(), repeated.pDebit.toRawBits())
        assertTrue("merged pNotTxn=${merged.pNotTxn}", merged.pNotTxn in 0.3f..0.7f)
        assertTrue("merged pDebit=${merged.pDebit}", merged.pDebit in 0.3f..0.7f)
        assertEquals(1.0, (merged.pNotTxn + merged.pDebit + merged.pCredit).toDouble(), 1e-4)
    }
}
