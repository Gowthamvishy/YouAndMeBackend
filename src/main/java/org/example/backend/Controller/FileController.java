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

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Please select a file to upload.");
        }
        try {
            int port = fileSharerService.offerFile(file);
            Map<String, Integer> response = Collections.singletonMap("port", port);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Could not upload the file: " + e.getMessage());
        }
    }

    @GetMapping("/download/{port}")
    public ResponseEntity<?> downloadFile(@PathVariable int port) {
        String fileUrl = fileSharerService.getFilePathForPort(port);
        if (fileUrl == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, fileUrl)
                .build();
    }

    @DeleteMapping("/cleanup/{port}")
    public ResponseEntity<?> cleanupPort(@PathVariable int port) {
        String storedValue = fileSharerService.getStoredValue(port);
        if (storedValue == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Port not active.");
        }
        fileSharerService.cleanupPort(port, storedValue);
        return ResponseEntity.ok("Port and file cleaned up.");
    }
}
