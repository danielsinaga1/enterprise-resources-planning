package id.neravoxa.erp.purchase;

import id.neravoxa.erp.core.Money;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Perhitungan baris invoice pembelian & total PO — port {@code purchaseInvoiceCalc.ts} dan
 * {@code purchaseOrderCalc.ts}.
 *
 * <p>Harga bahan baku penggilingan: timbanganTruk hanya informasi; timbanganDiterima (setelah
 * refaksi) adalah berat yang dibayar dan menggantikan {@code quantity}. PPN/PPh manual per baris,
 * potonganItem dalam Rupiah (bukan persen):
 * <pre>
 *   subtotal   = qty × rate × (1 − discount/100)
 *   hargaAkhir = subtotal + subtotal × ppn% − subtotal × pph% − potonganItem   (= kolom amount)
 * </pre>
 * PPN/PPh dilebur ke {@code amount} (bukan baris GL terpisah); harga satuan stok mencakup semuanya.
 * PO memakai rumus yang sama (tanpa PPh/potongan) agar PO dan PI berikutnya berharga identik.
 */
final class PurchaseCalc {

    private static final MathContext MC = MathContext.DECIMAL128;

    private PurchaseCalc() {
    }

    record Computed(BigDecimal effectiveQuantity, BigDecimal subtotal, BigDecimal ppnAmount, BigDecimal pphAmount,
                    BigDecimal hargaAkhir) {
    }

    /** timbanganDiterima (bila &gt; 0) menggantikan quantity untuk harga & stok. */
    static BigDecimal effectiveQuantity(BigDecimal quantity, BigDecimal timbanganDiterima) {
        if (timbanganDiterima != null && timbanganDiterima.signum() > 0) {
            return timbanganDiterima;
        }
        return Money.nz(quantity);
    }

    private static BigDecimal pctOf(BigDecimal pct) {
        return Money.nz(pct).divide(Money.HUNDRED, MC);
    }

    static Computed compute(BigDecimal quantity, BigDecimal timbanganDiterima, BigDecimal rate, BigDecimal discount,
                            BigDecimal taxPct, BigDecimal pphPct, BigDecimal potonganItem) {
        BigDecimal qty = effectiveQuantity(quantity, timbanganDiterima);
        BigDecimal base = qty.multiply(Money.nz(rate));
        BigDecimal subtotal = Money.round2(base.subtract(base.multiply(pctOf(discount))));
        BigDecimal ppn = Money.round2(subtotal.multiply(pctOf(taxPct)));
        BigDecimal pph = Money.round2(subtotal.multiply(pctOf(pphPct)));
        BigDecimal harga = Money.round2(subtotal.add(ppn).subtract(pph).subtract(Money.nz(potonganItem)));
        return new Computed(qty, subtotal, ppn, pph, harga);
    }

    static Computed compute(PurchaseRequests.PurchaseInvoiceItemInput it) {
        return compute(it.quantity(), it.timbanganDiterima(), it.rate(), it.discount(), it.taxPct(), it.pphPct(),
                it.potonganItem());
    }

    /** Σ potonganItem — cache kolom header {@code potongan}. */
    static BigDecimal sumPotonganItem(Collection<PurchaseRequests.PurchaseInvoiceItemInput> items) {
        return Money.sum(items, PurchaseRequests.PurchaseInvoiceItemInput::potonganItem);
    }

    // ───────────────────────────── PO ─────────────────────────────

    record PoLineInput(BigDecimal quantity, BigDecimal estimatedRate, BigDecimal discount, BigDecimal taxPct) {
    }

    record PoLine(BigDecimal gross, BigDecimal net, BigDecimal discountAmount, BigDecimal taxAmount, BigDecimal amount) {
    }

    record PoTotals(List<PoLine> lines, BigDecimal subtotal, BigDecimal discountTotal, BigDecimal taxTotal,
                    BigDecimal otherCosts, BigDecimal grandTotal) {
    }

    /**
     * gross = qty × rate (2 dp); net = PI subtotal; taxAmount = PI ppnAmount; amount = net + tax.
     */
    static PoLine poLine(PoLineInput line) {
        BigDecimal rate = Money.nz(line.estimatedRate());
        Computed calc = compute(line.quantity(), null, rate, Money.nz(line.discount()), Money.nz(line.taxPct()),
                BigDecimal.ZERO, BigDecimal.ZERO);
        BigDecimal gross = Money.round2(Money.nz(line.quantity()).multiply(rate));
        return new PoLine(gross, calc.subtotal(), gross.subtract(calc.subtotal()), calc.ppnAmount(), calc.hargaAkhir());
    }

    /** Header: subtotal = Σ gross, discountTotal = Σ (gross − net), taxTotal = Σ tax, grandTotal = Σ amount + otherCosts. */
    static PoTotals poTotals(List<PoLineInput> items, BigDecimal otherCosts) {
        List<PoLine> lines = new ArrayList<>();
        for (PoLineInput it : items) {
            lines.add(poLine(it));
        }
        BigDecimal other = Money.round2(Money.nz(otherCosts));
        return new PoTotals(lines,
                Money.sum(lines, PoLine::gross),
                Money.sum(lines, PoLine::discountAmount),
                Money.sum(lines, PoLine::taxAmount),
                other,
                Money.round2(Money.sum(lines, PoLine::amount).add(other)));
    }
}
