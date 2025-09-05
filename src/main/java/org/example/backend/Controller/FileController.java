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
import java.util.List;
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

    @PostMapping("/upload-multiple")
    public ResponseEntity<?> uploadMultiple(@RequestParam("files") List<MultipartFile> files) {
        if (files.isEmpty()) {
            return ResponseEntity.badRequest().body("Please select files to upload.");
        }
        try {
            int port = fileSharerService.offerFiles(files);
            Map<String, Integer> response = Collections.singletonMap("port", port);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Could not upload the files: " + e.getMessage());
        }
    }

    @GetMapping("/download/{port}")
    public ResponseEntity<?> downloadFiles(@PathVariable int port) {
        try {
            byte[] zipBytes = fileSharerService.getFilesAsZip(port);
            if (zipBytes == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Files not found or expired for port: " + port);
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"files.zip\"")
                    .header(HttpHeaders.CONTENT_TYPE, "application/zip")
                    .body(zipBytes);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to download files: " + e.getMessage());
        }
    }

    @DeleteMapping("/cleanup/{port}")
    public ResponseEntity<?> cleanupPort(@PathVariable int port) {
        var storedValue = fileSharerService.getStoredValue(port);
        if (storedValue == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Port not active.");
        }
        fileSharerService.cleanupPort(port, storedValue);
        return ResponseEntity.ok("Port and files cleaned up.");
    }
}
