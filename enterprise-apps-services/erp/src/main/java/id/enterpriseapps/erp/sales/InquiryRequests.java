package id.enterpriseapps.erp.sales;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Body request inquiry (port schema Zod di utils/schemas.ts). */
public final class InquiryRequests {

    private InquiryRequests() {
    }

    /** Port {@code InquiryItemInputSchema}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            @NotEmpty(message = "Nama item wajib diisi.")
            String itemName,
            @DecimalMin(value = "0", message = ZodText.NUMBER_GTE_0)
            BigDecimal quantity,
            String unit,
            @DecimalMin(value = "0", message = ZodText.NUMBER_GTE_0)
            BigDecimal estimatedPrice,
            String description) {
    }

    /**
     * Port {@code CreateInquirySchema} (= {@code UpdateInquirySchema}): lead customer/prospek yang
     * dicatat sebelum penawaran diterbitkan.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Upsert(
            @NotEmpty(message = "Tanggal wajib diisi.")
            String date,
            @Pattern(regexp = ZodText.CHANNEL_REGEX, message = ZodText.CHANNEL)
            String channel,
            @Size(min = 1, message = ZodText.STRING_MIN_1)
            String partyId,
            @NotEmpty(message = "Nama prospek/customer wajib diisi.")
            @Size(max = 200, message = ZodText.MAX_200)
            String prospectName,
            @Size(max = 50, message = ZodText.MAX_50)
            String prospectPhone,
            @Pattern(regexp = ZodText.EMAIL_OR_EMPTY_REGEX, message = ZodText.INVALID_EMAIL)
            String prospectEmail,
            @Pattern(regexp = "freetext|structured", message = ZodText.ITEMS_MODE)
            String itemsMode,
            String itemsText,
            List<Item> items,
            String notes,
            String followUpDate) implements ErpSalesRequestValidator.Refinable {

        /** Default Zod: channel 'Lainnya'. */
        String channelOrDefault() {
            return channel == null ? "Lainnya" : channel;
        }

        /** Default Zod: itemsMode 'freetext'. */
        String itemsModeOrDefault() {
            return itemsMode == null ? "freetext" : itemsMode;
        }

        List<Item> itemsOrEmpty() {
            return items == null ? List.of() : items;
        }

        @Override
        public String refineError() {
            if (!"structured".equals(itemsModeOrDefault()) || !itemsOrEmpty().isEmpty() || SalesSupport.hasText(itemsText)) {
                return null;
            }
            return "Inquiry harus berisi item (structured) atau permintaan (free-text).";
        }
    }

    /** Port {@code InquiryStatusUpdateSchema}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatusUpdate(
            @NotNull(message = ZodText.INQUIRY_STATUS)
            @Pattern(regexp = ZodText.INQUIRY_STATUS_REGEX, message = ZodText.INQUIRY_STATUS)
            String status,
            @Size(max = 500, message = ZodText.MAX_500)
            String lossReason) {
    }
}
