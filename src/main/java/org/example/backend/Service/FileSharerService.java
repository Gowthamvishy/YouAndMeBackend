@Service
public class FileSharerService {

    private final ConcurrentHashMap<Integer, StoredPortInfo> availablePorts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Long> portLastUsed = new ConcurrentHashMap<>();
    private final long INACTIVITY_TIMEOUT = 10 * 60 * 1000; // 10 minutes

    @Autowired
    private Cloudinary cloudinary;

    // Port can hold multiple files
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

    private static class StoredPortInfo {
        Map<String, StoredFileInfo> files = new ConcurrentHashMap<>();
    }

    public int offerFiles(MultipartFile[] files) throws IOException {
        StoredPortInfo portInfo = new StoredPortInfo();

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

            StoredFileInfo info = new StoredFileInfo(publicId, originalFilename, contentType);
            portInfo.files.put(originalFilename, info);
        }

        int port;
        while (true) {
            port = UploadUtils.generateCode();
            if (availablePorts.putIfAbsent(port, portInfo) == null) {
                portLastUsed.put(port, System.currentTimeMillis());
                break;
            }
        }
        return port;
    }

    public byte[] getFileBytes(int port, String filename) throws IOException {
        StoredPortInfo portInfo = availablePorts.get(port);
        if (portInfo == null) return null;

        StoredFileInfo info = portInfo.files.get(filename);
        if (info == null) return null;

        String signedUrl = cloudinary.url()
                .resourceType("raw")
                .type("upload")
                .secure(true)
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

    public Map<String, String> listFiles(int port) {
        StoredPortInfo portInfo = availablePorts.get(port);
        if (portInfo == null) return null;

        Map<String, String> response = new ConcurrentHashMap<>();
        for (String filename : portInfo.files.keySet()) {
            response.put(filename, filename); // you can also add full URL if needed
        }
        return response;
    }

    public void cleanupPort(int port) {
        StoredPortInfo portInfo = availablePorts.remove(port);
        portLastUsed.remove(port);

        if (portInfo != null) {
            for (StoredFileInfo info : portInfo.files.values()) {
                try {
                    cloudinary.uploader().destroy(info.publicId, ObjectUtils.asMap("resource_type", "raw"));
                    System.out.println("[Cleanup] Deleted from Cloudinary: " + info.publicId);
                } catch (Exception e) {
                    System.err.println("[Cleanup Error] Could not delete file: " + e.getMessage());
                }
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
                cleanupPort(entry.getKey());
                iterator.remove();
            }
        }
    }
}
