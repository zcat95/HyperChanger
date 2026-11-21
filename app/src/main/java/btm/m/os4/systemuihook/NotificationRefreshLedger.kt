// SPDX-License-Identifier: AGPL-3.0-or-later
package btm.m.os4.systemuihook

import java.util.Collections
import java.util.IdentityHashMap

/**
 * Bounds notification appearance refreshes.
 *
 * A detached target is dropped immediately, including any queued refresh.
 * Each attached target keeps one queued refresh; the caller replaces that
 * callback instead of posting another. Reattach records a live target with
 * an empty queue so the current appearance can be applied once.
 */
internal class NotificationRefreshLedger<T : Any> {
    private class Slot(var queued: Boolean = false)

    private val slots = Collections.synchronizedMap(IdentityHashMap<T, Slot>())

    class Plan<T>(val refresh: List<T>, val dropped: List<T>)

    fun track(target: T) {
        synchronized(slots) {
            if (target !in slots) slots[target] = Slot()
        }
    }

    /**
     * [Plan.refresh] is every attached target. Each one already counts as the
     * single queued refresh, so the caller cancels the previous callback and
     * posts one replacement. Detached targets are removed from [dropped].
     */
    fun planRefresh(targets: Collection<T>, attached: (T) -> Boolean): Plan<T> {
        val refresh = ArrayList<T>()
        val dropped = ArrayList<T>()
        synchronized(slots) {
            val seen = Collections.newSetFromMap(IdentityHashMap<T, Boolean>())
            for (target in targets) {
                if (!seen.add(target)) continue
                if (!attached(target)) {
                    slots.remove(target)
                    dropped.add(target)
                    continue
                }
                slots.getOrPut(target) { Slot() }.queued = true
                refresh.add(target)
            }
            val leftover = slots.keys.iterator()
            while (leftover.hasNext()) {
                val target = leftover.next()
                if (target !in seen) {
                    leftover.remove()
                    dropped.add(target)
                }
            }
        }
        return Plan(refresh, dropped)
    }

    fun finish(target: T) {
        synchronized(slots) { slots[target]?.queued = false }
    }

    /** Returns whether a callback was queued and must be removed from the view. */
    fun detach(target: T): Boolean {
        synchronized(slots) {
            val slot = slots.remove(target) ?: return false
            return slot.queued
        }
    }

    fun onAttached(target: T) {
        synchronized(slots) { slots[target] = Slot(queued = false) }
    }

    fun retainedCount(): Int = synchronized(slots) { slots.size }

    fun queuedCount(): Int = synchronized(slots) { slots.values.count { it.queued } }
}
