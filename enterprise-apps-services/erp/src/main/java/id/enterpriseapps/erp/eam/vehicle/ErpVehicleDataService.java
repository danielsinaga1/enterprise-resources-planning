package id.enterpriseapps.erp.eam.vehicle;

import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.entity.GaAsset;
import id.enterpriseapps.erp.entity.GaVehicleData;
import id.enterpriseapps.erp.entity.GaVehicleFuelType;
import id.enterpriseapps.erp.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data kendaraan (1-1 per aset, via {@code /assets/:id}) dan penugasan driver. Driver disimpan di
 * {@code GaAsset.picUserId} generik, diekspos di sini karena driver adalah konsep kendaraan.
 */
@Service
public class ErpVehicleDataService {

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    private GaVehicleData findByAsset(String assetId) {
        List<GaVehicleData> rows = em.createQuery("select v from GaVehicleData v where v.assetId = :a", GaVehicleData.class)
                .setParameter("a", assetId).getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    @ErpTx(readOnly = true)
    public GaVehicleData get(String assetId) {
        return findByAsset(assetId);
    }

    /** Upsert data kendaraan; field yang tidak dikirim dikosongkan (sama seperti sumber). */
    @ErpTx
    public GaVehicleData upsert(String assetId, VehicleRequests.VehicleData in) {
        GaVehicleData v = findByAsset(assetId);
        boolean create = v == null;
        if (create) {
            v = new GaVehicleData();
            v.setAssetId(assetId);
        }
        v.setNoPlat(in.noPlat());
        v.setBrand(in.brand());
        v.setTipe(in.tipe());
        v.setTahunProduksi(in.tahunProduksi());
        v.setNoMesin(in.noMesin());
        v.setNoRangka(in.noRangka());
        v.setJenisBBM(in.jenisBBM() == null ? null : GaVehicleFuelType.valueOf(in.jenisBBM()));
        v.setKapasitasTangki(in.kapasitasTangki());
        v.setWarna(in.warna());
        v.setOdometerSaatBeli(in.odometerSaatBeli());
        v.setOdometerTerakhir(in.odometerTerakhir());
        v.setNotes(in.notes());
        if (create) {
            em.persist(v);
        }
        em.flush();
        String id = v.getId();
        em.clear();
        return em.find(GaVehicleData.class, id);
    }

    /** Tugaskan/lepas driver → {@code { id, picUserId, picName }}. */
    @ErpTx
    public Map<String, Object> assignDriver(String assetId, String picUserId) {
        GaAsset asset = em.find(GaAsset.class, assetId);
        if (asset == null) {
            // prisma.gaAsset.update pada id yang tidak ada (P2025).
            throw new EntityNotFoundException();
        }
        asset.setPicUserId(picUserId);
        em.flush();
        String picName = null;
        if (asset.getPicUserId() != null) {
            User u = em.find(User.class, asset.getPicUserId());
            picName = u == null ? null : u.getFullName();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", asset.getId());
        out.put("picUserId", asset.getPicUserId());
        out.put("picName", picName);
        return out;
    }
}
