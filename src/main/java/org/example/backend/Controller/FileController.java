package org.example.backend.Controller;

import org.example.backend.Service.FileSharerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class FileController {

    private final FileSharerService fileSharerService;

    @Autowired
    public FileController(FileSharerService fileSharerService) {
        this.fileSharerService = fileSharerService;
    }

    // ✅ Upload multiple files under one port
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFiles(@RequestParam("files") MultipartFile[] files) {
        if (files.length == 0) {
            return ResponseEntity.badRequest().body("Please select at least one file to upload.");
        }
        try {
            int port = fileSharerService.offerFiles(files);
            Map<String, Object> response = Map.of(
                    "port", port,
                    "files", fileSharerService.listFiles(port)
            );
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Could not upload files: " + e.getMessage());
        }
    }

    // ✅ Download a specific file by port + filename
    @GetMapping("/download/{port}/{filename}")
    public ResponseEntity<?> downloadFile(@PathVariable int port, @PathVariable String filename) {
        try {
            byte[] fileBytes = fileSharerService.getFileBytes(port, filename);
            if (fileBytes == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("File not found for port: " + port + " and filename: " + filename);
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
                    .body(fileBytes);

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to download file: " + e.getMessage());
        }
    }

    @DeleteMapping("/cleanup/{port}")
    public ResponseEntity<?> cleanupPort(@PathVariable int port) {
        fileSharerService.cleanupPort(port);
        return ResponseEntity.ok("Port and files cleaned up.");
    }
}
