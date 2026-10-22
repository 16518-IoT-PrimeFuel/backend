package com.primefuel.fulltank.edge.application;

/** La lectura no cumple la política de la Edge y no se reenvía. */
public class ReadingRejectedException extends RuntimeException {

    public ReadingRejectedException(String reason) {
        super(reason);
    }
}
