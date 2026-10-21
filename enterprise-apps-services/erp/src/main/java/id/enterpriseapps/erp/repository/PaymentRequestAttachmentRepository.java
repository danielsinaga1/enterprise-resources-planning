package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.PaymentRequestAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository PaymentRequestAttachment (tabel payment_request_attachments). Dibangkitkan; query khusus taruh di paket domain. */
public interface PaymentRequestAttachmentRepository extends JpaRepository<PaymentRequestAttachment, String>, JpaSpecificationExecutor<PaymentRequestAttachment> {
}
