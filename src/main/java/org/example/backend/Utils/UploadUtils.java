package org.example.backend.Utils;

public class UploadUtils {
    public static int generateCode() {
        return 1024 + new java.util.Random().nextInt(65536 - 1024);
    }
}