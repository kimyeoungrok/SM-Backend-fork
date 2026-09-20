package Seoul_Milk.sm_server.global.infrastructure.upload.util;

import Seoul_Milk.sm_server.global.common.exception.CustomException;
import Seoul_Milk.sm_server.global.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.UUID;

@Component
public class AwsS3Util {

    @Value("${minio.endpoint}")
    private String endpoint;

    public String createFileName(String folderName, String fileName, boolean isImage) {
        String extension = getFileExtension(fileName, isImage);

        int lastDotIndex = fileName.lastIndexOf(".");
        String baseName = (lastDotIndex == -1) ? fileName : fileName.substring(0, lastDotIndex);

        String normalizedFileName = Normalizer.normalize(baseName, Normalizer.Form.NFC);
        String safeFileName = normalizedFileName.replaceAll("[^a-zA-Z0-9가-힣]", "_");
        String uniqueFileName = safeFileName + "_" + UUID.randomUUID() + "." + extension;

        return folderName + "/" + uniqueFileName;
    }

    public String getFileExtension(String fileName, boolean isImage) {
        fileName = fileName.trim();

        String fileExtension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();

        List<String> imageExtensions = List.of("jpg", "jpeg", "png", "pdf");
        List<String> documentExtensions = List.of("pdf", "pptx", "hwp", "docx", "xlsx", "txt", "csv", "zip");

        boolean isValidImage = imageExtensions.contains(fileExtension);
        boolean isValidDocument = documentExtensions.contains(fileExtension);

        if (isImage) {
            if (!isValidImage) {
                throw new CustomException(ErrorCode.INVALID_REQUEST);
            }
            return fileExtension;
        } else {
            if (!isValidDocument) {
                throw new CustomException(ErrorCode.INVALID_REQUEST);
            }
        }

        return fileExtension;
    }

    public String buildObjectUrl(String bucket, String objectKey) {
        return trimTrailingSlash(endpoint) + "/" + bucket + "/" + objectKey;
    }

    public String extractS3Key(String bucket, String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        String prefix = trimTrailingSlash(endpoint) + "/" + bucket + "/";
        if (!fileUrl.startsWith(prefix)) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        return fileUrl.substring(prefix.length());
    }

    private String trimTrailingSlash(String value) {
        if (value != null && value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
