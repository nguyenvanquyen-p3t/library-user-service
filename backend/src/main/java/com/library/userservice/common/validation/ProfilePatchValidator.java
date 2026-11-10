package com.library.userservice.common.validation;

import com.library.userservice.user.dto.ProfilePatchRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ProfilePatchValidator implements ConstraintValidator<ValidProfilePatch, ProfilePatchRequest> {
    @Override public boolean isValid(ProfilePatchRequest value, ConstraintValidatorContext context) {
        if (value == null) return true;
        context.disableDefaultConstraintViolation();
        if (!value.hasFullName() && !value.hasPhoneNumber()) {
            error(context, "$", ValidationCode.EMPTY_PATCH);
            return false;
        }
        var name = value.hasFullName() ? InputRules.check(value.getFullName(), Input.Kind.FULL_NAME, true) : null;
        var phone = value.hasPhoneNumber() ? InputRules.check(value.getPhoneNumber(), Input.Kind.PHONE, false) : null;
        if (name != null) error(context, "fullName", name);
        if (phone != null) error(context, "phoneNumber", phone);
        return name == null && phone == null;
    }

    private void error(ConstraintValidatorContext context, String field, ValidationCode code) {
        context.buildConstraintViolationWithTemplate(code.name()).addPropertyNode(field).addConstraintViolation();
    }
}

