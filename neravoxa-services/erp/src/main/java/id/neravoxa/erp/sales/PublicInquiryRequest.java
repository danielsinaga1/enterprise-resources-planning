package id.neravoxa.erp.sales;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Port {@code PublicInquirySchema} (routes/public.ts) — form inquiry swalayan tanpa login.
 * {@code website} adalah honeypot: field tersembunyi yang tidak pernah diisi pengguna sungguhan.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PublicInquiryRequest(
        @NotNull(message = ZodText.EXPECTED_STRING)
        @Size(min = 2, message = "Nama minimal 2 karakter.")
        @Size(max = 200, message = ZodText.MAX_200)
        String prospectName,
        @NotNull(message = ZodText.EXPECTED_STRING)
        @Size(min = 8, message = "Nomor telepon minimal 8 digit.")
        @Size(max = 50, message = ZodText.MAX_50)
        String prospectPhone,
        @Pattern(regexp = ZodText.EMAIL_OR_EMPTY_REGEX, message = ZodText.INVALID_EMAIL)
        String prospectEmail,
        @NotNull(message = ZodText.EXPECTED_STRING)
        @Size(min = 10, message = "Permintaan minimal 10 karakter.")
        @Size(max = 2000, message = ZodText.MAX_2000)
        String itemsText,
        @Pattern(regexp = ZodText.CHANNEL_REGEX, message = ZodText.CHANNEL)
        String channel,
        /** Honeypot — wajib kosong. */
        @Size(max = 0, message = "Bot detected.")
        String website) {

    /** Default Zod: channel 'Lainnya'. */
    String channelOrDefault() {
        return channel == null ? "Lainnya" : channel;
    }
}
