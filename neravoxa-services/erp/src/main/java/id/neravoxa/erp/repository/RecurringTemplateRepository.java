package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.RecurringTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository RecurringTemplate (tabel recurring_templates). Dibangkitkan; query khusus taruh di paket domain. */
public interface RecurringTemplateRepository extends JpaRepository<RecurringTemplate, String>, JpaSpecificationExecutor<RecurringTemplate> {
}
