package id.neravoxa.erp.deposit;

import id.neravoxa.erp.core.ErpException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.lang.reflect.RecordComponent;
import java.util.Set;

/**
 * Validasi body uang muka dengan semantik {@code validateBody} ENTARIS: anotasi Jakarta Validation
 * pada record, diperiksa BERURUTAN mengikuti komponen record (= urutan field schema Zod) sehingga
 * pesan 400 selalu issue pertama. {@code @Valid} di controller tidak dipakai karena urutan violation
 * Hibernate Validator tidak terjamin. Refinement objek ({@code .refine}) dijalankan setelah semua field lolos.
 */
@Component
public class ErpDepositRequestValidator {

    private final Validator validator;

    public ErpDepositRequestValidator(ObjectProvider<Validator> validators) {
        // Validator Spring bila tunggal; selain itu validator default Hibernate Validator.
        this.validator = validators.getIfUnique(() -> Validation.buildDefaultValidatorFactory().getValidator());
    }

    /** 400 dengan pesan issue pertama bila body tidak valid. */
    public void check(Record body) {
        if (body == null) {
            // z.object(...).safeParse(undefined)
            throw ErpException.business("Invalid input: expected object, received undefined");
        }
        for (RecordComponent c : body.getClass().getRecordComponents()) {
            Set<ConstraintViolation<Record>> violations = validator.validateProperty(body, c.getName());
            if (!violations.isEmpty()) {
                throw ErpException.business(violations.iterator().next().getMessage());
            }
        }
    }

    /**
     * Refund/offset: field dulu, lalu dua refinement schema sesuai urutannya —
     * minimal salah satu jumlah &gt; 0, dan akun kas wajib bila ada refund kas.
     */
    public void checkRefund(DepositRequests.Refund body, DepositKind kind) {
        check(body);
        if (body.offset().add(body.cash()).signum() <= 0) {
            throw ErpException.business(kind == DepositKind.CUSTOMER
                    ? "Masukkan jumlah offset piutang atau refund kas minimal salah satu."
                    : "Masukkan jumlah offset hutang atau refund kas minimal salah satu.");
        }
        if (body.cash().signum() != 0 && (body.cashAccountId() == null || body.cashAccountId().isEmpty())) {
            throw ErpException.business("Pilih akun kas/bank untuk refund kas.");
        }
    }
}
