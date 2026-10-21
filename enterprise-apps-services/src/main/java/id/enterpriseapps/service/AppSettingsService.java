package id.enterpriseapps.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import id.enterpriseapps.dto.GeneralSettingsDto;
import id.enterpriseapps.entity.AppSettingEntity;
import id.enterpriseapps.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sumber kebenaran untuk Pengaturan Umum. Dipakai UI (lewat REST) dan
 * EmailService (untuk branding template email).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppSettingsService {

    /** Dipakai juga sebagai cadangan saat DB tidak terbaca — lihat getSiteName(). */
    public static final String DEFAULT_SITE_NAME = "IRMS";
    public static final String DEFAULT_SITE_DESC = "Integrated Report Management System";

    private static final String KEY_SITE_NAME = "site_name";
    private static final String KEY_SITE_DESC = "site_desc";
    private static final String KEY_EMAIL = "site_email";
    private static final String KEY_PHONE = "site_phone";
    private static final String KEY_ADDRESS = "site_address";

    private final AppSettingRepository appSettingRepository;

    public GeneralSettingsDto getGeneralSettings() {
        return GeneralSettingsDto.builder()
                .siteName(readValue(KEY_SITE_NAME, DEFAULT_SITE_NAME))
                .siteDesc(readValue(KEY_SITE_DESC, DEFAULT_SITE_DESC))
                .email(readValue(KEY_EMAIL, ""))
                .phone(readValue(KEY_PHONE, ""))
                .address(readValue(KEY_ADDRESS, ""))
                .build();
    }

    @Transactional(transactionManager = "transactionManager")
    public GeneralSettingsDto saveGeneralSettings(GeneralSettingsDto request, String updatedBy) {
        writeValue(KEY_SITE_NAME, request.getSiteName(), updatedBy);
        writeValue(KEY_SITE_DESC, request.getSiteDesc(), updatedBy);
        writeValue(KEY_EMAIL, request.getEmail(), updatedBy);
        writeValue(KEY_PHONE, request.getPhone(), updatedBy);
        writeValue(KEY_ADDRESS, request.getAddress(), updatedBy);

        log.info("General settings updated by {}", updatedBy);
        return getGeneralSettings();
    }

    /**
     * Nama aplikasi untuk template email. Sengaja menelan error: pengaturan yang
     * gagal dibaca tidak boleh membatalkan pengiriman notifikasi.
     */
    public String getSiteName() {
        try {
            return readValue(KEY_SITE_NAME, DEFAULT_SITE_NAME);
        } catch (Exception e) {
            log.warn("Gagal membaca site_name, memakai default '{}': {}", DEFAULT_SITE_NAME, e.getMessage());
            return DEFAULT_SITE_NAME;
        }
    }

    /**
     * Deskripsi aplikasi untuk template email. Fail-safe seperti getSiteName().
     */
    public String getSiteDesc() {
        try {
            return readValue(KEY_SITE_DESC, DEFAULT_SITE_DESC);
        } catch (Exception e) {
            log.warn("Gagal membaca site_desc, memakai default: {}", e.getMessage());
            return DEFAULT_SITE_DESC;
        }
    }

    /** Baris yang belum ada atau bernilai kosong dianggap "belum diisi". */
    private String readValue(String key, String fallback) {
        return appSettingRepository.findBySettingKey(key)
                .map(AppSettingEntity::getSettingValue)
                .filter(value -> !value.isBlank())
                .orElse(fallback);
    }

    private void writeValue(String key, String value, String updatedBy) {
        if (value == null) {
            return;
        }

        Optional<AppSettingEntity> existing = appSettingRepository.findBySettingKey(key);
        AppSettingEntity setting = existing.orElseGet(() -> {
            AppSettingEntity created = new AppSettingEntity();
            created.setSettingKey(key);
            created.setCreatedAt(LocalDateTime.now());
            return created;
        });

        setting.setSettingValue(value);
        setting.setUpdatedBy(updatedBy);
        setting.setUpdatedAt(LocalDateTime.now());
        appSettingRepository.save(setting);
    }
}
