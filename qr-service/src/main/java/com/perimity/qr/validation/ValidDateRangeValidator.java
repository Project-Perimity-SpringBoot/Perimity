package com.perimity.qr.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.BeanWrapperImpl;

public class ValidDateRangeValidator implements ConstraintValidator<ValidDateRange, Object> {

    private String fromField;
    private String toField;
    private boolean endNullable;
    private int maxDays;
    private String message;

    @Override
    public void initialize(ValidDateRange annotation) {
        this.fromField = annotation.from();
        this.toField = annotation.to();
        this.endNullable = annotation.endNullable();
        this.maxDays = annotation.maxDays();
        this.message = annotation.message();
    }

    @Override
    public boolean isValid(Object target, ConstraintValidatorContext context) {
        if (target == null) {
            return true;
        }
        BeanWrapperImpl bean = new BeanWrapperImpl(target);
        LocalDate from = (LocalDate) bean.getPropertyValue(fromField);
        LocalDate to = (LocalDate) bean.getPropertyValue(toField);

        if (from == null) {
            return true;
        }
        if (to == null) {
            return endNullable || reject(context, "An end date is required", toField);
        }
        if (to.isBefore(from)) {
            return reject(context, message, toField);
        }
        if (maxDays > 0 && ChronoUnit.DAYS.between(from, to) + 1 > maxDays) {
            return reject(context, "The date range may not exceed " + maxDays + " days", toField);
        }
        return true;
    }

    private boolean reject(ConstraintValidatorContext context, String text, String field) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(text)
                .addPropertyNode(field)
                .addConstraintViolation();
        return false;
    }
}
