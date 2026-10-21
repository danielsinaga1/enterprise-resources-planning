package id.enterpriseapps.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "irms_user_user_alerts")
@Data
public class UserUserAlertEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_alert_id", nullable = false)
    private Long userAlertId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "`read`", nullable = false)
    private Boolean isRead = false;
}

