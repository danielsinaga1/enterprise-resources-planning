package id.neravoxa.erp.eam.ops;

import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpTx;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Laporan biaya pemeliharaan ({@code GET /ga/maintenance/cost-report?from=&to=}) — read-only. Dasar: work order
 * yang selesai (DONE/CLOSED) dengan {@code completed_at} dalam periode; biaya = tenaga kerja + vendor luar + part
 * (Σ qty × harga satuan). Diagregasi per aset dan per bulan, plus tingkat keterlambatan (WO selesai melewati
 * tenggat) dan porsi preventif vs korektif. WO aktif yang sudah lewat tenggat dihitung per saat ini.
 */
@Service
public class ErpMaintenanceCostReportService {

    private static final double MS_PER_MONTH = 30.44 * 24 * 3600 * 1000;

    private final JdbcTemplate jdbc;

    public ErpMaintenanceCostReportService(@Qualifier("erpJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    record Period(Instant from, Instant to, long months) {
    }

    /** {@code from}/{@code to} = YYYY-MM-DD (inklusif). Default: 6 bulan terakhir s.d. akhir bulan berjalan. */
    static Period parsePeriod(String fromRaw, String toRaw) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant toParsed = nonEmpty(toRaw);
        LocalDate toDay = toParsed != null ? toParsed.atZone(ZoneOffset.UTC).toLocalDate() : today.withDayOfMonth(today.lengthOfMonth());
        Instant fromParsed = nonEmpty(fromRaw);
        LocalDate fromDay = fromParsed != null ? fromParsed.atZone(ZoneOffset.UTC).toLocalDate() : toDay.withDayOfMonth(1).minusMonths(5);
        Instant from = fromDay.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = toDay.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusMillis(1);
        long months = Math.max(1, Math.round((to.toEpochMilli() - from.toEpochMilli()) / MS_PER_MONTH));
        return new Period(from, to, months);
    }

    private static Instant nonEmpty(String raw) {
        return raw == null || raw.isEmpty() ? null : ErpDates.tryInstant(raw);
    }

    /** Akumulator biaya & jumlah WO. */
    static final class Acc {
        BigDecimal labor = BigDecimal.ZERO;
        BigDecimal external = BigDecimal.ZERO;
        BigDecimal parts = BigDecimal.ZERO;
        BigDecimal laborHours = BigDecimal.ZERO;
        int count;
        int preventive;
        int corrective;
        int late;

        void add(Map<String, Object> w) {
            labor = labor.add(dec(w.get("labor")));
            external = external.add(dec(w.get("external")));
            parts = parts.add(dec(w.get("parts")));
            laborHours = laborHours.add(dec(w.get("labor_hours")));
            count++;
            if ("PREVENTIVE".equals(w.get("type"))) {
                preventive++;
            } else {
                corrective++;
            }
            if (Boolean.TRUE.equals(w.get("was_late"))) {
                late++;
            }
        }

        BigDecimal total() {
            return labor.add(external).add(parts);
        }

        void put(Map<String, Object> m) {
            m.put("labor", labor);
            m.put("external", external);
            m.put("parts", parts);
            m.put("total", total());
            m.put("workOrders", count);
            m.put("preventive", preventive);
            m.put("corrective", corrective);
            m.put("late", late);
        }
    }

    private static BigDecimal dec(Object v) {
        return v == null ? BigDecimal.ZERO : v instanceof BigDecimal b ? b : new BigDecimal(v.toString());
    }

    private static LocalDateTime utc(Instant i) {
        return LocalDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    @ErpTx(readOnly = true)
    public Map<String, Object> report(String fromRaw, String toRaw, Instant now) {
        Period period = parsePeriod(fromRaw, toRaw);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT w.id, w.asset_id, w.type, w.was_late, w.completed_at, w.reported_at, "
                        + "coalesce(w.labor_cost, 0) AS labor, coalesce(w.external_cost, 0) AS external, "
                        + "coalesce(w.labor_hours, 0) AS labor_hours, coalesce(p.parts, 0) AS parts, "
                        + "a.code, a.name, a.status AS asset_status, c.name AS category "
                        + "FROM ga_work_orders w JOIN ga_assets a ON a.id = w.asset_id "
                        + "LEFT JOIN ga_asset_categories c ON c.id = a.category_id "
                        + "LEFT JOIN (SELECT work_order_id, sum(quantity * unit_cost) AS parts FROM ga_work_order_parts "
                        + "GROUP BY work_order_id) p ON p.work_order_id = w.id "
                        + "WHERE w.status IN ('DONE', 'CLOSED') AND w.completed_at >= ? AND w.completed_at <= ? "
                        + "ORDER BY w.completed_at",
                utc(period.from()), utc(period.to()));

        Acc totals = new Acc();
        Map<String, Acc> byAsset = new LinkedHashMap<>();
        Map<String, Map<String, Object>> assetInfo = new LinkedHashMap<>();
        Map<String, Acc> byMonth = new LinkedHashMap<>();
        LocalDate cursor = period.from().atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1);
        LocalDate lastMonth = period.to().atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1);
        while (!cursor.isAfter(lastMonth)) {
            byMonth.put(month(cursor), new Acc());
            cursor = cursor.plusMonths(1);
        }
        double resolutionHours = 0;
        int resolved = 0;
        for (Map<String, Object> w : rows) {
            totals.add(w);
            String assetId = (String) w.get("asset_id");
            byAsset.computeIfAbsent(assetId, k -> new Acc()).add(w);
            Timestamp completed = (Timestamp) w.get("completed_at");
            assetInfo.computeIfAbsent(assetId, k -> new LinkedHashMap<>());
            Map<String, Object> info = assetInfo.get(assetId);
            info.put("code", w.get("code"));
            info.put("name", w.get("name"));
            info.put("category", w.get("category"));
            info.put("status", w.get("asset_status"));
            info.put("lastCompletedAt", completed.toLocalDateTime().toInstant(ZoneOffset.UTC));
            Acc m = byMonth.get(month(completed.toLocalDateTime().toLocalDate()));
            if (m != null) {
                m.add(w);
            }
            Timestamp reported = (Timestamp) w.get("reported_at");
            if (reported != null && !completed.before(reported)) {
                resolutionHours += (completed.getTime() - reported.getTime()) / 3_600_000d;
                resolved++;
            }
        }

        List<Map<String, Object>> assets = new ArrayList<>();
        byAsset.forEach((id, acc) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("assetId", id);
            m.putAll(assetInfo.get(id));
            acc.put(m);
            assets.add(m);
        });
        assets.sort(Comparator.comparing((Map<String, Object> m) -> (BigDecimal) m.get("total")).reversed());

        List<Map<String, Object>> trend = new ArrayList<>();
        byMonth.forEach((month, acc) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("month", month);
            acc.put(m);
            trend.add(m);
        });

        Long openOverdue = jdbc.queryForObject("SELECT count(*) FROM ga_work_orders WHERE status IN ('OPEN', 'ASSIGNED', 'IN_PROGRESS') "
                + "AND due_at IS NOT NULL AND due_at < ?", Long.class, utc(now));

        Map<String, Object> sum = new LinkedHashMap<>();
        totals.put(sum);
        sum.put("laborHours", totals.laborHours);
        sum.put("assetCount", assets.size());
        sum.put("lateRate", totals.count == 0 ? 0 : ratio(totals.late, totals.count));
        sum.put("preventiveRate", totals.count == 0 ? 0 : ratio(totals.preventive, totals.count));
        sum.put("avgResolutionHours", resolved == 0 ? null : BigDecimal.valueOf(resolutionHours / resolved).setScale(1, RoundingMode.HALF_UP));
        sum.put("costPerMonth", totals.total().divide(BigDecimal.valueOf(period.months()), 0, RoundingMode.HALF_UP));
        sum.put("openOverdue", openOverdue == null ? 0 : openOverdue);

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("from", period.from());
        p.put("to", period.to());
        p.put("months", period.months());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("period", p);
        out.put("totals", sum);
        out.put("monthlyTrend", trend);
        out.put("assets", assets);
        return out;
    }

    private static BigDecimal ratio(int part, int whole) {
        return BigDecimal.valueOf(part).divide(BigDecimal.valueOf(whole), 4, RoundingMode.HALF_UP);
    }

    private static String month(LocalDate d) {
        return d.getYear() + "-" + String.format("%02d", d.getMonthValue());
    }
}
