package com.xinwenwang.hetcons;

import com.isaacsheff.charlotte.proto.CryptoId;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class HetconsRestartStatus {

    private Future<?> m1bTimer;
    private Future<?> m2bTimer;
    private Future<?> restartTimer;

    private Set<CryptoId> leftObservers;

    private ExecutorService service;

    private Object lock = new Object();
    private Object numlock = new Object();

    public HetconsRestartStatus(List<CryptoId> leftObservers) {
        this.leftObservers = new HashSet<>(leftObservers);
        service = Executors.newSingleThreadExecutor();
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
    }

    public void shutdown() {
        synchronized (numlock) {
            if (leftObservers.size() > 0)
                return;
        }
        cancelTimers();
        service.shutdownNow();

        try {
            /* TODO: Eliminate waiting */
            service.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        } finally {

        }
    }

    public void decided(CryptoId id) {
        synchronized (numlock) {
            this.leftObservers.remove(id);
        }
    }


    public int getLeftObserversSize() {
        synchronized (numlock) {
            return leftObservers.size();
        }
    }

    public ExecutorService getService() {
        return service;
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
