package id.enterpriseapps.lims.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LimsAuditLogDto {
    private Long id;
    private String entityType;
    private Long entityId;
    /** CREATE | UPDATE | DELETE | STATUS_CHANGE */
    private String action;
    private String fieldName;
    private String oldValue;
    private String newValue;
    private String reason;
    private Long actorId;
    private String actorName;
    private LocalDateTime createdAt;
}
