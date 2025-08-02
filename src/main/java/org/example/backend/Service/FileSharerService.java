package org.example.backend.Service;

import org.example.backend.Utils.UploadUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FileSharerService {

    private final ConcurrentHashMap<Integer, String> availableFiles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Long> portLastUsed = new ConcurrentHashMap<>();

    private final long INACTIVITY_TIMEOUT = 10 * 60 * 1000; // 10 minutes

    private final Path uploadPath;
    private final FileTransferService fileTransferService;

    public FileSharerService(@Value("${peerlink.upload-dir}") String uploadDir,
                             FileTransferService fileTransferService) {
        this.uploadPath = Paths.get(uploadDir);
        this.fileTransferService = fileTransferService;
        try {
            Files.createDirectories(uploadPath);
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory!", e);
        }
    }

    public int offerFile(MultipartFile file) throws IOException {
        String uniqueFilename = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
        Path filePath = this.uploadPath.resolve(uniqueFilename);
        Files.copy(file.getInputStream(), filePath);

        int port;
        ServerSocket serverSocket;
        while (true) {
            port = UploadUtils.generateCode(); // Unique access code generation
            try {
                serverSocket = new ServerSocket(port);
                if (availableFiles.putIfAbsent(port, filePath.toString()) == null) {
                    portLastUsed.put(port, System.currentTimeMillis());
                    break;
                } else {
                    serverSocket.close();
                }
            } catch (IOException e) {
                // Port might be in use, retry with a different code
            }
        }

        fileTransferService.startFileServer(serverSocket, filePath.toString(), port, availableFiles);
        return port;
    }

    public String getFilePathForPort(int port) {
        return availableFiles.get(port);
    }

    public void cleanupPort(int port, String filePath) {
        availableFiles.remove(port);
        portLastUsed.remove(port);
        try {
            Files.deleteIfExists(Paths.get(filePath));
            System.out.println("[Cleanup] File deleted and port closed: " + port);
        } catch (IOException e) {
            System.err.println("[Cleanup Error] Could not delete file for port " + port + ": " + e.getMessage());
        }
    }

    public void updatePortUsage(int port) {
        portLastUsed.put(port, System.currentTimeMillis());
    }

    // Check every 1 minute for inactive ports and clean them up
    @Scheduled(fixedRate = 60_000)
    public void cleanUpInactivePorts() {
        long now = System.currentTimeMillis();
        System.out.println("[Scheduler] Checking for inactive ports...");

        Iterator<Map.Entry<Integer, Long>> iterator = portLastUsed.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Long> entry = iterator.next();
            int port = entry.getKey();
            long lastUsed = entry.getValue();

            if (now - lastUsed > INACTIVITY_TIMEOUT) {
                String filePath = availableFiles.get(port);
                if (filePath != null) {
                    cleanupPort(port, filePath);
                    iterator.remove();
                }
            }
        }

       // System.out.println("[Scheduler] Cleanup check complete. Active ports: " + availableFiles.size());
    }
        }
