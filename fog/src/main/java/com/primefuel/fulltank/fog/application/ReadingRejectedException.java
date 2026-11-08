package com.primefuel.fulltank.fog.application;

/** La lectura no cumple la política de la Fog y no se reenvía. */
public class ReadingRejectedException extends RuntimeException {

    public ReadingRejectedException(String reason) {
        super(reason);
    }
}
