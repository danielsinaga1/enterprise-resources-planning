package id.enterpriseapps.erp.paymentrequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.enterpriseapps.erp.core.ErpException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static id.enterpriseapps.erp.paymentrequest.PrZod.array;
import static id.enterpriseapps.erp.paymentrequest.PrZod.booleanDefault;
import static id.enterpriseapps.erp.paymentrequest.PrZod.element;
import static id.enterpriseapps.erp.paymentrequest.PrZod.enumValue;
import static id.enterpriseapps.erp.paymentrequest.PrZod.field;
import static id.enterpriseapps.erp.paymentrequest.PrZod.intRangeDefault;
import static id.enterpriseapps.erp.paymentrequest.PrZod.isoDay;
import static id.enterpriseapps.erp.paymentrequest.PrZod.maxItems;
import static id.enterpriseapps.erp.paymentrequest.PrZod.minItems;
import static id.enterpriseapps.erp.paymentrequest.PrZod.nullableMin1;
import static id.enterpriseapps.erp.paymentrequest.PrZod.optArray;
import static id.enterpriseapps.erp.paymentrequest.PrZod.optEnum;
import static id.enterpriseapps.erp.paymentrequest.PrZod.optIsoDay;
import static id.enterpriseapps.erp.paymentrequest.PrZod.optMax;
import static id.enterpriseapps.erp.paymentrequest.PrZod.optMin1;
import static id.enterpriseapps.erp.paymentrequest.PrZod.optMinMax;
import static id.enterpriseapps.erp.paymentrequest.PrZod.optPositiveMax;
import static id.enterpriseapps.erp.paymentrequest.PrZod.optText;
import static id.enterpriseapps.erp.paymentrequest.PrZod.positiveMax;
import static id.enterpriseapps.erp.paymentrequest.PrZod.requiredMin1;
import static id.enterpriseapps.erp.paymentrequest.PrZod.trimmed;

/**
 * Body request Pengajuan Pembayaran — port skema Zod {@code PaymentRequest*Schema}, {@code PaymentRequestTypeSchema}
 * dan {@code EamPaySchema} (utils/schemas.ts). Field diperiksa sesuai urutan skema; error pertama → 400 {@code { error }}.
 */
final class PrRequests {

    static final List<String> TRAVEL_CATEGORIES = List.of("TRANSPORT", "LODGING", "PER_DIEM", "MEALS", "OTHER");
    static final List<String> PAYEE_KINDS = List.of("EMPLOYEE", "VENDOR");
    static final List<String> PR_KINDS = List.of("REIMBURSEMENT", "VENDOR_PAYMENT", "CASH_ADVANCE", "TRAVEL", "OTHER");
    static final List<String> ATTACHMENT_MIME = List.of("image/jpeg", "image/png", "image/webp", "application/pdf");

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("10000000000000");
    private static final BigDecimal MAX_QUANTITY = new BigDecimal("1000000");
    private static final String AMOUNT_POSITIVE = "Nominal harus lebih dari 0.";
    private static final String AMOUNT_TOO_BIG = "Nominal terlalu besar.";

    private PrRequests() {
    }

    // ───────────────────────────── pengajuan ─────────────────────────────

    /** Port {@code PaymentRequestLineSchema}. */
    record LineInput(String description, BigDecimal amount, String expenseAccountId, String costCenterId,
                     String expenseDate, String category, BigDecimal quantity, BigDecimal unitAmount) {

        static LineInput parse(JsonNode raw) {
            JsonNode b = element(raw);
            String description = trimmed(field(b, "description"), 1, "Uraian rincian wajib diisi.", 500);
            BigDecimal amount = positiveMax(field(b, "amount"), AMOUNT_POSITIVE, MAX_AMOUNT, AMOUNT_TOO_BIG);
            String expenseAccountId = optMin1(field(b, "expenseAccountId"));
            String costCenterId = optMin1(field(b, "costCenterId"));
            String expenseDate = optIsoDay(field(b, "expenseDate"));
            String category = optEnum(field(b, "category"), TRAVEL_CATEGORIES, true);
            BigDecimal quantity = optPositiveMax(field(b, "quantity"), MAX_QUANTITY);
            BigDecimal unitAmount = optPositiveMax(field(b, "unitAmount"), MAX_AMOUNT);
            return new LineInput(description, amount, expenseAccountId, costCenterId, expenseDate, category, quantity, unitAmount);
        }
    }

    /** Port {@code PaymentRequestInputSchema} (POST / dan PUT /:id). */
    record PaymentRequestInput(String typeId, String costCenterId, String department, String payeeKind, String partyId,
                               String payeeName, String bankName, String bankAccountNo, String bankAccountHolder,
                               String purpose, String neededBy, String travelDestination, String travelStartDate,
                               String travelEndDate, String travelParticipants, List<LineInput> lines) {

        static PaymentRequestInput parse(JsonNode raw) {
            ObjectNode b = PrZod.object(raw);
            String typeId = requiredMin1(field(b, "typeId"), "Jenis pengajuan wajib dipilih.");
            String costCenterId = optMin1(field(b, "costCenterId"));
            String department = optText(field(b, "department"), 100);
            String payeeKind = enumValue(field(b, "payeeKind"), PAYEE_KINDS);
            String partyId = optMin1(field(b, "partyId"));
            String payeeName = trimmed(field(b, "payeeName"), 1, "Nama penerima wajib diisi.", 200);
            String bankName = optText(field(b, "bankName"), 100);
            String bankAccountNo = optText(field(b, "bankAccountNo"), 50);
            String bankAccountHolder = optText(field(b, "bankAccountHolder"), 200);
            String purpose = trimmed(field(b, "purpose"), 3, "Keperluan minimal 3 karakter.", 1000);
            String neededBy = optIsoDay(field(b, "neededBy"));
            String travelDestination = optText(field(b, "travelDestination"), 200);
            String travelStartDate = optIsoDay(field(b, "travelStartDate"));
            String travelEndDate = optIsoDay(field(b, "travelEndDate"));
            String travelParticipants = optText(field(b, "travelParticipants"), 500);
            List<LineInput> lines = new ArrayList<>();
            for (JsonNode l : array(field(b, "lines"))) {
                lines.add(LineInput.parse(l));
            }
            maxItems(lines, 100, "Maksimal 100 baris rincian.");
            return new PaymentRequestInput(typeId, costCenterId, department, payeeKind, partyId, payeeName, bankName,
                    bankAccountNo, bankAccountHolder, purpose, neededBy, travelDestination, travelStartDate, travelEndDate,
                    travelParticipants, lines);
        }
    }

    /** Satu pasangan baris → akun biaya ({@code lineAccounts}). */
    record LineAccount(String id, String expenseAccountId) {
    }

    private static List<LineAccount> lineAccounts(List<JsonNode> raw) {
        List<LineAccount> out = new ArrayList<>();
        for (JsonNode n : raw) {
            JsonNode e = element(n);
            String id = requiredMin1(field(e, "id"), null);
            String acc = nullableMin1(field(e, "expenseAccountId"));
            out.add(new LineAccount(id, acc));
        }
        return out;
    }

    private static List<LineAccount> optLineAccounts(JsonNode n) {
        List<JsonNode> raw = optArray(n);
        if (raw == null) {
            return null;
        }
        List<LineAccount> out = lineAccounts(raw);
        maxItems(out, 100, null);
        return out;
    }

    /** Port {@code PaymentRequestDecisionSchema}. */
    record Decision(String note, List<LineAccount> lineAccounts) {
        static Decision parse(JsonNode raw) {
            ObjectNode b = PrZod.objectOrEmpty(raw);
            String note = optText(field(b, "note"), 1000);
            List<LineAccount> la = optLineAccounts(field(b, "lineAccounts"));
            return new Decision(note, la);
        }
    }

    /** Port {@code PaymentRequestReasonSchema}. */
    record Reason(String reason) {
        static Reason parse(JsonNode raw) {
            ObjectNode b = PrZod.object(raw);
            return new Reason(trimmed(field(b, "reason"), 3, "Alasan minimal 3 karakter.", 1000));
        }
    }

    /** Port {@code PaymentRequestVerifySchema}. */
    record Verify(List<LineAccount> lineAccounts) {
        static Verify parse(JsonNode raw) {
            ObjectNode b = PrZod.object(raw);
            List<LineAccount> la = PrRequests.lineAccounts(array(field(b, "lineAccounts")));
            minItems(la, 1, null);
            maxItems(la, 100, null);
            return new Verify(la);
        }
    }

    /** Port {@code PaymentRequestPaySchema}. */
    record Pay(String cashAccountId, String paymentDate, String reference) {
        static Pay parse(JsonNode raw) {
            ObjectNode b = PrZod.object(raw);
            String cash = requiredMin1(field(b, "cashAccountId"), "Akun kas/bank wajib dipilih.");
            String date = isoDay(field(b, "paymentDate"));
            String ref = optText(field(b, "reference"), 100);
            return new Pay(cash, date, ref);
        }
    }

    // ───────────────────────────── pertanggungjawaban ─────────────────────────────

    /** Port {@code PaymentRequestSettlementLineSchema}. */
    record SettlementLineInput(String description, BigDecimal amount, String expenseDate, String category,
                               String plannedLineId, String expenseAccountId) {
        static SettlementLineInput parse(JsonNode raw) {
            JsonNode b = element(raw);
            String description = trimmed(field(b, "description"), 1, "Uraian realisasi wajib diisi.", 500);
            BigDecimal amount = positiveMax(field(b, "amount"), AMOUNT_POSITIVE, MAX_AMOUNT, AMOUNT_TOO_BIG);
            String expenseDate = optIsoDay(field(b, "expenseDate"));
            String category = optEnum(field(b, "category"), TRAVEL_CATEGORIES, true);
            String plannedLineId = optMin1(field(b, "plannedLineId"));
            String expenseAccountId = optMin1(field(b, "expenseAccountId"));
            return new SettlementLineInput(description, amount, expenseDate, category, plannedLineId, expenseAccountId);
        }
    }

    /** Port {@code PaymentRequestSettlementDraftSchema}. */
    record SettlementDraft(String note, String deadline, List<SettlementLineInput> lines) {
        static SettlementDraft parse(JsonNode raw) {
            ObjectNode b = PrZod.object(raw);
            String note = optText(field(b, "note"), 1000);
            String deadline = optIsoDay(field(b, "deadline"));
            List<SettlementLineInput> lines = new ArrayList<>();
            for (JsonNode l : array(field(b, "lines"))) {
                lines.add(SettlementLineInput.parse(l));
            }
            maxItems(lines, 100, "Maksimal 100 baris realisasi.");
            return new SettlementDraft(note, deadline, lines);
        }
    }

    /** Port {@code PaymentRequestSettlementPreviewSchema}. */
    record SettlementPreview(List<LineAccount> lineAccounts, String cashAccountId) {
        static SettlementPreview parse(JsonNode raw) {
            ObjectNode b = PrZod.objectOrEmpty(raw);
            List<LineAccount> la = optLineAccounts(field(b, "lineAccounts"));
            String cash = optMin1(field(b, "cashAccountId"));
            return new SettlementPreview(la, cash);
        }
    }

    /** Port {@code PaymentRequestSettlementPostSchema}. */
    record SettlementPost(String settlementDate, List<LineAccount> lineAccounts, String cashAccountId, String reference,
                          String note) {
        static SettlementPost parse(JsonNode raw) {
            ObjectNode b = PrZod.object(raw);
            String date = isoDay(field(b, "settlementDate"));
            List<LineAccount> la = optLineAccounts(field(b, "lineAccounts"));
            String cash = optMin1(field(b, "cashAccountId"));
            String ref = optText(field(b, "reference"), 100);
            String note = optText(field(b, "note"), 1000);
            return new SettlementPost(date, la, cash, ref, note);
        }
    }

    // ───────────────────────────── lampiran JSON ─────────────────────────────

    /** Port {@code PaymentRequestAttachmentJsonSchema} (jalur JSON untuk tool, tanpa multipart). */
    record AttachmentJson(String fileName, String contentBase64, String mimeType, String chatAttachmentId, String scope) {
        static AttachmentJson parse(JsonNode raw) {
            ObjectNode b = PrZod.object(raw);
            String fileName = optMinMax(field(b, "fileName"), 1, null, 255, true);
            String content = optMinMax(field(b, "contentBase64"), 8, "contentBase64 kosong.", 0, false);
            String mime = optEnum(field(b, "mimeType"), ATTACHMENT_MIME, false);
            String chatId = optMinMax(field(b, "chatAttachmentId"), 1, null, 100, false);
            String scope = optEnum(field(b, "scope"), List.of("request", "settlement"), false);
            if (content != null && chatId != null) {
                throw ErpException.business("Pilih salah satu: contentBase64 atau chatAttachmentId.");
            }
            if (content != null && fileName == null) {
                throw ErpException.business("fileName wajib bila contentBase64 diisi.");
            }
            return new AttachmentJson(fileName, content, mime, chatId, scope);
        }
    }

    // ───────────────────────────── jenis pengajuan ─────────────────────────────

    /** Port {@code PaymentRequestTypeSchema}. */
    record TypeInput(String code, String name, String kind, String description, String defaultExpenseAccountId,
                     boolean requiresAttachment, boolean requiresSettlement, boolean isActive, int sortOrder) {
        static TypeInput parse(JsonNode raw) {
            ObjectNode b = PrZod.object(raw);
            String code = trimmed(field(b, "code"), 2, null, 20);
            if (!code.matches("^[A-Z0-9_-]+$")) {
                throw ErpException.business("Kode hanya huruf besar, angka, _ atau -.");
            }
            String name = trimmed(field(b, "name"), 2, null, 100);
            String kind = enumValue(field(b, "kind"), PR_KINDS);
            String description = optText(field(b, "description"), 500);
            String defaultExpenseAccountId = optMin1(field(b, "defaultExpenseAccountId"));
            boolean requiresAttachment = booleanDefault(field(b, "requiresAttachment"), false);
            boolean requiresSettlement = booleanDefault(field(b, "requiresSettlement"), false);
            boolean isActive = booleanDefault(field(b, "isActive"), true);
            int sortOrder = intRangeDefault(field(b, "sortOrder"), 0, 9999, 0);
            return new TypeInput(code, name, kind, description, defaultExpenseAccountId, requiresAttachment,
                    requiresSettlement, isActive, sortOrder);
        }
    }

    // ───────────────────────────── Tagihan EAM ─────────────────────────────

    /** Port {@code EamPaySchema}. */
    record EamPay(String method, String accountId, String creditAccountId, String partyId, BigDecimal amount,
                  String date, String dueDate, String notes) {
        static EamPay parse(JsonNode raw) {
            ObjectNode b = PrZod.object(raw);
            String method = enumValue(field(b, "method"), List.of("JOURNAL", "PURCHASE_INVOICE"));
            String accountId = optMin1(field(b, "accountId"));
            String creditAccountId = optMin1(field(b, "creditAccountId"));
            String partyId = optMin1(field(b, "partyId"));
            BigDecimal amount = optPositiveMax(field(b, "amount"), null);
            String date = optMin1(field(b, "date"));
            String dueDate = optMin1(field(b, "dueDate"));
            String notes = optMax(field(b, "notes"), 1000);
            return new EamPay(method, accountId, creditAccountId, partyId, amount, date, dueDate, notes);
        }
    }
}
