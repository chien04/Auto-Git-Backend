package com.example.auto_git_be.service;

import io.minio.*;
import io.minio.http.Method;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;

    @Getter
    @Value("${minio.bucket-name}")
    private String bucketName;
    
    @Value("${minio.endpoint}")
    private String minioEndpoint;
    
    @Value("${minio.external-url}")
    private String minioExternalUrl;

    public void uploadFile(String objectKey, byte[] fileContent, String contentType) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(fileContent);
            
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .stream(inputStream, fileContent.length, -1)
                            .contentType(contentType)
                            .build()
            );

        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file to MinIO: " + objectKey, e);
        }
    }

    public InputStream downloadFile(String objectKey) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to download file from MinIO: " + objectKey, e);
        }
    }

    public void deleteFile(String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete file from MinIO: " + objectKey, e);
        }
    }
}
