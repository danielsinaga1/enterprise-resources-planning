package id.neravoxa.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = DatePastValidator.class)
@Target({ ElementType.METHOD, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface DatePastConstraint {

    String message() default "Tanggal harus di masa lalu";
    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
    String pattern() default "yyyy-MM-dd"; // default format

}

