package id.neravoxa.erp.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

/** Tabel payment_request_history. (Dibangkitkan dari model Prisma PaymentRequestHistory.) */
@Entity
@Table(name = "payment_request_history")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class PaymentRequestHistory {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "payment_request_id", nullable = false)
    private String paymentRequestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_request_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"history", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private PaymentRequest paymentRequest;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "actor_id", nullable = false)
    private String actorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"paymentRequestHistory", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private User actor;

    @Column(name = "note")
    private String note;

    @Column(name = "from_status")
    private String fromStatus;

    @Column(name = "to_status")
    private String toStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

}
