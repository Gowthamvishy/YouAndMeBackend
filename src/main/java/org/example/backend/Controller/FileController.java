package org.example.backend.Controller;

import org.example.backend.Service.FileSharerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
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
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Could not upload the file: " + e.getMessage());
        }
    }

    @GetMapping("/download/{port}")
    public ResponseEntity<Resource> downloadFile(@PathVariable int port) throws IOException {
        String filePath = fileSharerService.getFilePathForPort(port);
        if (filePath == null) {
            return ResponseEntity.notFound().build();
        }
        File file = new File(filePath);
        InputStreamResource resource = new InputStreamResource(new FileInputStream(file));
        String originalFilename = file.getName().substring(file.getName().indexOf('_') + 1);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + originalFilename + "\"")
                .header(HttpHeaders.CONTENT_TYPE, Files.probeContentType(file.toPath()))
                .contentLength(file.length())
                .body(resource);
    }

     @DeleteMapping("/cleanup/{port}")
    public ResponseEntity<?> cleanupPort(@PathVariable int port) {
        String filePath = fileSharerService.getFilePathForPort(port);
        if (filePath == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Port not active.");
        }

        fileSharerService.cleanupPort(port, filePath);
        return ResponseEntity.ok("Port and file cleaned up.");
    }

    @PostMapping("/cleanup/{port}")
    public ResponseEntity<?> cleanUpTabClose(@PathVariable int port) {
        System.out.println("Cleanup");

        String filePath = fileSharerService.getFilePathForPort(port);
        if (filePath == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Port not active.");
        }

        fileSharerService.cleanupPort(port, filePath);
        return ResponseEntity.ok("Port and file cleaned up.");
    }

}
