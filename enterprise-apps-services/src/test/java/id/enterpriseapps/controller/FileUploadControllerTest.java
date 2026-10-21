package id.enterpriseapps.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import id.enterpriseapps.service.FileUploadService;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("FileUploadController Integration Tests")
class FileUploadControllerTest extends id.enterpriseapps.support.AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FileUploadService fileUploadService;

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("POST /api/v1/uploads")
    class UploadTests {

        @Test
        @DisplayName("Upload satu file - 200 + return filename")
        void upload_Success() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "files", "photo.jpg", "image/jpeg", "fake-image".getBytes());

            when(fileUploadService.uploadFiles(any()))
                    .thenReturn(List.of("uuid-photo.jpg"));

            mockMvc.perform(multipart("/api/v1/uploads").file(file))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.message").value("Files uploaded successfully"))
                    .andExpect(jsonPath("$.data[0]").value("uuid-photo.jpg"));
        }

        @Test
        @DisplayName("Upload tipe file terlarang - 400 BadRequest")
        void upload_DisallowedType_BadRequest() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "files", "doc.pdf", "application/pdf", "data".getBytes());

            when(fileUploadService.uploadFiles(any()))
                    .thenThrow(new IllegalArgumentException("File type not allowed. Allowed: JPEG, PNG, GIF, WEBP"));

            mockMvc.perform(multipart("/api/v1/uploads").file(file))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("failed"))
                    .andExpect(jsonPath("$.errorMessage").value(
                            org.hamcrest.Matchers.containsString("File type not allowed")));
        }

        @Test
        @DisplayName("Upload error IO - 500 InternalServerError")
        void upload_IOError_InternalServerError() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "files", "p.jpg", "image/jpeg", "x".getBytes());

            when(fileUploadService.uploadFiles(any()))
                    .thenThrow(new IOException("Disk full"));

            mockMvc.perform(multipart("/api/v1/uploads").file(file))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status").value("failed"))
                    .andExpect(jsonPath("$.errorMessage").value(
                            org.hamcrest.Matchers.containsString("Disk full")));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/uploads/{filename}")
    class GetFileTests {

        @Test
        @DisplayName("Serve file yang ada - 200 + content")
        void getFile_Exists_Ok() throws Exception {
            Path file = Files.createFile(tempDir.resolve("photo.png"));
            Files.write(file, "image-bytes".getBytes());

            when(fileUploadService.getUploadDir()).thenReturn(tempDir);

            mockMvc.perform(get("/api/v1/uploads/photo.png"))
                    .andExpect(status().isOk())
                    .andExpect(content().bytes("image-bytes".getBytes()));
        }

        @Test
        @DisplayName("File tidak ditemukan - 404")
        void getFile_NotFound() throws Exception {
            when(fileUploadService.getUploadDir()).thenReturn(tempDir);

            mockMvc.perform(get("/api/v1/uploads/nonexistent.jpg"))
                    .andExpect(status().isNotFound());
        }
        // NOTE: Path-traversal defense for GET is enforced by Spring URL normalization
        // before reaching the controller; the service-layer guard is covered in
        // FileUploadServiceTest.deleteFile_PathTraversal_Rejected.
    }

    @Nested
    @DisplayName("DELETE /api/v1/uploads/{filename}")
    class DeleteFileTests {

        @Test
        @DisplayName("Delete berhasil - 200")
        void delete_Success() throws Exception {
            when(fileUploadService.deleteFile("photo.jpg")).thenReturn(true);

            mockMvc.perform(delete("/api/v1/uploads/photo.jpg"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.message").value("File deleted successfully"));
        }

        @Test
        @DisplayName("File tidak ada saat delete - 404")
        void delete_NotFound() throws Exception {
            when(fileUploadService.deleteFile("missing.jpg")).thenReturn(false);

            mockMvc.perform(delete("/api/v1/uploads/missing.jpg"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value("failed"));
        }
    }
}
