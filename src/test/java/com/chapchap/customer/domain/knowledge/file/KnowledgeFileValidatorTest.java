package com.chapchap.customer.domain.knowledge.file;

import com.chapchap.customer.global.error.custom.BusinessException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeFileValidatorTest {
    private final KnowledgeFileValidator validator = new KnowledgeFileValidator();

    @Test
    void acceptsParsablePdfWithMatchingSignatureAndContentType() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "refund-policy.pdf",
                "application/pdf",
                createPdf()
        );

        ValidatedKnowledgeFile validatedFile = validator.validate(file);

        assertThat(validatedFile.originalFilename()).isEqualTo("refund-policy.pdf");
        assertThat(validatedFile.contentType()).isEqualTo("application/pdf");
        assertThat(validatedFile.size()).isPositive();
    }

    @Test
    void rejectsPdfExtensionWithInvalidPdfContent() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "not-a-pdf.pdf",
                "application/pdf",
                "not a PDF".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void acceptsUtf8MarkdownWithoutActiveContent() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "refund-policy.md",
                "text/markdown",
                "# 환불 정책\n환불은 결제 수단으로 처리합니다.".getBytes(StandardCharsets.UTF_8)
        );

        ValidatedKnowledgeFile validatedFile = validator.validate(file);

        assertThat(validatedFile.contentType()).isEqualTo("text/markdown");
    }

    @Test
    void rejectsActiveContentDisguisedAsMarkdown() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "policy.md",
                "text/markdown",
                "<script>alert('xss')</script>".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsContentTypeThatDoesNotMatchTextExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "policy.txt",
                "application/pdf",
                "환불 정책".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(BusinessException.class);
    }

    private byte[] createPdf() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }
}
