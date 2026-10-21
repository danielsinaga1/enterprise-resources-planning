package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.CashBookLearnedMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository CashBookLearnedMap (tabel cash_book_learned_maps). Dibangkitkan; query khusus taruh di paket domain. */
public interface CashBookLearnedMapRepository extends JpaRepository<CashBookLearnedMap, String>, JpaSpecificationExecutor<CashBookLearnedMap> {
}
