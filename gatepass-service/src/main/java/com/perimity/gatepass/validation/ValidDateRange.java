package com.perimity.gatepass.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Cross-field date check. A single field annotation cannot express
 * "to must not be before from", because it can only see one field at a time.
 * That is why this one sits on the class.
 *
 * Usage:
 *   @ValidDateRange(from = "visitFrom", to = "visitTo")
 *   @ValidDateRange(from = "validFrom", to = "validTo", endNullable = true)
 */
@Documented
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidDateRangeValidator.class)
public @interface ValidDateRange {

    String message() default "The end date must not be before the start date";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Property name of the start date. */
    String from();

    /** Property name of the end date. */
    String to();

    /**
     * true when a null end date is legal - a standing DAILY pass has no end date.
     * false when both dates are required, as on an event or a visit window.
     */
    boolean endNullable() default false;

    /** Longest permitted window in days. 0 means no limit. */
    int maxDays() default 0;
}
