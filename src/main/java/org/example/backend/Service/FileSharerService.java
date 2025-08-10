package org.example.backend.Service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.apache.commons.io.IOUtils;
import org.example.backend.Utils.UploadUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FileSharerService {

    private final ConcurrentHashMap<Integer, StoredFileInfo> availableFiles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Long> portLastUsed = new ConcurrentHashMap<>();
    private final long INACTIVITY_TIMEOUT = 10 * 60 * 1000; // 10 minutes

    @Autowired
    private Cloudinary cloudinary;

    // Store all info needed to serve file properly
    private static class StoredFileInfo {
        String publicId;
        String originalFilename;
        String contentType;

        StoredFileInfo(String publicId, String originalFilename, String contentType) {
            this.publicId = publicId;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
        }
    }

    public int offerFile(MultipartFile file) throws IOException {
        Map uploadResult = cloudinary.uploader().upload(file.getBytes(),
                ObjectUtils.asMap(
                        "resource_type", "raw",
                        "type", "upload",
                        "filename_override", file.getOriginalFilename()
                ));

        String publicId = (String) uploadResult.get("public_id");
        String originalFilename = file.getOriginalFilename();
        String contentType = file.getContentType();

        StoredFileInfo info = new StoredFileInfo(publicId, originalFilename, contentType);

        int port;
        while (true) {
            port = UploadUtils.generateCode();
            if (availableFiles.putIfAbsent(port, info) == null) {
                portLastUsed.put(port, System.currentTimeMillis());
                break;
            }
        }
        return port;
    }

    public byte[] getFileBytesByPort(int port) throws IOException {
    StoredFileInfo info = availableFiles.get(port);
    if (info == null) return null;

    // Generate signed URL to access the file securely from Cloudinary
  String signedUrl = cloudinary.url()
    .resourceType("raw")
    .type("upload")
    .sign(true)
    .generate(info.publicId);


    URL url = new URL(signedUrl);
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setRequestMethod("GET");
    connection.setDoInput(true);
    connection.connect();

    try (InputStream inputStream = connection.getInputStream()) {
        return IOUtils.toByteArray(inputStream);
    }
}


    public String getFilenameByPort(int port) {
        StoredFileInfo info = availableFiles.get(port);
        return info != null ? info.originalFilename : null;
    }

    public String getContentTypeByPort(int port) {
        StoredFileInfo info = availableFiles.get(port);
        return info != null ? info.contentType : null;
    }

    public void cleanupPort(int port, StoredFileInfo info) {
        availableFiles.remove(port);
        portLastUsed.remove(port);

        try {
            cloudinary.uploader().destroy(info.publicId, ObjectUtils.asMap("resource_type", "raw"));
            System.out.println("[Cleanup] Deleted from Cloudinary: " + info.publicId);
        } catch (Exception e) {
            System.err.println("[Cleanup Error] Could not delete file: " + e.getMessage());
        }
    }

    @Scheduled(fixedRate = 60_000)
    public void cleanUpInactivePorts() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Integer, Long>> iterator = portLastUsed.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Integer, Long> entry = iterator.next();
            if (now - entry.getValue() > INACTIVITY_TIMEOUT) {
                StoredFileInfo info = availableFiles.get(entry.getKey());
                if (info != null) {
                    cleanupPort(entry.getKey(), info);
                    iterator.remove();
                }
            }
        }
    }

    public StoredFileInfo getStoredValue(int port) {
        return availableFiles.get(port);
    }
}
