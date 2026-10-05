package com.cobbleascend.store;

/** Fault-injection seam for recovery tests. Production code uses {@link #NONE}. */
public interface Faults {
    enum Point {
        /** Domain result computed and funds checked; nothing written yet. */
        AFTER_VALIDATION,
        /** Everything written inside the open transaction; not yet committed. */
        BEFORE_COMMIT,
        /** Committed durably; the caller has not yet seen the result. */
        AFTER_COMMIT
    }

    Faults NONE = point -> {};

    void at(Point point);
}
