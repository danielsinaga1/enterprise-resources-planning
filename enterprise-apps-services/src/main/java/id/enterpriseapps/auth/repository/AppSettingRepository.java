package id.enterpriseapps.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import id.enterpriseapps.auth.entity.AppSettingEntity;

public interface AppSettingRepository extends JpaRepository<AppSettingEntity, Long> {

    Optional<AppSettingEntity> findBySettingKey(String settingKey);
}
