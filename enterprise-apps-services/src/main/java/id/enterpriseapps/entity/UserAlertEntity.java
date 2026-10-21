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
@Table(name = "irms_user_alerts")
@Data
public class UserAlertEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_text", columnDefinition = "TEXT", nullable = false)
    private String alertText;

    @Column(name = "alert_link", length = 191)
    private String alertLink;

    @Column(name = "alert_type", length = 50)
    private String alertType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "reference_type", length = 100)
    private String referenceType;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

