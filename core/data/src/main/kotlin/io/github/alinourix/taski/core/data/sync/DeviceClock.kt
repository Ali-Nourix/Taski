package io.github.alinourix.taski.core.data.sync

import android.content.SharedPreferences
import io.github.alinourix.taski.core.domain.sync.DefaultHlcClock
import io.github.alinourix.taski.core.domain.sync.Hlc
import io.github.alinourix.taski.core.domain.sync.HlcClock
import io.github.alinourix.taski.core.domain.time.Clock
import java.security.SecureRandom

/**
 * This device's HLC node id and the last timestamp it issued, kept across
 * restarts so a clock set back while the app was closed still cannot produce
 * a revision older than one already written.
 */
class PersistentHlcClock(
    private val prefs: SharedPreferences,
    clock: Clock,
) : HlcClock {
    val node: String = prefs.getString(KEY_NODE, null) ?: newNode().also { prefs.edit().putString(KEY_NODE, it).apply() }

    private val delegate = DefaultHlcClock(clock, node, Hlc.parseOrNull(prefs.getString(KEY_LAST, null))?.copy(node = node))

    override fun now(): Hlc = delegate.now().also(::remember)

    override fun receive(remote: Hlc): Hlc = delegate.receive(remote).also(::remember)

    private fun remember(hlc: Hlc) {
        prefs.edit().putString(KEY_LAST, hlc.encode()).apply()
    }

    private companion object {
        const val KEY_NODE = "hlc_node"
        const val KEY_LAST = "hlc_last"

        fun newNode(): String {
            val bytes = ByteArray(Hlc.NODE_LENGTH / 2).also(SecureRandom()::nextBytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
