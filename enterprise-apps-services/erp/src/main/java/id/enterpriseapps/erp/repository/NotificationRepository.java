package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository Notification (tabel notifications). Dibangkitkan; query khusus taruh di paket domain. */
public interface NotificationRepository extends JpaRepository<Notification, String>, JpaSpecificationExecutor<Notification> {
}
