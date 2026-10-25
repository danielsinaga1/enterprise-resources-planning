package id.neravoxa.erp.production;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * OEE multi-mesin murni — port {@code computeOee} di {@code services/oee.ts}.
 *
 * <p>Satu run = satu lini mesin paralel; output berasal dari baris item run dan dialokasikan ke
 * mesin menurut waktu jalan efektif (basis = produk + samping). OEE = A × P × Q per mesin dan
 * untuk lini: Performance hanya produk utama, Quality level-run ((produk + samping) / input) sehingga
 * hanya susut yang menurunkannya. Komponen di-clamp 0..1 dan null bila tidak bisa dihitung; OEE null
 * kecuali ketiganya ada. Aritmetika meniru decimal.js (presisi 20 digit, HALF_UP).
 */
final class ProductionOee {

    static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);
    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);
    private static final BigDecimal MS_PER_MIN = BigDecimal.valueOf(60000);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private ProductionOee() {
    }

    record MachineInput(String machineId, Instant startAt, Instant endAt, BigDecimal idealRatePerHour) {
    }

    /** Downtime tanpa machineId tetap masuk total downtime run, tapi tidak ke mesin mana pun. */
    record DowntimeInput(String machineId, BigDecimal durationMin) {
    }

    /** inputQty = Σ input, produkQty = Σ output utama, sampingQty = Σ produk sampingan. */
    record Input(Instant plannedStart, Instant plannedEnd, Instant breakStart, Instant breakEnd,
                 List<MachineInput> runMachines, List<DowntimeInput> downtimes,
                 BigDecimal inputQty, BigDecimal produkQty, BigDecimal sampingQty) {
    }

    record MachineResult(String machineId, BigDecimal runMin, BigDecimal downtimeMin, BigDecimal allocatedQty,
                         BigDecimal availabilityPct, BigDecimal performancePct, BigDecimal qualityPct, BigDecimal oeePct) {
    }

    record Result(BigDecimal downtimeMin, BigDecimal availabilityPct, BigDecimal performancePct,
                  BigDecimal qualityPct, BigDecimal oeePct, List<MachineResult> machines) {
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal clamp01(BigDecimal x) {
        if (x.signum() < 0) {
            return BigDecimal.ZERO;
        }
        if (x.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE;
        }
        return x;
    }

    private static BigDecimal pct2(BigDecimal ratio) {
        return ratio.multiply(HUNDRED, MC).setScale(2, RoundingMode.HALF_UP);
    }

    /** Menit antara dua waktu (0 bila salah satu kosong / rentang tidak positif). */
    private static BigDecimal minutesBetween(Instant a, Instant b) {
        if (a == null || b == null) {
            return BigDecimal.ZERO;
        }
        long ms = b.toEpochMilli() - a.toEpochMilli();
        if (ms <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(ms).divide(MS_PER_MIN, MC);
    }

    /** Menit irisan [aStart,aEnd] ∩ [bStart,bEnd] (0 bila salah satu jendela kosong). */
    private static BigDecimal overlapMinutes(Instant aStart, Instant aEnd, Instant bStart, Instant bEnd) {
        if (aStart == null || aEnd == null || bStart == null || bEnd == null) {
            return BigDecimal.ZERO;
        }
        long start = Math.max(aStart.toEpochMilli(), bStart.toEpochMilli());
        long end = Math.min(aEnd.toEpochMilli(), bEnd.toEpochMilli());
        long ms = end - start;
        if (ms <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(ms).divide(MS_PER_MIN, MC);
    }

    static Result compute(Input in) {
        boolean hasBreak = in.breakStart() != null && in.breakEnd() != null;
        // plannedMin = (plannedEnd − plannedStart) − (breakEnd − breakStart)
        BigDecimal breakMin = hasBreak ? minutesBetween(in.breakStart(), in.breakEnd()) : BigDecimal.ZERO;
        BigDecimal plannedMin = minutesBetween(in.plannedStart(), in.plannedEnd()).subtract(breakMin, MC).max(BigDecimal.ZERO);

        BigDecimal inputQty = nz(in.inputQty());
        BigDecimal produkQty = nz(in.produkQty());
        BigDecimal sampingQty = nz(in.sampingQty());
        BigDecimal totalOutput = produkQty.add(sampingQty, MC);

        List<DowntimeInput> downtimes = in.downtimes() == null ? List.of() : in.downtimes();
        BigDecimal totalDowntimeMin = BigDecimal.ZERO;
        for (DowntimeInput d : downtimes) {
            totalDowntimeMin = totalDowntimeMin.add(nz(d.durationMin()), MC);
        }

        // runMin_m = max(onMin − breakOverlap − downtime mesin, 0)
        List<BigDecimal[]> perMachine = new ArrayList<>(); // [runMin, machineDowntimeMin]
        for (MachineInput m : in.runMachines()) {
            BigDecimal onMin = minutesBetween(m.startAt(), m.endAt());
            BigDecimal breakOverlap = hasBreak
                    ? overlapMinutes(m.startAt(), m.endAt(), in.breakStart(), in.breakEnd()) : BigDecimal.ZERO;
            BigDecimal machineDowntime = BigDecimal.ZERO;
            for (DowntimeInput d : downtimes) {
                if (d.machineId() != null && Objects.equals(d.machineId(), m.machineId())) {
                    machineDowntime = machineDowntime.add(nz(d.durationMin()), MC);
                }
            }
            BigDecimal runMin = onMin.subtract(breakOverlap, MC).subtract(machineDowntime, MC).max(BigDecimal.ZERO);
            perMachine.add(new BigDecimal[]{runMin, machineDowntime});
        }
        BigDecimal totalRunMin = BigDecimal.ZERO;
        for (BigDecimal[] x : perMachine) {
            totalRunMin = totalRunMin.add(x[0], MC);
        }

        BigDecimal quality = inputQty.signum() > 0 ? clamp01(totalOutput.divide(inputQty, MC)) : null;

        List<MachineResult> machines = new ArrayList<>();
        for (int i = 0; i < in.runMachines().size(); i++) {
            MachineInput m = in.runMachines().get(i);
            BigDecimal runMin = perMachine.get(i)[0];
            BigDecimal machineDowntime = perMachine.get(i)[1];
            boolean hasRun = totalRunMin.signum() > 0;
            BigDecimal allocFraction = hasRun ? runMin.divide(totalRunMin, MC) : BigDecimal.ZERO;
            BigDecimal allocatedQty = hasRun ? totalOutput.multiply(allocFraction, MC) : BigDecimal.ZERO;
            BigDecimal allocatedProduk = hasRun ? produkQty.multiply(allocFraction, MC) : BigDecimal.ZERO;

            BigDecimal availability = plannedMin.signum() > 0 ? clamp01(runMin.divide(plannedMin, MC)) : null;

            BigDecimal rate = nz(m.idealRatePerHour());
            BigDecimal performance = null;
            if (rate.signum() > 0 && runMin.signum() > 0 && allocatedProduk.signum() > 0) {
                BigDecimal idealQty = rate.divide(SIXTY, MC).multiply(runMin, MC);
                if (idealQty.signum() > 0) {
                    performance = clamp01(allocatedProduk.divide(idealQty, MC));
                }
            }
            BigDecimal oee = availability != null && performance != null && quality != null
                    ? availability.multiply(performance, MC).multiply(quality, MC) : null;
            machines.add(new MachineResult(m.machineId(),
                    runMin.setScale(2, RoundingMode.HALF_UP),
                    machineDowntime.setScale(2, RoundingMode.HALF_UP),
                    allocatedQty.setScale(3, RoundingMode.HALF_UP),
                    availability != null ? pct2(availability) : null,
                    performance != null ? pct2(performance) : null,
                    quality != null ? pct2(quality) : null,
                    oee != null ? pct2(oee) : null));
        }

        // Agregat lini
        int n = in.runMachines().size();
        BigDecimal runAvailability = n > 0 && plannedMin.signum() > 0
                ? clamp01(totalRunMin.divide(plannedMin.multiply(BigDecimal.valueOf(n), MC), MC)) : null;
        BigDecimal perfDenom = BigDecimal.ZERO;
        for (int i = 0; i < n; i++) {
            BigDecimal rate = nz(in.runMachines().get(i).idealRatePerHour());
            if (rate.signum() <= 0) {
                continue;
            }
            perfDenom = perfDenom.add(rate.divide(SIXTY, MC).multiply(perMachine.get(i)[0], MC), MC);
        }
        BigDecimal runPerformance = perfDenom.signum() > 0 && produkQty.signum() > 0
                ? clamp01(produkQty.divide(perfDenom, MC)) : null;
        BigDecimal runOee = runAvailability != null && runPerformance != null && quality != null
                ? runAvailability.multiply(runPerformance, MC).multiply(quality, MC) : null;

        return new Result(totalDowntimeMin.setScale(2, RoundingMode.HALF_UP),
                runAvailability != null ? pct2(runAvailability) : null,
                runPerformance != null ? pct2(runPerformance) : null,
                quality != null ? pct2(quality) : null,
                runOee != null ? pct2(runOee) : null,
                machines);
    }
}
