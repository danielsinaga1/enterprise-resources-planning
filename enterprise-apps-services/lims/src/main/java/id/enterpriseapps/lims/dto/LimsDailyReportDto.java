package id.enterpriseapps.lims.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LimsDailyReportDto {
    private Long id;

    @NotBlank(message = "Name is required")
    private String name;

    private String description;
    private LocalDate dueDate;
    private String location;
    private String status;
    private Long reviewedById;
    private String reviewedByName;
    private String reviewedAt;
    private Long acknowledgedById;
    private String acknowledgedByName;
    private String acknowledgedAt;
    private Long assignedToId;
    private Long unitId;
    private String unitName;
    private Long parameterId;
    private String parameterName;
    private String createdAt;
    private String updatedAt;
}

