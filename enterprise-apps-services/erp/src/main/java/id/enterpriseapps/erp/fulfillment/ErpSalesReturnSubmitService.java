package id.enterpriseapps.erp.fulfillment;

import id.enterpriseapps.erp.core.ErpDates;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.Money;
import id.enterpriseapps.erp.core.accounting.ErpAccountBalanceService;
import id.enterpriseapps.erp.core.accounting.ErpDocumentNumberService;
import id.enterpriseapps.erp.core.accounting.ErpFiscalYearService;
import id.enterpriseapps.erp.core.accounting.ErpGlPostingService;
import id.enterpriseapps.erp.core.accounting.ErpPartyBalanceService;
import id.enterpriseapps.erp.core.accounting.ErpSystemAccountService;
import id.enterpriseapps.erp.core.accounting.SystemAccountRole;
import id.enterpriseapps.erp.core.inventory.ErpWarehouseStockService;
import id.enterpriseapps.erp.entity.Account;
import id.enterpriseapps.erp.entity.DeliveryOrderItem;
import id.enterpriseapps.erp.entity.DocumentStatus;
import id.enterpriseapps.erp.entity.FiscalYear;
import id.enterpriseapps.erp.entity.InventoryItem;
import id.enterpriseapps.erp.entity.InvoiceStatus;
import id.enterpriseapps.erp.entity.Party;
import id.enterpriseapps.erp.entity.Payment;
import id.enterpriseapps.erp.entity.PaymentType;
import id.enterpriseapps.erp.entity.SalesInvoice;
import id.enterpriseapps.erp.entity.SalesInvoiceItem;
import id.enterpriseapps.erp.entity.SalesReturn;
import id.enterpriseapps.erp.entity.SalesReturnItem;
import id.enterpriseapps.erp.entity.StockMovement;
import id.enterpriseapps.erp.entity.StockMovementType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Submit retur penjualan (nota kredit) atas satu invoice — port {@code services/salesReturnSubmit.ts}.
 *
 * <p>Memposting, di dalam transaksi pemanggil:
 * <ul>
 *   <li>jurnal utama {@code JV-RN-...}: DR 4.3 Retur Penjualan (subtotal) + DR PPN Keluaran (pajak) /
 *       CR Piutang (porsi yang mengurangi outstanding) + CR Uang Muka Pelanggan (sisa → kredit pelanggan)</li>
 *   <li>baris restock: StockMovement In (referenceType 'SalesReturn'), stok bertambah, dan jurnal balik HPP
 *       {@code JV-COGS-RN-...}: DR Persediaan / CR HPP pada biaya asal</li>
 *   <li>Payment CustomerDeposit untuk porsi kredit (tanpa jurnal sendiri — liabilitas sudah di-CR jurnal utama)</li>
 * </ul>
 * Prioritas sumber biaya per baris: unitCost gerakan DO → unitCostLoaded baris invoice → averageCost item.
 * Harga, biaya, dan akun diturunkan dari baris invoice asal (tidak dipercaya dari klien).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpSalesReturnSubmitService {

    public static final String REFERENCE_TYPE = "SalesReturn";

    private final ErpFiscalYearService fiscalYears;
    private final ErpDocumentNumberService numbers;
    private final ErpSystemAccountService systemAccounts;
    private final ErpGlPostingService gl;
    private final ErpAccountBalanceService balances;
    private final ErpPartyBalanceService partyBalances;
    private final ErpWarehouseStockService stock;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** Nilai neto satu baris retur: qty × rate invoice dikurangi diskon baris (sebelum pajak), 2 desimal. */
    static BigDecimal returnLineAmount(BigDecimal quantity, BigDecimal rate, BigDecimal discountPct) {
        BigDecimal factor = BigDecimal.ONE.subtract(Money.nz(discountPct).divide(BigDecimal.valueOf(100), 20, RoundingMode.HALF_UP));
        return quantity.multiply(Money.nz(rate)).multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    private record Valued(BigDecimal amount, BigDecimal taxPct) {
    }

    /** Pajak baris (dibulatkan 2 desimal atas totalnya). */
    private static BigDecimal taxOf(List<Valued> lines) {
        BigDecimal tax = BigDecimal.ZERO;
        for (Valued v : lines) {
            tax = tax.add(v.amount().multiply(Money.nz(v.taxPct())).divide(BigDecimal.valueOf(100), 20, RoundingMode.HALF_UP));
        }
        return tax.setScale(2, RoundingMode.HALF_UP);
    }

    /** Nilai retur termasuk pajak baris. */
    private static BigDecimal returnTotal(List<Valued> lines) {
        BigDecimal subtotal = lines.stream().map(Valued::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return subtotal.add(taxOf(lines)).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Nilai retur bila diposting, dihitung dari baris invoice seperti submit — ditampilkan untuk retur yang
     * menunggu approval; baris tak dikenal dihitung nol. Nilai absurd tidak boleh memblokir antrian.
     */
    @ErpTx(readOnly = true)
    public BigDecimal estimateAmount(FulfillmentRequests.ReturnInput body) {
        List<String> ids = body.items().stream().map(FulfillmentRequests.ReturnItem::salesInvoiceItemId).toList();
        Map<String, Object[]> src = new HashMap<>();
        if (!ids.isEmpty()) {
            em.createQuery("select i.id, i.rate, i.discount, i.taxPct from SalesInvoiceItem i "
                            + "where i.id in :ids and i.salesInvoiceId = :inv", Object[].class)
                    .setParameter("ids", ids)
                    .setParameter("inv", body.salesInvoiceId())
                    .getResultList().forEach(r -> src.put((String) r[0], r));
        }
        List<Valued> lines = new ArrayList<>();
        for (FulfillmentRequests.ReturnItem l : body.items()) {
            Object[] s = src.get(l.salesInvoiceItemId());
            if (s != null) {
                lines.add(new Valued(returnLineAmount(l.quantity(), (BigDecimal) s[1], (BigDecimal) s[2]), (BigDecimal) s[3]));
            }
        }
        BigDecimal total = returnTotal(lines);
        return total.signum() >= 0 && total.compareTo(new BigDecimal("1e15")) < 0 ? total : BigDecimal.ZERO;
    }

    private record Resolved(String salesInvoiceItemId, String inventoryItemId, String warehouseId, String accountId,
                            String itemName, BigDecimal quantity, String unit, BigDecimal rate, BigDecimal discount,
                            BigDecimal amount, BigDecimal taxPct, boolean restock, BigDecimal unitCostLoaded,
                            String invAccountId) {
    }

    @ErpTx(timeout = 30)
    public SalesReturn submitInTx(FulfillmentRequests.ReturnInput body, String userId) {
        Instant date = ErpDates.tryInstant(body.date());
        if (date == null) {
            throw ErpException.business("Format tanggal tidak valid.");
        }
        FiscalYear fy = fiscalYears.getOpenFiscalYear(date);

        SalesInvoice invoice = em.find(SalesInvoice.class, body.salesInvoiceId());
        if (invoice == null) {
            throw ErpException.business("Invoice penjualan tidak ditemukan.");
        }
        if (invoice.getStatus() == InvoiceStatus.Cancelled) {
            throw ErpException.business("Invoice sudah dibatalkan, tidak bisa diretur.");
        }
        if (invoice.getStatus() == InvoiceStatus.Draft) {
            throw ErpException.business("Invoice masih draft, belum bisa diretur.");
        }
        Party customer = em.find(Party.class, invoice.getPartyId());
        List<SalesInvoiceItem> invoiceItems = em.createQuery(
                        "select i from SalesInvoiceItem i left join fetch i.inventoryItem left join fetch i.deliveryOrderItem d "
                                + "left join fetch d.stockMovement where i.salesInvoiceId = :inv", SalesInvoiceItem.class)
                .setParameter("inv", invoice.getId()).getResultList();

        // Qty yang sudah diretur per baris invoice (retur non-batal) — guard.
        Map<String, BigDecimal> returned = new HashMap<>();
        if (!invoiceItems.isEmpty()) {
            em.createQuery("select r.salesInvoiceItemId, sum(r.quantity) from SalesReturnItem r join r.salesReturn s "
                            + "where r.salesInvoiceItemId in :ids and s.status <> 'Cancelled' group by r.salesInvoiceItemId",
                            Object[].class)
                    .setParameter("ids", invoiceItems.stream().map(SalesInvoiceItem::getId).toList())
                    .getResultList().forEach(r -> returned.put((String) r[0], Money.nz((BigDecimal) r[1])));
        }

        // Akun sistem / lawan.
        ErpSystemAccountService.MappedAccount arAccount = systemAccounts.getAccount(SystemAccountRole.AR);
        ErpSystemAccountService.MappedAccount depositAccount = systemAccounts.getAccount(SystemAccountRole.CUSTOMER_DEPOSIT);
        ErpSystemAccountService.MappedAccount inventoryAccount = systemAccounts.getAccount(SystemAccountRole.INVENTORY);
        Account hppBeras = systemAccounts.findByNumber("5.1");
        String cogsAccountId = hppBeras != null ? hppBeras.getId() : systemAccounts.getAccount(SystemAccountRole.COGS).id();
        Account returAccount = systemAccounts.findByNumber("4.3");
        if (returAccount == null) {
            throw ErpException.business("Akun \"4.3 Retur Penjualan\" tidak ditemukan di bagan akun.");
        }

        List<Resolved> resolved = new ArrayList<>();
        for (FulfillmentRequests.ReturnItem line : body.items()) {
            SalesInvoiceItem src = invoiceItems.stream().filter(i -> i.getId().equals(line.salesInvoiceItemId()))
                    .findFirst().orElse(null);
            if (src == null) {
                throw ErpException.business("Baris invoice tidak ditemukan (id=" + line.salesInvoiceItemId() + ").");
            }
            BigDecimal remaining = Money.nz(src.getQuantity()).subtract(returned.getOrDefault(src.getId(), BigDecimal.ZERO));
            if (line.quantity().compareTo(remaining.add(new BigDecimal("1e-9"))) > 0) {
                throw ErpException.business("Qty retur \"" + src.getItemName() + "\" (" + FulfillmentJson.js(line.quantity())
                        + ") melebihi sisa yang bisa diretur (" + FulfillmentJson.js(remaining) + ").");
            }
            BigDecimal discount = Money.nz(src.getDiscount());
            BigDecimal rate = Money.nz(src.getRate());
            BigDecimal amount = returnLineAmount(line.quantity(), rate, discount);
            boolean restock = line.restock() && src.getInventoryItemId() != null;

            // Prioritas sumber biaya untuk balik HPP.
            BigDecimal unitCost = null;
            DeliveryOrderItem doItem = src.getDeliveryOrderItemId() == null ? null : src.getDeliveryOrderItem();
            StockMovement doMovement = doItem == null || doItem.getStockMovementId() == null ? null : doItem.getStockMovement();
            InventoryItem inv = src.getInventoryItemId() == null ? null : src.getInventoryItem();
            if (doMovement != null) {
                unitCost = Money.nz(doMovement.getUnitCost());
            } else if (src.getUnitCostLoaded() != null) {
                unitCost = src.getUnitCostLoaded();
            } else if (inv != null) {
                unitCost = Money.nz(inv.getAverageCost()).max(BigDecimal.ZERO);
            }
            String warehouseId = Zod.truthy(line.warehouseId()) != null ? line.warehouseId() : src.getWarehouseId();
            String invAccountId = inv != null && inv.getAccountId() != null ? inv.getAccountId() : inventoryAccount.id();
            resolved.add(new Resolved(src.getId(), src.getInventoryItemId(), warehouseId, src.getAccountId(),
                    src.getItemName(), line.quantity(), src.getUnit(), rate, discount, amount,
                    Money.nz(src.getTaxPct()), restock, unitCost, invAccountId));
        }

        // Total
        List<Valued> valued = resolved.stream().map(r -> new Valued(r.amount(), r.taxPct())).toList();
        BigDecimal subtotal = resolved.stream().map(Resolved::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal taxAmount = taxOf(valued);
        BigDecimal totalReturn = returnTotal(valued);
        // Retur qty-only (rate 0, mis. barang titipan pelanggan) valid selama minimal 1 baris qty>0 —
        // hanya mengembalikan stok fisik, tanpa nilai piutang/kredit.
        boolean hasReturnedQty = resolved.stream().anyMatch(r -> r.quantity().signum() > 0);
        if (totalReturn.signum() < 0) {
            throw ErpException.business("Nilai retur tidak boleh negatif.");
        }
        if (totalReturn.signum() == 0 && !hasReturnedQty) {
            throw ErpException.business("Pilih minimal satu baris dengan qty > 0.");
        }

        // Pembagian AR vs kredit pelanggan.
        BigDecimal outstanding = Money.nz(invoice.getOutstanding());
        BigDecimal arReduce = Money.round2(totalReturn.min(outstanding));
        BigDecimal creditPortion = Money.round2(totalReturn.subtract(arReduce));

        String returnNumber = numbers.generate("RN", date, fy.getId());
        Instant now = Instant.now();
        SalesReturn ret = new SalesReturn();
        ret.setReturnNumber(returnNumber);
        ret.setDate(date);
        ret.setPartyId(invoice.getPartyId());
        ret.setSalesInvoiceId(invoice.getId());
        ret.setStatus("Submitted");
        ret.setSubtotal(Money.round2(subtotal));
        ret.setTaxAmount(taxAmount);
        ret.setTotalReturn(totalReturn);
        ret.setArReducedAmount(arReduce);
        ret.setCreditCreatedAmount(creditPortion);
        ret.setReason(Zod.truthy(body.reason()));
        ret.setNotes(Zod.truthy(body.notes()));
        ret.setFiscalYearId(fy.getId());
        ret.setCreatedBy(userId);
        ret.setSubmittedAt(now);
        em.persist(ret);
        em.flush();
        Map<String, SalesReturnItem> returnItemByInvLine = new LinkedHashMap<>();
        for (Resolved r : resolved) {
            SalesReturnItem ri = new SalesReturnItem();
            ri.setSalesReturnId(ret.getId());
            ri.setSalesInvoiceItemId(r.salesInvoiceItemId());
            ri.setInventoryItemId(r.inventoryItemId());
            ri.setWarehouseId(r.warehouseId());
            ri.setAccountId(r.accountId());
            ri.setItemName(r.itemName());
            ri.setQuantity(r.quantity());
            ri.setUnit(r.unit());
            ri.setRate(r.rate());
            ri.setDiscount(r.discount());
            ri.setAmount(r.amount());
            ri.setTaxPct(r.taxPct());
            ri.setRestock(r.restock());
            ri.setUnitCostLoaded(r.unitCostLoaded());
            em.persist(ri);
            em.flush();
            returnItemByInvLine.put(r.salesInvoiceItemId(), ri);
        }

        // Jurnal utama pembalik penjualan (JV-RN-...).
        String customerName = customer != null ? customer.getName() : "";
        var je = gl.journal()
                .number("JV-" + returnNumber)
                .date(date)
                .narration("Retur Penjualan: " + returnNumber + " - " + customerName)
                .fiscalYear(fy.getId())
                .createdBy(userId)
                .submittedAt(now)
                .line(returAccount.getId(), Money.round2(subtotal), BigDecimal.ZERO, "Retur Penjualan: " + returnNumber);
        if (taxAmount.signum() > 0) {
            String taxOutput = systemAccounts.getAccount(SystemAccountRole.TAX_OUTPUT).id();
            je.line(taxOutput, taxAmount, BigDecimal.ZERO, "PPN Keluaran (retur): " + returnNumber);
        }
        if (arReduce.signum() > 0) {
            je.line(arAccount.id(), BigDecimal.ZERO, arReduce, "Pengurang Piutang (retur): " + returnNumber, invoice.getPartyId());
        }
        if (creditPortion.signum() > 0) {
            je.line(depositAccount.id(), BigDecimal.ZERO, creditPortion, "Kredit pelanggan dari retur: " + returnNumber,
                    invoice.getPartyId());
        }
        // Retur qty-only bernilai nol tidak punya baris bernilai: tidak ada dampak GL, jurnal tidak dibuat.
        if (totalReturn.signum() != 0 || subtotal.signum() != 0) {
            je.post();
        }

        // Kurangi outstanding invoice sebesar porsi AR (increment atomik).
        if (arReduce.signum() > 0) {
            em.createQuery("update SalesInvoice i set i.outstanding = i.outstanding - :v, i.updatedAt = :now where i.id = :id")
                    .setParameter("v", arReduce)
                    .setParameter("now", Instant.now())
                    .setParameter("id", invoice.getId())
                    .executeUpdate();
        }
        balances.recalcPartyOutstanding(invoice.getPartyId());

        // Porsi kredit → Payment CustomerDeposit (tanpa jurnal sendiri; liabilitas sudah di-CR).
        if (creditPortion.signum() > 0) {
            Payment deposit = new Payment();
            deposit.setPaymentNumber(numbers.generate("PAY", date, fy.getId()));
            deposit.setDate(date);
            deposit.setPaymentType(PaymentType.CustomerDeposit);
            deposit.setPartyId(invoice.getPartyId());
            deposit.setAccountId(depositAccount.id());
            deposit.setAmount(creditPortion);
            deposit.setStatus(DocumentStatus.Submitted);
            deposit.setReferenceNo(returnNumber);
            deposit.setNotes("Kredit dari Retur Penjualan " + returnNumber + " (invoice " + invoice.getInvoiceNumber() + ")");
            deposit.setFiscalYearId(fy.getId());
            deposit.setCreatedBy(userId);
            deposit.setSubmittedAt(Instant.now());
            em.persist(deposit);
            ret.setCustomerDepositId(deposit.getId());
            em.flush();
            // Saldo uang muka mitra di-cache agar UI invoice bisa menawarkan 'Gunakan Uang Muka'.
            partyBalances.incrementParty(invoice.getPartyId(), "customer_deposit_balance", creditPortion);
        }

        // Stok masuk + balik HPP untuk baris restock.
        BigDecimal totalCogsReversal = BigDecimal.ZERO;
        Map<String, BigDecimal> cogsDebitByAccount = new LinkedHashMap<>();
        Map<String, List<String>> namesByAccount = new LinkedHashMap<>();
        for (Resolved r : resolved) {
            if (!r.restock() || r.inventoryItemId() == null) {
                continue;
            }
            BigDecimal cost = Money.nz(r.unitCostLoaded());
            BigDecimal smTotal = Money.round2(r.quantity().multiply(cost));
            StockMovement sm = new StockMovement();
            sm.setMovementNumber(numbers.generate("SM", date, fy.getId()));
            sm.setDate(date);
            sm.setItemId(r.inventoryItemId());
            sm.setWarehouseId(r.warehouseId());
            sm.setMovementType(StockMovementType.In);
            sm.setQuantity(r.quantity());
            sm.setUnitCost(cost);
            sm.setTotalValue(smTotal);
            sm.setReferenceType(REFERENCE_TYPE);
            sm.setReferenceId(ret.getId());
            sm.setReferenceNumber(returnNumber);
            // Jurnal balik HPP meng-kredit HPP untuk setiap baris restock bernilai.
            sm.setOffsetAccountId(smTotal.signum() > 0 ? cogsAccountId : null);
            sm.setFiscalYearId(fy.getId());
            sm.setCreatedById(userId);
            em.persist(sm);
            SalesReturnItem ri = returnItemByInvLine.get(r.salesInvoiceItemId());
            if (ri != null) {
                ri.setStockMovementId(sm.getId());
            }
            em.flush();
            stock.adjustItemStock(r.inventoryItemId(), r.quantity());
            stock.adjustWarehouseStock(r.inventoryItemId(), r.warehouseId(), r.quantity());

            if (smTotal.signum() > 0) {
                totalCogsReversal = totalCogsReversal.add(smTotal);
                cogsDebitByAccount.merge(r.invAccountId(), smTotal, BigDecimal::add);
                namesByAccount.computeIfAbsent(r.invAccountId(), k -> new ArrayList<>()).add(r.itemName());
            }
        }

        // Jurnal balik HPP: DR Persediaan / CR HPP.
        BigDecimal totalCogs = Money.round2(totalCogsReversal);
        if (totalCogs.signum() > 0) {
            var cogsJe = gl.journal()
                    .number("JV-COGS-" + returnNumber)
                    .date(date)
                    .narration("Balik HPP Retur Penjualan: " + returnNumber + " - " + customerName)
                    .fiscalYear(fy.getId())
                    .createdBy(userId)
                    .submittedAt(Instant.now());
            cogsDebitByAccount.forEach((acct, amt) -> cogsJe.line(acct, amt, BigDecimal.ZERO,
                    "Persediaan masuk (retur) " + String.join(", ", namesByAccount.get(acct)) + ": " + returnNumber));
            cogsJe.line(cogsAccountId, BigDecimal.ZERO, totalCogs, "Balik HPP (retur): " + returnNumber);
            cogsJe.post();
        }
        em.flush();

        log.info("Sales return submitted returnId={} returnNumber={} invoice={} totalReturn={} arReduce={} credit={} cogsReversal={}",
                ret.getId(), returnNumber, invoice.getInvoiceNumber(), totalReturn, arReduce, creditPortion, totalCogs);
        return ret;
    }
}
