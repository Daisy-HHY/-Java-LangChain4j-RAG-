package com.kgqa.service.rag;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

@Component
public class DocumentLoader {

    private final Tika tika = new Tika();

    private static final List<String> SUPPORTED_TYPES = Arrays.asList(
            "text/plain",
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword"
    );

    public String loadText(MultipartFile file) throws IOException, TikaException {
        String contentType = file.getContentType();
        if (contentType == null || !SUPPORTED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported file type: " + contentType);
        }

        try (InputStream is = file.getInputStream()) {
            return tika.parseToString(is);
        }
    }

    public String loadText(byte[] content, String contentType) throws IOException, TikaException {
        if (contentType == null || !SUPPORTED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported file type: " + contentType);
        }
        try (InputStream is = new java.io.ByteArrayInputStream(content)) {
            return tika.parseToString(is);
        }
    }

    public boolean isSupported(String contentType) {
        return contentType != null && SUPPORTED_TYPES.contains(contentType);
    }
}
