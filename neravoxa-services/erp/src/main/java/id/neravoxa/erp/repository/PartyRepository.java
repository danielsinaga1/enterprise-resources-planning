package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.Party;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository Party (tabel parties). Dibangkitkan; query khusus taruh di paket domain. */
public interface PartyRepository extends JpaRepository<Party, String>, JpaSpecificationExecutor<Party> {
}
