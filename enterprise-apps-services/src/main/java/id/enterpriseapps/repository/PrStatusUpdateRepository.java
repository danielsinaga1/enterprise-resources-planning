package id.enterpriseapps.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import id.enterpriseapps.entity.PrStatusUpdateEntity;

public interface PrStatusUpdateRepository extends JpaRepository<PrStatusUpdateEntity, Long> {

    List<PrStatusUpdateEntity> findByPurchaseRequisitionIdOrderByUpdateDateDesc(Long purchaseRequisitionId);

    Optional<PrStatusUpdateEntity> findByPurchaseRequisitionIdAndUpdateDate(
            Long purchaseRequisitionId, LocalDate updateDate);

    void deleteByPurchaseRequisitionId(Long purchaseRequisitionId);
}
