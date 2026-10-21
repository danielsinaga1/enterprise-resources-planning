package id.enterpriseapps.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

@DisplayName("FileUploadService Unit Tests")
class FileUploadServiceTest {

    private FileUploadService fileUploadService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        fileUploadService = new FileUploadService();
        ReflectionTestUtils.setField(fileUploadService, "uploadDir", tempDir.toAbsolutePath().normalize());
    }

    @Nested
    @DisplayName("uploadFiles()")
    class UploadFilesTests {

        @Test
        @DisplayName("Upload satu file JPEG - return UUID filename dengan ekstensi")
        void uploadFiles_SingleJpeg_Success() throws IOException {
            MultipartFile file = new MockMultipartFile(
                    "files", "photo.jpg", "image/jpeg", "fake-image-content".getBytes());

            var filenames = fileUploadService.uploadFiles(new MultipartFile[]{file});

            assertThat(filenames).hasSize(1);
            assertThat(filenames.get(0)).endsWith(".jpg");
            assertThat(Files.exists(tempDir.resolve(filenames.get(0)))).isTrue();
        }

        @Test
        @DisplayName("Upload beberapa file - semua tersimpan")
        void uploadFiles_Multiple_Success() throws IOException {
            MultipartFile file1 = new MockMultipartFile(
                    "files", "a.png", "image/png", "a".getBytes());
            MultipartFile file2 = new MockMultipartFile(
                    "files", "b.webp", "image/webp", "b".getBytes());

            var filenames = fileUploadService.uploadFiles(new MultipartFile[]{file1, file2});

            assertThat(filenames).hasSize(2);
            assertThat(filenames.get(0)).endsWith(".png");
            assertThat(filenames.get(1)).endsWith(".webp");
        }

        @Test
        @DisplayName("Upload file kosong - di-skip, tidak masuk hasil")
        void uploadFiles_EmptyFile_Skipped() throws IOException {
            MultipartFile empty = new MockMultipartFile(
                    "files", "empty.jpg", "image/jpeg", new byte[0]);
            MultipartFile valid = new MockMultipartFile(
                    "files", "valid.jpg", "image/jpeg", "data".getBytes());

            var filenames = fileUploadService.uploadFiles(new MultipartFile[]{empty, valid});

            assertThat(filenames).hasSize(1);
            assertThat(filenames.get(0)).endsWith(".jpg");
        }

        @Test
        @DisplayName("Upload file > 10MB - throw IllegalArgumentException")
        void uploadFiles_FileTooLarge_Throws() {
            byte[] large = new byte[11 * 1024 * 1024]; // 11MB
            MultipartFile file = new MockMultipartFile(
                    "files", "big.jpg", "image/jpeg", large);

            assertThatThrownBy(() -> fileUploadService.uploadFiles(new MultipartFile[]{file}))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exceeds maximum size");
        }

        @Test
        @DisplayName("Upload file dengan content-type terlarang - throw IllegalArgumentException")
        void uploadFiles_DisallowedType_Throws() {
            MultipartFile file = new MockMultipartFile(
                    "files", "doc.pdf", "application/pdf", "pdf-data".getBytes());

            assertThatThrownBy(() -> fileUploadService.uploadFiles(new MultipartFile[]{file}))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("File type not allowed");
        }

        @Test
        @DisplayName("Upload file dengan content-type null - throw IllegalArgumentException")
        void uploadFiles_NullContentType_Throws() {
            MultipartFile file = new MockMultipartFile(
                    "files", "unknown", null, "data".getBytes());

            assertThatThrownBy(() -> fileUploadService.uploadFiles(new MultipartFile[]{file}))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Upload membuat upload directory jika belum ada")
        void uploadFiles_CreatesDirectoryIfMissing() throws IOException {
            Path missingDir = tempDir.resolve("nested/uploads");
            ReflectionTestUtils.setField(fileUploadService, "uploadDir", missingDir);

            MultipartFile file = new MockMultipartFile(
                    "files", "a.png", "image/png", "x".getBytes());

            fileUploadService.uploadFiles(new MultipartFile[]{file});

            assertThat(Files.exists(missingDir)).isTrue();
        }
    }

    @Nested
    @DisplayName("deleteFile()")
    class DeleteFileTests {

        @Test
        @DisplayName("Hapus file yang ada - return true")
        void deleteFile_Exists_True() throws IOException {
            Path file = Files.createFile(tempDir.resolve("delete-me.jpg"));

            boolean result = fileUploadService.deleteFile("delete-me.jpg");

            assertThat(result).isTrue();
            assertThat(Files.exists(file)).isFalse();
        }

        @Test
        @DisplayName("Hapus file yang tidak ada - return false")
        void deleteFile_NotExists_False() {
            boolean result = fileUploadService.deleteFile("nonexistent.jpg");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Path traversal (../) - ditolak, return false")
        void deleteFile_PathTraversal_Rejected() throws IOException {
            // Create a sibling file outside the uploadDir
            Path outside = Files.createFile(tempDir.getParent().resolve("outside-" + System.nanoTime() + ".txt"));
            try {
                String traversal = "../" + outside.getFileName().toString();

                boolean result = fileUploadService.deleteFile(traversal);

                assertThat(result).isFalse();
                assertThat(Files.exists(outside)).isTrue();
            } finally {
                Files.deleteIfExists(outside);
            }
        }
    }

    @Nested
    @DisplayName("getUploadDir()")
    class GetUploadDirTests {

        @Test
        @DisplayName("Default uploadDir mengarah ke {user.dir}/uploads")
        void getUploadDir_DefaultPath() {
            FileUploadService freshService = new FileUploadService();

            Path dir = freshService.getUploadDir();

            assertThat(dir).isEqualTo(
                    Paths.get(System.getProperty("user.dir"), "uploads").toAbsolutePath().normalize());
        }
    }
}
