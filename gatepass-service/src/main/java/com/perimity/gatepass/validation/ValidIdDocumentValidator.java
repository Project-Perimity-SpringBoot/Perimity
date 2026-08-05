package com.perimity.gatepass.validation;

import com.perimity.gatepass.dto.request.VisitorRequestCreateDto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Reports against the field the visitor would change, not the object root -
 * an error with no field attaches to the form instead of the input, and the
 * person is left hunting for what to fix.
 */
public class ValidIdDocumentValidator
        implements ConstraintValidator<ValidIdDocument, VisitorRequestCreateDto> {

    @Override
    public boolean isValid(VisitorRequestCreateDto dto, ConstraintValidatorContext context) {
        if (dto == null) {
            return true;
        }

        boolean hasType = dto.getIdType() != null;
        boolean hasNumber = dto.getIdNumber() != null && !dto.getIdNumber().isBlank();

        // Both absent is fine - the whole document is optional.
        if (!hasType && !hasNumber) {
            return true;
        }

        if (hasType && !hasNumber) {
            return fail(context, "idNumber", "Enter the number for the ID you chose");
        }
        if (!hasType && hasNumber) {
            return fail(context, "idType", "Choose which ID this number belongs to");
        }
        if (!IdDocumentValidator.isValid(dto.getIdType(), dto.getIdNumber())) {
            return fail(context, "idNumber", IdDocumentValidator.messageFor(dto.getIdType()));
        }
        return true;
    }

    private boolean fail(ConstraintValidatorContext context, String field, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(field)
                .addConstraintViolation();
        return false;
    }
}
