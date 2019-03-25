package com.luxoft.fabric.events.ordering;

import java.util.concurrent.Callable;

public class FabricQueryException extends Exception {

    public FabricQueryException(String message) {
        super(message);
    }

    public FabricQueryException(String message, Throwable cause) {
        super(message, cause);
    }

    public FabricQueryException(Throwable cause) {
        super(cause);
    }

    public static <T> T withGuard(Callable<T> callable) throws FabricQueryException {
        try {
            return callable.call();
        } catch (FabricQueryException e) {
            throw e;
        } catch (Exception ex) {
            throw new FabricQueryException(ex);
        }
    }

    @FunctionalInterface
    public interface Runnable {
        void run() throws FabricQueryException;
    }

    @FunctionalInterface
    public interface Producer<T> {
        T get() throws FabricQueryException;
    }

    @FunctionalInterface
    public interface Consumer<T> {
        void apply(T data) throws FabricQueryException;
    }
}
