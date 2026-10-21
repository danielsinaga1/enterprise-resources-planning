package id.enterpriseapps.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "irms_chart_of_accounts")
@Data
public class ChartOfAccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_code", length = 50, nullable = false, unique = true)
    private String accountCode;

    @Column(name = "account_name", length = 191, nullable = false)
    private String accountName;

    @Column(name = "account_type", length = 50, nullable = false)
    private String accountType;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

