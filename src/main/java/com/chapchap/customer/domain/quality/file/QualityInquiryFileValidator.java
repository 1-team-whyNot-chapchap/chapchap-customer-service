package com.chapchap.customer.domain.quality.file;

import com.chapchap.customer.global.error.custom.quality.QualityInquiryValidationException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import java.util.Map;

@Component
public class QualityInquiryFileValidator {
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp",
            "pdf", "application/pdf"
    );

    public ValidatedQualityInquiryFile validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_FILE_SIZE) {
            throw invalidFile();
        }

        String originalFilename = file.getOriginalFilename();
        String extension = extensionOf(originalFilename);
        String contentType = normalizeContentType(file.getContentType());
        if (!CONTENT_TYPES.containsKey(extension) || !CONTENT_TYPES.get(extension).equals(contentType)) {
            throw invalidFile();
        }

        byte[] content = readContent(file);
        switch (extension) {
            case "jpg", "jpeg" -> validateImage(content, this::startsWithJpegSignature);
            case "png" -> validateImage(content, this::startsWithPngSignature);
            case "webp" -> validateWebp(content);
            case "pdf" -> validatePdf(content);
            default -> throw invalidFile();
        }

        return new ValidatedQualityInquiryFile(originalFilename, contentType, content.length, content);
    }

    private void validateImage(byte[] content, SignatureValidator signatureValidator) {
        if (!signatureValidator.matches(content)) {
            throw invalidFile();
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                throw invalidFile();
            }
        } catch (IOException exception) {
            throw invalidFile();
        }
    }

    private void validateWebp(byte[] content) {
        if (content.length < 20
                || !hasAscii(content, 0, "RIFF")
                || !hasAscii(content, 8, "WEBP")
                || (!hasAscii(content, 12, "VP8 ") && !hasAscii(content, 12, "VP8L") && !hasAscii(content, 12, "VP8X"))) {
            throw invalidFile();
        }

        long declaredSize = Integer.toUnsignedLong(ByteBuffer.wrap(content, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
        long chunkSize = Integer.toUnsignedLong(ByteBuffer.wrap(content, 16, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
        if (declaredSize != content.length - 8L || chunkSize > content.length - 20L) {
            throw invalidFile();
        }
    }

    private void validatePdf(byte[] content) {
        if (!startsWithPdfSignature(content)) {
            throw invalidFile();
        }
        try (PDDocument document = Loader.loadPDF(content)) {
            if (document.isEncrypted()) {
                throw invalidFile();
            }
        } catch (IOException exception) {
            throw invalidFile();
        }
    }

    private boolean startsWithJpegSignature(byte[] content) {
        return content.length >= 3
                && (content[0] & 0xFF) == 0xFF
                && (content[1] & 0xFF) == 0xD8
                && (content[2] & 0xFF) == 0xFF;
    }

    private boolean startsWithPngSignature(byte[] content) {
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        if (content.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (content[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private boolean startsWithPdfSignature(byte[] content) {
        return content.length >= 5
                && content[0] == '%'
                && content[1] == 'P'
                && content[2] == 'D'
                && content[3] == 'F'
                && content[4] == '-';
    }

    private boolean hasAscii(byte[] content, int offset, String value) {
        if (content.length < offset + value.length()) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (content[offset + index] != (byte) value.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private byte[] readContent(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw invalidFile();
        }
    }

    private String extensionOf(String originalFilename) {
        if (originalFilename == null) {
            throw invalidFile();
        }
        int extensionStart = originalFilename.lastIndexOf('.');
        if (extensionStart < 1 || extensionStart == originalFilename.length() - 1) {
            throw invalidFile();
        }
        return originalFilename.substring(extensionStart + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw invalidFile();
        }
        return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private QualityInquiryValidationException invalidFile() {
        return new QualityInquiryValidationException("허용되지 않았거나 손상된 품질 문의 첨부파일입니다.");
    }

    @FunctionalInterface
    private interface SignatureValidator {
        boolean matches(byte[] content);
    }
}
