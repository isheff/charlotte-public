package com.xinwenwang.hetcons;

import com.isaacsheff.charlotte.proto.CryptoId;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

public class HetconsRestartStatus {

    private static final ExecutorService gService = Executors.newCachedThreadPool();

    private Future<?> m1bTimer;
    private Future<?> m2bTimer;
    private Future<?> restartTimer;

    private Set<CryptoId> leftObservers;

    /* global */
    private Thread restartThread;

    private Object lock = new Object();
    private Object numlock = new Object();

    public HetconsRestartStatus(List<CryptoId> leftObservers) {
        this.leftObservers = new HashSet<>(leftObservers);
    }

    public Future<?> getM1bTimer() {
        return m1bTimer;
    }

    public Future<?> getM2bTimer() {
        return m2bTimer;
    }

    public Future<?> getRestartTimer() {
        return restartTimer;
    }

    public void cancelTimers() {
        if (m1bTimer != null)
            m1bTimer.cancel(true);
        if (m2bTimer != null)
            m2bTimer.cancel(true);
        if (restartTimer != null)
            restartTimer.cancel(true);

        m1bTimer = null;
        m2bTimer = null;
        restartTimer = null;
        if (restartThread != null)
            restartThread.interrupt();
    }

    public void shutdown() {
        synchronized (numlock) {
            if (leftObservers.size() > 0)
                return;
        }
        cancelTimers();
        try {
            /* TODO: Eliminate waiting */
            gService.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        } finally {

        }
    }

    public void decided(CryptoId id) {
        synchronized (numlock) {
            this.leftObservers.remove(id);
        }
        shutdown();
    }


    public int getLeftObserversSize() {
        synchronized (numlock) {
            return leftObservers.size();
        }
    }

    public ExecutorService getService() {
        return gService;
    }

    public void setRestartThread(Thread restartThread) {
        this.restartThread = restartThread;
    }

    public Object getLock() {
        return lock;
    }

    public void setM1bTimer(Future<?> m1bTimer) {
        this.m1bTimer = m1bTimer;
    }

    public void setM2bTimer(Future<?> m2bTimer) {
        this.m2bTimer = m2bTimer;
    }

    public void setRestartTimer(Future<?> restartTimer) {
        this.restartTimer = restartTimer;
    }
}
