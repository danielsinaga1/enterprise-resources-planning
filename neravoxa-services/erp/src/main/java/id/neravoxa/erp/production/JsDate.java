package id.neravoxa.erp.production;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Padanan {@code z.coerce.date().nullable().optional()}: null lolos; selain itu harus bisa
 * diubah {@code new Date(x)} (lihat {@link ProdDates#parseJsDate(String)}).
 */
@Documented
@Constraint(validatedBy = JsDate.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface JsDate {

    /** Pesan Zod 4 untuk tanggal tidak valid. */
    String MESSAGE = "Invalid input: expected date, received Date";

    String message() default MESSAGE;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Validator: null = valid (nullable), selain itu harus terparse. */
    class Validator implements ConstraintValidator<JsDate, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || ProdDates.parseJsDate(value) != null;
        }
    }
}
