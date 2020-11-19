/*
 * Copyright (c) [2016] [ <ether.camp> ]
 * This file is part of the ethereumJ library.
 *
 * The ethereumJ library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ethereumJ library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ethereumJ library. If not, see <http://www.gnu.org/licenses/>.
 */
package org.ethereum.mine;

import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.List;

/**
 *  Future completes when any of child futures completed. All others are cancelled
 *  upon completion.
 */
public class AnyFuture<V> extends AbstractFuture<V> {
    private List<ListenableFuture<V>> futures = new ArrayList<>();

    /**
     * Add a Future delegate
     * 主要原因有：
     * 1.存在共享数据
     * 2.多线程共同操作共享数据。
     * 关键字synchronized可以保证在同一时刻，
     * 只有一个线程可以执行某个方法或某个代码块，
     * 同时synchronized可以保证一个线程的变化可见（可见性），即可以代替volatile。
     */
    public synchronized void add(final ListenableFuture<V> f) {
        if (isCancelled() || isDone()) return;

        f.addListener(() -> futureCompleted(f),  MoreExecutors.directExecutor());
        futures.add(f);
    }

    private synchronized void futureCompleted(ListenableFuture<V> f) {
        if (isCancelled() || isDone()) return;
        if (f.isCancelled()) return;

        try {
            cancelOthers(f);
            
            V v = f.get();
            postProcess(v);
            set(v);
        } catch (Exception e) {
            setException(e);
        }
    }

    /**
     * Subclasses my override to perform some task on the calculated
     * value before returning it via Future
     */
    protected void postProcess(V v) {}

    private void cancelOthers(ListenableFuture besidesThis) {
        for (ListenableFuture future : futures) {
            if (future != besidesThis) {
                try {
                    future.cancel(true);
                } catch (Exception e) {
                }
            }
        }
    }

    @Override
    protected void interruptTask() {
        cancelOthers(null);
    }
}
