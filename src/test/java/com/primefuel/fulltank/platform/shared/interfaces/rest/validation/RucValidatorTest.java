package com.primefuel.fulltank.platform.shared.interfaces.rest.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RucValidatorTest {

    private final RucValidator validator = new RucValidator();

    @Test
    void acceptsRucsWithValidCheckDigitAndNullButRejectsTheRest() {
        assertTrue(validator.isValid("20100070970", null));
        assertTrue(validator.isValid("20131312955", null));
        assertTrue(validator.isValid(null, null));
        assertFalse(validator.isValid("20100070971", null));
        assertFalse(validator.isValid("2010007097", null));
        assertFalse(validator.isValid("2010007097A", null));
        assertFalse(validator.isValid("", null));
    }
}
