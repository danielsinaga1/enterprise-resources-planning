package id.enterpriseapps.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import id.enterpriseapps.entity.AppSettingEntity;

public interface AppSettingRepository extends JpaRepository<AppSettingEntity, Long> {

    Optional<AppSettingEntity> findBySettingKey(String settingKey);
}
