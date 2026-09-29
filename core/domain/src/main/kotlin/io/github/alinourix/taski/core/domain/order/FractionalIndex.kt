package io.github.alinourix.taski.core.domain.order

/**
 * Fractional index keys for manual order: strings that sort lexicographically,
 * with a key between any two neighbours. Moving an item writes one key on that
 * item only, so two devices reordering at the same time never renumber each
 * other's rows. Equal keys (two devices inserting into the same gap) are
 * ordered by row id.
 *
 * Port of the algorithm in rocicorp/fractional-indexing (CC0), base 62. Keys
 * are compared bytewise: SQLite's default BINARY collation does that, and on
 * Postgres the column is declared `COLLATE "C"`.
 */
object FractionalIndex {
    const val DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    private const val ZERO = '0'
    private val SMALLEST_INTEGER = "A" + ZERO.toString().repeat(26)

    /** A key strictly between [a] and [b]; null means "no neighbour on that side". */
    fun between(a: String?, b: String?): String {
        a?.let(::validate)
        b?.let(::validate)
        if (a != null && b != null) require(a < b) { "keys out of order: $a >= $b" }

        if (a == null) {
            if (b == null) return "a$ZERO"
            val ib = integerPart(b)
            val fb = b.substring(ib.length)
            if (ib == SMALLEST_INTEGER) return ib + midpoint("", fb)
            if (ib < b) return ib
            return requireNotNull(decrementInteger(ib)) { "cannot decrement below $b" }
        }
        if (b == null) {
            val ia = integerPart(a)
            val fa = a.substring(ia.length)
            return incrementInteger(ia) ?: (ia + midpoint(fa, null))
        }
        val ia = integerPart(a)
        val fa = a.substring(ia.length)
        val ib = integerPart(b)
        val fb = b.substring(ib.length)
        if (ia == ib) return ia + midpoint(fa, fb)
        val i = requireNotNull(incrementInteger(ia)) { "cannot increment $a" }
        return if (i < b) i else ia + midpoint(fa, null)
    }

    /** [n] evenly spread keys between [a] and [b], in order. */
    fun nBetween(a: String?, b: String?, n: Int): List<String> {
        if (n <= 0) return emptyList()
        if (n == 1) return listOf(between(a, b))
        if (b == null) {
            var c = between(a, null)
            val result = mutableListOf(c)
            repeat(n - 1) { c = between(c, null); result += c }
            return result
        }
        if (a == null) {
            var c = between(null, b)
            val result = mutableListOf(c)
            repeat(n - 1) { c = between(null, c); result += c }
            return result.reversed()
        }
        val mid = n / 2
        val c = between(a, b)
        return nBetween(a, c, mid) + c + nBetween(c, b, n - mid - 1)
    }

    fun isValid(key: String): Boolean = runCatching { validate(key) }.isSuccess

    private fun midpoint(a: String, b: String?): String {
        if (b != null) require(a < b) { "midpoint out of order: $a >= $b" }
        require(a.lastOrNull() != ZERO && b?.lastOrNull() != ZERO) { "trailing zero" }
        if (b != null) {
            var n = 0
            while (n < b.length && (a.getOrNull(n) ?: ZERO) == b[n]) n++
            if (n > 0) return b.substring(0, n) + midpoint(a.substring(minOf(n, a.length)), b.substring(n))
        }
        val digitA = if (a.isNotEmpty()) DIGITS.indexOf(a[0]) else 0
        val digitB = if (b != null) DIGITS.indexOf(b[0]) else DIGITS.length
        if (digitB - digitA > 1) return DIGITS[(digitA + digitB + 1) / 2].toString()
        return if (b != null && b.length > 1) {
            b.substring(0, 1)
        } else {
            DIGITS[digitA] + midpoint(if (a.isNotEmpty()) a.substring(1) else "", null)
        }
    }

    private fun integerLength(head: Char): Int = when (head) {
        in 'a'..'z' -> head - 'a' + 2
        in 'A'..'Z' -> 'Z' - head + 2
        else -> throw IllegalArgumentException("invalid order key head: $head")
    }

    private fun integerPart(key: String): String {
        val length = integerLength(key[0])
        require(length <= key.length) { "invalid order key: $key" }
        return key.substring(0, length)
    }

    private fun validate(key: String) {
        require(key.isNotEmpty() && key.all { it in DIGITS }) { "invalid order key: $key" }
        require(key != SMALLEST_INTEGER) { "invalid order key: $key" }
        val integer = integerPart(key)
        require(key.substring(integer.length).lastOrNull() != ZERO) { "invalid order key: $key" }
    }

    private fun incrementInteger(x: String): String? {
        val head = x[0]
        val digits = x.substring(1).toCharArray().toMutableList()
        var carry = true
        var i = digits.lastIndex
        while (carry && i >= 0) {
            val d = DIGITS.indexOf(digits[i]) + 1
            if (d == DIGITS.length) {
                digits[i] = ZERO
            } else {
                digits[i] = DIGITS[d]
                carry = false
            }
            i--
        }
        if (!carry) return head + digits.joinToString("")
        if (head == 'Z') return "a$ZERO"
        if (head == 'z') return null
        val h = head + 1
        if (h > 'a') digits += ZERO else digits.removeAt(digits.lastIndex)
        return h + digits.joinToString("")
    }

    private fun decrementInteger(x: String): String? {
        val head = x[0]
        val digits = x.substring(1).toCharArray().toMutableList()
        var borrow = true
        var i = digits.lastIndex
        while (borrow && i >= 0) {
            val d = DIGITS.indexOf(digits[i]) - 1
            if (d == -1) {
                digits[i] = DIGITS.last()
            } else {
                digits[i] = DIGITS[d]
                borrow = false
            }
            i--
        }
        if (!borrow) return head + digits.joinToString("")
        if (head == 'a') return "Z" + DIGITS.last()
        if (head == 'A') return null
        val h = head - 1
        if (h < 'Z') digits += DIGITS.last() else digits.removeAt(digits.lastIndex)
        return h + digits.joinToString("")
    }
}
