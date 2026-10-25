package id.neravoxa.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import id.neravoxa.auth.entity.AppSettingEntity;

public interface AppSettingRepository extends JpaRepository<AppSettingEntity, Long> {

    Optional<AppSettingEntity> findBySettingKey(String settingKey);
}
