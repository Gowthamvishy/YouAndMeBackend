package org.example.backend.Service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FileTransferService {

    @Async
    public void startFileServer(ServerSocket serverSocket, String filePath, int port, ConcurrentHashMap<Integer, String> availableFiles) {
        File file = new File(filePath);
        if (!file.exists()) {
            System.err.println("File not found to serve: " + filePath);
            return;
        }

        try (serverSocket) {
            System.out.println("Serving file '" + file.getName() + "' on port " + port);
            Socket clientSocket = serverSocket.accept();
            System.out.println("Peer connected for download: " + clientSocket.getInetAddress());

            try (FileInputStream fis = new FileInputStream(file);
                 OutputStream os = clientSocket.getOutputStream()) {

                fis.transferTo(os);

                System.out.println("File '" + file.getName() + "' sent to peer.");
            }

        } catch (IOException e) {
            System.err.println("Error in file sharing server on port " + port + ": " + e.getMessage());
        } finally {
            availableFiles.remove(port);
            try {
                Files.delete(Paths.get(filePath));
                System.out.println("Cleaned up file and port: " + filePath);
            } catch (IOException e) {
                System.err.println("Error cleaning up file: " + filePath);
            }
        }
    }

}

