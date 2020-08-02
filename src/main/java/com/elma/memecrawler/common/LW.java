package com.elma.memecrawler.common;

import java.util.concurrent.Callable;

public final class LW {
    private LW() {
    }

    public static <T> T wrap(Callable<T> callable) {
        try {
            return callable.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void wrap(Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @FunctionalInterface
    public interface Runnable {
        void run() throws Exception;
    }
}
