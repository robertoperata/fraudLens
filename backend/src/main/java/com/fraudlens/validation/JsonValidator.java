package com.fraudlens.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class JsonValidator implements ConstraintValidator<ValidJson, String> {

    // ObjectMapper is thread-safe after configuration — safe to share
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) return true; // field is optional
        try {
            MAPPER.readTree(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
