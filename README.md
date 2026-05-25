# Hướng Dẫn Cài Đặt Backend

## 1. Clone Repository Backend

Chọn một thư mục làm việc, sau đó clone backend:

```powershell
git clone https://github.com/chien04/Auto-Git-Backend.git
```

## 2. Yêu Cầu Môi Trường

Cài đặt trước:

| Thành phần | Phiên bản khuyến nghị | Mục đích |
|---|---:|---|
| Java JDK | 21 trở lên | Chạy Spring Boot |
| Git | Bản mới nhất | Quản lý mã nguồn và thao tác GitHub |
| Docker Desktop | Bản mới nhất | Chạy PostgreSQL, Redis, MinIO, Qdrant |
| Maven Wrapper | Có sẵn trong repo | Build và chạy backend |

Kiểm tra:

```powershell
java -version
git -v
docker --version
docker compose version
```

## 3. Khởi Động Hạ Tầng Docker

Backend cần các dịch vụ:

| Dịch vụ | Cổng | Vai trò |
|---|---:|---|
| PostgreSQL | 5432 | Lưu dữ liệu hệ thống |
| Redis | 6379 | Cache OTP, lịch sử chat, hash file |
| MinIO | 9000, 9001 | Lưu test case và file lớn |
| Qdrant | 6333, 6334 | Lưu vector cho AI |

Chạy:

```powershell
cd Auto-Git-Backend
docker compose up -d
```

Kiểm tra container:

```powershell
docker compose ps
```

MinIO Console:

```text
http://localhost:9001
```

## 4. Cấu Hình Biến Môi Trường

File `src/main/resources/application.properties` đọc cấu hình từ biến môi trường. Tạo file `.env` trong thư mục backend với mẫu sau:

```env
DB_NAME=mydb
DB_URL=jdbc:postgresql://localhost:5432/mydb
DB_USERNAME=postgres
DB_PASSWORD=postgres

JWT_SECRET=replace-with-a-long-random-secret

GOOGLE_CLIENT_ID=replace-with-google-client-id
GOOGLE_CLIENT_SECRET=replace-with-google-client-secret
GOOGLE_REDIRECT_URL=http://localhost:8080/api/auth/google/callback

GITHUB_TOKEN=replace-with-github-personal-access-token
BACKEND_URL=http://localhost:8080

OPENAI=replace-with-openai-api-key

MINIO_ENDPOINT=http://localhost:9000
MINIO_EXTERNAL_URL=http://localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
MINIO_BUCKET_NAME=auto-git

EMAIL_USERNAME=your-email@gmail.com
EMAIL_DB_PASSWORD=your-gmail-app-password

JUDGE0_POLLING_MAX_WAIT_MS=60000
```

## 5. Cấu Hình Google OAuth

Trong Google Cloud Console:

1. Tạo project mới.
2. Vào `APIs & Services`.
3. Tạo OAuth 2.0 Client ID loại `Web application`.
4. Thêm redirect URI:

```text
http://localhost:8080/login/oauth2/code/google
http://localhost:8080/api/auth/google/callback
```

Lưu giá trị vào:

```env
GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...
```

## 6. Cấu Hình GitHub Token

Backend dùng GitHub API để tạo repository, tạo branch, push code và đồng bộ bài nộp.

Tạo Personal Access Token tại:

```text
https://github.com/settings/tokens
```

Quyền cần có:

- `repo`
- `delete_repo` nếu hệ thống cần xóa repository

Cấu hình:

```env
GITHUB_TOKEN=...
```

## 7. Cấu Hình Email OTP

Nếu dùng Gmail:

1. Bật `2-Step Verification`.
2. Tạo `App Password`.
3. Cấu hình:

```env
EMAIL_USERNAME=your-email@gmail.com
EMAIL_DB_PASSWORD=your-gmail-app-password
```

## 8. Cài Judge0

Chọn thư mục:

```powershell
git clone https://github.com/judge0/judge0.git
```

Chạy lệnh:

```powershell
docker compose up -d
```

Backend gọi Judge0 tại:

```text
http://localhost:2358
```

Nếu dùng chức năng Run Code hoặc Submit Code, cần chạy Judge0 API ở cổng này.

## 9. Cài Ollama Cho AI Embedding

Backend dùng Ollama tại:

```text
http://localhost:11434
```

Model embedding:

```text
bge-m3
```

Tải model:

```powershell
ollama pull bge-m3
```

## 10. Build Backend

```powershell
cd Auto-Git-Backend
.\mvnw.cmd -DskipTests compile
```

## 11. Chạy Backend

```powershell
cd Auto-Git-Backend
.\mvnw.cmd spring-boot:run
```

Backend chạy ở:

```text
http://localhost:8080
```

## 12. Kiểm Tra Nhanh

Sau khi chạy backend:

1. Kiểm tra Docker container còn hoạt động:

```powershell
docker compose ps
```

2. Kiểm tra log backend không có lỗi kết nối PostgreSQL, Redis, MinIO hoặc Qdrant.
3. Mở MinIO Console ở `http://localhost:9001`.
4. Đảm bảo Judge0 chạy ở `localhost:2358` nếu cần chấm code.
5. Đảm bảo Ollama chạy ở `localhost:11434` nếu dùng AI embedding.

## 13. Lỗi Thường Gặp

### Không kết nối được PostgreSQL

Kiểm tra:

```env
DB_URL
DB_USERNAME
DB_PASSWORD
```

Và kiểm tra container:

```powershell
docker compose ps
```

### MinIO lỗi credential hoặc bucket

Kiểm tra:

```env
MINIO_ACCESS_KEY
MINIO_SECRET_KEY
MINIO_BUCKET_NAME
MINIO_ENDPOINT
```

### Submit hoặc Run Code không hoạt động

Kiểm tra Judge0:

```text
http://localhost:2358
```

### AI không hoạt động

Kiểm tra:

- `OPENAI` hợp lệ.
- Qdrant chạy ở `localhost:6334`.
- Ollama chạy ở `localhost:11434`.
- Model `bge-m3` đã được tải.

```powershell
ollama list
```

