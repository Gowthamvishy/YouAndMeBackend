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
    public ResponseEntity<byte[]> downloadFile(@PathVariable int port) {
        try {
            byte[] fileBytes = fileSharerService.getFileBytesByPort(port);
            String filename = fileSharerService.getFilenameByPort(port);
            String contentType = fileSharerService.getContentTypeByPort(port);

            if (fileBytes == null || filename == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, contentType != null ? contentType : "application/octet-stream")
                    .body(fileBytes);

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @DeleteMapping("/cleanup/{port}")
    public ResponseEntity<?> cleanupPort(@PathVariable int port) {
        var storedValue = fileSharerService.getStoredValue(port);
        if (storedValue == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Port not active.");
        }
        fileSharerService.cleanupPort(port, storedValue);
        return ResponseEntity.ok("Port and file cleaned up.");
    }

} // end of FileController class
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Could not upload the file: " + e.getMessage());
        }
    }

    

    @DeleteMapping("/cleanup/{port}")
    public ResponseEntity<?> cleanupPort(@PathVariable int port) {
        StoredFileInfo storedFileInfo = fileSharerService.getStoredValue(port);
        if (storedFileInfo == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Port not active or file does not exist.");
        }
        fileSharerService.cleanupPort(port, storedFileInfo);
        return ResponseEntity.ok("Port and file cleaned up.");
    }
}
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Could not upload the file: " + e.getMessage());
        }
    }


@GetMapping("/download/{port}")
public ResponseEntity<byte[]> downloadFile(@PathVariable int port) {
    try {
        byte[] fileBytes = fileSharerService.getFileBytesByPort(port);
        String filename = fileSharerService.getFilenameByPort(port);
        String contentType = fileSharerService.getContentTypeByPort(port);

        if (fileBytes == null || filename == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_TYPE, contentType != null ? contentType : "application/octet-stream")
                .body(fileBytes);

    } catch (IOException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(null);
    }
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
