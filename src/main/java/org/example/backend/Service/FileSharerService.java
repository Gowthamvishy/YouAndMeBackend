package org.example.backend.Service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.apache.commons.io.IOUtils;
import org.example.backend.Utils.UploadUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class FileSharerService {

    private final ConcurrentHashMap<Integer, StoredFileInfo> availableFiles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Long> portLastUsed = new ConcurrentHashMap<>();
    private final long INACTIVITY_TIMEOUT = 10 * 60 * 1000; // 10 minutes

    @Autowired
    private Cloudinary cloudinary;

    // Store info for multiple files
    public static class StoredFileInfo {
        public static class FileMeta {
            String publicId;
            String originalFilename;
            String contentType;

            FileMeta(String publicId, String originalFilename, String contentType) {
                this.publicId = publicId;
                this.originalFilename = originalFilename;
                this.contentType = contentType;
            }
        }

        List<FileMeta> files = new ArrayList<>();
    }

    public int offerFiles(List<MultipartFile> files) throws IOException {
        StoredFileInfo info = new StoredFileInfo();

        for (MultipartFile file : files) {
            Map uploadResult = cloudinary.uploader().upload(file.getBytes(),
                    ObjectUtils.asMap(
                            "resource_type", "raw",
                            "type", "upload",
                            "filename_override", file.getOriginalFilename()
                    ));

            String publicId = (String) uploadResult.get("public_id");
            String originalFilename = file.getOriginalFilename();
            String contentType = file.getContentType();

            info.files.add(new StoredFileInfo.FileMeta(publicId, originalFilename, contentType));
        }

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

    public byte[] getFilesAsZip(int port) throws IOException {
        StoredFileInfo info = availableFiles.get(port);
        if (info == null || info.files.isEmpty()) return null;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);

        for (StoredFileInfo.FileMeta meta : info.files) {
            String signedUrl = cloudinary.url()
                    .resourceType("raw")
                    .type("upload")
                    .secure(true)
                    .generate(meta.publicId);

            URL url = new URL(signedUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            try (InputStream in = connection.getInputStream()) {
                zos.putNextEntry(new ZipEntry(meta.originalFilename));
                IOUtils.copy(in, zos);
                zos.closeEntry();
            }
        }

        zos.close();
        return baos.toByteArray();
    }

    public void cleanupPort(int port, StoredFileInfo info) {
        availableFiles.remove(port);
        portLastUsed.remove(port);
        for (StoredFileInfo.FileMeta meta : info.files) {
            try {
                cloudinary.uploader().destroy(meta.publicId, ObjectUtils.asMap("resource_type", "raw"));
                System.out.println("[Cleanup] Deleted: " + meta.publicId);
            } catch (Exception e) {
                System.err.println("[Cleanup Error] " + e.getMessage());
            }
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
