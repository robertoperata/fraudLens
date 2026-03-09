package com.fraudlens.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

public class IpAddressValidator implements ConstraintValidator<ValidIpAddress, String> {

    // Strict IPv4: each octet is 0-255
    private static final Pattern IPV4 = Pattern.compile(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) return true; // @NotBlank handles null/blank

        // IPv4 — fast path via regex
        if (IPV4.matcher(value).matches()) return true;

        // IPv6 — colon is mandatory in any valid IPv6 literal; InetAddress.getByName()
        // resolves IP literals without a DNS lookup, so this is safe for syntactic validation.
        if (value.contains(":")) {
            try {
                InetAddress.getByName(value);
                return true;
            } catch (UnknownHostException e) {
                return false;
            }
        }

        return false;
    }
}
