package com.primefuel.fulltank.platform;

/** Genera RUC de prueba con dígito verificador módulo 11 (SUNAT) para que pasen la validación REST. */
public final class RucFixtures {

    private static final int[] WEIGHTS = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};

    private RucFixtures() {
    }

    /** Añade el dígito verificador a 10 dígitos. */
    public static String withCheckDigit(String first10) {
        if (!first10.matches("[0-9]{10}")) {
            throw new IllegalArgumentException(first10);
        }
        int sum = 0;
        for (int i = 0; i < WEIGHTS.length; i++) {
            sum += (first10.charAt(i) - '0') * WEIGHTS[i];
        }
        int check = 11 - sum % 11;
        return first10 + (check == 10 ? 0 : check == 11 ? 1 : check);
    }

    /** RUC válido: {@code prefix} seguido de {@code sequence} con relleno a 10 dígitos más el verificador. */
    public static String sequenced(String prefix, long sequence) {
        return withCheckDigit(prefix + String.format("%0" + (10 - prefix.length()) + "d", sequence));
    }
}
