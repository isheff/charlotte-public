package com.xinwenwang.hetcons;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class HetconsRestartStatus {

    private Future<?> m1bTimer;
    private Future<?> m2bTimer;
    private Future<?> restartTimer;

    private ExecutorService service;

    private Object lock = new Object();

    public HetconsRestartStatus() {
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
