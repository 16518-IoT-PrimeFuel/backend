package com.primefuel.fulltank.platform.shared.interfaces.rest.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** RUC de 11 dígitos con dígito verificador válido (módulo 11, SUNAT). Nulo se considera válido. */
@Documented
@Constraint(validatedBy = RucValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidRuc {
    String message() default "RUC inválido: dígito verificador incorrecto";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
