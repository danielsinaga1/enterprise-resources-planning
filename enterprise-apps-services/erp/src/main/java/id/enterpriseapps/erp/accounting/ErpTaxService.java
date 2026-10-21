package id.enterpriseapps.erp.accounting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.enterpriseapps.erp.core.ErpDates;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpJson;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.entity.InvoiceStatus;
import id.enterpriseapps.erp.entity.TaxConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Konfigurasi pajak & laporan PPN bulanan — port {@code routes/tax.ts}.
 */
@Service
@RequiredArgsConstructor
public class ErpTaxService {

    /** decimal.js default: 20 digit signifikan, ROUND_HALF_UP untuk setiap operasi. */
    private static final MathContext DEC = new MathContext(20, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final List<InvoiceStatus> REPORTED = List.of(
            InvoiceStatus.Submitted, InvoiceStatus.Paid, InvoiceStatus.PartiallyPaid, InvoiceStatus.Overdue);

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** GET /tax/config — aktif dulu, lalu tipe & nama. */
    @ErpTx(readOnly = true)
    public ArrayNode listConfigs() {
        ArrayNode arr = ErpJson.arrayNode();
        for (TaxConfig t : em.createQuery("select t from TaxConfig t order by t.isActive desc, t.type asc, t.name asc",
                TaxConfig.class).getResultList()) {
            arr.add(AccountingJson.pick(t, AccountingJson.TAX_CONFIG));
        }
        return arr;
    }

    /** POST /tax/config. */
    @ErpTx
    public ObjectNode createConfig(AccountingRequests.TaxConfigCreate body) {
        BigDecimal rate = rate(body.rate());
        TaxConfig t = new TaxConfig();
        t.setName(body.name());
        t.setRate(rate.setScale(2, RoundingMode.HALF_UP));
        t.setType(body.type());
        t.setAccountId(body.accountId() == null || body.accountId().isEmpty() ? null : body.accountId());
        em.persist(t);
        AccountingDb.flush(em);
        em.refresh(t);
        return AccountingJson.pick(t, AccountingJson.TAX_CONFIG);
    }

    /** PUT /tax/config/:id — hanya field yang dikirim yang diubah (data: body). */
    @ErpTx
    public ObjectNode updateConfig(String id, AccountingRequests.TaxConfigUpdate body) {
        BigDecimal rate = body.rate() == null ? null : rate(body.rate());
        JsonNode accountId = body.accountId();
        if (accountId != null && !accountId.isNull() && !accountId.isTextual()) {
            throw ErpException.business(ZodLite.invalidType("string", accountId));
        }
        TaxConfig t = em.find(TaxConfig.class, id);
        if (t == null) {
            throw ErpException.business("Konfigurasi pajak tidak ditemukan.");
        }
        if (body.name() != null) {
            t.setName(body.name());
        }
        if (rate != null) {
            t.setRate(rate.setScale(2, RoundingMode.HALF_UP));
        }
        if (body.type() != null) {
            t.setType(body.type());
        }
        if (accountId != null) {
            t.setAccountId(accountId.isNull() ? null : accountId.textValue());
        }
        if (body.isActive() != null) {
            t.setIsActive(body.isActive());
        }
        t.setUpdatedAt(Instant.now());
        AccountingDb.flush(em);
        em.refresh(t);
        return AccountingJson.pick(t, AccountingJson.TAX_CONFIG);
    }

    /** DELETE /tax/config/:id — soft delete (isActive=false). */
    @ErpTx
    public ObjectNode deactivateConfig(String id) {
        TaxConfig t = em.find(TaxConfig.class, id);
        if (t == null) {
            throw ErpException.business("Konfigurasi pajak tidak ditemukan.");
        }
        t.setIsActive(false);
        t.setUpdatedAt(Instant.now());
        return AccountingJson.message("Konfigurasi pajak dinonaktifkan.");
    }

    /** rate: z.coerce.number().min(0, ...).max(100, ...). */
    private static BigDecimal rate(JsonNode raw) {
        BigDecimal v = ZodLite.requireNumber(raw);
        if (v.signum() < 0) {
            throw ErpException.business("Tarif tidak boleh negatif.");
        }
        if (v.compareTo(HUNDRED) > 0) {
            throw ErpException.business("Tarif maksimal 100%.");
        }
        return v;
    }

    // ───────────────────────────── laporan pajak ─────────────────────────────

    private record Invoice(String id, Instant date, BigDecimal grandTotal, BigDecimal taxPct) {
    }

    private static final class Month {
        BigDecimal keluaran = BigDecimal.ZERO;
        BigDecimal masukan = BigDecimal.ZERO;
        BigDecimal pph = BigDecimal.ZERO;
    }

    /**
     * GET /tax/report — PPN Keluaran (faktur penjualan) & Masukan (faktur pembelian) per bulan.
     * Pajak per item bila ada item ber-taxPct &gt; 0; selain itu dari grandTotal × pct / (100 + pct).
     */
    @ErpTx(readOnly = true)
    public ObjectNode report(String startDate, String endDate) {
        if (startDate == null || startDate.isEmpty() || endDate == null || endDate.isEmpty()) {
            throw ErpException.business("Parameter startDate dan endDate wajib diisi.");
        }
        Instant start = parseDate(startDate);
        // end.setHours(23, 59, 59, 999) pada server ber-TZ UTC.
        Instant end = parseDate(endDate).atZone(ZoneOffset.UTC).toLocalDate().plusDays(1)
                .atStartOfDay(ZoneOffset.UTC).toInstant().minusMillis(1);

        TreeMap<String, Month> months = new TreeMap<>();
        accumulate(months, "SalesInvoice", "SalesInvoiceItem", "salesInvoiceId", start, end, true);
        accumulate(months, "PurchaseInvoice", "PurchaseInvoiceItem", "purchaseInvoiceId", start, end, false);

        ArrayNode rows = ErpJson.arrayNode();
        List<BigDecimal[]> rounded = new ArrayList<>();
        for (Map.Entry<String, Month> e : months.entrySet()) {
            Month m = e.getValue();
            BigDecimal k = round2(m.keluaran);
            BigDecimal ms = round2(m.masukan);
            BigDecimal p = round2(m.pph);
            ObjectNode o = rows.addObject();
            o.put("month", e.getKey());
            o.set("ppnKeluaran", JsNumbers.node(k));
            o.set("ppnMasukan", JsNumbers.node(ms));
            o.set("pph", JsNumbers.node(p));
            o.set("net", JsNumbers.node(round2(m.keluaran.subtract(m.masukan, DEC))));
            rounded.add(new BigDecimal[]{k, ms, p});
        }
        BigDecimal tk = BigDecimal.ZERO;
        BigDecimal tm = BigDecimal.ZERO;
        BigDecimal tp = BigDecimal.ZERO;
        for (BigDecimal[] r : rounded) {
            tk = tk.add(r[0], DEC);
            tm = tm.add(r[1], DEC);
            tp = tp.add(r[2], DEC);
        }
        ObjectNode totals = ErpJson.object();
        totals.set("ppnKeluaran", JsNumbers.node(round2(tk)));
        totals.set("ppnMasukan", JsNumbers.node(round2(tm)));
        totals.set("pph", JsNumbers.node(round2(tp)));
        totals.set("net", JsNumbers.node(round2(tk.subtract(tm, DEC))));
        ObjectNode out = ErpJson.object();
        out.set("months", rows);
        out.set("totals", totals);
        return out;
    }

    private void accumulate(Map<String, Month> months, String invoiceEntity, String itemEntity, String fk,
                            Instant start, Instant end, boolean sales) {
        List<Invoice> invoices = new ArrayList<>();
        for (Object[] r : em.createQuery("select i.id, i.date, i.grandTotal, i.taxPct from " + invoiceEntity
                        + " i where i.date >= :start and i.date <= :end and i.status in :st", Object[].class)
                .setParameter("start", start).setParameter("end", end).setParameter("st", REPORTED)
                .getResultList()) {
            invoices.add(new Invoice((String) r[0], (Instant) r[1], (BigDecimal) r[2], (BigDecimal) r[3]));
        }
        if (invoices.isEmpty()) {
            return;
        }
        Map<String, List<BigDecimal[]>> items = new HashMap<>();
        for (Object[] r : em.createQuery("select it." + fk + ", it.amount, it.taxPct from " + itemEntity
                        + " it where it." + fk + " in :ids", Object[].class)
                .setParameter("ids", invoices.stream().map(Invoice::id).toList()).getResultList()) {
            items.computeIfAbsent((String) r[0], k -> new ArrayList<>())
                    .add(new BigDecimal[]{nz((BigDecimal) r[1]), nz((BigDecimal) r[2])});
        }
        for (Invoice inv : invoices) {
            List<BigDecimal[]> its = items.getOrDefault(inv.id(), List.of());
            boolean perItem = its.stream().anyMatch(i -> i[1].signum() > 0);
            BigDecimal tax;
            if (perItem) {
                tax = BigDecimal.ZERO;
                for (BigDecimal[] i : its) {
                    tax = tax.add(i[0].multiply(i[1], DEC).divide(HUNDRED, DEC), DEC);
                }
            } else {
                BigDecimal pct = nz(inv.taxPct());
                if (pct.signum() <= 0) {
                    continue;
                }
                tax = nz(inv.grandTotal()).multiply(pct, DEC).divide(HUNDRED.add(pct, DEC), DEC);
            }
            if (tax.signum() <= 0) {
                continue;
            }
            ZonedDateTime d = inv.date().atZone(ZoneOffset.UTC);
            String key = String.format("%d-%02d", d.getYear(), d.getMonthValue());
            Month m = months.computeIfAbsent(key, k -> new Month());
            if (sales) {
                m.keluaran = m.keluaran.add(tax, DEC);
            } else {
                m.masukan = m.masukan.add(tax, DEC);
            }
        }
    }

    private static Instant parseDate(String s) {
        Instant v = ErpDates.tryInstant(s);
        if (v == null) {
            throw ErpException.business("Tanggal tidak valid.");
        }
        return v;
    }

    private static BigDecimal round2(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
