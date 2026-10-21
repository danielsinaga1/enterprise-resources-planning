package id.enterpriseapps.erp.core.accounting;

import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.entity.FiscalYear;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Tahun fiskal terbuka untuk sebuah tanggal — port {@code utils/fiscalYear.ts}. */
@Service
public class ErpFiscalYearService {

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** Tahun fiskal terbuka yang memuat {@code date}; 400 bila tidak ada / sudah ditutup. */
    @ErpTx
    public FiscalYear getOpenFiscalYear(Instant date) {
        return findOpen(date).orElseThrow(() ->
                ErpException.business("Tahun fiskal tidak ditemukan atau sudah ditutup untuk tanggal ini."));
    }

    @ErpTx(readOnly = true)
    public Optional<FiscalYear> findOpen(Instant date) {
        List<FiscalYear> rows = em.createQuery(
                        "select f from FiscalYear f where f.startDate <= :d and f.endDate >= :d and f.isClosed = false "
                                + "order by f.startDate", FiscalYear.class)
                .setParameter("d", date)
                .setMaxResults(1)
                .getResultList();
        return rows.stream().findFirst();
    }

    /** Tahun fiskal (terbuka/tertutup) yang memuat tanggal. */
    @ErpTx(readOnly = true)
    public Optional<FiscalYear> findContaining(Instant date) {
        List<FiscalYear> rows = em.createQuery(
                        "select f from FiscalYear f where f.startDate <= :d and f.endDate >= :d order by f.startDate",
                        FiscalYear.class)
                .setParameter("d", date)
                .setMaxResults(1)
                .getResultList();
        return rows.stream().findFirst();
    }
}
