package org.janusgraph.diskstorage.util;

import com.codahale.metrics.Counter;
import org.janusgraph.util.stats.MetricManager;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class InstrumentedSemaphore extends Semaphore {

    private final Counter counter;

    public InstrumentedSemaphore(MetricManager mgr, String name, int permits) {
        super(permits);
        counter = mgr.getCounter(name);
    }

    public InstrumentedSemaphore(MetricManager mgr, String name, int permits, boolean fair) {
        super(permits, fair);
        counter = mgr.getCounter(name);
    }

    @Override
    public void acquire() throws InterruptedException {
        super.acquire();
        counter.inc();
    }

    @Override
    public void acquireUninterruptibly() {
        super.acquireUninterruptibly();
        counter.inc();
    }

    @Override
    public boolean tryAcquire() {
        boolean acquired = super.tryAcquire();
        if (acquired) {
            counter.inc();
        }
        return acquired;
    }

    @Override
    public boolean tryAcquire(long timeout, TimeUnit unit) throws InterruptedException {
        boolean acquired = super.tryAcquire(timeout, unit);
        if (acquired) {
            counter.inc();
        }
        return acquired;
    }

    @Override
    public void acquire(int permits) throws InterruptedException {
        super.acquire(permits);
        counter.inc(permits);
    }

    @Override
    public void acquireUninterruptibly(int permits) {
        super.acquireUninterruptibly(permits);
        counter.inc(permits);
    }

    @Override
    public boolean tryAcquire(int permits) {
        boolean acquired = super.tryAcquire(permits);
        if (acquired) {
            counter.inc(permits);
        }
        return acquired;
    }

    @Override
    public boolean tryAcquire(int permits, long timeout, TimeUnit unit) throws InterruptedException {
        boolean acquired = super.tryAcquire(permits, timeout, unit);
        if (acquired) {
            counter.inc(permits);
        }
        return acquired;
    }

    @Override
    public void release() {
        super.release();
        counter.dec();
    }

    @Override
    public void release(int permits) {
        super.release(permits);
        counter.dec(permits);
    }

    @Override
    public int drainPermits() {
        int  permits = super.drainPermits();
        if (permits > 0) {
            counter.dec(permits);
        }
        return permits;
    }

    @Override
    protected void reducePermits(int reduction) {
        super.reducePermits(reduction);
        counter.dec(reduction);
    }
}
