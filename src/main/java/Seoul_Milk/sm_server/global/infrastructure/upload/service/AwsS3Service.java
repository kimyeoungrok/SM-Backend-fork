package Seoul_Milk.sm_server.global.infrastructure.upload.service;

import Seoul_Milk.sm_server.global.common.exception.CustomException;
import Seoul_Milk.sm_server.global.common.exception.ErrorCode;
import Seoul_Milk.sm_server.global.infrastructure.upload.util.AwsS3Util;
import Seoul_Milk.sm_server.global.common.util.CustomMultipartFile;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AwsS3Service {
    @Value("${minio.bucket}")
    private String bucket;

    private final AwsS3Util awsS3Util;
    private final S3Client s3Client;

    public List<String> uploadFiles(String folderName, List<MultipartFile> files, boolean isImage) {
        return uploadFilesToFolder(folderName, files, isImage);
    }

    public String uploadFile(String folderName, MultipartFile file, boolean isImage) {
        return uploadFileToFolder(folderName, file, isImage);
    }

    private List<String> uploadFilesToFolder(String folderName, List<MultipartFile> files, boolean isImage) {
        long startTime = System.currentTimeMillis();
        List<String> fileUrlList = new ArrayList<>();

        files.forEach(file -> {
            String fileName = awsS3Util.createFileName(folderName, file.getOriginalFilename(), isImage);

            try (InputStream inputStream = file.getInputStream()) {
                s3Client.putObject(PutObjectRequest.builder()
                                .bucket(bucket)
                                .key(fileName)
                                .contentType(file.getContentType())
                                .build(),
                        RequestBody.fromInputStream(inputStream, file.getSize())
                );
            } catch (IOException e) {
                throw new CustomException(ErrorCode.UPLOAD_FAILED);
            }

            fileUrlList.add(awsS3Util.buildObjectUrl(bucket, fileName));
        });

        long endTime = System.currentTimeMillis();
        long totalDuration = endTime - startTime;
        System.out.println("전체 파일 업로드 소요 시간: " + totalDuration + "ms");

        return fileUrlList;
    }

    private String uploadFileToFolder(String folderName, MultipartFile file, boolean isImage) {
        String fileName = awsS3Util.createFileName(folderName, file.getOriginalFilename(), isImage);

        try (InputStream inputStream = file.getInputStream()) {
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(fileName)
                            .contentType(file.getContentType())
                            .build(),
                    RequestBody.fromInputStream(inputStream, file.getSize())
            );
        } catch (IOException e) {
            throw new CustomException(ErrorCode.UPLOAD_FAILED);
        }

        return awsS3Util.buildObjectUrl(bucket, fileName);
    }

    public void deleteFile(String fileUrl) {
        try {
            String fileKey = awsS3Util.extractS3Key(bucket, fileUrl);

            System.out.println("삭제할 파일 경로: " + fileKey);
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileKey)
                    .build()
            );
            System.out.println("S3 파일 삭제 완료: " + fileKey);

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("[ERROR] S3 파일 삭제 실패: " + e.getMessage());
            throw new CustomException(ErrorCode.DELETE_FAILED);
        }
    }

    public MultipartFile downloadFileFromS3(String fileUrl) {
        try {
            System.out.println("[DEBUG] S3에서 파일 다운로드 시작: " + fileUrl);

            String fileKey = awsS3Util.extractS3Key(bucket, fileUrl);

            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileKey)
                    .build();

            ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getObjectRequest);
            byte[] fileData = IOUtils.toByteArray(s3Object);
            String contentType = s3Object.response().contentType();

            System.out.println("[DEBUG] S3 파일 다운로드 완료: " + fileKey);

            return new CustomMultipartFile(fileData, fileKey, contentType);

        } catch (IOException e) {
            System.out.println("[ERROR] S3 파일 다운로드 실패: " + e.getMessage());
            throw new RuntimeException("S3 파일 다운로드 실패", e);
        }
    }

    public String moveFileToFinalFolder(String fileUrl, String destinationFolder) {
        String sourceKey = awsS3Util.extractS3Key(bucket, fileUrl);
        String fileName = sourceKey.substring(sourceKey.lastIndexOf("/") + 1);
        String destinationKey = destinationFolder + "/" + fileName;

        try {
            s3Client.copyObject(CopyObjectRequest.builder()
                    .sourceBucket(bucket)
                    .sourceKey(sourceKey)
                    .destinationBucket(bucket)
                    .destinationKey(destinationKey)
                    .build()
            );

            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(sourceKey)
                    .build()
            );

            return awsS3Util.buildObjectUrl(bucket, destinationKey);
        } catch (NoSuchKeyException e) {
            System.out.println("[ERROR] 이동한 파일이 S3에 존재하지 않습니다: " + destinationKey);
            throw new CustomException(ErrorCode.S3_FILE_NOT_FOUND);
        } catch (Exception e) {
            System.out.println("[ERROR] S3 파일 이동 중 오류 발생: " + e.getMessage());
            throw new CustomException(ErrorCode.S3_FILE_MOVE_FAILED);
        }
    }
}
