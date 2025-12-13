# Auto Git Classroom - Backend

Spring Boot backend cho hệ thống quản lý lớp học tự động.

## Tính năng

- 🔐 Google OAuth 2.0 Authentication
- 🗄️ PostgreSQL Database
- 🔑 JWT Token Authentication
- 🐙 GitHub API Integration
- 📚 Classroom Management
- 👥 Student Branch Management

## Yêu cầu

- Java 21+
- PostgreSQL 14+
- Maven 3.8+
- GitHub Personal Access Token hoặc GitHub App

## Cấu hình

### 1. PostgreSQL Database

Tạo database:

```sql
CREATE DATABASE autogit_db;
```

### 2. Google OAuth

1. Truy cập [Google Cloud Console](https://console.cloud.google.com/)
2. Tạo project mới
3. Enable Google+ API
4. Tạo OAuth 2.0 Client ID
5. Thêm redirect URI: `http://localhost:8080/api/auth/google/callback`
6. Lưu Client ID và Client Secret

### 3. GitHub Token

#### Option 1: Personal Access Token (Đơn giản)

1. Truy cập GitHub Settings > Developer settings > Personal access tokens > Tokens (classic)
2. Generate new token với scopes:
   - `repo` (Full control of private repositories)
   - `admin:repo_hook` (Full control of repository hooks)
   - `delete_repo` (Delete repositories)
3. Copy token

#### Option 2: GitHub App (Khuyến nghị cho production)

1. Truy cập GitHub Settings > Developer settings > GitHub Apps
2. Tạo GitHub App mới
3. Cấu hình permissions:
   - Repository permissions: Contents (Read & Write), Metadata (Read)
   - Organization permissions: Members (Read)
4. Generate private key
5. Install app vào organization/account

### 4. application.properties

Cập nhật file `src/main/resources/application.properties`:

```properties
# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/autogit_db
spring.datasource.username=postgres
spring.datasource.password=your_password

# JWT Secret (tạo key mạnh)
jwt.secret=your-256-bit-secret-key-here

# Google OAuth
spring.security.oauth2.client.registration.google.client-id=your-client-id
spring.security.oauth2.client.registration.google.client-secret=your-client-secret

# GitHub Token
github.app.token=your-github-token
github.organization=your-org-name (optional)
```

## Chạy Application

### Development Mode

```bash
cd auto-git-be
mvn spring-boot:run
```

### Build JAR

```bash
mvn clean package
java -jar target/auto-git-be-0.0.1-SNAPSHOT.jar
```

## API Endpoints

### Authentication

#### `GET /api/auth/google/url`
Lấy Google OAuth URL

**Response:**
```json
{
  "authUrl": "https://accounts.google.com/o/oauth2/v2/auth?..."
}
```

#### `POST /api/auth/google/callback`
Xử lý Google OAuth callback

**Request:**
```json
{
  "code": "authorization_code"
}
```

**Response:**
```json
{
  "token": "jwt_token",
  "email": "user@example.com",
  "name": "User Name",
  "userId": "123",
  "role": "BOTH"
}
```

#### `GET /api/auth/verify`
Verify JWT token

**Headers:** `Authorization: Bearer {token}`

### Classroom Management

#### `POST /api/class/create`
Tạo lớp học mới (Teacher)

**Headers:** `Authorization: Bearer {token}`

**Request:**
```json
{
  "className": "OOP-2024"
}
```

**Response:**
```json
{
  "classId": "1",
  "classCode": "ABC12345",
  "repoUrl": "https://github.com/owner/oop-2024-abc12345",
  "className": "OOP-2024"
}
```

#### `POST /api/class/join`
Tham gia lớp học (Student)

**Headers:** `Authorization: Bearer {token}`

**Request:**
```json
{
  "studentName": "Nguyen Van A",
  "classCode": "ABC12345"
}
```

**Response:**
```json
{
  "repoUrl": "https://github.com/owner/oop-2024-abc12345",
  "branch": "student/123",
  "token": "github_token",
  "studentId": "456"
}
```

#### `GET /api/class/{classCode}`
Lấy thông tin lớp học

**Headers:** `Authorization: Bearer {token}`

#### `GET /api/class/{classCode}/students`
Lấy danh sách sinh viên (Teacher)

**Headers:** `Authorization: Bearer {token}`

#### `GET /api/class/my-classes`
Lấy danh sách lớp học của user

**Headers:** `Authorization: Bearer {token}`

## Database Schema

### Users Table
```sql
CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  email VARCHAR(255) UNIQUE NOT NULL,
  name VARCHAR(255) NOT NULL,
  google_id VARCHAR(255) UNIQUE,
  profile_picture VARCHAR(500),
  role VARCHAR(50) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP
);
```

### Classrooms Table
```sql
CREATE TABLE classrooms (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  class_code VARCHAR(8) UNIQUE NOT NULL,
  repo_url VARCHAR(500) NOT NULL,
  repo_name VARCHAR(255) NOT NULL,
  github_repo_id BIGINT,
  teacher_id BIGINT REFERENCES users(id),
  is_active BOOLEAN DEFAULT true,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP
);
```

### Students Table
```sql
CREATE TABLE students (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT REFERENCES users(id),
  classroom_id BIGINT REFERENCES classrooms(id),
  student_name VARCHAR(255) NOT NULL,
  branch_name VARCHAR(255) NOT NULL,
  github_token VARCHAR(500),
  last_commit_at TIMESTAMP,
  commit_count INTEGER DEFAULT 0,
  joined_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP
);
```

## Cấu trúc Project

```
auto-git-be/
├── src/main/java/com/example/auto_git_be/
│   ├── config/
│   │   ├── JwtAuthenticationFilter.java
│   │   └── SecurityConfig.java
│   ├── controller/
│   │   ├── AuthController.java
│   │   └── ClassController.java
│   ├── dto/
│   │   ├── LoginResponse.java
│   │   ├── CreateClassRequest.java
│   │   └── ...
│   ├── entity/
│   │   ├── User.java
│   │   ├── ClassRoom.java
│   │   └── Student.java
│   ├── repository/
│   │   ├── UserRepository.java
│   │   ├── ClassRoomRepository.java
│   │   └── StudentRepository.java
│   ├── service/
│   │   ├── AuthService.java
│   │   ├── ClassRoomService.java
│   │   ├── GitHubService.java
│   │   └── JwtService.java
│   └── AutoGitBeApplication.java
└── src/main/resources/
    └── application.properties
```

## Security Notes

⚠️ **Production Considerations:**

1. **JWT Secret**: Sử dụng key mạnh, tối thiểu 256 bits
2. **CORS**: Chỉ định chính xác origins thay vì `*`
3. **GitHub Token**: Sử dụng GitHub App với fine-grained permissions
4. **HTTPS**: Bật HTTPS cho production
5. **Rate Limiting**: Thêm rate limiting cho APIs
6. **Input Validation**: Validate mọi input từ client

## Troubleshooting

### Database connection failed
- Kiểm tra PostgreSQL đang chạy
- Verify username/password trong application.properties
- Đảm bảo database đã được tạo

### GitHub API errors
- Verify GitHub token còn hạn
- Kiểm tra token có đủ permissions
- Check rate limits

### Google OAuth errors
- Verify redirect URI khớp với Google Console
- Check client ID và secret
- Đảm bảo Google+ API được enable

## License

MIT
