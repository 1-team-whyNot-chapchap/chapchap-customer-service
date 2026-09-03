package com.chapchap.customer.domain.knowledge.file;

import com.chapchap.customer.global.error.custom.BusinessException;
import com.chapchap.customer.global.response.constant.CustomResponseCode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

@Component
public class KnowledgeFileValidator {
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> MARKDOWN_CONTENT_TYPES = Set.of("text/markdown", "text/x-markdown", "text/plain");

    public ValidatedKnowledgeFile validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_FILE_SIZE) {
            throw invalidFile();
        }

        String originalFilename = file.getOriginalFilename();
        String extension = extensionOf(originalFilename);
        String contentType = normalizeContentType(file.getContentType());
        byte[] content = readContent(file);

        switch (extension) {
            case "pdf" -> validatePdf(contentType, content);
            case "md" -> validateText(contentType, content, MARKDOWN_CONTENT_TYPES);
            case "txt" -> validateText(contentType, content, Set.of("text/plain"));
            default -> throw invalidFile();
        }

        return new ValidatedKnowledgeFile(originalFilename, contentType, content.length, content);
    }

    private void validatePdf(String contentType, byte[] content) {
        if (!"application/pdf".equals(contentType) || !startsWithPdfSignature(content)) {
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

    private void validateText(String contentType, byte[] content, Set<String> allowedContentTypes) {
        if (!allowedContentTypes.contains(contentType)) {
            throw invalidFile();
        }

        String text = decodeUtf8(content);
        if (text.isBlank() || containsActiveContent(text)) {
            throw invalidFile();
        }
    }

    private String decodeUtf8(byte[] content) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw invalidFile();
        }
    }

    private boolean startsWithPdfSignature(byte[] content) {
        return content.length >= 5
                && content[0] == '%'
                && content[1] == 'P'
                && content[2] == 'D'
                && content[3] == 'F'
                && content[4] == '-';
    }

    private boolean containsActiveContent(String text) {
        String lowerCase = text.toLowerCase(Locale.ROOT);
        return lowerCase.contains("<script")
                || lowerCase.contains("<html")
                || lowerCase.contains("<svg")
                || lowerCase.contains("javascript:")
                || lowerCase.indexOf('\u0000') >= 0;
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
        return contentType.toLowerCase(Locale.ROOT);
    }

    private BusinessException invalidFile() {
        return new BusinessException(CustomResponseCode.INVALID_PARAMETER_ERROR, "허용되지 않았거나 손상된 Knowledge 파일입니다.");
    }
}
