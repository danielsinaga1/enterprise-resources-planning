package id.enterpriseapps.erp.eam.asset;

import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.entity.GaAsset;
import jakarta.persistence.EntityManager;

import java.sql.SQLException;
import java.util.List;

/**
 * Port {@code services/fixedAssetGaLink.ts}: tautan aset tetap (akuntansi) ↔ aset EAM (GaAsset). Satu aset
 * tetap per aset EAM: {@code fixed_assets.ga_asset_id} unik, dan duplikat ditolak dengan pesan jelas sebelum
 * menulis (indeks unik tetap menjaga request paralel → {@link #CONFLICT_MESSAGE}).
 */
final class FixedAssetGaLink {

    static final String CONFLICT_MESSAGE =
            "Aset EAM ini sudah ditautkan ke aset tetap lain. Satu aset EAM hanya boleh punya satu aset tetap.";

    private FixedAssetGaLink() {
    }

    /** Hasil: {@code present=false} = biarkan (field tidak dikirim); selain itu id (null = lepas tautan). */
    record Link(boolean present, String gaAssetId) {
    }

    /**
     * Validasi tautan yang diminta. {@code present=false} = tidak diubah, '' / null = lepas tautan.
     * {@code selfId} = aset tetap yang sedang diedit (tautannya sendiri bukan duplikat).
     */
    static Link resolve(EntityManager em, boolean present, String gaAssetId, String selfId) {
        if (!present) {
            return new Link(false, null);
        }
        String id = gaAssetId == null || gaAssetId.strip().isEmpty() ? null : gaAssetId.strip();
        if (id == null) {
            return new Link(true, null);
        }
        if (em.find(GaAsset.class, id) == null) {
            throw ErpException.business("Aset EAM yang dipilih tidak ditemukan.");
        }
        List<Object[]> taken;
        if (selfId != null) {
            taken = em.createQuery("select f.assetNumber, f.name from FixedAsset f where f.gaAssetId = :id "
                            + "and f.id <> :self", Object[].class)
                    .setParameter("id", id).setParameter("self", selfId).setMaxResults(1).getResultList();
        } else {
            taken = em.createQuery("select f.assetNumber, f.name from FixedAsset f where f.gaAssetId = :id",
                            Object[].class)
                    .setParameter("id", id).setMaxResults(1).getResultList();
        }
        if (!taken.isEmpty()) {
            throw ErpException.conflict("Aset EAM ini sudah ditautkan ke aset tetap " + taken.get(0)[0] + " ("
                    + taken.get(0)[1] + "). Satu aset EAM hanya boleh punya satu aset tetap.");
        }
        return new Link(true, id);
    }

    /** True bila penulisan gagal karena indeks unik ga_asset_id (tautan paralel). */
    static boolean isConflict(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof SQLException s && "23505".equals(s.getSQLState())
                    && String.valueOf(s.getMessage()).contains("ga_asset_id")) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }
}
