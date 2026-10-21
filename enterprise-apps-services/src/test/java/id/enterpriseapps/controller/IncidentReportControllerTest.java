package id.enterpriseapps.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import id.enterpriseapps.constant.StatusConstant;
import id.enterpriseapps.request.incident.AddIncidentReportRequest;
import id.enterpriseapps.request.incident.AssignSubordinateRequest;
import id.enterpriseapps.request.incident.SubmitFollowUpRequest;
import id.enterpriseapps.request.incident.UpdateIncidentReportRequest;
import id.enterpriseapps.request.incident.WorkflowActionRequest;
import id.enterpriseapps.response.Response;
import id.enterpriseapps.service.incident.IncidentReportService;
import id.enterpriseapps.service.logging.LoggingService;
import id.enterpriseapps.utils.ConvertUtil;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class IncidentReportControllerTest extends id.enterpriseapps.support.AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private IncidentReportService incidentReportService;

    @MockitoBean
    private LoggingService loggingService;

    @MockitoBean
    private ConvertUtil convertUtil;

    // ===================== LIST / GET ALL =====================

    @Nested
    @DisplayName("GET /api/v1/incident-reports")
    class GetAllTests {

        @Test
        @DisplayName("Ambil semua incident report - sukses")
        void getAllIncidentReports_Success() throws Exception {
            Response response = Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .data(java.util.List.of())
                    .page(1)
                    .countData(10L)
                    .totalPage(1)
                    .build();

            when(incidentReportService.getAllIncidentReports(
                    anyString(), anyString(), anyString(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(response);

            mockMvc.perform(get("/api/v1/incident-reports")
                            .param("page", "1")
                            .param("limit", "10")
                            .param("sort-by", "id")
                            .param("sort-order", "desc")
                            .param("status", "New"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"));
        }

        @Test
        @DisplayName("Ambil semua incident report - tanpa parameter")
        void getAllIncidentReports_NoParams() throws Exception {
            Response response = Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .data(java.util.List.of())
                    .build();

            when(incidentReportService.getAllIncidentReports(null, null, null, null, null, null, null, null, null))
                    .thenReturn(response);

            mockMvc.perform(get("/api/v1/incident-reports"))
                    .andExpect(status().isOk());
        }
    }

    // ===================== GET BY ID =====================

    @Nested
    @DisplayName("GET /api/v1/incident-reports/{id}")
    class GetByIdTests {

        @Test
        @DisplayName("Ambil detail incident report - ditemukan")
        void getById_Found() throws Exception {
            Response response = Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .data(java.util.Map.of("id", 1L, "reportNumber", "IR-2026-001"))
                    .build();

            when(incidentReportService.getIncidentReportById(1L)).thenReturn(response);

            mockMvc.perform(get("/api/v1/incident-reports/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"));
        }

        @Test
        @DisplayName("Ambil detail incident report - tidak ditemukan")
        void getById_NotFound() throws Exception {
            Response response = Response.builder()
                    .status(StatusConstant.FAILED)
                    .httpStatus(HttpStatus.NOT_FOUND)
                    .errorMessage("Incident report not found")
                    .build();

            when(incidentReportService.getIncidentReportById(999L)).thenReturn(response);

            // Controller sengaja meneruskan status dari service, bukan selalu 200:
            // memaksa 200 membuat "tidak ditemukan" terbaca sukses oleh klien HTTP,
            // sehingga SPA menahan laporan basi di layar dengan tombol aksi tetap aktif.
            mockMvc.perform(get("/api/v1/incident-reports/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value("failed"));
        }
    }

    // ===================== CREATE =====================

    @Nested
    @DisplayName("POST /api/v1/incident-reports")
    class CreateTests {

        @Test
        @DisplayName("Buat incident report baru - sukses")
        void create_Success() throws Exception {
            AddIncidentReportRequest request = new AddIncidentReportRequest();
            request.setTitle("Kecelakaan di area produksi");
            request.setDescription("Terjadi kecelakaan kerja di area produksi lantai 2");
            request.setCategoryId(1L);
            request.setClassificationId(1L);
            request.setReporterId(1L);
            request.setSeverity(3);
            request.setProbability(2);

            Response response = Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.CREATED)
                    .data(java.util.Map.of("id", 1L, "reportNumber", "IR-2026-001"))
                    .build();

            when(incidentReportService.createIncidentReport(any(AddIncidentReportRequest.class)))
                    .thenReturn(response);

            mockMvc.perform(post("/api/v1/incident-reports")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("success"));
        }

        @Test
        @DisplayName("Buat incident report - validasi gagal (title kosong)")
        void create_ValidationFailed() throws Exception {
            // title is @NotBlank, send empty
            AddIncidentReportRequest request = new AddIncidentReportRequest();
            request.setDescription("Deskripsi saja tanpa judul");

            mockMvc.perform(post("/api/v1/incident-reports")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ===================== UPDATE =====================

    @Nested
    @DisplayName("PATCH /api/v1/incident-reports/{id}")
    class UpdateTests {

        @Test
        @DisplayName("Update incident report - sukses")
        void update_Success() throws Exception {
            UpdateIncidentReportRequest request = new UpdateIncidentReportRequest();
            request.setTitle("Judul yang diupdate");
            request.setDescription("Deskripsi yang diperbarui");

            Response response = Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .build();

            when(incidentReportService.updateIncidentReport(eq(1L), any(UpdateIncidentReportRequest.class)))
                    .thenReturn(response);

            mockMvc.perform(patch("/api/v1/incident-reports/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"));
        }
    }

    // ===================== DELETE =====================

    @Nested
    @DisplayName("DELETE /api/v1/incident-reports/{id}")
    class DeleteTests {

        @Test
        @DisplayName("Hapus incident report - sukses (soft delete)")
        // @AutoConfigureMockMvc(addFilters = false) hanya mematikan filter chain, bukan
        // method security — @PreAuthorize("hasRole('SUPER_ADMIN')") di controller tetap
        // dievaluasi. Tanpa SecurityContext terisi, endpoint ini melempar dan berakhir 500.
        @WithMockUser(roles = "SUPER_ADMIN")
        void delete_Success() throws Exception {
            Response response = Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .message("Incident report deleted successfully")
                    .build();

            when(incidentReportService.deleteIncidentReport(1L)).thenReturn(response);

            mockMvc.perform(delete("/api/v1/incident-reports/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"));
        }
    }

    // ===================== WORKFLOW — SUPERINTENDENT =====================

    @Nested
    @DisplayName("Workflow - Superintendent Approval")
    class SuperintendentWorkflowTests {

        @Test
        @DisplayName("Superintendent approve - sukses")
        void approveBySuperintendent_Success() throws Exception {
            WorkflowActionRequest request = new WorkflowActionRequest();
            request.setUserId(5L);
            request.setNotes("Disetujui oleh superintendent");

            Response response = Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .message("Approved by Superintendent")
                    .build();

            when(incidentReportService.approveBySuperintendent(eq(1L), any(WorkflowActionRequest.class)))
                    .thenReturn(response);

            mockMvc.perform(post("/api/v1/incident-reports/1/approve/superintendent")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"));
        }

        @Test
        @DisplayName("Superintendent reject (revisi) - sukses")
        void rejectBySuperintendent_Success() throws Exception {
            WorkflowActionRequest request = new WorkflowActionRequest();
            request.setUserId(5L);
            request.setNotes("Perlu revisi deskripsi insiden");

            Response response = Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .build();

            when(incidentReportService.rejectBySuperintendent(eq(1L), any(WorkflowActionRequest.class)))
                    .thenReturn(response);

            mockMvc.perform(post("/api/v1/incident-reports/1/reject/superintendent")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Resubmit setelah revisi - sukses")
        void resubmitAfterRevision_Success() throws Exception {
            WorkflowActionRequest request = new WorkflowActionRequest();
            request.setUserId(1L);
            request.setNotes("Sudah diperbaiki");

            Response response = Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .build();

            when(incidentReportService.resubmitAfterRevision(eq(1L), any(WorkflowActionRequest.class)))
                    .thenReturn(response);

            mockMvc.perform(post("/api/v1/incident-reports/1/resubmit")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }
    }

    // ===================== WORKFLOW — MANAGER =====================

    @Nested
    @DisplayName("Workflow - Manager Approval")
    class ManagerWorkflowTests {

        @Test
        @DisplayName("Manager approve - sukses")
        void approveByManager_Success() throws Exception {
            WorkflowActionRequest request = new WorkflowActionRequest();
            request.setUserId(10L);
            request.setNotes("Disetujui");

            Response response = Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .build();

            when(incidentReportService.approveByManager(eq(1L), any(WorkflowActionRequest.class)))
                    .thenReturn(response);

            mockMvc.perform(post("/api/v1/incident-reports/1/approve/manager")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Manager reject - sukses")
        void rejectByManager_Success() throws Exception {
            WorkflowActionRequest request = new WorkflowActionRequest();
            request.setUserId(10L);
            request.setNotes("Ditolak, data tidak lengkap");

            Response response = Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .build();

            when(incidentReportService.rejectByManager(eq(1L), any(WorkflowActionRequest.class)))
                    .thenReturn(response);

            mockMvc.perform(post("/api/v1/incident-reports/1/reject/manager")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }
    }

    // ===================== WORKFLOW — ASSIGN SUBORDINATE =====================

    @Nested
    @DisplayName("Workflow - Assign Subordinate")
    class AssignSubordinateTests {

        @Test
        @DisplayName("Assign subordinate - sukses")
        void assignSubordinate_Success() throws Exception {
            AssignSubordinateRequest request = new AssignSubordinateRequest();
            request.setManagerId(10L);
            request.setSubordinateId(20L);
            request.setNotes("Ditugaskan ke teknisi A");

            Response response = Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .build();

            when(incidentReportService.assignSubordinate(eq(1L), any(AssignSubordinateRequest.class)))
                    .thenReturn(response);

            mockMvc.perform(post("/api/v1/incident-reports/1/assign-subordinate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }
    }

    // ===================== WORKFLOW — FOLLOW-UP & COMPLETE =====================

    @Nested
    @DisplayName("Workflow - Follow-up & Complete")
    class FollowUpTests {

        @Test
        @DisplayName("Submit follow-up - sukses")
        void submitFollowUp_Success() throws Exception {
            SubmitFollowUpRequest request = new SubmitFollowUpRequest();
            request.setUserId(20L);
            request.setPerbaikanAwal("Mesin telah diisolasi");
            request.setCorrective("Komponen diganti baru");
            request.setPreventive("Jadwal maintenance ditambahkan");
            request.setFollowUpNotes("Tindak lanjut selesai");

            Response response = Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .build();

            when(incidentReportService.submitFollowUpAndComplete(eq(1L), any(SubmitFollowUpRequest.class)))
                    .thenReturn(response);

            mockMvc.perform(post("/api/v1/incident-reports/1/follow-up")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }
    }

    // ===================== DASHBOARD STATS =====================

    @Nested
    @DisplayName("GET /api/v1/incident-reports/dashboard/stats")
    class DashboardStatsTests {

        @Test
        @DisplayName("Ambil statistik dashboard - sukses")
        void getDashboardStats_Success() throws Exception {
            Response response = Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .data(java.util.Map.of(
                            "totalIncidents", 27,
                            "newCount", 5,
                            "inProgressCount", 10,
                            "doneCount", 12
                    ))
                    .build();

            when(incidentReportService.getDashboardStats()).thenReturn(response);

            mockMvc.perform(get("/api/v1/incident-reports/dashboard/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.data.totalIncidents").value(27));
        }
    }
}

