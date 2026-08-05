package com.perimity.gatepass.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level, because the rule spans two fields: idNumber is only meaningful
 * against the idType it belongs to. A @Pattern on the field cannot see the
 * other one.
 *
 * Also enforces that the two travel together - a type with no number cannot be
 * checked at the gate, and a number with no type cannot be checked at all.
 * That pairing used to live only in the browser, so the API accepted either
 * alone.
 */
@Documented
@Constraint(validatedBy = ValidIdDocumentValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidIdDocument {
    String message() default "That ID number does not match the ID type";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
