package com.chapchap.customer.global.minio;


import com.chapchap.auth.global.error.custom.business.FileManagedException;
import com.chapchap.auth.global.error.custom.business.InvalidParameterException;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MinioManager {
    private static final long MAX_PROFILE_IMAGE_SIZE = 5L * 1024 * 1024;
    private final MinioConfig minioConfig;
    private final MinioClient minioClient;

    /**
     * 파일 확장자 추출 및 파일 검증
     * @param file
     * @return 확장자(소문자)
     */
    public String extractExtension(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileManagedException("파일 업로드 실패: 파일없음");
        }
        String fileName = file.getOriginalFilename();
        if(fileName == null || !fileName.contains(".")) {
            throw new FileManagedException("파일 업로드 실패: 파일명 이상");
        }

        String fileExtension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();

        // 허용 확장자 검증
        if(!minioConfig.allowImageExtensions().contains("image/" + fileExtension)){
            throw new FileManagedException("파일 업로드 실패: 허용하지 않는 확장자");
        }

        return fileExtension;
    }

    public String generateFileName() {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        LocalDate now =LocalDate.now();
        return now.format(dateFormatter) + "_" + UUID.randomUUID();
    }

    public String generateObjectKey(MultipartFile file) {
        Path path = Path.of(minioConfig.minioProfilePath(), this.generateFileName() + "." + this.extractExtension(file));
        return path.toString().replace(File.separator, "/");
    }

    public String generateProfileImageObjectKey(MultipartFile file) {
        String extension = validateProfileImage(file);
        return Path.of(minioConfig.minioProfilePath(), generateFileName() + "." + extension)
                .toString()
                .replace(File.separator, "/");
    }

    public String validateProfileImage(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_PROFILE_IMAGE_SIZE) {
            throw new InvalidParameterException("프로필 이미지는 비어 있지 않은 5MB 이하 파일이어야 합니다.");
        }

        try {
            byte[] bytes = file.getBytes();
            String detectedMimeType = detectProfileImageMimeType(bytes);
            String declaredMimeType = file.getContentType();
            if (!detectedMimeType.equalsIgnoreCase(declaredMimeType)) {
                throw new InvalidParameterException("프로필 이미지 MIME Type과 파일 형식이 일치하지 않습니다.");
            }
            return switch (detectedMimeType) {
                case "image/jpeg" -> "jpg";
                case "image/png" -> "png";
                case "image/webp" -> "webp";
                default -> throw new InvalidParameterException("허용하지 않는 프로필 이미지 형식입니다.");
            };
        } catch (IOException e) {
            throw new InvalidParameterException("프로필 이미지 파일을 읽을 수 없습니다.");
        }
    }

    private String detectProfileImageMimeType(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes, 8),
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A})) {
            return "image/png";
        }
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        throw new InvalidParameterException("JPEG, PNG, WebP 프로필 이미지만 업로드할 수 있습니다.");
    }

    public void uploadFile(String objectKey, MultipartFile file) {
        try(InputStream inputStream = file.getInputStream()) {
            minioClient
                .putObject(
                    PutObjectArgs.builder()
                        .bucket(minioConfig.minioBucket())  // 파일이 저장될 MinIO의 버킷명
                        .object(objectKey)  // 버킷 내부에서 관리될 전체 저장 경로
                        .stream(
                            inputStream,  // 업로드할 파일의 InputStream
                            file.getSize(),  // 업로드할 파일의 크기
                            -1  // 업로드시 패킷 크기(-1은 MinIO SDK가 적절하게 조절해서 전송)
                        )
                        .contentType(file.getContentType())  // 파일의 MIME 타입
                        .build()
                );
        } catch (Exception e) {
            throw new FileManagedException("파일 업로드 실패: MinIo 업로드 실패, " + objectKey + "\n" + e.getMessage());
        }
    }

    public String createMinioObjectUri(String objectKey){
        Path path = Path.of(minioConfig.minioBucket(), objectKey);
        return String.format(
            "%s/%s",
            minioConfig.minioEndpoint(),
            path.toString().replace(File.separator, "/")
        );
    }

    public void deleteFile(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioConfig.minioBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            throw new FileManagedException("MinIO 파일 삭제에 실패했습니다.");
        }
    }

}
