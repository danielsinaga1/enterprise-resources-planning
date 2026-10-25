package id.neravoxa.erp.eam.ops;

import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpJson;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Perhitungan dashboard pemeliharaan — port {@code computeMaintenanceDashboard} (services/dashboardMaintenance.ts).
 * Definisinya harus sama dengan halaman GA:
 * <ul>
 *   <li>Jadwal preventif: terlambat bila nextDueAt &lt; awal hari ini (zona bisnis); minggu ini = hari ini..+6.</li>
 *   <li>Work order (OPEN/ASSIGNED/IN_PROGRESS): terlambat bila dueAt &lt; sekarang.</li>
 *   <li>Pajak/STNK/KIR &amp; asuransi (ACTIVE): kedaluwarsa sebelum awal hari ini, segera habis dalam 30 hari.</li>
 *   <li>Dokumen GA: aturan deriveExpiryState daftar dokumen.</li>
 * </ul>
 */
final class MaintenanceDashboardCalc {

    private MaintenanceDashboardCalc() {
    }

    static final long DAY_MS = 24L * 60 * 60 * 1000;
    static final int WEEK_DAYS = 7;
    static final int UPCOMING_LIMIT = 8;

    record Access(boolean maintenance, boolean vehicle, boolean documents) {
    }

    record ScheduleRow(String id, String assetId, String assetName, Instant nextDueAt, boolean isActive) {
    }

    record WorkOrderRow(String id, String code, String title, String assetName, String status, Instant dueAt) {
    }

    record VehicleTaxRow(String id, String assetId, String assetName, String type, Instant dueDate, String status) {
    }

    record InsuranceRow(String id, String assetId, String assetName, String provider, Instant endDate, String status) {
    }

    record DocumentRow(String id, String title, String assetName, Instant expiryDate, String status) {
    }

    record Input(List<ScheduleRow> schedules, List<WorkOrderRow> workOrders, List<VehicleTaxRow> taxes,
                 List<InsuranceRow> insurances, List<DocumentRow> documents) {
    }

    static final Map<String, String> TAX_LABEL = Map.of(
            "STNK_TAHUNAN", "Pajak STNK",
            "STNK_5_TAHUNAN", "STNK 5 tahunan",
            "KIR", "KIR",
            "PAJAK_KENDARAAN", "Pajak kendaraan");

    private static final Set<String> WO_ACTIVE = Set.of("OPEN", "ASSIGNED", "IN_PROGRESS");

    private record Item(String key, String kind, String kindLabel, String title, String assetName, Instant due,
                        String dueAt, long daysLeft, String state, String href, boolean overdue, boolean expiring) {
        Map<String, Object> toUpcoming() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", key);
            m.put("kind", kind);
            m.put("kindLabel", kindLabel);
            m.put("title", title);
            m.put("assetName", assetName);
            m.put("dueAt", dueAt);
            m.put("daysLeft", daysLeft);
            m.put("state", state);
            m.put("href", href);
            return m;
        }
    }

    private static String stateOf(Instant due, Instant todayStart, boolean overdue, Instant weekEnd) {
        if (overdue) {
            return "overdue";
        }
        if (due.toEpochMilli() < todayStart.toEpochMilli() + DAY_MS) {
            return "today";
        }
        return due.isBefore(weekEnd) ? "soon" : "scheduled";
    }

    /** {@code deriveExpiryState} dokumen GA: expired / expiring / valid / null. */
    static String documentExpiryState(String status, Instant expiryDate, Instant[] window) {
        if ("EXPIRED".equals(status)) {
            return "expired";
        }
        if (!"ACTIVE".equals(status)) {
            return null;
        }
        if (expiryDate == null) {
            return "valid";
        }
        if (expiryDate.isBefore(window[0])) {
            return "expired";
        }
        if (expiryDate.isBefore(window[1])) {
            return "expiring";
        }
        return "valid";
    }

    static Map<String, Object> compute(Input input, Access access, Instant now) {
        // [awal hari ini, awal (hari ini + 30) + 1 hari) zona bisnis = jendela kedaluwarsa dokumen/pajak.
        Instant[] expiry = ErpDates.dueBoundaries(now);
        Instant todayStart = expiry[0];
        Instant weekEnd = todayStart.plus(Duration.ofDays(WEEK_DAYS));
        List<Item> items = new ArrayList<>();
        class Pusher {
            void push(String key, String kind, String kindLabel, String title, String assetName, String href,
                      Instant due, boolean overdue, boolean expiring) {
                long daysLeft = Math.floorDiv(due.toEpochMilli() - todayStart.toEpochMilli(), DAY_MS);
                items.add(new Item(key, kind, kindLabel, title, assetName, due, ErpJson.tree(due).asText(), daysLeft,
                        stateOf(due, todayStart, overdue, weekEnd), href, overdue, expiring));
            }
        }
        Pusher p = new Pusher();

        if (access.maintenance()) {
            for (ScheduleRow s : input.schedules()) {
                if (!s.isActive()) {
                    continue;
                }
                p.push("sch-" + s.id(), "schedule", "Jadwal preventif", "Perawatan berkala", s.assetName(),
                        "/ga/maintenance?tab=schedules", s.nextDueAt(), s.nextDueAt().isBefore(todayStart), false);
            }
            for (WorkOrderRow w : input.workOrders()) {
                if (w.dueAt() == null || !WO_ACTIVE.contains(w.status())) {
                    continue;
                }
                p.push("wo-" + w.id(), "workOrder", "Work order", w.code() + " " + w.title(), w.assetName(),
                        "/ga/maintenance?wo=" + w.id(), w.dueAt(), w.dueAt().isBefore(now), false);
            }
        }
        if (access.vehicle()) {
            for (VehicleTaxRow t : input.taxes()) {
                if (!"ACTIVE".equals(t.status())) {
                    continue;
                }
                String label = TAX_LABEL.getOrDefault(t.type(), "Pajak kendaraan");
                p.push("tax-" + t.id(), "vehicleTax", label, label, t.assetName(), "/ga/vehicle?vehicle=" + t.assetId(),
                        t.dueDate(), t.dueDate().isBefore(expiry[0]), t.dueDate().isBefore(expiry[1]));
            }
            for (InsuranceRow i : input.insurances()) {
                if (!"ACTIVE".equals(i.status())) {
                    continue;
                }
                p.push("ins-" + i.id(), "vehicleInsurance", "Asuransi kendaraan", "Asuransi " + i.provider(),
                        i.assetName(), "/ga/vehicle?vehicle=" + i.assetId(), i.endDate(),
                        i.endDate().isBefore(expiry[0]), i.endDate().isBefore(expiry[1]));
            }
        }
        if (access.documents()) {
            for (DocumentRow d : input.documents()) {
                if (d.expiryDate() == null) {
                    continue;
                }
                String st = documentExpiryState(d.status(), d.expiryDate(), expiry);
                if (!"expired".equals(st) && !"expiring".equals(st)) {
                    continue;
                }
                p.push("doc-" + d.id(), "document", "Dokumen", d.title(), d.assetName(), "/ga/documents?doc=" + d.id(),
                        d.expiryDate(), "expired".equals(st), true);
            }
        }

        items.sort(Comparator.comparing(Item::dueAt)); // stabil, seperti Array.prototype.sort
        Predicate<Item> vehicleKinds = i -> "vehicleTax".equals(i.kind()) || "vehicleInsurance".equals(i.kind());
        Predicate<Item> expiryKinds = i -> vehicleKinds.test(i) || "document".equals(i.kind());
        long overdueSchedules = count(items, i -> "schedule".equals(i.kind()) && i.overdue());
        long overdueWorkOrders = count(items, i -> "workOrder".equals(i.kind()) && i.overdue());
        long expired = count(items, i -> expiryKinds.test(i) && i.overdue());
        long expiring = count(items, i -> expiryKinds.test(i) && !i.overdue() && i.expiring());
        long dueThisWeek = count(items, i -> !i.overdue() && i.daysLeft() < WEEK_DAYS);

        List<Map<String, Object>> attention = new ArrayList<>();
        addAttention(attention, "mt-schedule", "danger", overdueSchedules,
                overdueSchedules + " jadwal pemeliharaan terlambat",
                "Terlama " + oldest(items, i -> "schedule".equals(i.kind())) + " hari", "/ga/maintenance?tab=schedules");
        addAttention(attention, "mt-wo", "danger", overdueWorkOrders, overdueWorkOrders + " work order lewat target",
                "Masih terbuka setelah tanggal target", "/ga/maintenance");
        long vehExpired = count(items, i -> vehicleKinds.test(i) && i.overdue());
        long vehExpiring = count(items, i -> vehicleKinds.test(i) && !i.overdue() && i.expiring());
        addAttention(attention, "mt-veh-expired", "danger", vehExpired,
                vehExpired + " pajak/asuransi kendaraan lewat masa berlaku",
                "Terlama " + oldest(items, vehicleKinds) + " hari", "/ga/vehicle");
        addAttention(attention, "mt-veh-expiring", "warning", vehExpiring,
                vehExpiring + " pajak/asuransi kendaraan habis dalam 30 hari", "STNK, KIR, pajak, dan polis", "/ga/vehicle");
        long docExpired = count(items, i -> "document".equals(i.kind()) && i.overdue());
        long docExpiring = count(items, i -> "document".equals(i.kind()) && !i.overdue());
        addAttention(attention, "mt-doc-expired", "danger", docExpired, docExpired + " dokumen legal kedaluwarsa",
                "Terlama " + oldest(items, i -> "document".equals(i.kind())) + " hari", "/ga/documents");
        addAttention(attention, "mt-doc-expiring", "warning", docExpiring, docExpiring + " dokumen legal habis dalam 30 hari",
                "Segera perpanjang", "/ga/documents");

        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("overdue", overdueSchedules + overdueWorkOrders + expired);
        kpi.put("dueThisWeek", dueThisWeek);
        kpi.put("overdueSchedules", overdueSchedules);
        kpi.put("overdueWorkOrders", overdueWorkOrders);
        kpi.put("expired", expired);
        kpi.put("expiring", expiring);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("access", GaOpsJson.map("maintenance", access.maintenance(), "vehicle", access.vehicle(),
                "documents", access.documents()));
        out.put("kpi", kpi);
        out.put("attention", attention);
        out.put("upcoming", items.stream().limit(UPCOMING_LIMIT).map(Item::toUpcoming).toList());
        return out;
    }

    private static long count(List<Item> items, Predicate<Item> pred) {
        return items.stream().filter(pred).count();
    }

    /** Keterlambatan terlama (hari) di antara item terlambat jenis tertentu. */
    private static long oldest(List<Item> items, Predicate<Item> kinds) {
        return items.stream().filter(i -> kinds.test(i) && i.overdue()).mapToLong(i -> -i.daysLeft()).max().orElse(0);
    }

    private static void addAttention(List<Map<String, Object>> out, String key, String tone, long n, String title,
                                     String detail, String href) {
        if (n > 0) {
            out.add(GaOpsJson.map("key", key, "tone", tone, "title", title, "detail", detail, "href", href));
        }
    }
}
