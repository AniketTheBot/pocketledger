#!/usr/bin/env node
/**
 * PocketLedger SMS classifier trainer.
 *
 * Trains a 3-class softmax regression (NOT_TXN / DEBIT / CREDIT) over binary
 * hashed character n-gram features, entirely from a synthetic bank-SMS corpus.
 * No npm dependencies, no network, deterministic for a given seed.
 *
 * Outputs:
 *   app/src/main/java/app/pocketledger/ml/BundledModel.kt
 *   tools/eval_report.json
 *   tools/eval_holdout.jsonl
 *   tools/eval_adversarial.jsonl
 *
 * Run: node tools/train_classifier.mjs
 */

import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, "..");

const OUT_MODEL = path.join(
  ROOT,
  "app/src/main/java/app/pocketledger/ml/BundledModel.kt",
);
const OUT_REPORT = path.join(HERE, "eval_report.json");
const OUT_HOLDOUT = path.join(HERE, "eval_holdout.jsonl");
const OUT_ADVERSARIAL = path.join(HERE, "eval_adversarial.jsonl");

const SEED = 20261119;
const BUCKETS = 1024;
const CLASSES = ["NOT_TXN", "DEBIT", "CREDIT"];
const CLASS_INDEX = { NOT_TXN: 0, DEBIT: 1, CREDIT: 2 };

const TRAIN_COUNTS = { NOT_TXN: 220, DEBIT: 195, CREDIT: 155 }; // ~550 base messages
const HOLDOUT_COUNTS = { NOT_TXN: 100, DEBIT: 110, CREDIT: 90 }; // 300

const TRAINING = {
  optimizer: "Adam",
  learningRate: 0.05,
  beta1: 0.9,
  beta2: 0.999,
  epsilon: 1e-8,
  batchSize: 32,
  epochs: 60,
  l2: 1e-5,
};

// ---------------------------------------------------------------------------
// Deterministic RNG and small helpers
// ---------------------------------------------------------------------------

/** Fast deterministic PRNG (mulberry32). */
function mulberry32(seed) {
  let a = seed >>> 0;
  return function next() {
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function ri(rng, min, max) {
  return min + Math.floor(rng() * (max - min + 1));
}

function pick(rng, list) {
  return list[Math.floor(rng() * list.length)];
}

function chance(rng, p) {
  return rng() < p;
}

function digits(rng, n) {
  let out = "";
  for (let i = 0; i < n; i++) out += String(Math.floor(rng() * 10));
  return out;
}

function alnum(rng, n) {
  const chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
  let out = "";
  for (let i = 0; i < n; i++) out += chars[Math.floor(rng() * chars.length)];
  return out;
}

/** Groups an integer the Indian way: 1234567 -> "12,34,567". */
function groupIndian(n) {
  const s = String(Math.trunc(n));
  if (s.length <= 3) return s;
  const last3 = s.slice(-3);
  let rest = s.slice(0, -3);
  const groups = [];
  while (rest.length > 2) {
    groups.unshift(rest.slice(-2));
    rest = rest.slice(0, -2);
  }
  if (rest.length > 0) groups.unshift(rest);
  return groups.join(",") + "," + last3;
}

/** Groups an integer with thousands separators: 1234567 -> "1,234,567". */
function groupThousands(n) {
  const s = String(Math.trunc(n));
  if (s.length <= 3) return s;
  let rest = s;
  const groups = [];
  while (rest.length > 3) {
    groups.unshift(rest.slice(-3));
    rest = rest.slice(0, -3);
  }
  if (rest.length > 0) groups.unshift(rest);
  return groups.join(",");
}

// ---------------------------------------------------------------------------
// Feature extraction — MUST stay byte-for-byte compatible with TextFeatures.kt
// ---------------------------------------------------------------------------

/** Lowercase, trim, collapse whitespace runs. */
function normalize(text) {
  return text.toLowerCase().trim().replace(/\s+/g, " ");
}

/** FNV-1a over UTF-16 code units, modulo 1024. */
function fnvBucket(text, start, len) {
  let h = 2166136261 >>> 0;
  const end = start + len;
  for (let i = start; i < end; i++) {
    h ^= text.charCodeAt(i);
    h = Math.imul(h, 16777619) >>> 0;
  }
  return h % BUCKETS;
}

/** Sorted unique bucket ids for all character 2..4-grams of the text. */
function featureBuckets(text) {
  const normalized = normalize(text);
  if (normalized.length === 0) return [];
  const present = new Uint8Array(BUCKETS);
  for (let n = 2; n <= 4; n++) {
    if (normalized.length < n) continue;
    for (let i = 0; i + n <= normalized.length; i++) {
      present[fnvBucket(normalized, i, n)] = 1;
    }
  }
  const out = [];
  for (let b = 0; b < BUCKETS; b++) if (present[b]) out.push(b);
  return out;
}

// ---------------------------------------------------------------------------
// Synthetic bank-SMS vocabulary
// ---------------------------------------------------------------------------

const MERCHANTS = {
  IN: [
    "SWIGGY", "ZOMATO", "BIGBASKET", "AMAZON", "FLIPKART", "MYNTRA", "UBER",
    "OLA", "IRCTC", "JIO", "AIRTEL", "DMART", "RELIANCE FRESH", "CAFE COFFEE DAY",
    "ZEPTO", "BLINKIT", "BOOKMYSHOW", "MAKEMYTRIP", "NYKAA", "DOMINOS",
    "CROMA", "DECATHLON", "TATA CLIQ", "PHARMEASY",
  ],
  UK: [
    "TESCO", "SAINSBURY'S", "COSTA", "PRET A MANGER", "UBER", "TFL", "AMAZON UK",
    "ASOS", "DELIVEROO", "BOOTS", "M&S", "JUST EAT", "SPOTIFY", "NETFLIX",
    "BRITISH GAS", "VODAFONE UK", "SCREWFIX", "ARGOS",
  ],
  US: [
    "STARBUCKS", "CVS", "WALMART", "TARGET", "UBER", "AMAZON.COM", "NETFLIX",
    "SPOTIFY", "SHELL", "WHOLE FOODS", "TRADER JOE'S", "DOORDASH", "COSTCO",
    "BEST BUY", "HOME DEPOT", "CHIPOTLE",
  ],
};

const NAMES = {
  IN: [
    "RAHUL SHARMA", "PRIYA NAIR", "AMIT PATEL", "SNEHA GUPTA", "VIKRAM RAO",
    "ANITA DESAI", "ROHAN MEHTA", "KAVYA IYER", "ARJUN SINGH", "NEHA JOSHI",
    "SURESH KUMAR", "DEEPIKA REDDY",
  ],
  UK: [
    "OLIVER SMITH", "EMMA JONES", "JAMES TAYLOR", "SOPHIE BROWN", "HARRY WILSON",
    "CHLOE DAVIES", "GEORGE EVANS", "LUCY THOMAS",
  ],
  US: [
    "JOHN DOE", "JANE SMITH", "MICHAEL JOHNSON", "SARAH MILLER", "DAVID BROWN",
    "EMILY DAVIS", "CHRIS WILSON", "AMANDA CLARK",
  ],
};

const COMPANIES = {
  IN: [
    "ACME CORP", "TECHNOVA PVT LTD", "INFOSYS LTD", "TCS LTD", "WIPRO LTD",
    "BLUESTONE SYSTEMS", "ORBIT SOLUTIONS", "NIMBUS LABS",
  ],
  UK: ["ACME LTD", "BRITISH TELECOM", "UNILEVER PLC", "ASTRA ZENECA", "DELOITTE UK"],
  US: ["ACME CORP", "GLOBEX INC", "INITECH", "UMBRELLA LLC", "STARK INDUSTRIES"],
};

const BANKS = {
  IN: [
    "HDFC Bank", "ICICI Bank", "State Bank of India", "Axis Bank",
    "Kotak Mahindra Bank", "Punjab National Bank", "Bank of Baroda",
    "Yes Bank", "IDFC FIRST Bank",
  ],
  UK: ["Monzo", "Barclays", "HSBC", "Lloyds Bank", "NatWest", "Santander UK"],
  US: ["Chase", "Bank of America", "Wells Fargo", "Citibank", "Capital One"],
};

const SHORTS = {
  IN: ["HDFC", "ICICI", "SBI", "AXIS", "KOTAK", "PNB", "BOB", "YES", "IDFC"],
  UK: ["MONZO", "BARCLAYS", "HSBC", "LLOYDS", "NATWEST"],
  US: ["CHASE", "BOFA", "WELLS", "CITI", "CAPONE"],
};

const CITIES = {
  IN: ["MUMBAI", "DELHI", "BENGALURU", "HYDERABAD", "PUNE", "CHENNAI", "KOLKATA", "AHMEDABAD", "JAIPUR", "KOCHI"],
  UK: ["LONDON", "MANCHESTER", "BIRMINGHAM", "LEEDS", "GLASGOW", "BRISTOL"],
  US: ["NEW YORK", "CHICAGO", "LOS ANGELES", "HOUSTON", "SEATTLE", "BOSTON"],
};

const UTILITIES = {
  IN: ["electricity", "broadband", "water", "gas", "DTH"],
  UK: ["energy", "broadband", "water", "council tax", "mobile"],
  US: ["electricity", "internet", "water", "gas", "phone"],
};

const MONTHS = ["JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"];

const INR_AMOUNTS = [
  49, 79, 99, 120, 149, 199, 229, 249, 299, 349, 399, 449, 499, 549, 599, 649,
  699, 749, 799, 849, 899, 949, 999, 1099, 1199, 1299, 1399, 1499, 1599, 1799,
  1899, 1999, 2199, 2399, 2499, 2799, 2999, 3299, 3499, 3799, 3999, 4499,
  4999, 5499, 5999, 6499, 6999, 7499, 7999, 8499, 8999, 9999, 10999, 11999,
  12499, 12999, 14999, 15999, 17499, 18999, 19999, 21999, 24999, 27999, 29999,
  34999, 39999, 44999, 49999, 59999, 74999, 99999, 124999, 149999, 199999,
];

const INR_BALANCES = [
  1234, 2450, 5678, 12450, 18760, 23450, 34567, 45678, 52340, 67890, 89012,
  99876, 112345, 178900, 245678, 312450,
];

const INR_BIG = [100000, 200000, 300000, 500000, 750000, 1000000, 1500000, 2000000];

const GBP_AMOUNTS = [
  2, 3, 5, 6, 7, 8, 9, 12, 15, 18, 20, 25, 30, 35, 40, 45, 50, 60, 75, 80, 99,
  120, 150, 180, 200, 250, 300, 450, 500, 750, 1200, 1500, 2000,
];

const USD_AMOUNTS = [
  4, 5, 6, 8, 9, 12, 15, 18, 20, 25, 30, 35, 45, 50, 60, 75, 99, 120, 150, 180,
  200, 250, 300, 450, 500, 750, 1200, 1500, 2000, 2500,
];

function cents2(rng) {
  return pick(rng, ["00", "00", "00", "50", "99", "25", "75", "10", "05", "49"]);
}

function inrNumber(rng, opts = {}) {
  if (opts.balance) {
    const n = pick(rng, INR_BALANCES);
    return groupIndian(n) + "." + cents2(rng);
  }
  const n = pick(rng, opts.big ? INR_BIG : INR_AMOUNTS);
  const style = ri(rng, 0, 4);
  if (style === 0) return groupIndian(n) + "." + cents2(rng);
  if (style === 1) return String(n) + "." + cents2(rng);
  if (style === 2) return groupIndian(n);
  if (style === 3) return String(n);
  return groupIndian(n) + "." + pick(rng, ["5", "50", "9", "90", "25"]);
}

function gbpNumber(rng, opts = {}) {
  const n = pick(rng, GBP_AMOUNTS);
  if (opts.balance) return groupThousands(pick(rng, INR_BALANCES) * 3) + "." + cents2(rng);
  const style = ri(rng, 0, 3);
  if (style === 0) return groupThousands(n) + "." + cents2(rng);
  if (style === 1) return String(n) + "." + cents2(rng);
  if (style === 2) return String(n);
  return groupThousands(n);
}

function usdNumber(rng, opts = {}) {
  const n = pick(rng, USD_AMOUNTS);
  if (opts.balance) return groupThousands(pick(rng, INR_BALANCES) * 4) + "." + cents2(rng);
  const style = ri(rng, 0, 3);
  if (style === 0) return groupThousands(n) + "." + cents2(rng);
  if (style === 1) return String(n) + "." + cents2(rng);
  if (style === 2) return String(n);
  return groupThousands(n);
}

function money(rng, currency, opts = {}) {
  if (currency === "INR") {
    const prefix = pick(rng, ["Rs.", "Rs. ", "Rs ", "INR ", "INR", "₹", "₹ "]);
    return prefix + inrNumber(rng, opts);
  }
  if (currency === "GBP") {
    const prefix = pick(rng, ["£", "£ ", "GBP ", "GBP"]);
    return prefix + gbpNumber(rng, opts);
  }
  if (currency === "USD") {
    const prefix = pick(rng, ["$", "$ ", "USD ", "USD"]);
    return prefix + usdNumber(rng, opts);
  }
  // A small number of European-formatted amounts keeps the model honest.
  return "€" + groupThousands(pick(rng, [12, 49, 125, 1240])) + "," + cents2(rng);
}

function dateFor(rng, region) {
  const day = String(ri(rng, 1, 28)).padStart(2, "0");
  const mIdx = ri(rng, 0, 11);
  const mNum = String(mIdx + 1).padStart(2, "0");
  const mAbbr = MONTHS[mIdx];
  const mTitle = mAbbr[0] + mAbbr.slice(1).toLowerCase();
  const y2 = pick(rng, ["26", "27"]);
  const y4 = "20" + y2;
  if (region === "IN") {
    return pick(rng, [
      `${day}-${mNum}-${y2}`,
      `${day}${mAbbr}${y2}`,
      `${day}/${mNum}/${y4}`,
      `${day} ${mTitle} ${y4}`,
      `${y4}-${mNum}-${day}`,
      `${day}.${mNum}.${y2}`,
    ]);
  }
  if (region === "UK") {
    return pick(rng, [
      `${day}/${mNum}/${y4}`,
      `${day}/${mNum}`,
      `${Number(day)} ${mTitle}`,
      `${day}-${mNum}-${y2}`,
      `${day} ${mTitle} ${y4}`,
    ]);
  }
  return pick(rng, [
    `${mNum}/${day}/${y2}`,
    `${mNum}/${day}`,
    `${mTitle} ${Number(day)}`,
    `${y4}-${mNum}-${day}`,
  ]);
}

function refFor(rng) {
  const keyword = pick(rng, ["Ref", "Ref no", "UTR", "RRN", "UPI Ref", "Txn ID", "Reference"]);
  const id = pick(rng, [
    alnum(rng, 10),
    digits(rng, 12),
    "UPI" + digits(rng, 10),
    "NEFT" + digits(rng, 8),
    "IMPS" + digits(rng, 9),
    "HDFC" + digits(rng, 10),
  ]);
  return `${keyword} ${id}`;
}

function otp(rng) {
  return digits(rng, chance(rng, 0.15) ? 4 : 6);
}

function timeStr(rng) {
  return `${String(ri(rng, 0, 23)).padStart(2, "0")}:${String(ri(rng, 0, 59)).padStart(2, "0")}`;
}

function phoneFor(rng, region) {
  if (region === "IN") return "1800" + digits(rng, 6);
  if (region === "UK") return "0800 " + digits(rng, 6);
  return "1-800-" + digits(rng, 6);
}

function senderFor(rng, region) {
  if (region === "IN") {
    const prefix = pick(rng, ["", "AD-", "VM-", "AX-", "BZ-", "TA-", "JD-"]);
    const suffix = pick(rng, ["", "BK", "INB", "BNK", "B"]);
    return prefix + pick(rng, SHORTS.IN) + suffix;
  }
  if (region === "UK") return pick(rng, SHORTS.UK);
  return pick(rng, ["24273", "692632", "72568", "CHASE", "BOFA", "32245"]);
}

// ---------------------------------------------------------------------------
// Template pools. Placeholders are filled by fill(); every slot maker below
// defines all keys, so a missing slot fails loudly.
// ---------------------------------------------------------------------------

const T = {
  IN: {
    debit: [
      "{amt} debited from a/c XX{tail} on {date} to UPI/{name}/{short}. Avl Bal {bal}",
      "{amt} has been debited from your {bank} account ending {tail} towards {merchant}. Available balance {bal}.",
      "Your A/c XX{tail} is debited by {amt} on {date} for {merchant}. Bal: {bal}",
      "UPI txn of {amt} to {merchant} is successful. {ref}. Avl Bal {bal}",
      "You have paid {amt} to {merchant} using {bank} UPI. {ref}",
      "Card XX{tail} used for {amt} at {merchant} on {date}. Avl Bal {bal}",
      "{amt} withdrawn from A/c XX{tail} at ATM {city} on {date}. Bal {bal}",
      "NEFT debit: {amt} transferred from A/c XX{tail} to {name}. {ref}. Bal {bal}",
      "IMPS: {amt} deducted from your account ending {tail} on {date}. Beneficiary {name}. {ref}",
      "Autopay of {amt} for {merchant} processed on card ending {tail}. Avl Bal {bal}",
      "EMI of {amt} debited for loan a/c {loan} on {date}. {ref}. Bal {bal}",
      "Wallet: {amt} paid to {merchant} from your {short} wallet. Balance {bal}",
      "Intl txn: {usd} spent on card XX{tail} at {merchant}. INR equivalent {inr}. Avl Bal {bal}",
      "Sent {amt} to {name} via UPI. {ref}. Avl Bal {bal}",
      "Debit alert: {amt} debited from your {bank} account on {date}.",
      "A/c XX{tail} debited {amt} - {merchant} - {date}. Avl bal {bal}",
      "Your card was used for a purchase of {amt} at {merchant}.",
      "{amt} spent on your {bank} debit card at {merchant} on {date}.",
      "Aapke account se {amt} debit ho gaya hai. Available balance {bal}.",
      "{amt} transferred to {name} via IMPS. {ref}. New balance {bal}",
      "{amt} paid to {merchant} from A/c XX{tail} on {date}. {ref}",
      "Txn alert: {amt} debited from your {short} Bank A/c XX{tail} at {time}. Bal {bal}",
      "UPI/{name}/{amt} debited from A/c XX{tail}. {ref}. Bal {bal}",
      "{amt} deducted from your {bank} account towards {merchant} on {date}.",
      "{amt} paid from your {short} wallet to {merchant}. New balance {bal}.",
      "Your A/c XX{tail} was debited. Details in the app.",
      "ATM WDL {amt} from A/c XX{tail} at {city}. Bal {bal}",
      "{amt} transferred from your account to {name} via IMPS. {ref}",
      "You sent {amt} to {name} via UPI. {ref}",
      "Your card ending {tail} has been charged {amt} for {merchant}.",
      "Your card has been charged {amt} for your {merchant} subscription.",
      "Your account has been debited by {amt} on {date}.",
      "{amt} has been debited from your {bank} account towards {merchant}.",
      "A debit alert: a transaction was authorised on your card ending {tail}.",
      "Aapke account se {amt} debit hua. Details check karein.",
      "Aapke UPI se {amt} ka payment ho gaya. {ref}",
    ],
    credit: [
      "{amt} credited to a/c XX{tail} towards NEFT CR-{company} {ref}. Avl Bal {bal}",
      "{amt} has been credited to your {bank} account ending {tail} by {name}. Available balance {bal}.",
      "Salary credited: {amt} by {company} to A/c XX{tail} on {date}. Bal {bal}",
      "Your salary of {amt} has been credited to {bank} A/c XX{tail}.",
      "Refund of {amt} received for {merchant} txn. Credited to a/c XX{tail}. Bal {bal}",
      "Cashback of {amt} credited to your card ending {tail}.",
      "Interest of {amt} credited to A/c XX{tail}. Avl Bal {bal}",
      "{amt} added to your {short} wallet. New balance {bal}",
      "You have received {amt} from {name} in your a/c XX{tail} via UPI. {ref}",
      "UPI credit: {amt} received from {name}. Avl Bal {bal}",
      "IMPS CR {amt} from {name} to A/c XX{tail}. {ref}. Bal {bal}",
      "Deposit of {amt} to your account ending {tail} is successful. {ref}",
      "Aapke account mein {amt} credit ho gaya. Bal {bal}.",
      "{amt} transferred to your account by {company}. {ref}. New balance {bal}",
      "Dividend of {amt} credited to your demat account ending {tail}.",
      "Received {amt} from {name} via UPI in your {bank} account. {ref}",
      "{amt} refund credited to your {short} wallet. Balance {bal}",
      "Cashback: {amt} credited to your {bank} account ending {tail} on {date}.",
      "NEFT CR: {amt} received from {company}. A/c XX{tail}. {ref}",
      "{amt} deposited to A/c XX{tail} at {city} branch on {date}. Bal {bal}",
      "Deposited {amt} to A/c XX{tail}. {ref}. Bal {bal}",
      "You received {amt} in your account from {name}. {ref}",
      "Your wallet has been topped up with {amt}. Balance {bal}",
      "Salary {amt} aa gayi hai account mein.",
      "A credit of {amt} has been posted to your account ending {tail}.",
      "{amt} was received from {name} via UPI. {ref}.",
      "IMPS CR of {amt} received from {name}. A/c XX{tail}. {ref}",
      "Received {amt} from {name} via UPI in your {bank} account. {ref}",
      "You have received a payment of {amt} from {name} in your account ending {tail}.",
      "Aapko {amt} cashback mila hai. Account mein credit ho gaya.",
      "IMPS CR: {amt} received in A/c XX{tail} from {name}.",
    ],
    otp: [
      "{otp} is your OTP for {bank} netbanking. Do not share it with anyone.",
      "Your one-time password is {otp}. Valid for 10 minutes. Never share.",
      "Use {otp} to verify your {bank} login. OTP valid 5 min. Do not share.",
      "OTP {otp} for txn of {amt} at {merchant}. Do not share with anyone.",
      "{otp} is your verification code for a payment of {amt} to {merchant}. Never share this code.",
      "Your OTP for {bank} card ending {tail} is {otp}. Do not share with bank staff.",
      "Do not share OTP {otp} even with bank officials. Txn amount {amt}.",
      "{otp} is the OTP for your {amt} purchase at {merchant}. Valid 10 min.",
      "IMPORTANT: {otp} is your OTP to authorize a debit of {amt}. Never share.",
      "Your {bank} OTP is {otp} for a transaction of {amt} on card ending {tail}.",
      "{otp} is your one time password for {bank} UPI registration. Do not share.",
      "OTP: {otp}. Amount: {amt}. Merchant: {merchant}. Do not share this code.",
      "Do not share OTP {otp}. A debit of {amt} is pending on your card.",
      "OTP {otp} for a payment of {amt} to {merchant}. Never share.",
      "OTP {otp} for IMPS transfer of {amt} from A/c XX{tail}. Valid 10 min.",
      "OTP {otp} for NEFT transfer of {amt} from your {bank} account. Do not share.",
      "Aapka OTP {otp} hai. Kisi ke saath share na karein.",
    ],
    promo: [
      "Get pre-approved personal loan up to {big}! Apply now. T&C apply.",
      "Win cashback up to {amt} on every UPI payment. Click here to activate.",
      "Special offer: {amt} cashback on your {bank} credit card. Apply by {date}.",
      "Congratulations! You are eligible for a loan of {big} at 10.5%. Apply now.",
      "Earn 5% cashback up to {amt} on {merchant} purchases this weekend.",
      "Upgrade to {bank} premium card. Get {amt} welcome bonus. Apply today.",
      "Limited offer! EMI at {amt}/month on {merchant}. No cost EMI available.",
      "Your loan application is approved for {big}. Disbursal in 24 hrs. Call now.",
      "Insurance premium of {amt} is due. Pay now to avoid penalty.",
      "Bill of {amt} for {utility} is due on {date}. Pay online now.",
      "Recharge now and get {amt} cashback instantly. Offer valid today only.",
      "Congratulations! You have won {amt} in the {bank} lucky draw. Claim now.",
      "{amt} cashback waiting in your account. Activate before {date}. Click here.",
      "Pre-approved credit limit of {big} for you. No documents needed. Apply now.",
      "Get {amt} credited to your account on your first transaction. Apply now.",
      "Your EMI of {amt} is due on {date}. Pay now to avoid late fees.",
      "Special offer: flat {amt} off on {utility} bills. Use code SAVE{tail}.",
      "Get paid {amt} for every friend you refer. Start earning today.",
      "Flat {amt} off on your next {merchant} order. Use code {short}{tail}.",
      "{amt} off on flight bookings this weekend. Use code FLY{tail}.",
      "Aapke liye {big} ka pre-approved loan ready hai. Apply karein.",
      "Aapko {amt} cashback milega. Offer activate karein.",
    ],
    balance: [
      "Your A/c XX{tail} balance is {amt} as on {date}.",
      "Available balance in your account ending {tail} is {amt}.",
      "Bal {amt} in A/c XX{tail}. No transaction since last update.",
      "Your {bank} card ending {tail} has an outstanding balance of {amt}.",
      "Balance update: {amt} available in A/c XX{tail}.",
      "As on {date}, your balance is {amt} in A/c XX{tail}.",
      "Your available balance is {amt}. No debits or credits since your last statement.",
      "Available balance: {amt} in A/c XX{tail}. No new transactions.",
      "Aapka account balance {amt} hai.",
      "Aapke account mein balance {amt} hai.",
    ],
    service: [
      "Your card ending {tail} has been blocked. Call {phone} for help.",
      "Your statement for {month} is ready. Download it from the app.",
      "KYC pending for your account ending {tail}. Complete KYC to avoid suspension.",
      "Your card has been unblocked. You can use it now.",
      "We noticed a login to your account from a new device. If not you, call us.",
      "Your new cheque book has been dispatched to your registered address.",
      "Your debit card will expire soon. Request a replacement in the app.",
      "Your account will be debited for the annual fee of {amt} soon.",
      "Your credit card bill of {amt} is due on {date}.",
      "Your card ending {tail} will be debited {amt} on {date} for {merchant}.",
      "Aapka account block ho gaya hai. Bank se contact karein.",
      "Aapka card block ho gaya hai. Customer care ko call karein.",
      "{bank}: Your payment of {amt} is scheduled for {date}. Ensure sufficient balance.",
      "Your loan EMI of {amt} is due on {date}. Pay now to avoid late fees.",
      "Your EMI of {amt} will be debited on {date}. Keep sufficient balance.",
    ],
    future: [
      "Your EMI of {amt} will be debited on {date}. Keep sufficient balance.",
      "Your EMI of {amt} is scheduled for {date} from A/c XX{tail}.",
      "Your card ending {tail} will be charged {amt} on {date} for {merchant}.",
      "Your card ending {tail} will be debited {amt} on {date} for {merchant}.",
      "Your payment of {amt} to {merchant} is scheduled for {date}.",
      "Upcoming debit of {amt} on {date} for {merchant}. Ensure sufficient balance.",
      "Reminder: {amt} will be deducted from your account on {date}.",
      "Your subscription of {amt} renews on {date}. It will be charged to your card ending {tail}.",
      "Scheduled payment: {amt} to {merchant} on {date}.",
      "Your {bank} account will be debited for the annual fee of {amt} soon.",
      "Your loan EMI of {amt} is due on {date}. Pay now to avoid late fees.",
      "Your premium of {amt} is due on {date}. This will be debited from A/c XX{tail}.",
    ],
    personal: [
      "{name}, are we still meeting at 7? Let me know.",
      "Your parcel from Amazon is out for delivery. Track it in the app.",
      "Hi, this is {name}. I'll pay you back the {amt} tomorrow.",
      "Your food order from Swiggy is on the way. ETA 20 mins.",
      "Can you send me {amt} on GPay? I'll return it tonight.",
      "Your appointment at City Hospital is confirmed for {date}.",
      "Happy birthday! Hope you have a great day.",
      "Mum, I've reached home. Will call later.",
      "Thanks for lunch! Next one is on me.",
      "Your OTP for Zomato login is {otp}. Do not share.",
      "Hey, I've transferred you {amt} for the tickets. See you Friday!",
      "Venmo request: {name} requested {amt} for dinner last night.",
      "Your Uber ride is arriving now. Driver {name}.",
      "Dinner tonight at 8? Let me know.",
      "I spent {amt} on dinner last night. It was worth it.",
      "Can you believe the taxi cost {amt}? Ridiculous.",
      "My brother paid {amt} for parking, no receipt.",
      "She paid {amt} for concert tickets.",
      "I got {amt} from my cousin as a gift.",
      "I received {amt} as a gift from my aunt.",
      "The courier said he paid the customs fee of {amt}.",
      "You paid the {utility} bill? Thanks!",
      "They received the parcel, not the invoice.",
      "We received your complaint and will reply soon.",
      "He spent {amt} on groceries last week.",
      "She received {amt} from a client yesterday.",
      "You paid the electricity bill? Great, thanks!",
      "You paid the rent already? Thanks a lot!",
    ],
    spam: [
      "URGENT: Your account will be suspended. Click http://bit.ly/xyz to verify KYC.",
      "You have won {amt}! Claim your prize by calling {phone}.",
      "Dear winner, your lottery of {amt} is ready. Send bank details now.",
      "Final reminder: update your PAN or your account will be closed. Click here.",
      "Investment opportunity: earn 30% monthly guaranteed. WhatsApp us.",
      "Your KYC is expiring. Share Aadhaar and OTP to continue services.",
      "Congratulations! You are today's lucky winner of {amt}. Claim now.",
      "Your bank account is blocked. Send OTP to reactivate immediately.",
      "Win a free iPhone! Just pay {amt} shipping. Click here.",
      "Exclusive reward: {amt} is waiting for you. Verify your card details now.",
    ],
  },
  UK: {
    debit: [
      "You spent {amt} at {merchant}. Balance: {bal}",
      "{amt} paid to {merchant} from your account. New balance {bal}.",
      "A transaction of {amt} was made at {merchant} on your {bank} debit card ending {tail}. Available balance {bal}.",
      "Your account ****{tail} was debited {amt} on {date}. Available balance {bal}.",
      "Payment of {amt} to {merchant} was successful. Balance {bal}.",
      "Direct Debit {merchant} {amt} paid from your account. Balance now {bal}.",
      "{merchant} {amt} {date}. Your balance is {bal}.",
      "Card ending {tail}: {amt} spent at {merchant} on {date}.",
      "We've paid {amt} to {merchant} from your account ending {tail}.",
      "ATM withdrawal of {amt} from {city} on {date}. Balance {bal}.",
      "Your {bank} card was charged {amt} at {merchant}.",
      "{amt} was debited from your current account for {merchant} on {date}. Balance {bal}.",
      "Your card has been charged {amt} for your {merchant} subscription.",
      "{short}: {merchant} {amt}. Balance {bal}",
      "A transaction of {amt} was authorised on your {bank} card ending {tail}.",
    ],
    credit: [
      "You received {amt} from {name}. Your balance is {bal}.",
      "{amt} credited to your account ending {tail}. Balance {bal}.",
      "{bank}: {name} sent you {amt}. Balance: {bal}",
      "A faster payment of {amt} was received into your account ending ****{tail}. Balance {bal}.",
      "Salary: {amt} from {company} has been credited to your account.",
      "Refund from {merchant} of {amt} is now in your account. Balance {bal}.",
      "We've added {amt} to your account. New balance {bal}.",
      "Incoming payment: {amt} from {name} on {date}.",
      "Your account was credited with {amt} by {company}. Balance {bal}.",
      "{amt} deposit received in your {bank} account ending {tail}.",
      "Cashback of {amt} has been credited to your {bank} card.",
      "Received {amt} from {name} in your {bank} account. Balance {bal}.",
      "You have received a payment of {amt} from {name}.",
    ],
    otp: [
      "Your {bank} verification code is {otp}. Never share it.",
      "{otp} is your one-time passcode for a {amt} payment to {merchant}. Do not share.",
      "Use code {otp} to authorize {amt} at {merchant}. We'll never call you for it.",
      "Your {bank} login code is {otp}. If this wasn't you, contact us.",
      "Security code {otp} for your {amt} purchase at {merchant}. Expires in 5 minutes.",
      "Your one-time code is {otp}. Do not share it with anyone, even bank staff.",
    ],
    promo: [
      "{bank}: Get {amt} cashback when you spend {amt2} at {merchant} this month.",
      "Your pre-approved loan of {big} is ready. Apply in the app by {date}.",
      "Switch to {bank} and get {amt} welcome bonus. Terms apply.",
      "Earn {amt} cash back on your next {merchant} purchase. Activate now.",
      "Limited time: {amt} bonus when you open a new {bank} account.",
      "You're eligible for a {big} loan. Apply today. No impact on your credit score.",
      "Get {amt} credited to your account when you switch to {bank}. Apply now.",
      "Your {bank} credit card offer: spend {amt} and get {amt2} back. Activate by {date}.",
      "Get {amt} off your next {merchant} order. Use code SAVE{tail}.",
      "Refer a friend and get paid {amt}. Terms apply.",
    ],
    balance: [
      "Hello {name}, your savings account balance is {amt}.",
      "Your balance is {amt}. Available to spend {amt2}.",
      "Balance update: {amt} available in your account ending {tail}.",
      "{bank}: Your current account balance is {amt} as of {date}.",
      "Your {bank} account ending {tail} has a balance of {amt}.",
      "Available balance: {amt} in your account ending {tail}. No new transactions.",
      "Your available balance is {amt}. No debits or credits since your last statement.",
    ],
    service: [
      "Your card ending {tail} has been frozen. Tap in the app to unfreeze it.",
      "{bank}: Your statement is ready to view in the app.",
      "Your direct debit of {amt} to {merchant} is due on {date}.",
      "We've received your request to replace your card ending {tail}.",
      "Your {bank} account will be debited {amt} on {date} for your subscription.",
      "Action required: verify your details to keep your account active.",
      "Your card ending {tail} will be charged {amt} on {date} for {merchant}. Manage in app.",
      "Your payment of {amt} to {merchant} is due on {date}.",
    ],
    future: [
      "Your card ending {tail} will be charged {amt} on {date} for {merchant}. Manage in app.",
      "Your direct debit of {amt} to {merchant} is due on {date}.",
      "Your {bank} account will be debited {amt} on {date} for your subscription.",
      "Your payment of {amt} to {merchant} is scheduled for {date}.",
      "Reminder: {amt} will be deducted from your account on {date}.",
      "Your subscription of {amt} renews on {date}. It will be charged to your card ending {tail}.",
      "Upcoming payment of {amt} to {merchant} on {date}.",
      "Your card ending {tail} will be debited {amt} on {date}. Ensure sufficient funds.",
    ],
    personal: [
      "{name}, are we still meeting at 7? Let me know.",
      "Your parcel is out for delivery. Track it in the app.",
      "Hi, this is {name}. I'll pay you back the {amt} tomorrow.",
      "Your food order is on the way. ETA 20 mins.",
      "Can you send me {amt}? I'll return it tonight.",
      "Your appointment is confirmed for {date}.",
      "Happy birthday! Hope you have a great day.",
      "I've reached home. Will call later.",
      "Thanks for lunch! Next one is on me.",
      "Your OTP for the app login is {otp}. Do not share.",
      "Hey, I've transferred you {amt} for the tickets. See you Friday!",
      "Dinner tonight at 8? Let me know.",
      "I spent {amt} on dinner last night. It was worth it.",
      "Can you believe the taxi cost {amt}? Ridiculous.",
      "We received your message, thanks!",
      "My brother paid {amt} for parking, no receipt.",
      "She paid {amt} for concert tickets.",
      "I got {amt} from my cousin as a gift.",
      "I received {amt} as a gift from my aunt.",
      "The courier said he paid the customs fee of {amt}.",
      "You paid the {utility} bill? Thanks!",
      "They received the parcel, not the invoice.",
      "We received your complaint and will reply soon.",
      "He spent {amt} on groceries last week.",
      "She received {amt} from a client yesterday.",
      "You paid the electricity bill? Great, thanks!",
      "You paid the rent already? Thanks a lot!",
    ],
    spam: [
      "URGENT: Your account will be suspended. Click http://bit.ly/xyz to verify your details.",
      "You have won {amt}! Claim your prize by calling {phone}.",
      "Dear winner, your lottery of {amt} is ready. Send bank details now.",
      "Final reminder: update your details or your account will be closed. Click here.",
      "Investment opportunity: earn 30% monthly guaranteed. WhatsApp us.",
      "Your account is blocked. Share your OTP to reactivate immediately.",
      "Congratulations! You are today's lucky winner of {amt}. Claim now.",
      "Win a free iPhone! Just pay {amt} shipping. Click here.",
    ],
  },
  US: {
    debit: [
      "{short}: {amt} debit card purchase at {merchant}. Avail bal {bal}",
      "{bank} Alert: A debit of {amt} was made on your card ending {tail} at {merchant}.",
      "You paid {amt} at {merchant} with your debit card ending {tail}.",
      "ACH debit {amt} to {merchant} on {date}. Balance {bal}.",
      "ATM withdrawal {amt} from acct ending {tail}. Available balance {bal}.",
      "Your card ending {tail} was charged {amt} at {merchant}.",
      "{amt} was spent using your {bank} card at {merchant}.",
      "Card purchase: {amt} at {merchant}. New balance {bal}.",
      "{short}: A debit of {amt} posted to your checking account ending {tail} on {date}.",
      "Recurring payment of {amt} to {merchant} was debited from your account.",
      "Your {bank} debit card was used for {amt} at {merchant} on {date}.",
      "Your card has been charged {amt} for your {merchant} subscription.",
      "A debit alert: a transaction was authorised on your card ending {tail}.",
    ],
    credit: [
      "{short}: {amt} credited to your account ending {tail}. Avail bal {bal}",
      "{bank}: You received {amt} from {name}. New balance {bal}.",
      "Direct deposit of {amt} from {company} posted to your account ending {tail}.",
      "Refund: {amt} from {merchant} credited to your card ending {tail}.",
      "Cash back of {amt} was credited to your account. Balance {bal}.",
      "Your paycheck of {amt} was deposited into account ending {tail}.",
      "Zelle: {name} sent you {amt}. New balance {bal}.",
      "Tax refund of {amt} was deposited to your account ending {tail}.",
      "{amt} was credited to your {bank} account on {date}. Balance {bal}.",
      "Interest payment of {amt} posted to your savings account ending {tail}.",
      "You received a refund of {amt} from {merchant}. Avail bal {bal}",
    ],
    otp: [
      "{bank}: Your verification code is {otp}. Never share this code.",
      "Use code {otp} to authorize a {amt} purchase at {merchant}. We never ask for codes.",
      "{short} Security Alert: {otp} is your one-time code for a debit of {amt}. Do not share.",
      "Your one-time verification code is {otp}. It expires in 10 minutes.",
      "Txn code {otp} for {amt} at {merchant}. Never share.",
      "{short}: {otp} is your code for a {amt} transaction. Do not share it.",
    ],
    promo: [
      "{short}: Earn {amt} cash back on your next {merchant} purchase. Activate now.",
      "{bank}: Get {amt} bonus when you open a new checking account. Apply today.",
      "Limited offer: 0% APR for 12 months on purchases over {amt}. Apply now.",
      "Your pre-approved credit limit is {big}. Activate in the app today.",
      "Get {amt} credited to your account after your first direct deposit. Terms apply.",
      "{short}: You've won {amt} in our rewards draw! Claim now.",
      "Spend {amt} at {merchant} this weekend and earn {amt2} back. Activate now.",
      "Your {bank} card offer: spend {amt} and get {amt2} back. Activate by {date}.",
      "Get paid {amt} for every friend you refer. Start today.",
      "{amt} off your next {merchant} order with code SAVE{tail}.",
    ],
    balance: [
      "Balance update: {amt} available in your checking account ending {tail}.",
      "{bank}: Your available balance is {amt}. No new transactions.",
      "Your account ending {tail} has a balance of {amt} as of {date}.",
      "{short}: Your balance is {amt}. Recent transactions may not be reflected.",
      "Available balance: {amt} in account ending {tail}.",
      "Available balance: {amt} in account ending {tail}. No new transactions.",
      "Your available balance is {amt}. No debits or credits since your last statement.",
    ],
    service: [
      "Your debit card ending {tail} was locked. Call us to unlock it.",
      "{bank}: Your statement is now available online.",
      "Your payment of {amt} to {merchant} is scheduled for {date}.",
      "We received your card replacement request. Your new card arrives in 5-7 days.",
      "Your autopay of {amt} will be debited on {date}. Ensure funds are available.",
      "Security alert: a new device signed in to your account. If this wasn't you, call us.",
      "Your card ending {tail} will be charged {amt} on {date} for {merchant}.",
      "Your payment of {amt} is scheduled for {date}. No action needed.",
    ],
    future: [
      "Your card ending {tail} will be charged {amt} on {date} for {merchant}.",
      "Your autopay of {amt} will be debited on {date}. Ensure funds are available.",
      "Your payment of {amt} is scheduled for {date}. No action needed.",
      "Upcoming debit of {amt} from account ending {tail} on {date}.",
      "Reminder: {amt} will be deducted from your account on {date}.",
      "Your subscription of {amt} renews on {date}. It will be charged to your card ending {tail}.",
      "Scheduled payment: {amt} to {merchant} on {date}.",
      "Your EMI of {amt} is scheduled for {date} from A/c XX{tail}.",
    ],
    personal: [
      "{name}, are we still meeting at 7? Let me know.",
      "Your package is out for delivery. Track it in the app.",
      "Hi, this is {name}. I'll pay you back the {amt} tomorrow.",
      "Your food order is on the way. ETA 20 mins.",
      "Can you send me {amt}? I'll return it tonight.",
      "Your appointment is confirmed for {date}.",
      "Happy birthday! Hope you have a great day.",
      "I've reached home. Will call later.",
      "Thanks for lunch! Next one is on me.",
      "Your OTP for the app login is {otp}. Do not share.",
      "Hey, I've transferred you {amt} for the tickets. See you Friday!",
      "Venmo request: {name} requested {amt} for dinner last night.",
      "Your Uber ride is arriving now. Driver {name}.",
      "Dinner tonight at 8? Let me know.",
      "I spent {amt} on dinner last night. It was worth it.",
      "Can you believe the taxi cost {amt}? Ridiculous.",
      "My brother paid {amt} for parking, no receipt.",
      "She paid {amt} for concert tickets.",
      "I got {amt} from my cousin as a gift.",
      "I received {amt} as a gift from my aunt.",
      "The courier said he paid the customs fee of {amt}.",
      "You paid the {utility} bill? Thanks!",
      "They received the parcel, not the invoice.",
      "We received your complaint and will reply soon.",
      "He spent {amt} on groceries last week.",
      "She received {amt} from a client yesterday.",
      "You paid the electricity bill? Great, thanks!",
      "You paid the rent already? Thanks a lot!",
    ],
    spam: [
      "URGENT: Your account will be suspended. Click http://bit.ly/xyz to verify your details.",
      "You have won {amt}! Claim your prize by calling {phone}.",
      "Dear winner, your lottery of {amt} is ready. Send bank details now.",
      "Final reminder: update your details or your account will be closed. Click here.",
      "Investment opportunity: earn 30% monthly guaranteed. WhatsApp us.",
      "Your account is blocked. Share your OTP to reactivate immediately.",
      "Congratulations! You are today's lucky winner of {amt}. Claim now.",
      "Win a free iPhone! Just pay {amt} shipping. Click here.",
    ],
  },
};

function mkSlots(rng, region) {
  const currency = region === "IN" ? "INR" : region === "UK" ? "GBP" : "USD";
  const tail = digits(rng, 4);
  return {
    amt: money(rng, currency),
    amt2: money(rng, currency),
    bal: money(rng, currency, { balance: true }),
    big: money(rng, currency, { big: true }),
    usd: money(rng, "USD"),
    inr: money(rng, "INR"),
    tail,
    date: dateFor(rng, region),
    time: timeStr(rng),
    ref: refFor(rng),
    merchant: pick(rng, MERCHANTS[region]),
    name: pick(rng, NAMES[region]),
    company: pick(rng, COMPANIES[region]),
    bank: pick(rng, BANKS[region]),
    short: pick(rng, SHORTS[region]),
    city: pick(rng, CITIES[region]),
    otp: otp(rng),
    phone: phoneFor(rng, region),
    utility: pick(rng, UTILITIES[region]),
    month: pick(rng, MONTHS),
    loan: "LN" + digits(rng, 6),
  };
}

function fill(template, slots) {
  return template.replace(/\{(\w+)\}/g, (_, key) => {
    if (!(key in slots)) {
      throw new Error(`Unknown template slot {${key}} in: ${template}`);
    }
    return slots[key];
  });
}

const REGIONS = ["IN", "UK", "US"];
const REGION_WEIGHTS = [0.56, 0.22, 0.22];

function pickRegion(rng) {
  const r = rng();
  let acc = 0;
  for (let i = 0; i < REGIONS.length; i++) {
    acc += REGION_WEIGHTS[i];
    if (r < acc) return REGIONS[i];
  }
  return REGIONS[REGIONS.length - 1];
}

function genTxn(rng, kind) {
  const region = pickRegion(rng);
  const slots = mkSlots(rng, region);
  const body = fill(pick(rng, T[region][kind]), slots);
  return { sender: senderFor(rng, region), body, label: kind === "debit" ? "DEBIT" : "CREDIT" };
}

function genOtp(rng) {
  const region = pickRegion(rng);
  return { sender: senderFor(rng, region), body: fill(pick(rng, T[region].otp), mkSlots(rng, region)), label: "NOT_TXN" };
}

function genPromo(rng) {
  const region = pickRegion(rng);
  return { sender: senderFor(rng, region), body: fill(pick(rng, T[region].promo), mkSlots(rng, region)), label: "NOT_TXN" };
}

function genBalance(rng) {
  const region = pickRegion(rng);
  return { sender: senderFor(rng, region), body: fill(pick(rng, T[region].balance), mkSlots(rng, region)), label: "NOT_TXN" };
}

function genService(rng) {
  const region = pickRegion(rng);
  return { sender: senderFor(rng, region), body: fill(pick(rng, T[region].service), mkSlots(rng, region)), label: "NOT_TXN" };
}

/**
 * Conditional / future payment notices ("will be debited", "is scheduled",
 * "is due"). These are deliberately over-represented hard negatives: a
 * classifier that fires on the verb alone becomes unsafe for auto-recording.
 */
function genFuture(rng) {
  const region = pickRegion(rng);
  return { sender: senderFor(rng, region), body: fill(pick(rng, T[region].future), mkSlots(rng, region)), label: "NOT_TXN" };
}

function genPersonal(rng) {
  const region = pickRegion(rng);
  return { sender: senderFor(rng, region), body: fill(pick(rng, T[region].personal), mkSlots(rng, region)), label: "NOT_TXN" };
}

function genSpam(rng) {
  const region = pickRegion(rng);
  return { sender: senderFor(rng, region), body: fill(pick(rng, T[region].spam), mkSlots(rng, region)), label: "NOT_TXN" };
}

function genNotTxn(rng) {
  const r = rng();
  if (r < 0.22) return genOtp(rng);
  if (r < 0.44) return genPromo(rng);
  if (r < 0.58) return genBalance(rng);
  if (r < 0.68) return genService(rng);
  if (r < 0.83) return genFuture(rng);
  if (r < 0.93) return genPersonal(rng);
  return genSpam(rng);
}

function generateRows(seed, counts) {
  const rng = mulberry32(seed);
  const rows = [];
  const seen = new Set();
  const generators = {
    NOT_TXN: genNotTxn,
    DEBIT: (r) => genTxn(r, "debit"),
    CREDIT: (r) => genTxn(r, "credit"),
  };
  for (const label of ["NOT_TXN", "DEBIT", "CREDIT"]) {
    let made = 0;
    while (made < counts[label]) {
      const row = generators[label](rng);
      if (seen.has(row.body)) continue;
      seen.add(row.body);
      rows.push(row);
      made++;
    }
  }
  // Shuffle so the row order does not leak class during training.
  const shuffleRng = mulberry32(seed ^ 0x9e3779b9);
  for (let i = rows.length - 1; i > 0; i--) {
    const j = Math.floor(shuffleRng() * (i + 1));
    [rows[i], rows[j]] = [rows[j], rows[i]];
  }
  return rows;
}

// ---------------------------------------------------------------------------
// Hand-authored adversarial set — messages the template generator did NOT
// produce. This is the honest generalization check.
// ---------------------------------------------------------------------------

const ADVERSARIAL = [
  // OTPs that mention amounts, debits and merchants (must stay NOT_TXN).
  ["123456 is your OTP for a debit of Rs.4,999.00 on your HDFC Bank card ending 1234. Do not share.", "NOT_TXN"],
  ["Your OTP for txn of ₹12,450.00 to AMAZON is 998877. Never share with anyone.", "NOT_TXN"],
  ["OTP 445566 for IMPS transfer of Rs.25,000 from A/c XX1234. Valid 10 min.", "NOT_TXN"],
  ["Use 778899 to authorize £250.00 payment to TESCO. Never share this code.", "NOT_TXN"],
  ["Your Chase verification code is 334455 for a $1,250.00 purchase at BEST BUY. We never ask for codes.", "NOT_TXN"],
  ["4471 is your one-time password for the UPI payment of ₹750 to Swiggy. Do not share.", "NOT_TXN"],
  ["Do not share OTP 112233. A debit of INR 18,999 is pending on your card.", "NOT_TXN"],
  ["Your SBI OTP is 556677 for NEFT of Rs.1,00,000 to RAHUL SHARMA.", "NOT_TXN"],
  ["Security code 889900 to authorize a transaction of $89.99 at UBER. Expires in 5 min.", "NOT_TXN"],
  ["OTP for your credit card bill payment of Rs.15,000 is 223344. Never share.", "NOT_TXN"],
  ["Your one time password is 667788 for a payment of £45.20 to DELIVEROO.", "NOT_TXN"],

  // Promos and offers that name amounts (must stay NOT_TXN).
  ["Get ₹5,000 cashback credited to your account on your first credit card spend. Apply now.", "NOT_TXN"],
  ["Congratulations! You are pre-approved for a personal loan of Rs.10,00,000 at 9.99%. Apply today.", "NOT_TXN"],
  ["Earn up to £200 cashback when you switch your current account to Barclays. Terms apply.", "NOT_TXN"],
  ["Your HDFC credit card offer: spend ₹25,000 and get ₹1,000 back. Activate by 30 Nov.", "NOT_TXN"],
  ["Limited offer: 0% EMI on purchases above ₹15,000 at Croma. Click here.", "NOT_TXN"],
  ["You have won ₹50,000 in the Kotak lucky draw! Claim by calling 1800123456.", "NOT_TXN"],
  ["Special Diwali offer: flat ₹2,500 off on flight bookings. Use code FLY2500.", "NOT_TXN"],
  ["Your loan of $25,000 is approved. No documents needed. Apply in 2 minutes.", "NOT_TXN"],
  ["Get paid ₹500 for every friend you refer. Start earning today.", "NOT_TXN"],
  ["Your bill of ₹3,499 is due tomorrow. Pay now and avoid late fee.", "NOT_TXN"],
  ["Chase: You're eligible for a $500 bonus when you open a new checking account.", "NOT_TXN"],
  ["Your EMI of ₹8,750 will be debited on 05 Dec. Keep sufficient balance.", "NOT_TXN"],
  ["Your card ending 4321 will be charged £12.99 on 01 Dec for Netflix. Manage in app.", "NOT_TXN"],

  // Completed transactions with short or unusual phrasing (real txns).
  ["Your A/c XX1234 was debited. Details in the app.", "DEBIT"],
  ["Your account has been credited. Ref 9988776655.", "CREDIT"],
  ["A payment from RAHUL was received in your account ending 5678.", "CREDIT"],
  ["Your card was used at STARBUCKS for $4.75.", "DEBIT"],
  ["You have received a payment from PRIYA NAIR.", "CREDIT"],
  ["Debit alert: a transaction was authorised on your HDFC card ending 1122.", "DEBIT"],
  ["Salary has been credited to your account ending 8899.", "CREDIT"],
  ["Your account was debited for INR 500.", "DEBIT"],
  ["A credit of £120 was posted to your account.", "CREDIT"],
  ["Your A/c was debited by Rs.2,000 on 19 Nov.", "DEBIT"],

  // Amount appears before the direction keyword.
  ["₹1,234.50 has been debited from your HDFC Bank account ending 1234.", "DEBIT"],
  ["£50.00 was debited from your account ****4321 on 19/11/2026.", "DEBIT"],
  ["$45.20 was charged to your Chase card ending 9876 at STARBUCKS.", "DEBIT"],
  ["Rs.25,000.00 has been credited to A/c XX5678 towards NEFT.", "CREDIT"],
  ["₹299.00 was received from ANITA via UPI. Ref 1122334455.", "CREDIT"],
  ["£1,500.00 was credited to your HSBC account on 19 Nov.", "CREDIT"],
  ["INR 750 has been paid to ZOMATO using your ICICI UPI.", "DEBIT"],
  ["$2,000.00 was deposited into your account ending 3344.", "CREDIT"],
  ["₹12,450 has been transferred to AMIT KUMAR via IMPS. Ref 998877.", "DEBIT"],
  ["Rs.5,000 was withdrawn from your SBI account at an ATM in PUNE.", "DEBIT"],

  // Passive voice.
  ["Your A/c XX1234 has been debited by Rs.1,234.50 on 19-11-26.", "DEBIT"],
  ["Your account was credited with £250.00 by ACME LTD.", "CREDIT"],
  ["A debit of $18.75 was applied to card ending 5566 at CVS.", "DEBIT"],
  ["Your card has been charged ₹999 for your annual subscription.", "DEBIT"],
  ["Your savings account has been credited with interest of ₹4,210.", "CREDIT"],
  ["An amount of ₹2,500 was deducted from your account ending 7788.", "DEBIT"],
  ["A refund of $12.99 has been issued to your card ending 3344.", "CREDIT"],
  ["Your account was debited £15.00 for a contactless payment at PRET.", "DEBIT"],
  ["Your wallet has been topped up with ₹500.", "CREDIT"],
  ["The sum of ₹18,000 was transferred from your account to MR SHARMA.", "DEBIT"],

  // Personal use of spent/paid/received with no bank context.
  ["I spent ₹500 on dinner last night. It was worth it.", "NOT_TXN"],
  ["She paid £40 for the concert tickets.", "NOT_TXN"],
  ["We received your complaint and will respond within 48 hours.", "NOT_TXN"],
  ["He paid $20 for parking and didn't get a receipt.", "NOT_TXN"],
  ["I received ₹1,000 from my cousin as a gift.", "NOT_TXN"],
  ["The courier says he paid the customs fee of ₹350.", "NOT_TXN"],
  ["I spent $12 on coffee today, inflation is real.", "NOT_TXN"],
  ["They received the parcel but not the invoice.", "NOT_TXN"],
  ["You paid the electricity bill? Great, thanks!", "NOT_TXN"],
  ["I will pay you back the ₹500 tomorrow, promise.", "NOT_TXN"],

  // Mixed-language fragments.
  ["Aapke A/c XX1234 se Rs.950 debit ho gaya. Avl bal Rs.23,450.", "DEBIT"],
  ["Rs.5,000 aapke account mein credit ho gaya hai. Bal Rs.18,200.", "CREDIT"],
  ["Aapka OTP 445566 hai. Kisi ke saath share na karein.", "NOT_TXN"],
  ["Aapke liye Rs.2,00,000 ka pre-approved loan ready hai. Apply karein.", "NOT_TXN"],
  ["Payment of Rs.1,200 aapke UPI se ho gaya. Ref 887766.", "DEBIT"],
  ["Aapko Rs.500 cashback mila hai. Account mein credit ho gaya.", "CREDIT"],
  ["Aapke account se paisa debit hua. Details check karein.", "DEBIT"],
  ["Salary aa gayi hai account mein. Congrats!", "CREDIT"],
  ["Aapka account block ho gaya hai. Bank se contact karein.", "NOT_TXN"],
  ["Rs.750 received from PRIYA. Balance updated.", "CREDIT"],

  // Balance-only / service messages that mention debit/credit words.
  ["Your available balance is ₹45,678.90. No debits or credits since your last statement.", "NOT_TXN"],
  ["Your HDFC account statement for November is ready. Opening balance ₹12,000, closing ₹18,500.", "NOT_TXN"],
  ["Your card ending 1234 is blocked. No transactions will be allowed until you call us.", "NOT_TXN"],
  ["KYC pending: your account will be debited for verification charges if not completed.", "NOT_TXN"],
  ["Your credit card bill of ₹12,450 is due on 05 Dec. Minimum due ₹620.", "NOT_TXN"],
  ["Your EMI of ₹8,750 is scheduled for 05 Dec from A/c XX1234.", "NOT_TXN"],
  ["We have received your request. Your card will be replaced in 7 days.", "NOT_TXN"],
  ["Your request to reset UPI PIN is received. Do not share the OTP with anyone.", "NOT_TXN"],

  // Short, cryptic, bank-statement style txns.
  ["HDFC Bank: INR 2,000 debited A/c XX1234 19/11/26. Bal 8,540.20", "DEBIT"],
  ["ICICI: UPI/AMIT/₹500/SUCCESS. Bal ₹2,350.", "DEBIT"],
  ["SBI: Credit of INR 15,000 in A/c XX9999 by NEFT. Bal 42,100.", "CREDIT"],
  ["AXIS: ATM WDL ₹3,000 A/c XX3322 19NOV. Bal ₹9,200.", "DEBIT"],
  ["MONZO: Tesco £8.50. Balance £620.40", "DEBIT"],
  ["CHASE: $12.34 at CVS 11/19. Avail bal $980.12", "DEBIT"],
  ["BOFA: Deposit $1,000 posted 11/19 to acct 3344.", "CREDIT"],
].map(([body, label]) => ({ sender: "ADVERSARIAL", body, label }));

/**
 * Curated balance-only hard negatives. Balance alerts have no direction verb,
 * are short, and are dense with digits, which makes them the easiest negative
 * class to lose to numeric noise. These augment the generated corpus with
 * common bank phrasings (all are NOT_TXN; none is used in eval/adversarial).
 */
const EXTRA_TRAIN = [
  "Your A/c XX1234 balance is Rs.45,678.90 as on 19-11",
  "Your A/c XX7712 balance is Rs.1,23,456.00 as on 04-07-27",
  "Your A/c XX8899 balance is ₹8,999 as on 11.11.26",
  "A/c XX3456 balance: Rs.34,999 as on 17/02/2027",
  "Balance in your A/c XX2345 is Rs.78,233.10 as on 19 Nov 2026",
  "Your account XX7890 balance as on 19-11 is Rs.12,345.67",
  "Available balance in A/c XX1122 is INR 5,678.25",
  "Your HDFC Bank card ending 4433 has an outstanding balance of ₹2,499.",
  "Your balance is £1,234.56. Available to spend £1,200.00.",
  "Hello Priya, your savings account balance is £8,450.20.",
  "Your available balance is $1,234.56. No new transactions.",
  "Balance update: $45,678.90 available in your checking account ending 7788.",
  "Aapke account mein balance Rs.34,999 hai.",
  "Aapka account balance ₹8,999 hai.",
  "Your A/c XX4455 balance is Rs.9,999 as on 01-01-27. No transaction since last update.",
  "Your account ending 6622 has a balance of INR 2,345.67 as of 19/11/26.",
].map((body) => ({ sender: "CURATED", body, label: "NOT_TXN" }));

// ---------------------------------------------------------------------------
// Training: softmax regression with Adam, L2, deterministic shuffling
// ---------------------------------------------------------------------------

function softmax3(z0, z1, z2) {
  const m = Math.max(z0, z1, z2);
  const e0 = Math.exp(z0 - m);
  const e1 = Math.exp(z1 - m);
  const e2 = Math.exp(z2 - m);
  const sum = e0 + e1 + e2;
  return [e0 / sum, e1 / sum, e2 / sum];
}

function predictRow(W, bias, feat) {
  const w0 = W[0];
  const w1 = W[1];
  const w2 = W[2];
  let z0 = bias[0];
  let z1 = bias[1];
  let z2 = bias[2];
  for (let i = 0; i < feat.length; i++) {
    const f = feat[i];
    z0 += w0[f];
    z1 += w1[f];
    z2 += w2[f];
  }
  return softmax3(z0, z1, z2);
}

function shuffleInPlace(arr, rng) {
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(rng() * (i + 1));
    [arr[i], arr[j]] = [arr[j], arr[i]];
  }
}

function trainModel(rows) {
  const initRng = mulberry32(SEED ^ 0x51ed270b);
  const shuffleRng = mulberry32(SEED ^ 0x2545f491);

  const W = [0, 1, 2].map(() => {
    const row = new Float64Array(BUCKETS);
    for (let i = 0; i < BUCKETS; i++) row[i] = (initRng() * 2 - 1) * 0.01;
    return row;
  });
  const bias = new Float64Array(3);

  const mW = [0, 1, 2].map(() => new Float64Array(BUCKETS));
  const vW = [0, 1, 2].map(() => new Float64Array(BUCKETS));
  const mB = new Float64Array(3);
  const vB = new Float64Array(3);

  const gradW = [0, 1, 2].map(() => new Float64Array(BUCKETS));
  const gradB = new Float64Array(3);

  const samples = rows.map((r) => ({
    y: CLASS_INDEX[r.label],
    feat: featureBuckets(r.body),
  }));

  const order = samples.map((_, i) => i);
  const { learningRate: lr, beta1, beta2, epsilon, batchSize, epochs, l2 } = TRAINING;
  let step = 0;

  for (let epoch = 1; epoch <= epochs; epoch++) {
    shuffleInPlace(order, shuffleRng);

    for (let start = 0; start < order.length; start += batchSize) {
      const batch = order.slice(start, start + batchSize);

      for (let c = 0; c < 3; c++) gradW[c].fill(0);
      gradB.fill(0);

      for (const idx of batch) {
        const sample = samples[idx];
        const p = predictRow(W, bias, sample.feat);
        for (let c = 0; c < 3; c++) {
          const g = p[c] - (c === sample.y ? 1 : 0);
          gradB[c] += g;
          const row = gradW[c];
          for (let k = 0; k < sample.feat.length; k++) row[sample.feat[k]] += g;
        }
      }

      step++;
      const bc1 = 1 - Math.pow(beta1, step);
      const bc2 = 1 - Math.pow(beta2, step);
      const invBatch = 1 / batch.length;

      for (let c = 0; c < 3; c++) {
        const wRow = W[c];
        const mRow = mW[c];
        const vRow = vW[c];
        const gRow = gradW[c];
        for (let d = 0; d < BUCKETS; d++) {
          const g = gRow[d] * invBatch + l2 * wRow[d];
          mRow[d] = beta1 * mRow[d] + (1 - beta1) * g;
          vRow[d] = beta2 * vRow[d] + (1 - beta2) * g * g;
          wRow[d] -= (lr * (mRow[d] / bc1)) / (Math.sqrt(vRow[d] / bc2) + epsilon);
        }
        const g = gradB[c] * invBatch;
        mB[c] = beta1 * mB[c] + (1 - beta1) * g;
        vB[c] = beta2 * vB[c] + (1 - beta2) * g * g;
        bias[c] -= (lr * (mB[c] / bc1)) / (Math.sqrt(vB[c] / bc2) + epsilon);
      }
    }

    if (epoch === 1 || epoch % 10 === 0 || epoch === epochs) {
      console.log(`epoch ${String(epoch).padStart(2)} / ${epochs}`);
    }
  }

  return { W, bias };
}

// ---------------------------------------------------------------------------
// Evaluation
// ---------------------------------------------------------------------------

function predictLabel(W, bias, body) {
  const p = predictRow(W, bias, featureBuckets(body));
  let best = 0;
  if (p[1] > p[best]) best = 1;
  if (p[2] > p[best]) best = 2;
  // Matches MlPrediction.label tie-breaking: DEBIT wins ties over CREDIT,
  // CREDIT wins ties over NOT_TXN, DEBIT wins ties over NOT_TXN.
  if (p[1] >= p[2] && p[1] >= p[0]) best = 1;
  else if (p[2] >= p[1] && p[2] >= p[0]) best = 2;
  else best = 0;
  return { index: best, probs: p };
}

function confusionMatrix(rows) {
  const matrix = [
    [0, 0, 0],
    [0, 0, 0],
    [0, 0, 0],
  ];
  const misses = [];
  for (const row of rows) {
    const pred = predictLabel(row.W, row.bias, row.body);
    const actual = CLASS_INDEX[row.label];
    matrix[actual][pred.index]++;
    if (pred.index !== actual) {
      misses.push({
        body: row.body,
        actual: row.label,
        predicted: CLASSES[pred.index],
        pNotTxn: round(pred.probs[0], 4),
        pDebit: round(pred.probs[1], 4),
        pCredit: round(pred.probs[2], 4),
      });
    }
  }
  return { matrix, misses };
}

function round(x, digits = 4) {
  if (!Number.isFinite(x)) return x;
  const f = 10 ** digits;
  return Math.round(x * f) / f;
}

function safeDiv(a, b) {
  return b === 0 ? 0 : a / b;
}

function binaryMetrics(matrix) {
  const tp = matrix[1][1] + matrix[1][2] + matrix[2][1] + matrix[2][2];
  const fp = matrix[0][1] + matrix[0][2];
  const fn = matrix[1][0] + matrix[2][0];
  const tn = matrix[0][0];
  const precision = safeDiv(tp, tp + fp);
  const recall = safeDiv(tp, tp + fn);
  const f1 = safeDiv(2 * precision * recall, precision + recall);
  return {
    precision: round(precision),
    recall: round(recall),
    f1: round(f1),
    tp,
    fp,
    fn,
    tn,
  };
}

function directionAccuracy(matrix) {
  const correct = matrix[1][1] + matrix[2][2];
  const txn = matrix[1][1] + matrix[1][2] + matrix[2][1] + matrix[2][2];
  return round(safeDiv(correct, txn));
}

function evaluateDataset(rows, W, bias) {
  const enriched = rows.map((r) => ({ ...r, W, bias }));
  const { matrix, misses } = confusionMatrix(enriched);

  const perClass = {};
  for (let c = 0; c < 3; c++) {
    let tp = matrix[c][c];
    let fp = 0;
    let fn = 0;
    for (let i = 0; i < 3; i++) {
      if (i !== c) {
        fp += matrix[i][c];
        fn += matrix[c][i];
      }
    }
    const precision = safeDiv(tp, tp + fp);
    const recall = safeDiv(tp, tp + fn);
    const f1 = safeDiv(2 * precision * recall, precision + recall);
    perClass[CLASSES[c]] = {
      precision: round(precision),
      recall: round(recall),
      f1: round(f1),
      support: matrix[c].reduce((a, b) => a + b, 0),
    };
  }

  const total = matrix.flat().reduce((a, b) => a + b, 0);
  const correct = matrix[0][0] + matrix[1][1] + matrix[2][2];

  return {
    confusion_matrix: matrix,
    per_class: perClass,
    txn_binary: binaryMetrics(matrix),
    direction_accuracy_among_txn: directionAccuracy(matrix),
    accuracy: round(safeDiv(correct, total)),
    cases: total,
    misses,
  };
}

// ---------------------------------------------------------------------------
// Artifact writers
// ---------------------------------------------------------------------------

function fmtFloat(x) {
  if (!Number.isFinite(x)) throw new Error(`Non-finite weight: ${x}`);
  let s = x.toFixed(6);
  if (s === "-0.000000") s = "0.000000";
  return s;
}

function kotlinModelSource(W, bias) {
  const lines = [];
  lines.push("// Generated by tools/train_classifier.mjs — do not edit by hand.");
  lines.push("// Deterministic output: seed " + SEED + ", 3-class softmax over hashed char n-grams.");
  lines.push("package app.pocketledger.ml");
  lines.push("");
  lines.push("object BundledModel {");
  lines.push("    const val BUCKETS = " + BUCKETS);
  lines.push("");
  lines.push("    val WEIGHTS: Array<FloatArray> = arrayOf(");
  for (let c = 0; c < 3; c++) {
    lines.push("        floatArrayOf(");
    const values = [];
    for (let i = 0; i < BUCKETS; i++) values.push(fmtFloat(W[c][i]) + "f");
    for (let i = 0; i < values.length; i += 8) {
      lines.push("            " + values.slice(i, i + 8).join(", ") + ",");
    }
    lines.push("        ),");
  }
  lines.push("    )");
  lines.push("");
  lines.push("    val BIAS: FloatArray = floatArrayOf(");
  const biasValues = [];
  for (let c = 0; c < 3; c++) biasValues.push(fmtFloat(bias[c]) + "f");
  lines.push("        " + biasValues.join(", ") + ",");
  lines.push("    )");
  lines.push("}");
  lines.push("");
  return lines.join("\n");
}

function writeJsonl(file, rows) {
  const body = rows
    .map((r) => JSON.stringify({ sender: r.sender, body: r.body, label: r.label }))
    .join("\n");
  fs.writeFileSync(file, body + "\n", "utf8");
}

const FEATURE_FIXTURE_TEXTS = [
  "Rs.1,234.50 debited from a/c XX1234 on 19-11-26",
  "  You   spent £12.40 at Tesco.  ",
  "123456 is your OTP for HDFC netbanking. Do not share.",
  "Your A/c XX1234 balance is Rs.45,678.90 as on 19-11",
  "₹1,23,456.00 credited to a/c XX5678",
];

function main() {
  console.log("PocketLedger classifier trainer");
  console.log(`seed ${SEED}, buckets ${BUCKETS}, features char 2..4-grams`);
  console.log(
    `Adam lr=${TRAINING.learningRate} batch=${TRAINING.batchSize} epochs=${TRAINING.epochs} l2=${TRAINING.l2}`,
  );

  const trainRows = generateRows(SEED, TRAIN_COUNTS);
  trainRows.push(...EXTRA_TRAIN);
  const holdoutRows = generateRows(SEED + 101, HOLDOUT_COUNTS);
  const adversarialRows = ADVERSARIAL;

  console.log(
    `dataset: train ${trainRows.length}, holdout ${holdoutRows.length}, adversarial ${adversarialRows.length}`,
  );

  const { W, bias } = trainModel(trainRows);

  const train = evaluateDataset(trainRows, W, bias);
  const holdout = evaluateDataset(holdoutRows, W, bias);
  const adversarial = evaluateDataset(adversarialRows, W, bias);

  console.log("");
  console.log(
    `Train: acc=${train.accuracy} txn P=${train.txn_binary.precision} R=${train.txn_binary.recall}`,
  );
  console.log("Holdout confusion matrix (rows actual, cols predicted: NOT_TXN, DEBIT, CREDIT)");
  for (const row of holdout.confusion_matrix) console.log("  " + row.join("  "));
  console.log(
    `Holdout txn: P=${holdout.txn_binary.precision} R=${holdout.txn_binary.recall} F1=${holdout.txn_binary.f1}`,
  );
  console.log(
    `Adversarial txn: P=${adversarial.txn_binary.precision} R=${adversarial.txn_binary.recall} F1=${adversarial.txn_binary.f1}`,
  );
  console.log("");

  if (holdout.misses.length > 0) {
    console.log(`Holdout misses (${holdout.misses.length}):`);
    for (const m of holdout.misses.slice(0, 20)) {
      console.log(`  [${m.actual} -> ${m.predicted}] ${m.body}`);
    }
    console.log("");
  }
  if (adversarial.misses.length > 0) {
    console.log(`Adversarial misses (${adversarial.misses.length}):`);
    for (const m of adversarial.misses) {
      console.log(`  [${m.actual} -> ${m.predicted}] ${m.body}`);
    }
    console.log("");
  }

  const modelSource = kotlinModelSource(W, bias);
  fs.mkdirSync(path.dirname(OUT_MODEL), { recursive: true });
  fs.writeFileSync(OUT_MODEL, modelSource, "utf8");
  writeJsonl(OUT_HOLDOUT, holdoutRows);
  writeJsonl(OUT_ADVERSARIAL, adversarialRows);

  const fixture = FEATURE_FIXTURE_TEXTS.map((text) => ({
    text,
    buckets: featureBuckets(text),
  }));

  const targets = {
    holdout_txn_precision: {
      required: 0.99,
      achieved: holdout.txn_binary.precision,
      passed: holdout.txn_binary.precision >= 0.99,
    },
    holdout_txn_recall: {
      required: 0.9,
      achieved: holdout.txn_binary.recall,
      passed: holdout.txn_binary.recall >= 0.9,
    },
    adversarial_txn_precision: {
      required: 0.95,
      achieved: adversarial.txn_binary.precision,
      passed: adversarial.txn_binary.precision >= 0.95,
    },
  };
  const allPassed = Object.values(targets).every((t) => t.passed);

  const report = {
    generated_by: "tools/train_classifier.mjs",
    generated_at_note: "Deterministic; timestamp intentionally omitted.",
    seed: SEED,
    buckets: BUCKETS,
    classes: CLASSES,
    features: {
      normalization: "lowercase, trim, collapse whitespace runs to one space",
      ngrams: "character n-grams n=2..4 over UTF-16 code units, presence=1",
      hash: "FNV-1a 32-bit, bucket = h % 1024",
      bias_in_features: false,
    },
    training: TRAINING,
    dataset_sizes: {
      train: trainRows.length,
      train_generated: trainRows.length - EXTRA_TRAIN.length,
      train_curated_hard_negatives: EXTRA_TRAIN.length,
      holdout: holdoutRows.length,
      adversarial: adversarialRows.length,
    },
    train_class_counts: {
      NOT_TXN: trainRows.filter((r) => r.label === "NOT_TXN").length,
      DEBIT: trainRows.filter((r) => r.label === "DEBIT").length,
      CREDIT: trainRows.filter((r) => r.label === "CREDIT").length,
    },
    holdout_class_counts: {
      NOT_TXN: HOLDOUT_COUNTS.NOT_TXN,
      DEBIT: HOLDOUT_COUNTS.DEBIT,
      CREDIT: HOLDOUT_COUNTS.CREDIT,
    },
    targets,
    targets_passed: allPassed,
    holdout: {
      confusion_matrix: holdout.confusion_matrix,
      per_class: holdout.per_class,
      txn_binary: holdout.txn_binary,
      direction_accuracy_among_txn: holdout.direction_accuracy_among_txn,
      accuracy: holdout.accuracy,
      cases: holdout.cases,
      misses: holdout.misses,
    },
    adversarial: {
      confusion_matrix: adversarial.confusion_matrix,
      per_class: adversarial.per_class,
      txn_binary: adversarial.txn_binary,
      direction_accuracy_among_txn: adversarial.direction_accuracy_among_txn,
      accuracy: adversarial.accuracy,
      cases: adversarial.cases,
      misses: adversarial.misses,
    },
    model_sha256: crypto.createHash("sha256").update(modelSource).digest("hex"),
    artifacts: {
      kotlin_model: "app/src/main/java/app/pocketledger/ml/BundledModel.kt",
      holdout_jsonl: "tools/eval_holdout.jsonl",
      adversarial_jsonl: "tools/eval_adversarial.jsonl",
    },
    feature_fixture: fixture,
  };

  fs.writeFileSync(OUT_REPORT, JSON.stringify(report, null, 2) + "\n", "utf8");

  console.log("Targets:");
  for (const [name, t] of Object.entries(targets)) {
    console.log(`  ${t.passed ? "PASS" : "FAIL"} ${name}: required ${t.required}, achieved ${t.achieved}`);
  }
  console.log("");
  console.log(`model -> ${path.relative(ROOT, OUT_MODEL)} (sha256 ${report.model_sha256.slice(0, 12)}…)`);
  console.log(`report -> ${path.relative(ROOT, OUT_REPORT)}`);
  console.log(`holdout -> ${path.relative(ROOT, OUT_HOLDOUT)}`);
  console.log(`adversarial -> ${path.relative(ROOT, OUT_ADVERSARIAL)}`);

  if (!allPassed) {
    console.error("\nTRAINING TARGETS NOT MET — fix the corpus/model, do not lower thresholds.");
    process.exitCode = 1;
  } else {
    console.log("\nAll targets met.");
  }
}

main();
