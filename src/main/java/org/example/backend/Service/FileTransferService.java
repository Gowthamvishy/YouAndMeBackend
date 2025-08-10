package org.example.backend.Service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class FileTransferService {

    /**
     * In Cloudinary mode, we don't need to start a socket server.
     * This method is kept for compatibility but does nothing.
     */
    public void startFileServer(Object serverSocket, String filePath, int port, ConcurrentHashMap<Integer, String> availableFiles) {
        System.out.println("[Cloud Mode] No socket server started. File is available at Cloudinary URL: " + filePath);
    }
}
