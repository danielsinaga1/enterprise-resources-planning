package id.neravoxa.erp.sales;

import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.accounting.ErpDocumentNumberService;
import id.neravoxa.erp.core.accounting.ErpFiscalYearService;
import id.neravoxa.erp.core.security.ErpPermissionService;
import id.neravoxa.erp.entity.FiscalYear;
import id.neravoxa.erp.entity.Inquiry;
import id.neravoxa.erp.entity.InquiryChannel;
import id.neravoxa.erp.entity.InquiryStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Data untuk rute publik tanpa login — port {@code routes/public.ts}: branding perusahaan
 * (halaman login & form publik) dan pembuatan inquiry dari form swalayan.
 */
@Service
@RequiredArgsConstructor
public class ErpPublicInquiryService {

    private final ErpFiscalYearService fiscalYears;
    private final ErpDocumentNumberService numbers;
    private final ErpPermissionService permissions;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** {@code companySettings} slug default → companyName, logoUrl, logoUrlDark, phone, email (null bila tidak ada). */
    @ErpTx(readOnly = true)
    public Map<String, Object> branding() {
        List<Object[]> rows = em.createQuery(
                        "select c.companyName, c.logoUrl, c.logoUrlDark, c.phone, c.email from CompanySettings c "
                                + "where c.slug = 'default'", Object[].class)
                .setMaxResults(1).getResultList();
        Object[] r = rows.isEmpty() ? new Object[5] : rows.get(0);
        return brandingMap(r[0], r[1], r[2], r[3], r[4]);
    }

    static Map<String, Object> brandingMap(Object companyName, Object logoUrl, Object logoUrlDark, Object phone, Object email) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("companyName", companyName);
        out.put("logoUrl", logoUrl);
        out.put("logoUrlDark", logoUrlDark);
        out.put("phone", phone);
        out.put("email", email);
        return out;
    }

    /**
     * Buat inquiry status 'Baru' dari form publik (tanpa partyId — masih prospek). Tidak ada konteks
     * login, jadi {@code createdBy} diatribusikan ke admin aktif (role super-admin ERP, padanan
     * {@code role: 'Admin'} di sumber).
     */
    @ErpTx
    public Inquiry create(PublicInquiryRequest data, String ip) {
        Instant date = Instant.now();
        FiscalYear fy = fiscalYears.getOpenFiscalYear(date);
        String inquiryNumber = numbers.generate("INQ", date, fy.getId());
        String sysUserId = findAdminUserId();
        if (sysUserId == null) {
            throw new IllegalStateException("No Admin user available to attribute public inquiry to");
        }
        Inquiry inq = new Inquiry();
        inq.setInquiryNumber(inquiryNumber);
        inq.setDate(date);
        inq.setStatus(InquiryStatus.Baru);
        inq.setChannel(InquiryChannel.valueOf(data.channelOrDefault()));
        inq.setPartyId(null);
        inq.setProspectName(data.prospectName());
        inq.setProspectPhone(data.prospectPhone());
        inq.setProspectEmail(SalesSupport.orNull(data.prospectEmail()));
        inq.setItemsMode("freetext");
        inq.setItemsText(data.itemsText());
        inq.setNotes("Submitted via public form from IP " + ip);
        inq.setFiscalYearId(fy.getId());
        inq.setCreatedBy(sysUserId);
        em.persist(inq);
        SalesSupport.flush(em);
        return inq;
    }

    /** Pengguna aktif pertama dengan role super-admin ERP (role utama dulu, lalu daftar role). */
    private String findAdminUserId() {
        Set<String> keys = Set.copyOf(permissions.superAdminRoleKeys());
        if (keys.isEmpty()) {
            return null;
        }
        List<Object[]> users = em.createQuery(
                        "select u.id, u.role, u.roles from User u where u.isActive = true order by u.createdAt asc, u.id asc",
                        Object[].class)
                .getResultList();
        for (Object[] u : users) {
            if (keys.contains((String) u[1])) {
                return (String) u[0];
            }
        }
        for (Object[] u : users) {
            String csv = (String) u[2];
            if (csv != null && Arrays.stream(csv.split(",")).map(String::trim).anyMatch(keys::contains)) {
                return (String) u[0];
            }
        }
        return null;
    }
}
