package id.enterpriseapps.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Handles email notifications for the report approval workflow:
 *
 * 1. New Report            → Superior review
 * 2. Partially Approved    → Manager final approval
 * 3. Fully Approved        → Dept. Manager delegation
 * 4. Delegated (In Progress) → Subordinate assignment
 * 5. Completed (Closed)    → Creator & managers notified
 * 6. Reminder              → Re-notify pending action holder after N days of inactivity
 */
@Slf4j
@Service
public class WorkflowEmailNotificationService {

    private static final String TEMPLATE_DIR = "email/";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMMM yyyy");

    @Autowired
    private EmailService emailService;

    @Value("${app.base-url:http://localhost:5173}")
    private String baseUrl;

    /**
     * Link to the report detail page. Keyed by the numeric primary key, not the
     * human-readable number (IR-YYYYMM-NNNNN): the SPA route is /:id, and the
     * number is not routable. Must stay in step with the in-app notification link
     * built in IncidentReportService.
     */
    private String reportUrl(Long reportDbId) {
        return baseUrl + "/dashboard/irms/incident-reports/" + reportDbId;
    }

    // ────────────────────────────────────────────────────────────────
    // Trigger 1: New Report → Notify Superior
    // ────────────────────────────────────────────────────────────────
    @Async
    public void notifySuperiorNewReport(String superiorEmail,
                                        String superiorName,
                                        String creatorName,
                                        Long reportDbId,
                                        String reportId,
                                        String reportTitle,
                                        String classificationName,
                                        String categoryName) {
        String subject = "[Action Required] Approval Laporan Baru: " + reportId + " - " + reportTitle;
        String actionUrl = reportUrl(reportDbId);

        Map<String, Object> vars = new HashMap<>();
        vars.put("recipientName", superiorName);
        vars.put("creatorName", creatorName);
        vars.put("reportId", reportId);
        vars.put("reportTitle", reportTitle);
        vars.put("submissionDate", LocalDate.now().format(DATE_FMT));
        vars.put("classificationName", classificationName != null ? classificationName : "-");
        vars.put("categoryName", categoryName != null ? categoryName : "-");
        vars.put("actionUrl", actionUrl);

        emailService.sendTemplateEmail(superiorEmail, subject,
                TEMPLATE_DIR + "workflow-01-new-report", vars);
    }

    // ────────────────────────────────────────────────────────────────
    // Trigger 2: Partially Approved → Notify Manager for final approval
    // ────────────────────────────────────────────────────────────────
    @Async
    public void notifyManagerPartialApproval(String managerEmail,
                                             String managerName,
                                             String creatorName,
                                             String approverName,
                                             Long reportDbId,
                                             String reportId,
                                             String reportTitle,
                                             String classificationName,
                                             String categoryName,
                                             String reviewedBy) {
        String subject = "[Final Approval] Persetujuan Manajerial: " + reportId;
        String actionUrl = reportUrl(reportDbId);

        Map<String, Object> vars = new HashMap<>();
        vars.put("recipientName", managerName);
        vars.put("creatorName", creatorName);
        vars.put("approverName", approverName);
        vars.put("reportId", reportId);
        vars.put("reportTitle", reportTitle);
        vars.put("classificationName", classificationName != null ? classificationName : "-");
        vars.put("categoryName", categoryName != null ? categoryName : "-");
        vars.put("reviewedBy", reviewedBy != null ? reviewedBy : "-");
        vars.put("actionUrl", actionUrl);

        emailService.sendTemplateEmail(managerEmail, subject,
                TEMPLATE_DIR + "workflow-02-partial-approved", vars);
    }

    // ────────────────────────────────────────────────────────────────
    // Trigger 3: Fully Approved → Notify Destination Dept. Manager
    // ────────────────────────────────────────────────────────────────
    @Async
    public void notifyDeptManagerFullyApproved(String deptManagerEmail,
                                               String deptManagerName,
                                               String creatorName,
                                               String approverName,
                                               Long reportDbId,
                                               String reportId,
                                               String reportTitle,
                                               String classificationName,
                                               String categoryName,
                                               String reviewedBy,
                                               String acknowledgedBy) {
        String subject = "[Action Required] Delegasi Laporan Masuk: " + reportId;
        String actionUrl = reportUrl(reportDbId);

        Map<String, Object> vars = new HashMap<>();
        vars.put("recipientName", deptManagerName);
        vars.put("creatorName", creatorName);
        vars.put("approverName", approverName);
        vars.put("reportId", reportId);
        vars.put("reportTitle", reportTitle);
        vars.put("classificationName", classificationName != null ? classificationName : "-");
        vars.put("categoryName", categoryName != null ? categoryName : "-");
        vars.put("reviewedBy", reviewedBy != null ? reviewedBy : "-");
        vars.put("acknowledgedBy", acknowledgedBy != null ? acknowledgedBy : "-");
        vars.put("actionUrl", actionUrl);

        emailService.sendTemplateEmail(deptManagerEmail, subject,
                TEMPLATE_DIR + "workflow-03-fully-approved", vars);
    }

    // ────────────────────────────────────────────────────────────────
    // Trigger 4: Delegated → Notify Subordinate
    // ────────────────────────────────────────────────────────────────
    @Async
    public void notifySubordinateDelegated(String subordinateEmail,
                                           String subordinateName,
                                           String delegatorName,
                                           Long reportDbId,
                                           String reportId,
                                           String reportTitle,
                                           String deadline,
                                           String classificationName,
                                           String categoryName,
                                           String reviewedBy,
                                           String acknowledgedBy) {
        String subject = "[Assignment] Tugas Baru: " + reportId + " - " + reportTitle;
        String actionUrl = reportUrl(reportDbId);

        Map<String, Object> vars = new HashMap<>();
        vars.put("recipientName", subordinateName);
        vars.put("delegatorName", delegatorName);
        vars.put("reportId", reportId);
        vars.put("reportTitle", reportTitle);
        vars.put("deadline", deadline);
        vars.put("classificationName", classificationName != null ? classificationName : "-");
        vars.put("categoryName", categoryName != null ? categoryName : "-");
        vars.put("reviewedBy", reviewedBy != null ? reviewedBy : "-");
        vars.put("acknowledgedBy", acknowledgedBy != null ? acknowledgedBy : "-");
        vars.put("actionUrl", actionUrl);

        emailService.sendTemplateEmail(subordinateEmail, subject,
                TEMPLATE_DIR + "workflow-04-delegated", vars);
    }

    // ────────────────────────────────────────────────────────────────
    // Trigger 5: Completed → Notify Creator & Related Managers
    // ────────────────────────────────────────────────────────────────
    @Async
    public void notifyReportCompleted(String[] recipientEmails,
                                      String recipientName,
                                      String completedByName,
                                      Long reportDbId,
                                      String reportId,
                                      String reportTitle,
                                      String classificationName,
                                      String categoryName,
                                      String reviewedBy,
                                      String acknowledgedBy) {
        String subject = "[Completed] Laporan Selesai & Ditutup: " + reportId;
        String actionUrl = reportUrl(reportDbId);

        Map<String, Object> vars = new HashMap<>();
        vars.put("recipientName", recipientName);
        vars.put("completedByName", completedByName);
        vars.put("reportId", reportId);
        vars.put("reportTitle", reportTitle);
        vars.put("completionDate", LocalDate.now().format(DATE_FMT));
        vars.put("classificationName", classificationName != null ? classificationName : "-");
        vars.put("categoryName", categoryName != null ? categoryName : "-");
        vars.put("reviewedBy", reviewedBy != null ? reviewedBy : "-");
        vars.put("acknowledgedBy", acknowledgedBy != null ? acknowledgedBy : "-");
        vars.put("actionUrl", actionUrl);

        emailService.sendTemplateEmail(recipientEmails, subject,
                TEMPLATE_DIR + "workflow-05-completed", vars);
    }

    // ────────────────────────────────────────────────────────────────
    // Trigger 6: Reminder → Re-notify pending action holder
    // ────────────────────────────────────────────────────────────────

    /**
     * Send a reminder to a single recipient whose action is overdue.
     *
     * @param pendingAction  Short label for what is awaited, e.g. "Review Laporan" / "Persetujuan Final" / "Penyelesaian Tugas"
     * @param currentStatus  Current workflow status label shown in the email card
     * @param daysPending    Number of days the report has been waiting for this action
     */
    @Async
    public void notifyReminder(String recipientEmail,
                               String recipientName,
                               Long reportDbId,
                               String reportId,
                               String reportTitle,
                               String pendingAction,
                               String currentStatus,
                               int daysPending,
                               String submissionDate,
                               String classificationName,
                               String categoryName) {
        String subject = "[Reminder] Tindakan Belum Dilakukan: " + reportId + " - " + reportTitle;
        String actionUrl = reportUrl(reportDbId);

        Map<String, Object> vars = new HashMap<>();
        vars.put("recipientName", recipientName);
        vars.put("reportId", reportId);
        vars.put("reportTitle", reportTitle);
        vars.put("pendingAction", pendingAction);
        vars.put("currentStatus", currentStatus);
        vars.put("daysPending", daysPending);
        vars.put("submissionDate", submissionDate != null ? submissionDate : LocalDate.now().format(DATE_FMT));
        vars.put("classificationName", classificationName != null ? classificationName : "-");
        vars.put("categoryName", categoryName != null ? categoryName : "-");
        vars.put("actionUrl", actionUrl);

        emailService.sendTemplateEmail(recipientEmail, subject,
                TEMPLATE_DIR + "workflow-06-reminder", vars);
    }

    /**
     * Send reminders to multiple recipients for the same stalled report.
     */
    @Async
    public void notifyReminder(String[] recipientEmails,
                               String recipientName,
                               Long reportDbId,
                               String reportId,
                               String reportTitle,
                               String pendingAction,
                               String currentStatus,
                               int daysPending,
                               String submissionDate,
                               String classificationName,
                               String categoryName) {
        for (String email : recipientEmails) {
            notifyReminder(email, recipientName, reportDbId, reportId, reportTitle,
                    pendingAction, currentStatus, daysPending,
                    submissionDate, classificationName, categoryName);
        }
    }
}

