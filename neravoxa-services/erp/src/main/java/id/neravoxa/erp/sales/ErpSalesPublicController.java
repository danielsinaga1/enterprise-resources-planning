package id.neravoxa.erp.sales;

import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.entity.Inquiry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Rute publik TANPA login — port {@code routes/public.ts} (mount {@code /api/public}). Path ini
 * dikecualikan dari ErpRouteGuard/audit dan di-whitelist di security & gateway platform.
 */
@Slf4j
@RestController
@RequestMapping("/api/erp/public")
@RequiredArgsConstructor
@Tag(name = "ERP - Publik", description = "Branding perusahaan & form inquiry publik (tanpa login)")
public class ErpSalesPublicController {

    static final String RATE_LIMIT_MESSAGE = "Terlalu banyak permintaan dari IP ini. Coba lagi dalam 1 jam.";

    private final ErpPublicInquiryService publicService;
    private final ErpPublicInquiryRateLimiter limiter;
    private final ErpSalesRequestValidator validator;

    /** Branding perusahaan untuk halaman login & permukaan pra-login lain. Gagal → semua null (tetap 200). */
    @GetMapping("/branding")
    @Operation(summary = "Branding perusahaan (publik)")
    public Map<String, Object> branding() {
        try {
            return publicService.branding();
        } catch (RuntimeException e) {
            log.error("GET /public/branding error", e);
            return ErpPublicInquiryService.brandingMap(null, null, null, null, null);
        }
    }

    /**
     * Form inquiry swalayan untuk prospek/customer; tiap kiriman masuk pipeline Inquiry berstatus
     * 'Baru'. Anti-spam: honeypot + rate limit ketat per IP (5/jam). Rate limit dijalankan sebelum
     * validasi seperti middleware express-rate-limit.
     */
    @PostMapping("/inquiry")
    @Operation(summary = "Kirim inquiry dari form publik")
    public Map<String, Object> inquiry(@RequestBody(required = false) PublicInquiryRequest body,
                                       HttpServletRequest request, HttpServletResponse response) {
        String ip = limiter.clientIp(request);
        ErpPublicInquiryRateLimiter.Hit hit = limiter.hit(ip);
        // standardHeaders: true (draft-6), legacyHeaders: false.
        response.setHeader("RateLimit-Policy", ErpPublicInquiryRateLimiter.MAX + ";w=" + ErpPublicInquiryRateLimiter.WINDOW.toSeconds());
        response.setHeader("RateLimit-Limit", String.valueOf(ErpPublicInquiryRateLimiter.MAX));
        response.setHeader("RateLimit-Remaining", String.valueOf(hit.remaining()));
        response.setHeader("RateLimit-Reset", String.valueOf(hit.resetSeconds()));
        if (hit.blocked()) {
            response.setHeader("Retry-After", String.valueOf(hit.resetSeconds()));
            throw ErpException.of(HttpStatus.TOO_MANY_REQUESTS, RATE_LIMIT_MESSAGE);
        }

        PublicInquiryRequest data = body != null ? body
                : new PublicInquiryRequest(null, null, null, null, null, null);
        validator.check(data);

        // Honeypot berisi → bot. Balas 200 agar bot tidak mempelajari jebakannya.
        if (data.website() != null && !data.website().isEmpty()) {
            log.warn("Honeypot triggered on /public/inquiry — silently dropped ip={}", ip);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("success", true);
            out.put("message", "Terima kasih. Kami akan menghubungi Anda.");
            return out;
        }

        Inquiry result;
        try {
            result = publicService.create(data, ip);
        } catch (RuntimeException e) {
            // Sumber: semua kegagalan (termasuk tahun fiskal tertutup) → 500 dengan pesan ini.
            log.error("POST /public/inquiry error", e);
            throw ErpException.of(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Gagal menyimpan permintaan. Silakan coba lagi atau hubungi kami langsung.");
        }
        log.info("Public inquiry created inquiryNumber={} channel={}", result.getInquiryNumber(), data.channelOrDefault());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("inquiryNumber", result.getInquiryNumber());
        out.put("message", "Permintaan Anda telah kami terima. Tim sales akan menghubungi Anda secepatnya.");
        return out;
    }
}
