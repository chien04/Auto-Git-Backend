package com.example.auto_git_be.service;

import io.minio.*;
import io.minio.http.Method;
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

    /**
     * Download a file from MinIO
     * @param objectKey The key/path of the object in MinIO
     * @return InputStream of the file
     */
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

    /**
     * Generate a presigned URL for downloading a file (valid for 10 minutes)
     * @param objectKey The key/path of the object in MinIO
     * @return Presigned URL (with external URL if configured)
     */
    public String getPresignedUrl(String objectKey) {
        try {
            String presignedUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectKey)
                            .expiry(10, TimeUnit.MINUTES) // 10 minutes expiration for security
                            .build()
            );
            
            // Replace internal endpoint with external URL (for GitHub Actions)
            // This allows GitHub Actions to download from public MinIO endpoint
            if (minioExternalUrl != null && !minioExternalUrl.equals(minioEndpoint)) {
                presignedUrl = presignedUrl.replace(minioEndpoint, minioExternalUrl);
            }
            
            return presignedUrl;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate presigned URL for: " + objectKey, e);
        }
    }

    /**
     * Delete a file from MinIO
     * @param objectKey The key/path of the object in MinIO
     */
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

    /**
     * Check if a file exists in MinIO
     * @param objectKey The key/path of the object in MinIO
     * @return true if exists, false otherwise
     */
    public boolean fileExists(String objectKey) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get file size
     * @param objectKey The key/path of the object in MinIO
     * @return File size in bytes
     */
    public long getFileSize(String objectKey) {
        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
            return stat.size();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get file size from MinIO: " + objectKey, e);
        }
    }
}
