package org.di.ai_investigator_report.service;

import io.minio.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;
    private final FileCipher fileCipher;

    @Value("${minio.bucket.name:cases}")
    private String bucketName;

    @Value("${minio.url}")
    private String minioUrl;

    private void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucketName).build()
        );
        if (!exists) {
            minioClient.makeBucket(
                    MakeBucketArgs.builder().bucket(bucketName).build()
            );
            log.info("Created bucket: {}", bucketName);
        }
    }

    private String extractObjectNameFromUrl(String fileUrl) {
        return fileUrl.substring(fileUrl.indexOf(bucketName) + bucketName.length() + 1);
    }

    public InputStream downloadFile(String fileUrl) {
        try {
            String objectName = extractObjectNameFromUrl(fileUrl);
            log.info("Downloading file: bucket={}, object={}", bucketName, objectName);
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            log.error("Error downloading file from Minio: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to download file", e);
        }
    }

    public String uploadReportFile(byte[] bytes, String caseNumber, String fileName) {
        try {
            ensureBucketExists();

            byte[] payload;
            String objectFileName;

            if (fileCipher.isEnabled()) {
                payload = fileCipher.encrypt(bytes);
                objectFileName = fileName + ".enc";
            } else {
                payload = bytes;
                objectFileName = fileName;
            }

            String objectName = String.format("%s/report/%s", caseNumber, objectFileName);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(new ByteArrayInputStream(payload), payload.length, -1)
                    .contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                    .build());

            String path = bucketName + "/" + objectName;
            log.info("Report file uploaded{}: {}", fileCipher.isEnabled() ? " (encrypted)" : "", path);
            return path;
        } catch (Exception e) {
            log.error("Error uploading report file: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upload report file", e);
        }
    }
}
