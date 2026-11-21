// SPDX-License-Identifier: AGPL-3.0-or-later
package btm.m.os4.systemuihook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The notification hook posts one replacement callback per attached root and
 * cancels it on detach. This host is that policy without Android views.
 */
class NotificationRefreshLedgerTest {
    private class Host {
        val ledger = NotificationRefreshLedger<Any>()
        val queued = HashMap<Any, Int>()
        val applied = HashMap<Any, Int>()
        var appearance = 0

        fun refresh(targets: Collection<Any>, attached: Set<Any>) {
            val plan = ledger.planRefresh(targets) { it in attached }
            plan.dropped.forEach { queued.remove(it) }
            plan.refresh.forEach { target ->
                queued[target] = 1
            }
        }

        fun drain(attached: Set<Any>) {
            val pending = queued.keys.toList()
            pending.forEach { target ->
                queued.remove(target)
                ledger.finish(target)
                if (target in attached) applied[target] = appearance
            }
        }

        fun detach(target: Any) {
            if (ledger.detach(target)) queued.remove(target)
            queued.remove(target)
        }

        fun attach(target: Any) {
            ledger.onAttached(target)
            queued.remove(target)
            applied[target] = appearance
        }
    }

    @Test
    fun detachDropsTheTargetAndItsCallback() {
        val host = Host()
        val root = Any()
        val child = Any()
        host.refresh(listOf(root, child), setOf(root, child))
        assertEquals(2, host.queued.size)
        host.detach(root)
        host.detach(child)
        assertEquals(0, host.queued.size)
        assertEquals(0, host.ledger.retainedCount())
        assertEquals(0, host.ledger.queuedCount())
        host.drain(emptySet())
        assertEquals(0, host.applied.size)
    }

    @Test
    fun repeatedDarkAndSettingsRefreshesStayBounded() {
        val host = Host()
        val attached = List(40) { Any() }
        val detached = List(20) { Any() }
        val all = attached + detached
        repeat(100) {
            host.appearance = it
            host.refresh(all, attached.toSet())
            assertEquals(attached.size, host.queued.size)
            assertEquals(attached.size, host.ledger.queuedCount())
            assertTrue(detached.none { view -> view in host.queued })
        }
        assertEquals(attached.size, host.ledger.retainedCount())
        host.drain(attached.toSet())
        assertEquals(0, host.ledger.queuedCount())
        assertEquals(0, host.queued.size)
        attached.forEach { assertEquals(99, host.applied[it]) }
        detached.forEach { assertFalse(host.applied.containsKey(it)) }
    }

    @Test
    fun reattachAppliesTheCurrentAppearanceOnce() {
        val host = Host()
        val root = Any()
        host.appearance = 1
        host.refresh(listOf(root), setOf(root))
        assertEquals(1, host.queued.size)
        host.detach(root)
        assertEquals(0, host.queued.size)
        host.appearance = 3
        host.attach(root)
        assertEquals(3, host.applied[root])
        assertEquals(0, host.queued.size)
        assertEquals(0, host.ledger.queuedCount())
        assertEquals(1, host.ledger.retainedCount())
        host.drain(setOf(root))
        assertEquals(3, host.applied[root])
    }
}
