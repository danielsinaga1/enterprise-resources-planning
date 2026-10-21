package id.enterpriseapps.erp.eam.asset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.enterpriseapps.erp.core.ErpDates;
import id.enterpriseapps.erp.core.ErpJson;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.entity.GaBuildingData;
import id.enterpriseapps.erp.entity.GaBuildingOwnership;
import id.enterpriseapps.erp.entity.GaBuildingRental;
import id.enterpriseapps.erp.entity.GaBuildingRentalArah;
import id.enterpriseapps.erp.entity.GaBuildingRentalPeriode;
import id.enterpriseapps.erp.entity.GaBuildingRentalStatus;
import id.enterpriseapps.erp.entity.GaBuildingType;
import id.enterpriseapps.erp.entity.GaDocument;
import id.enterpriseapps.erp.entity.GaLandCertificateType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Data bangunan (1-1 per aset, di-upsert) dan kontrak sewa (1-n) — port {@code routes/ga/building.ts}.
 */
@Service
public class ErpGaBuildingService {

    static final String[] BUILDING_TYPES = {"KANTOR", "GUDANG", "PABRIK", "RUMAH", "MESS", "RUKO", "LAINNYA"};
    static final String[] OWNERSHIP = {"MILIK_SENDIRI", "SEWA_MASUK", "SEWA_KELUAR", "PINJAM_PAKAI"};
    static final String[] LAND_CERT_TYPES = {"HM", "HGB", "HGU", "HP_AKTA", "GIRIK", "BELUM_SERTIFIKAT"};
    static final String[] RENTAL_ARAH = {"SEWA_MASUK", "SEWA_KELUAR"};
    static final String[] RENTAL_PERIODE = {"BULANAN", "TAHUNAN"};

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    // ───────────────────────────── data bangunan ─────────────────────────────

    @ErpTx(readOnly = true)
    public JsonNode buildingData(String assetId) {
        List<GaBuildingData> rows = em.createQuery("select b from GaBuildingData b where b.assetId = :id",
                GaBuildingData.class).setParameter("id", assetId).getResultList();
        return rows.isEmpty() ? NullNode.getInstance() : GaJs.scalars(rows.get(0));
    }

    /** Upsert {@code BuildingDataSchema}: field opsional yang absen ditulis null (create & update sama). */
    @ErpTx
    public ObjectNode saveBuildingData(String assetId, JsonNode body) {
        GaZod z = GaZod.of(body, false);
        String jenis = z.enumValue("jenis", null, false, false, BUILDING_TYPES);
        String ownership = z.enumValue("ownership", "MILIK_SENDIRI", false, false, OWNERSHIP);
        BigDecimal luasTanah = z.number("luasTanah", true, false);
        BigDecimal luasBangunan = z.number("luasBangunan", true, false);
        Integer jumlahLantai = z.integer("jumlahLantai", true, null, null, false);
        Integer tahunBangun = z.integer("tahunBangun", false, 1800, 2100, false);
        Integer tahunRenovasi = z.integer("tahunRenovasi", false, 1800, 2100, false);
        String alamatLengkap = z.optString("alamatLengkap", 500);
        String kelurahan = z.optString("kelurahan", 100);
        String kecamatan = z.optString("kecamatan", 100);
        String kota = z.optString("kota", 100);
        String provinsi = z.optString("provinsi", 100);
        String kodePos = z.optString("kodePos", 10);
        String certificateType = z.enumValue("certificateType", null, true, true, LAND_CERT_TYPES);
        String certificateNumber = z.optString("certificateNumber", 100);
        BigDecimal njopTanah = z.number("njopTanah", true, false);
        BigDecimal njopBangunan = z.number("njopBangunan", true, false);
        String notes = z.optString("notes", null);
        z.throwIfInvalid();

        List<GaBuildingData> rows = em.createQuery("select b from GaBuildingData b where b.assetId = :id",
                GaBuildingData.class).setParameter("id", assetId).getResultList();
        GaBuildingData b = rows.isEmpty() ? new GaBuildingData() : rows.get(0);
        b.setAssetId(assetId);
        b.setJenis(GaBuildingType.valueOf(jenis));
        b.setOwnership(GaBuildingOwnership.valueOf(ownership));
        b.setLuasTanah(luasTanah);
        b.setLuasBangunan(luasBangunan);
        b.setJumlahLantai(jumlahLantai);
        b.setTahunBangun(tahunBangun);
        b.setTahunRenovasi(tahunRenovasi);
        b.setAlamatLengkap(alamatLengkap);
        b.setKelurahan(kelurahan);
        b.setKecamatan(kecamatan);
        b.setKota(kota);
        b.setProvinsi(provinsi);
        b.setKodePos(kodePos);
        b.setCertificateType(certificateType == null ? null : GaLandCertificateType.valueOf(certificateType));
        b.setCertificateNumber(certificateNumber);
        b.setNjopTanah(njopTanah);
        b.setNjopBangunan(njopBangunan);
        b.setNotes(notes);
        if (rows.isEmpty()) {
            em.persist(b);
        }
        em.flush();
        return GaJs.scalars(b);
    }

    // ───────────────────────────── sewa ─────────────────────────────

    @ErpTx(readOnly = true)
    public ArrayNode rentals(String assetId) {
        ArrayNode out = ErpJson.arrayNode();
        for (GaBuildingRental r : em.createQuery("select r from GaBuildingRental r left join fetch r.document "
                        + "where r.assetId = :id order by r.status asc, r.tanggalMulai desc", GaBuildingRental.class)
                .setParameter("id", assetId).getResultList()) {
            ObjectNode o = GaJs.scalars(r);
            o.set("document", GaJs.pick(r.getDocument(), "id", "code", "title"));
            out.add(o);
        }
        return out;
    }

    private record RentalInput(GaZod z, String arah, String pihak, String kontak, BigDecimal nilaiSewa,
                               String periode, String tanggalMulai, String tanggalAkhir, String documentId,
                               String notes) {
    }

    private static RentalInput parseRental(JsonNode body, boolean partial) {
        GaZod z = GaZod.of(body, partial);
        String arah = z.enumValue("arah", null, false, false, RENTAL_ARAH);
        String pihak = z.requiredString("pihak", 1, null, 200);
        String kontak = z.optString("kontak", 100);
        BigDecimal nilaiSewa = z.number("nilaiSewa", true, true);
        String periode = z.enumValue("periode", null, false, false, RENTAL_PERIODE);
        String tanggalMulai = z.requiredString("tanggalMulai", 1, null, null);
        String tanggalAkhir = z.requiredString("tanggalAkhir", 1, null, null);
        String documentId = z.optString("documentId", null);
        String notes = z.optString("notes", null);
        z.throwIfInvalid();
        return new RentalInput(z, arah, pihak, kontak, nilaiSewa, periode, tanggalMulai, tanggalAkhir, documentId, notes);
    }

    @ErpTx
    public ObjectNode createRental(String assetId, JsonNode body, String userId) {
        RentalInput in = parseRental(body, false);
        GaBuildingRental r = new GaBuildingRental();
        r.setAssetId(assetId);
        r.setArah(GaBuildingRentalArah.valueOf(in.arah()));
        r.setPihak(in.pihak());
        r.setKontak(in.kontak());
        r.setNilaiSewa(in.nilaiSewa());
        r.setPeriode(GaBuildingRentalPeriode.valueOf(in.periode()));
        r.setTanggalMulai(ErpDates.toInstant(in.tanggalMulai()));
        r.setTanggalAkhir(ErpDates.toInstant(in.tanggalAkhir()));
        r.setDocumentId(in.documentId());
        r.setNotes(in.notes());
        r.setCreatedById(userId);
        em.persist(r);
        em.flush();
        return GaJs.scalars(r);
    }

    private GaBuildingRental requireRental(String rid) {
        GaBuildingRental r = em.find(GaBuildingRental.class, rid);
        if (r == null) {
            throw GaJs.p2025();
        }
        return r;
    }

    /** Hanya field yang dikirim (dan truthy untuk enum/teks wajib) yang diubah, persis seperti sumber. */
    @ErpTx
    public ObjectNode updateRental(String rid, JsonNode body) {
        RentalInput in = parseRental(body, true);
        GaBuildingRental r = requireRental(rid);
        if (in.arah() != null) {
            r.setArah(GaBuildingRentalArah.valueOf(in.arah()));
        }
        if (in.pihak() != null && !in.pihak().isEmpty()) {
            r.setPihak(in.pihak());
        }
        if (in.z().has("kontak")) {
            r.setKontak(in.kontak());
        }
        if (in.nilaiSewa() != null) {
            r.setNilaiSewa(in.nilaiSewa());
        }
        if (in.periode() != null) {
            r.setPeriode(GaBuildingRentalPeriode.valueOf(in.periode()));
        }
        if (in.tanggalMulai() != null && !in.tanggalMulai().isEmpty()) {
            r.setTanggalMulai(ErpDates.toInstant(in.tanggalMulai()));
        }
        if (in.tanggalAkhir() != null && !in.tanggalAkhir().isEmpty()) {
            r.setTanggalAkhir(ErpDates.toInstant(in.tanggalAkhir()));
        }
        if (in.z().has("documentId")) {
            String docId = in.documentId();
            if (docId != null && !docId.isEmpty()) {
                // connect ke dokumen yang tidak ada → P2025 Prisma.
                if (em.find(GaDocument.class, docId) == null) {
                    throw GaJs.p2025();
                }
                r.setDocumentId(docId);
            } else {
                r.setDocumentId(null);
            }
        }
        if (in.z().has("notes")) {
            r.setNotes(in.notes());
        }
        em.flush();
        return GaJs.scalars(r);
    }

    @ErpTx
    public ObjectNode endRental(String rid) {
        GaBuildingRental r = requireRental(rid);
        r.setStatus(GaBuildingRentalStatus.BERAKHIR);
        em.flush();
        return GaJs.scalars(r);
    }

    @ErpTx
    public void deleteRental(String rid) {
        em.remove(requireRental(rid));
        em.flush();
    }
}
