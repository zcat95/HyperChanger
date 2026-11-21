// SPDX-License-Identifier: AGPL-3.0-or-later
package btm.m.os4.systemuihook.hypermusiccover;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * Card elements the shade sweep may still style.
 *
 * Keys and rows are weak. A row holds its element, so a strong row value would
 * keep the whole notification subtree after SystemUI has dropped it. Detached
 * elements are removed explicitly and are not left for a later garbage
 * collection. The first row recorded for an element is kept, matching the
 * previous candidate map.
 */
final class CardCandidateTable<E, R> {
    static final class Hit<E, R> {
        final E element;
        final R row;

        Hit(E element, R row) {
            this.element = element;
            this.row = row;
        }
    }

    static final class Snapshot<E, R> {
        final List<Hit<E, R>> attached;
        final int dropped;

        Snapshot(List<Hit<E, R>> attached, int dropped) {
            this.attached = attached;
            this.dropped = dropped;
        }
    }

    private final Map<E, WeakReference<R>> rows = Collections.synchronizedMap(new WeakHashMap<>());

    void note(E element, R row) {
        if (element == null) return;
        synchronized (rows) {
            if (rows.containsKey(element)) return;
            rows.put(element, row == null ? null : new WeakReference<>(row));
        }
    }

    void forget(E element) {
        if (element == null) return;
        synchronized (rows) {
            rows.remove(element);
        }
    }

    Snapshot<E, R> snapshotAttached(Predicate<E> attached) {
        final List<Map.Entry<E, WeakReference<R>>> copy;
        synchronized (rows) {
            copy = new ArrayList<>(rows.entrySet());
        }
        final List<Hit<E, R>> live = new ArrayList<>();
        int dropped = 0;
        for (int i = 0; i < copy.size(); i++) {
            final Map.Entry<E, WeakReference<R>> entry = copy.get(i);
            final E element = entry.getKey();
            if (element == null || !attached.test(element)) {
                forget(element);
                dropped++;
                continue;
            }
            final WeakReference<R> ref = entry.getValue();
            live.add(new Hit<>(element, ref == null ? null : ref.get()));
        }
        return new Snapshot<>(live, dropped);
    }

    int size() {
        synchronized (rows) {
            return rows.size();
        }
    }
}
