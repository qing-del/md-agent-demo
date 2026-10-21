package com.jacolp.agent.markdown;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import com.jacolp.agent.markdown.model.DocumentId;

/** JVM-local locks shared by SDK and application writes; not a distributed lock. */
public final class DocumentLocks {
    private static final ConcurrentMap<DocumentId, ReentrantLock> LOCKS = new ConcurrentHashMap<>();

    private DocumentLocks() { }

    static ConcurrentMap<DocumentId, ReentrantLock> locks() {
        return LOCKS;
    }

    public static <T> T withLock(DocumentId id, Supplier<T> action) {
        ReentrantLock lock = LOCKS.computeIfAbsent(id, ignored -> new ReentrantLock());
        lock.lock();
        try {
            return action.get();
        }
        finally {
            lock.unlock();
        }
    }
}
