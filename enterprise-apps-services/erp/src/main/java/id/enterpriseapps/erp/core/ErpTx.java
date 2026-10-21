package id.enterpriseapps.erp.core;

import org.springframework.core.annotation.AliasFor;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Transaksi db_erp.
 *
 * <p>WAJIB dipakai (bukan {@code @Transactional} polos) di semua service ERP. Tanpa
 * {@code transactionManager} eksplisit Spring memakai manager {@code @Primary} (db_irms),
 * sehingga tulisan ke db_erp tidak ikut transaksi dan rollback tidak terjadi — kesalahan
 * yang baru terlihat ketika ada error di tengah posting jurnal. Setara
 * {@code prisma.$transaction(...)} di sumber ENTARIS; rollback juga untuk checked exception.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Transactional(transactionManager = ErpTx.MANAGER, rollbackFor = Exception.class)
public @interface ErpTx {

    String MANAGER = "erpTransactionManager";

    @AliasFor(annotation = Transactional.class, attribute = "readOnly")
    boolean readOnly() default false;

    @AliasFor(annotation = Transactional.class, attribute = "propagation")
    Propagation propagation() default Propagation.REQUIRED;

    @AliasFor(annotation = Transactional.class, attribute = "isolation")
    Isolation isolation() default Isolation.DEFAULT;

    @AliasFor(annotation = Transactional.class, attribute = "timeout")
    int timeout() default -1;
}
