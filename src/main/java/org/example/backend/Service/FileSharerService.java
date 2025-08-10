package org.example.backend.Service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.example.backend.Utils.UploadUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FileSharerService {

    private final ConcurrentHashMap<Integer, String> availableFiles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Long> portLastUsed = new ConcurrentHashMap<>();
    private final long INACTIVITY_TIMEOUT = 10 * 60 * 1000; // 10 minutes

    @Autowired
    private Cloudinary cloudinary;

    public int offerFile(MultipartFile file) throws IOException {
        Map uploadResult = cloudinary.uploader().upload(file.getBytes(),
                ObjectUtils.asMap(
                        "resource_type", "raw",
                        "type", "upload"
                ));

        String fileUrl = (String) uploadResult.get("secure_url");
        String publicId = (String) uploadResult.get("public_id"); // no extension here

        String storedValue = fileUrl + "|" + publicId; // store URL + publicId

        int port;
        while (true) {
            port = UploadUtils.generateCode();
            if (availableFiles.putIfAbsent(port, storedValue) == null) {
                portLastUsed.put(port, System.currentTimeMillis());
                break;
            }
        }
        return port;
    }

    public String getFilePathForPort(int port) {
        String val = availableFiles.get(port);
        if (val == null) return null;

        String publicId = val.split("\\|")[1]; // no extension

        return cloudinary.url()
                .resourceType("raw")
                .secure(true)
                .signed(true)
                .generate(publicId); // generate without extension
    }

    public void cleanupPort(int port, String storedValue) {
        availableFiles.remove(port);
        portLastUsed.remove(port);

        try {
            String publicId = storedValue.split("\\|")[1];
            cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", "raw"));
            System.out.println("[Cleanup] Deleted from Cloudinary: " + publicId);
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
                String storedValue = availableFiles.get(entry.getKey());
                if (storedValue != null) {
                    cleanupPort(entry.getKey(), storedValue);
                    iterator.remove();
                }
            }
        }
    }

    public String getStoredValue(int port) {
        return availableFiles.get(port);
    }
}
