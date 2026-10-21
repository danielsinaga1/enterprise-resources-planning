package id.enterpriseapps.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import id.enterpriseapps.entity.InvestigationDetailEntity;

public interface InvestigationDetailRepository extends JpaRepository<InvestigationDetailEntity, Long> {
    List<InvestigationDetailEntity> findByIncidentReportId(long incidentReportId);
}

