package id.enterpriseapps.validation;

import id.enterpriseapps.utils.ConvertUtil;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

public class DatePastValidator implements ConstraintValidator<DatePastConstraint , String> {

    private ConvertUtil convertUtil;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {

        try {
            LocalDateTime date = convertUtil.stringToDateLocal(value);

            return date.isBefore(LocalDateTime.now());

        } catch (DateTimeParseException e) {
            return false; // format salah dianggap invalid
        }
    }
}

