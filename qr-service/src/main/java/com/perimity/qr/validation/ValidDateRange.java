package com.perimity.qr.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Cross-field date check. A single-field annotation can never express
 * "to must not be before from" because it only ever sees one field.
 *
 * endNullable = true because a QR record mirroring a standing DAILY pass has
 * no end date.
 */
@Documented
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidDateRangeValidator.class)
public @interface ValidDateRange {

    String message() default "The end date must not be before the start date";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    String from();

    String to();

    boolean endNullable() default false;

    int maxDays() default 0;
}
