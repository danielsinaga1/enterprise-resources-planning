package id.enterpriseapps.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import id.enterpriseapps.constant.StatusConstant;
import id.enterpriseapps.response.Response;
import id.enterpriseapps.service.FileUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "File Upload", description = "File upload APIs for photos/attachments")
@RestController
@RequestMapping("/api/v1/uploads")
public class FileUploadController {

    @Autowired
    private FileUploadService fileUploadService;

    @Operation(summary = "Upload photos", description = "Upload one or more photo files. Returns list of stored filenames.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Response> uploadFiles(
            @RequestParam("files") MultipartFile[] files) {
        try {
            List<String> filenames = fileUploadService.uploadFiles(files);
            return ResponseEntity.ok(
                    Response.builder()
                            .status(StatusConstant.SUCCESS)
                            .httpStatus(HttpStatus.OK)
                            .data(filenames)
                            .message("Files uploaded successfully")
                            .build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Response.builder()
                            .status(StatusConstant.FAILED)
                            .httpStatus(HttpStatus.BAD_REQUEST)
                            .errorMessage(e.getMessage())
                            .build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    Response.builder()
                            .status(StatusConstant.FAILED)
                            .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                            .errorMessage("Failed to upload files: " + e.getMessage())
                            .build());
        }
    }

    @Operation(summary = "Get a photo", description = "Serve a previously uploaded photo by filename")
    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> getFile(@PathVariable String filename) {
        try {
            Path filePath = fileUploadService.getUploadDir().resolve(filename).normalize();
            if (!filePath.startsWith(fileUploadService.getUploadDir())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }
            Resource resource = new UrlResource(filePath.toUri());
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) contentType = "application/octet-stream";
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Delete a photo", description = "Delete a previously uploaded photo by filename")
    @DeleteMapping("/{filename}")
    public ResponseEntity<Response> deleteFile(@PathVariable String filename) {
        boolean deleted = fileUploadService.deleteFile(filename);
        if (deleted) {
            return ResponseEntity.ok(
                    Response.builder()
                            .status(StatusConstant.SUCCESS)
                            .httpStatus(HttpStatus.OK)
                            .message("File deleted successfully")
                            .build());
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Response.builder()
                            .status(StatusConstant.FAILED)
                            .httpStatus(HttpStatus.NOT_FOUND)
                            .errorMessage("File not found or could not be deleted")
                            .build());
        }
    }
}

