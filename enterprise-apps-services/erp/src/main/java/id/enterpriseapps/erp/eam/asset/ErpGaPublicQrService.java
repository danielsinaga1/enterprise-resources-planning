package id.enterpriseapps.erp.eam.asset;

import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.storage.ErpFileStorage;
import id.enterpriseapps.erp.entity.GaAsset;
import id.enterpriseapps.erp.entity.GaAssetPhoto;
import id.enterpriseapps.erp.entity.GaAssetQr;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lookup QR publik tanpa login — port {@code routes/ga/publicQr.ts}. Hanya field non-sensitif yang dibuka
 * (TIDAK pernah acquisitionCost, customFields, PIC, atau catatan internal); proxy foto hanya melayani foto
 * milik aset yang dipindai.
 */
@Service
@RequiredArgsConstructor
public class ErpGaPublicQrService {

    /** Prefix URL foto publik (sumber: {@code /api/ga/public}). */
    static final String PUBLIC_BASE = "/api/erp/ga/public/";

    private final ErpFileStorage storage;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    @ErpTx(readOnly = true)
    public Map<String, Object> info(String qrCode) {
        List<GaAssetQr> rows = em.createQuery("select q from GaAssetQr q join fetch q.asset a "
                        + "left join fetch a.category left join fetch a.branch where q.qrCode = :c", GaAssetQr.class)
                .setParameter("c", qrCode).getResultList();
        if (rows.isEmpty() || rows.get(0).getAsset() == null) {
            throw ErpException.notFound("Aset tidak ditemukan.");
        }
        GaAssetQr qr = rows.get(0);
        GaAsset a = qr.getAsset();
        List<Map<String, Object>> photos = new ArrayList<>();
        for (Object[] p : em.createQuery("select p.id, p.caption from GaAssetPhoto p where p.assetId = :id "
                        + "order by p.orderIdx asc, p.createdAt asc", Object[].class)
                .setParameter("id", a.getId()).getResultList()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p[0]);
            m.put("url", PUBLIC_BASE + qr.getQrCode() + "/photos/" + p[0] + ".jpg");
            m.put("caption", p[1]);
            photos.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", a.getCode());
        out.put("name", a.getName());
        out.put("category", a.getCategory() == null ? null : a.getCategory().getName());
        out.put("categoryCode", a.getCategory() == null ? null : a.getCategory().getCode());
        out.put("branch", a.getBranch() == null ? null : a.getBranch().getName());
        out.put("status", a.getStatus());
        out.put("condition", a.getCondition());
        out.put("location", a.getLocation());
        out.put("photos", photos);
        return out;
    }

    /** Path berkas foto bila foto itu milik aset QR ini dan berkasnya ada; null bila tidak. */
    @ErpTx(readOnly = true)
    public Path photo(String qrCode, String photoId) {
        List<String> assetIds = em.createQuery("select q.assetId from GaAssetQr q where q.qrCode = :c", String.class)
                .setParameter("c", qrCode).getResultList();
        if (assetIds.isEmpty()) {
            return null;
        }
        List<GaAssetPhoto> photos = em.createQuery("select p from GaAssetPhoto p where p.id = :id and p.assetId = :a",
                        GaAssetPhoto.class)
                .setParameter("id", photoId).setParameter("a", assetIds.get(0)).getResultList();
        if (photos.isEmpty()) {
            return null;
        }
        return storage.resolvePublic(photos.get(0).getFilePath());
    }
}
