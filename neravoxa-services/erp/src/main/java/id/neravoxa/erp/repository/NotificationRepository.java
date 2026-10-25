package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository Notification (tabel notifications). Dibangkitkan; query khusus taruh di paket domain. */
public interface NotificationRepository extends JpaRepository<Notification, String>, JpaSpecificationExecutor<Notification> {
}
