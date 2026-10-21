package id.enterpriseapps.lims.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@MappedSuperclass
@Getter
@Setter
public abstract class LimsBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // Pelaku perubahan. "Kapan" saja tidak cukup untuk jejak audit lab: hasil
    // uji yang bisa diubah tanpa nama pemiliknya tidak bisa dipertanggung-
    // jawabkan. Diisi di service dari konteks keamanan (LimsAuditService
    // .currentActor()), bukan dari body request, supaya tidak bisa dipalsukan.
    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

