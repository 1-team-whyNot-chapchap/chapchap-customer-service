package com.chapchap.customer.domain.quality.file;

import com.chapchap.customer.global.error.custom.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QualityInquiryFileValidatorTest {
    private final QualityInquiryFileValidator validator = new QualityInquiryFileValidator();

    @Test
    void acceptsDecodablePngWithMatchingExtensionAndContentType() throws IOException {
        MockMultipartFile file = new MockMultipartFile("attachments", "damage.png", "image/png", createPng());

        ValidatedQualityInquiryFile validatedFile = validator.validate(file);

        assertThat(validatedFile.originalFilename()).isEqualTo("damage.png");
        assertThat(validatedFile.contentType()).isEqualTo("image/png");
        assertThat(validatedFile.size()).isPositive();
    }

    @Test
    void acceptsParsedWebpContainerWithMatchingSignature() {
        MockMultipartFile file = new MockMultipartFile("attachments", "damage.webp", "image/webp", createWebpContainer());

        ValidatedQualityInquiryFile validatedFile = validator.validate(file);

        assertThat(validatedFile.contentType()).isEqualTo("image/webp");
    }

    @Test
    void rejectsImageExtensionWithInvalidContent() {
        MockMultipartFile file = new MockMultipartFile("attachments", "damage.png", "image/png", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsMismatchedContentType() throws IOException {
        MockMultipartFile file = new MockMultipartFile("attachments", "damage.jpg", "image/png", createPng());

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(BusinessException.class);
    }

    private byte[] createPng() throws IOException {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }

    private byte[] createWebpContainer() {
        byte[] content = new byte[26];
        System.arraycopy("RIFF".getBytes(), 0, content, 0, 4);
        ByteBuffer.wrap(content, 4, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(18);
        System.arraycopy("WEBPVP8L".getBytes(), 0, content, 8, 8);
        ByteBuffer.wrap(content, 16, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(6);
        content[20] = 0x2F;
        return content;
    }
}
