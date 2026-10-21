package id.enterpriseapps.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Bentuknya sengaja dibuat sama persis dengan GeneralSettings di frontend
 * (useGeneralSettings.ts) supaya bisa dipakai langsung tanpa pemetaan.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneralSettingsDto {
    private String siteName;
    private String siteDesc;
    private String email;
    private String phone;
    private String address;
}
