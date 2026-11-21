// SPDX-License-Identifier: AGPL-3.0-or-later
package btm.m.os4.systemuihook.hypermusiccover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.ref.WeakReference

class CardCandidateTableTest {
    private class Subtree(val element: Any, val child: Any)

    @Test
    fun detachForgetsTheElementWhileTheCallerStillHoldsIt() {
        val table = CardCandidateTable<Any, Any>()
        val element = Any()
        val row = Subtree(element, Any())
        table.note(element, row)
        assertEquals(1, table.size())
        table.forget(element)
        assertEquals(0, table.size())
    }

    @Test
    fun snapshotRemovesDetachedElements() {
        val table = CardCandidateTable<Any, Any>()
        val live = Any()
        val gone = Any()
        table.note(live, Any())
        table.note(gone, Subtree(gone, Any()))
        val snap = table.snapshotAttached { it === live }
        assertEquals(1, snap.attached.size)
        assertEquals(live, snap.attached[0].element)
        assertEquals(1, snap.dropped)
        assertEquals(1, table.size())
        val second = table.snapshotAttached { false }
        assertEquals(0, second.attached.size)
        assertEquals(1, second.dropped)
        assertEquals(0, table.size())
    }

    @Test
    fun firstRowIsKeptUntilTheElementIsForgotten() {
        val table = CardCandidateTable<Any, String>()
        val element = Any()
        table.note(element, "first")
        table.note(element, "second")
        val snap = table.snapshotAttached { true }
        assertEquals("first", snap.attached[0].row)
        table.forget(element)
        table.note(element, "second")
        assertEquals("second", table.snapshotAttached { true }.attached[0].row)
    }

    @Test
    fun tableDoesNotRetainADetachedSubtree() {
        val table = CardCandidateTable<Any, Subtree>()
        val refs = detachedSubtree(table)
        awaitCollected(refs.element)
        awaitCollected(refs.row)
        awaitCollected(refs.child)
        assertNull(refs.element.get())
        assertNull(refs.row.get())
        assertNull(refs.child.get())
        assertEquals(0, table.size())
    }

    @Test
    fun weakRowDoesNotKeepTheSubtreeAliveAfterTheCallerDropsIt() {
        val table = CardCandidateTable<Any, Subtree>()
        val refs = rememberedThenDropped(table)
        awaitCollected(refs.element)
        awaitCollected(refs.row)
        awaitCollected(refs.child)
        assertNull(refs.element.get())
        assertNull(refs.row.get())
        assertNull(refs.child.get())
        assertEquals(0, table.size())
    }

    private class Refs(
        val element: WeakReference<Any>,
        val row: WeakReference<Subtree>,
        val child: WeakReference<Any>,
    )

    private fun detachedSubtree(table: CardCandidateTable<Any, Subtree>): Refs {
        var element: Any? = Any()
        var child: Any? = Any()
        var row: Subtree? = Subtree(element!!, child!!)
        table.note(element!!, row!!)
        table.forget(element!!)
        val refs = Refs(WeakReference(element), WeakReference(row), WeakReference(child))
        element = null
        child = null
        row = null
        return refs
    }

    private fun rememberedThenDropped(table: CardCandidateTable<Any, Subtree>): Refs {
        var element: Any? = Any()
        var child: Any? = Any()
        var row: Subtree? = Subtree(element!!, child!!)
        table.note(element!!, row!!)
        val refs = Refs(WeakReference(element), WeakReference(row!!), WeakReference(child))
        element = null
        child = null
        row = null
        return refs
    }

    private fun awaitCollected(ref: WeakReference<*>) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (ref.get() != null && System.nanoTime() < deadline) {
            System.gc()
            System.runFinalization()
            Thread.sleep(20)
        }
        assertTrue(ref.get() == null)
    }
}
