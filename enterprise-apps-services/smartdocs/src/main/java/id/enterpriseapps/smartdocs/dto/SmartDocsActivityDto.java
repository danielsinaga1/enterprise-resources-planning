package id.enterpriseapps.smartdocs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/** Satu baris jejak aktivitas. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmartDocsActivityDto {
    private Integer id;
    private String action;
    private String username;
    private Integer documentId;
    private OffsetDateTime timestamp;
}
