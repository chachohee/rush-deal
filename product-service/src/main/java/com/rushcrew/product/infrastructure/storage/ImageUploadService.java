package com.rushcrew.product.infrastructure.storage;

import com.rushcrew.common.exception.BusinessException;
import com.rushcrew.product.domain.exception.ProductErrorCode;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageUploadService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
        "image/jpeg", "image/png", "image/webp", "image/gif"
    );
    private static final long MAX_SIZE = 5 * 1024 * 1024;

    private final S3Client s3Client;
    private final MinioProperties properties;

    public String upload(MultipartFile file) {
        validate(file);

        String key = "products/" + UUID.randomUUID() + extension(file);
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();
            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException | S3Exception e) {
            log.error("이미지 업로드 실패: key={}", key, e);
            throw new BusinessException(ProductErrorCode.IMAGE_UPLOAD_FAILED);
        }

        return properties.publicEndpoint() + "/" + properties.bucket() + "/" + key;
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ProductErrorCode.IMAGE_EMPTY);
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException(ProductErrorCode.IMAGE_TOO_LARGE);
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessException(ProductErrorCode.IMAGE_TYPE_NOT_SUPPORTED);
        }
    }

    private String extension(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot).toLowerCase() : "";
    }
}
