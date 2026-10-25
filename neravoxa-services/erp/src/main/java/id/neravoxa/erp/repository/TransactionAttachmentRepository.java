package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.TransactionAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository TransactionAttachment (tabel transaction_attachments). Dibangkitkan; query khusus taruh di paket domain. */
public interface TransactionAttachmentRepository extends JpaRepository<TransactionAttachment, String>, JpaSpecificationExecutor<TransactionAttachment> {
}
