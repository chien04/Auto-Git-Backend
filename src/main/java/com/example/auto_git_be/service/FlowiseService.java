package com.example.auto_git_be.service;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FlowiseService {

    private final String FLOWISE_API_URL = "http://localhost:4000/api/v1/prediction/1a5b319c-371c-4cf0-88a9-57b38e0bd912";
    private final RestTemplate restTemplate = new RestTemplate();

    public String processFileWithAi(MultipartFile file, String query) throws Exception {
        // 1. Header tổng của toàn bộ Request
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        // (Tuỳ chọn) Thêm dòng này để xoá cái log UNKNOWN ORIGIN cho đỡ ngứa mắt
        headers.setOrigin("http://localhost:8080");

        // 2. Xử lý File: Bọc file và lấy tên chuẩn
        Resource fileAsResource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        };

        // 3. BƯỚC QUAN TRỌNG NHẤT: Tạo Header riêng cho cái File đó
        HttpHeaders fileHeaders = new HttpHeaders();
        // Lấy chuẩn định dạng từ VS Code gửi lên (VD: application/pdf, text/plain)
        fileHeaders.setContentType(MediaType.parseMediaType(file.getContentType()));

        // Đóng gói File + Header riêng của File thành một khối
        HttpEntity<Resource> filePart = new HttpEntity<>(fileAsResource, fileHeaders);

        // 4. Tạo Body Multipart
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("question", query);
        body.add("files", filePart); // Truyền khối file đã đóng gói cẩn thận vào đây

        // 5. Thực thi Request
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(FLOWISE_API_URL, requestEntity, String.class);
            return response.getBody();
        } catch (Exception e) {
            throw new Exception("Lỗi: " + e.getMessage());
        }
    }
}
