package org.example.backend.Service;

import org.example.backend.Utils.UploadUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FileSharerService {

    private final ConcurrentHashMap<Integer, String> availableFiles = new ConcurrentHashMap<>();
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
            port = UploadUtils.generateCode();
            try {
                serverSocket = new ServerSocket(port);
                if (availableFiles.putIfAbsent(port, filePath.toString()) == null) {
                    break;
                } else {
                    serverSocket.close();
                }
            } catch (IOException e) {
            }
        }

        fileTransferService.startFileServer(serverSocket, filePath.toString(), port, availableFiles);

        return port;
    }

    public String getFilePathForPort(int port) {
        return availableFiles.get(port);
    }
}
