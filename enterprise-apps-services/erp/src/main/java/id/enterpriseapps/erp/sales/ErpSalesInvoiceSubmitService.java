package id.enterpriseapps.erp.sales;

import com.fasterxml.jackson.databind.node.ObjectNode;
import id.enterpriseapps.erp.core.ErpDates;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.Money;
import id.enterpriseapps.erp.core.accounting.ErpAccountBalanceService;
import id.enterpriseapps.erp.core.accounting.ErpDocumentNumberService;
import id.enterpriseapps.erp.core.accounting.ErpFiscalYearService;
import id.enterpriseapps.erp.core.accounting.ErpGlPostingService;
import id.enterpriseapps.erp.core.accounting.ErpSystemAccountService;
import id.enterpriseapps.erp.core.accounting.SystemAccountRole;
import id.enterpriseapps.erp.entity.Account;
import id.enterpriseapps.erp.entity.AccountingLedgerEntry;
import id.enterpriseapps.erp.entity.DeliveryOrderItem;
import id.enterpriseapps.erp.entity.DocumentStatus;
import id.enterpriseapps.erp.entity.FiscalYear;
import id.enterpriseapps.erp.entity.InventoryItem;
import id.enterpriseapps.erp.entity.InvoiceStatus;
import id.enterpriseapps.erp.entity.JournalEntry;
import id.enterpriseapps.erp.entity.JournalItem;
import id.enterpriseapps.erp.entity.Party;
import id.enterpriseapps.erp.entity.SalesInvoice;
import id.enterpriseapps.erp.entity.SalesInvoiceItem;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Submit invoice penjualan — port {@code services/salesInvoiceSubmit.ts}.
 *
 * <p>Jurnal pendapatan {@code JV-<nomor>}: DR Piutang (party) sebesar grand total (+ DR PPh 1.5.4),
 * CR satu baris per akun pendapatan (+ CR PPN Keluaran). Potongan/biaya lain dialokasikan ke akun
 * pendapatan terbesar agar jurnal seimbang. Lalu jurnal HPP terpisah {@code JV-COGS-<nomor>}.
 *
 * <p>Barang hanya keluar lewat Surat Jalan: setiap baris inventori WAJIB terkait baris DO yang sudah
 * submit (tidak ada stock-out ganda). HPP memakai unit cost yang terkunci di gerakan stok DO (biaya
 * barang yang benar-benar dikirim), dan DO yang ditagih ditandai Invoiced.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpSalesInvoiceSubmitService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ErpFiscalYearService fiscalYears;
    private final ErpDocumentNumberService numbers;
    private final ErpSystemAccountService systemAccounts;
    private final ErpGlPostingService gl;
    private final ErpAccountBalanceService balances;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** Baris yang sudah di-resolve (akun pendapatan, nominal) — padanan {@code resolvedItems}. */
    private record ResolvedItem(String itemName, String itemType, String inventoryItemId, String warehouseId,
                                String serviceItemId, BigDecimal quantity, String unit, BigDecimal rate,
                                BigDecimal discount, BigDecimal amount, BigDecimal taxPct, BigDecimal pphPct,
                                String accountId, String description, String deliveryOrderItemId,
                                String sourceMovementId) {
    }

    private record JLine(String accountId, BigDecimal debit, BigDecimal credit, String description, String partyId) {
    }

    /**
     * POST /sales/invoices: submit lalu kembalikan invoice beserta customer + items
     * ({@code include: { customer: true, items: true }}).
     */
    @ErpTx(timeout = 15)
    public ObjectNode submit(SalesInvoiceRequests.Create body, String userId) {
        SalesInvoice invoice = submitInTx(body, userId);
        String id = invoice.getId();
        em.flush();
        em.clear();
        SalesInvoice reloaded = em.createQuery(
                        "select i from SalesInvoice i left join fetch i.customer left join fetch i.items where i.id = :id",
                        SalesInvoice.class)
                .setParameter("id", id).getSingleResult();
        return ErpSalesInvoiceService.withCustomerAndItems(reloaded);
    }

    /**
     * Inti submit (padanan {@code submitSalesInvoiceInTx}); pemanggil memiliki transaksi.
     * BusinessError sumber → {@link ErpException} 400.
     */
    @ErpTx(timeout = 15)
    public SalesInvoice submitInTx(SalesInvoiceRequests.Create body, String userId) {
        Instant parsedDate = SalesSupport.parseDate(body.date());
        if (parsedDate == null) {
            throw ErpException.business("Format tanggal tidak valid.");
        }
        FiscalYear fiscalYear = fiscalYears.getOpenFiscalYear(parsedDate);

        Party party = em.find(Party.class, body.partyId());
        if (party == null) {
            throw ErpException.business("Data customer tidak ditemukan.");
        }
        if (!Boolean.TRUE.equals(party.getIsActive())) {
            throw ErpException.business("Customer sudah tidak aktif.");
        }

        ErpSystemAccountService.MappedAccount arAccount = systemAccounts.getAccount(SystemAccountRole.AR);
        ErpSystemAccountService.MappedAccount salesAccount = systemAccounts.getAccount(SystemAccountRole.SALES);
        ErpSystemAccountService.MappedAccount serviceRevenueAccount = systemAccounts.getAccount(SystemAccountRole.SERVICE_REVENUE);
        ErpSystemAccountService.MappedAccount inventoryAccount = systemAccounts.getAccount(SystemAccountRole.INVENTORY);
        // Utamakan HPP Beras (5.1) dibanding induk HPP generik (5).
        Account hppBeras = systemAccounts.findByNumber("5.1");
        String cogsAccountId = hppBeras != null ? hppBeras.getId() : systemAccounts.getAccountId(SystemAccountRole.COGS);

        List<SalesItemRequest> lines = body.items();
        for (SalesItemRequest it : lines) {
            if (it == null) {
                throw ErpException.business(ZodText.EXPECTED_OBJECT_NULL);
            }
        }

        // Ambil akun & layanan yang dirujuk sekaligus (hindari 2N query di dalam transaksi).
        Set<String> customAccountIds = new LinkedHashSet<>();
        Set<String> serviceItemIds = new LinkedHashSet<>();
        for (SalesItemRequest it : lines) {
            if (SalesSupport.hasText(it.accountId())) {
                customAccountIds.add(it.accountId());
            }
            if (SalesSupport.hasText(it.serviceItemId())) {
                serviceItemIds.add(it.serviceItemId());
            }
        }
        Set<String> customAccountSet = new HashSet<>(customAccountIds.isEmpty() ? List.of()
                : em.createQuery("select a.id from Account a where a.id in :ids", String.class)
                .setParameter("ids", customAccountIds).getResultList());
        Map<String, String> serviceItemAccount = new HashMap<>();
        if (!serviceItemIds.isEmpty()) {
            em.createQuery("select s.id, s.accountId from ServiceItem s where s.id in :ids", Object[].class)
                    .setParameter("ids", serviceItemIds).getResultList()
                    .forEach(r -> serviceItemAccount.put((String) r[0], (String) r[1]));
        }

        List<ResolvedItem> resolvedItems = new ArrayList<>();
        for (SalesItemRequest item : lines) {
            BigDecimal discount = Money.nz(item.discount());
            BigDecimal itemAmount = item.quantity()
                    .multiply(Money.nz(item.rate()))
                    .multiply(BigDecimal.ONE.subtract(discount.divide(HUNDRED)))
                    .setScale(2, RoundingMode.HALF_UP);

            String lineAccountId = salesAccount.id();
            if (SalesSupport.hasText(item.accountId()) && customAccountSet.contains(item.accountId())) {
                lineAccountId = item.accountId();
            } else if (SalesSupport.hasText(item.serviceItemId())) {
                String svcAccount = serviceItemAccount.get(item.serviceItemId());
                if (svcAccount != null) {
                    lineAccountId = svcAccount;
                }
            } else if ("service".equals(item.itemType())) {
                lineAccountId = serviceRevenueAccount.id();
            }

            resolvedItems.add(new ResolvedItem(
                    item.itemName(),
                    SalesSupport.hasText(item.itemType()) ? item.itemType() : "product",
                    SalesSupport.orNull(item.inventoryItemId()),
                    SalesSupport.orNull(item.warehouseId()),
                    SalesSupport.orNull(item.serviceItemId()),
                    item.quantity(),
                    SalesSupport.hasText(item.unit()) ? item.unit() : "pcs",
                    Money.nz(item.rate()),
                    discount,
                    itemAmount,
                    Money.nz(item.taxPct()),
                    Money.nz(item.pphPct()),
                    lineAccountId,
                    SalesSupport.orNull(item.description()),
                    SalesSupport.orNull(item.deliveryOrderItemId()),
                    SalesSupport.orNull(item.sourceMovementId())));
        }

        // Barang keluar hanya lewat Surat Jalan: baris inventori wajib terkait baris DO yang sudah
        // submit (tidak ada stock-out ganda). Baris jasa / akun bebas tidak perlu tautan.
        List<ResolvedItem> orphanInventoryLines = resolvedItems.stream()
                .filter(it -> it.inventoryItemId() != null && it.deliveryOrderItemId() == null).toList();
        if (!orphanInventoryLines.isEmpty()) {
            // Ambil nama item supaya pesan errornya jelas — staff tahu baris mana.
            Map<String, String> offenders = new HashMap<>();
            em.createQuery("select v.id, v.name from InventoryItem v where v.id in :ids", Object[].class)
                    .setParameter("ids", orphanInventoryLines.stream().map(ResolvedItem::inventoryItemId).toList())
                    .getResultList()
                    .forEach(r -> offenders.put((String) r[0], (String) r[1]));
            LinkedHashSet<String> names = new LinkedHashSet<>();
            for (ResolvedItem l : orphanInventoryLines) {
                names.add(offenders.getOrDefault(l.inventoryItemId(), l.itemName()));
            }
            throw ErpException.business("Item inventori \"" + String.join(", ", names) + "\" tidak bisa di-invoice langsung. "
                    + "Buat Surat Jalan (Delivery Order) terlebih dahulu. Invoice hanya bisa di-generate dari DO yang sudah submit. "
                    + "Stok keluar lewat DO, bukan invoice.");
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal taxSum = BigDecimal.ZERO;
        BigDecimal pphSum = BigDecimal.ZERO;
        for (ResolvedItem it : resolvedItems) {
            subtotal = subtotal.add(it.amount());
            taxSum = taxSum.add(it.amount().multiply(it.taxPct().divide(HUNDRED)));
            pphSum = pphSum.add(it.amount().multiply(it.pphPct().divide(HUNDRED)));
        }
        BigDecimal taxAmount = taxSum.setScale(2, RoundingMode.HALF_UP);
        BigDecimal pphAmount = pphSum.setScale(2, RoundingMode.HALF_UP);
        BigDecimal potongan = Money.nz(body.potongan());
        BigDecimal biayaLain = Money.nz(body.biayaLain());
        BigDecimal grandTotal = subtotal.add(taxAmount).subtract(pphAmount).subtract(potongan).add(biayaLain)
                .setScale(2, RoundingMode.HALF_UP);
        String invoiceNumber = numbers.generate("SI", parsedDate, fiscalYear.getId());

        SalesInvoice invoice = new SalesInvoice();
        invoice.setInvoiceNumber(invoiceNumber);
        invoice.setDate(parsedDate);
        invoice.setDueDate(SalesSupport.hasText(body.dueDate()) ? ErpDates.toInstant(body.dueDate()) : null);
        invoice.setPartyId(party.getId());
        invoice.setGrandTotal(grandTotal);
        invoice.setOutstanding(grandTotal);
        invoice.setTaxPct(BigDecimal.ZERO);
        invoice.setPotongan(potongan);
        invoice.setBiayaLain(biayaLain);
        invoice.setLabelPotongan(SalesSupport.orNull(body.labelPotongan()));
        invoice.setLabelBiaya(SalesSupport.orNull(body.labelBiaya()));
        invoice.setTerms(SalesSupport.orNull(body.terms()));
        invoice.setCustomerRefNo(SalesSupport.orNull(body.customerRefNo()));
        // Invoice bernilai Rp 0 (sumbangan/sampel/bonus) tidak punya tagihan → langsung Paid supaya
        // tidak menggantung sebagai invoice terbuka. Efek GL tetap: HPP + pengurangan persediaan
        // lewat jurnal COGS.
        invoice.setStatus(grandTotal.signum() == 0 ? InvoiceStatus.Paid : InvoiceStatus.Submitted);
        invoice.setNotes(SalesSupport.orNull(body.notes()));
        invoice.setFiscalYearId(fiscalYear.getId());
        invoice.setCreatedBy(userId);
        invoice.setSubmittedAt(Instant.now());
        em.persist(invoice);

        List<SalesInvoiceItem> invoiceItems = new ArrayList<>();
        for (ResolvedItem ri : resolvedItems) {
            SalesInvoiceItem si = new SalesInvoiceItem();
            si.setSalesInvoiceId(invoice.getId());
            si.setItemName(ri.itemName());
            si.setItemType(ri.itemType());
            si.setInventoryItemId(ri.inventoryItemId());
            si.setWarehouseId(ri.warehouseId());
            si.setServiceItemId(ri.serviceItemId());
            si.setQuantity(ri.quantity());
            si.setUnit(ri.unit());
            si.setRate(ri.rate());
            si.setDiscount(ri.discount());
            si.setAmount(ri.amount());
            si.setTaxPct(ri.taxPct());
            si.setPphPct(ri.pphPct());
            si.setAccountId(ri.accountId());
            si.setDescription(ri.description());
            si.setDeliveryOrderItemId(ri.deliveryOrderItemId());
            si.setSourceMovementId(ri.sourceMovementId());
            em.persist(si);
            invoiceItems.add(si);
        }
        // Tautan DO unik per baris invoice / FK tidak valid → 400 seperti P2002/P2003.
        SalesSupport.flush(em);

        postRevenueJournal(invoice, party, resolvedItems, salesAccount.id(), arAccount.id(), taxAmount, pphAmount,
                potongan, biayaLain, grandTotal, userId);
        balances.recalcPartyOutstanding(party.getId());

        postCogs(invoice, party, invoiceItems, inventoryAccount.id(), cogsAccountId, userId);
        return invoice;
    }

    /** Jurnal pendapatan multi-akun {@code JV-<nomor>}. */
    private void postRevenueJournal(SalesInvoice invoice, Party party, List<ResolvedItem> resolvedItems,
                                    String salesAccountId, String arAccountId, BigDecimal taxAmount,
                                    BigDecimal pphAmount, BigDecimal potongan, BigDecimal biayaLain,
                                    BigDecimal grandTotal, String userId) {
        String no = invoice.getInvoiceNumber();
        Map<String, BigDecimal> revenueByAccount = new LinkedHashMap<>();
        for (ResolvedItem ri : resolvedItems) {
            revenueByAccount.merge(ri.accountId(), ri.amount(), BigDecimal::add);
        }
        // potongan/biayaLain → dialokasikan ke akun pendapatan terbesar agar jurnal seimbang.
        BigDecimal adjustment = biayaLain.subtract(potongan);
        if (adjustment.signum() != 0) {
            String primaryAccountId = salesAccountId;
            BigDecimal maxAmount = BigDecimal.ZERO;
            for (Map.Entry<String, BigDecimal> e : revenueByAccount.entrySet()) {
                if (e.getValue().compareTo(maxAmount) > 0) {
                    maxAmount = e.getValue();
                    primaryAccountId = e.getKey();
                }
            }
            revenueByAccount.merge(primaryAccountId, adjustment, BigDecimal::add);
        }

        List<JLine> creditLines = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : revenueByAccount.entrySet()) {
            BigDecimal credit = e.getValue().setScale(2, RoundingMode.HALF_UP);
            if (credit.signum() == 0) {
                continue;
            }
            List<String> names = em.createQuery("select a.name from Account a where a.id = :id", String.class)
                    .setParameter("id", e.getKey()).getResultList();
            String accName = names.isEmpty() || names.get(0) == null ? "Penjualan" : names.get(0);
            creditLines.add(new JLine(e.getKey(), BigDecimal.ZERO, credit, accName + ": " + no, null));
        }
        if (taxAmount.signum() > 0) {
            String taxOutputAccountId = systemAccounts.getAccountId(SystemAccountRole.TAX_OUTPUT);
            creditLines.add(new JLine(taxOutputAccountId, BigDecimal.ZERO, taxAmount, "PPN Keluaran: " + no, null));
        }

        List<JLine> debitLines = new ArrayList<>();
        debitLines.add(new JLine(arAccountId, grandTotal, BigDecimal.ZERO, "Piutang: " + no, party.getId()));
        if (pphAmount.signum() > 0) {
            Account pphAccount = systemAccounts.findByNumber("1.5.4");
            if (pphAccount != null) {
                debitLines.add(new JLine(pphAccount.getId(), pphAmount, BigDecimal.ZERO, "PPh Penjualan: " + no, null));
            }
        }

        List<JLine> all = new ArrayList<>(debitLines);
        all.addAll(creditLines);
        postJournal("JV-" + no, invoice.getDate(), "Penjualan: " + no + " - " + party.getName(),
                invoice.getFiscalYearId(), userId, all);
    }

    /**
     * HPP: stock-out sudah terjadi saat DO submit, jadi HPP memakai unit cost yang terkunci di gerakan
     * stok DO. DO yang ditagih ditandai Invoiced (ditautkan ke invoice ini).
     */
    private void postCogs(SalesInvoice invoice, Party party, List<SalesInvoiceItem> items, String inventoryAccountId,
                          String cogsAccountId, String userId) {
        String no = invoice.getInvoiceNumber();
        BigDecimal totalCogs = BigDecimal.ZERO;
        Map<String, BigDecimal> cogsByAccount = new LinkedHashMap<>();
        Map<String, List<String>> namesByAccount = new HashMap<>();
        Set<String> linkedDoIds = new LinkedHashSet<>();

        for (SalesInvoiceItem item : items) {
            String invItemId = item.getInventoryItemId();
            if (invItemId == null) {
                continue;
            }
            InventoryItem inventoryItem = em.find(InventoryItem.class, invItemId);
            if (inventoryItem == null || !Boolean.TRUE.equals(inventoryItem.getIsActive())) {
                continue;
            }
            // Kuantitas sebagaimana tersimpan (NUMERIC(15,3)).
            BigDecimal qty = item.getQuantity().setScale(3, RoundingMode.HALF_UP);

            if (item.getDeliveryOrderItemId() != null) {
                List<DeliveryOrderItem> found = em.createQuery(
                                "select d from DeliveryOrderItem d join fetch d.deliveryOrder left join fetch d.stockMovement where d.id = :id",
                                DeliveryOrderItem.class)
                        .setParameter("id", item.getDeliveryOrderItemId()).getResultList();
                if (found.isEmpty()) {
                    throw ErpException.business("Baris dari Surat Jalan tidak ditemukan (id=" + item.getDeliveryOrderItemId() + ").");
                }
                DeliveryOrderItem doItem = found.get(0);
                if (doItem.getDeliveryOrder().getStatus() == DocumentStatus.Cancelled) {
                    throw ErpException.business("Surat Jalan " + doItem.getDeliveryOrder().getDoNumber()
                            + " sudah dibatalkan dan tidak bisa di-invoice.");
                }
                String doInvoiceId = doItem.getDeliveryOrder().getInvoiceId();
                if (doInvoiceId != null && !doInvoiceId.equals(invoice.getId())) {
                    throw ErpException.business("Surat Jalan " + doItem.getDeliveryOrder().getDoNumber()
                            + " sudah ada di invoice lain.");
                }
                // averageCost hanya cadangan defensif bila baris DO tidak punya gerakan stok.
                BigDecimal cogsUnitCost = doItem.getStockMovement() != null
                        ? Money.nz(doItem.getStockMovement().getUnitCost())
                        : Money.max(BigDecimal.ZERO, Money.nz(inventoryItem.getAverageCost()));
                BigDecimal cogsAmount = qty.multiply(cogsUnitCost).setScale(2, RoundingMode.HALF_UP);

                totalCogs = totalCogs.add(cogsAmount);
                String invAcctId = inventoryItem.getAccountId() != null ? inventoryItem.getAccountId() : inventoryAccountId;
                cogsByAccount.merge(invAcctId, cogsAmount, BigDecimal::add);
                namesByAccount.computeIfAbsent(invAcctId, k -> new ArrayList<>()).add(item.getItemName());

                linkedDoIds.add(doItem.getDeliveryOrderId());
                log.info("Sales invoice line billed from existing delivery order invoiceId={} doId={} doItemId={} qty={} lockedUnitCost={}",
                        invoice.getId(), doItem.getDeliveryOrderId(), doItem.getId(), qty, cogsUnitCost);
                continue;
            }

            // Direct sale sudah ditutup; baris inventori tanpa DO ditolak lebih awal, ini defensif.
            throw ErpException.business("Item \"" + inventoryItem.getName() + "\" harus di-kirim via Surat Jalan terlebih dahulu. "
                    + "Direct-sale dari invoice sudah ditutup.");
        }

        // Tandai semua DO yang tersentuh sebagai Invoiced (tautkan balik ke invoice ini).
        if (!linkedDoIds.isEmpty()) {
            Instant now = Instant.now();
            em.createQuery("update DeliveryOrder d set d.invoiceId = :inv, d.invoicedAt = :now, d.updatedAt = :now "
                            + "where d.id in :ids")
                    .setParameter("inv", invoice.getId())
                    .setParameter("now", now)
                    .setParameter("ids", linkedDoIds)
                    .executeUpdate();
        }

        BigDecimal totalCogsNum = totalCogs.setScale(2, RoundingMode.HALF_UP);
        if (totalCogsNum.signum() > 0) {
            List<JLine> cogsLines = new ArrayList<>();
            cogsLines.add(new JLine(cogsAccountId, totalCogsNum, BigDecimal.ZERO, "HPP: " + no, null));
            for (Map.Entry<String, BigDecimal> e : cogsByAccount.entrySet()) {
                cogsLines.add(new JLine(e.getKey(), BigDecimal.ZERO, e.getValue(),
                        "Persediaan keluar " + String.join(", ", namesByAccount.get(e.getKey())) + ": " + no, null));
            }
            postJournal("JV-COGS-" + no, invoice.getDate(), "HPP Penjualan: " + no + " - " + party.getName(),
                    invoice.getFiscalYearId(), userId, cogsLines);
        }
    }

    /**
     * Posting lewat {@link ErpGlPostingService} (jurnal + ledger + saldo akun). Kernel membuang baris
     * bernilai nol dan menolak jurnal tanpa baris bernilai; invoice Rp 0 di sumber tetap mencatat
     * jurnal {@code JV-<nomor>} dengan baris nol, jadi kasus itu ditulis langsung (tanpa efek saldo).
     */
    private void postJournal(String entryNumber, Instant date, String narration, String fiscalYearId, String userId,
                             List<JLine> lines) {
        boolean anyValue = lines.stream().anyMatch(l -> l.debit().signum() != 0 || l.credit().signum() != 0);
        if (anyValue) {
            ErpGlPostingService.Builder b = gl.journal().number(entryNumber).date(date).narration(narration)
                    .fiscalYear(fiscalYearId).createdBy(userId);
            for (JLine l : lines) {
                if (l.partyId() != null) {
                    b.line(l.accountId(), l.debit(), l.credit(), l.description(), l.partyId());
                } else {
                    b.line(l.accountId(), l.debit(), l.credit(), l.description());
                }
            }
            b.post();
            return;
        }
        postZeroJournal(entryNumber, date, narration, fiscalYearId, userId, lines);
    }

    /** Jurnal bernilai nol (invoice Rp 0): header + baris + ledger apa adanya, saldo tidak berubah. */
    private void postZeroJournal(String entryNumber, Instant date, String narration, String fiscalYearId,
                                 String userId, List<JLine> lines) {
        JournalEntry je = new JournalEntry();
        je.setEntryNumber(entryNumber);
        je.setDate(date);
        je.setNarration(narration);
        je.setStatus(DocumentStatus.Submitted);
        je.setFiscalYearId(fiscalYearId);
        je.setCreatedBy(userId);
        je.setSubmittedAt(Instant.now());
        em.persist(je);
        for (JLine l : lines) {
            JournalItem ji = new JournalItem();
            ji.setJournalEntryId(je.getId());
            ji.setAccountId(l.accountId());
            ji.setPartyId(l.partyId());
            ji.setDebit(Money.round2(l.debit()));
            ji.setCredit(Money.round2(l.credit()));
            ji.setDescription(l.description());
            em.persist(ji);

            AccountingLedgerEntry le = new AccountingLedgerEntry();
            le.setDate(date);
            le.setAccountId(l.accountId());
            le.setPartyId(l.partyId());
            le.setDebit(Money.round2(l.debit()));
            le.setCredit(Money.round2(l.credit()));
            le.setReferenceType(ErpGlPostingService.REF_JOURNAL);
            le.setReferenceId(je.getId());
            le.setDescription(l.description());
            le.setFiscalYearId(fiscalYearId);
            em.persist(le);
        }
        SalesSupport.flush(em);
    }
}
