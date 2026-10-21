package id.enterpriseapps.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import id.enterpriseapps.entity.LookupEntity;

public interface LookupRepository extends JpaRepository<LookupEntity, Long> {
     List<LookupEntity> findByType(String type);

     Optional<LookupEntity> findByTypeAndValue(String type, String value);
}

