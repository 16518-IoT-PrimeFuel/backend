package com.primefuel.fulltank.platform.shared.interfaces.rest.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class RucValidator implements ConstraintValidator<ValidRuc, String> {

    private static final int[] WEIGHTS = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};

    @Override
    public boolean isValid(String ruc, ConstraintValidatorContext context) {
        return ruc == null || hasValidCheckDigit(ruc);
    }

    static boolean hasValidCheckDigit(String ruc) {
        if (ruc == null || !ruc.matches("[0-9]{11}")) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < WEIGHTS.length; i++) {
            sum += (ruc.charAt(i) - '0') * WEIGHTS[i];
        }
        int check = 11 - sum % 11;
        if (check == 10) {
            check = 0;
        } else if (check == 11) {
            check = 1;
        }
        return check == ruc.charAt(10) - '0';
    }
}
