# Hướng dẫn Test Backend với Postman

## 🔐 Cách lấy JWT Token

### Bước 1: Login để lấy Token

Backend hỗ trợ 2 phương thức đăng nhập:

#### **Phương án 1: Login bằng Google OAuth (Khuyến nghị)**

1. **Lấy URL đăng nhập Google:**
```
GET http://localhost:8080/api/auth/google/url
```

**Response:**
```json
{
  "authUrl": "https://accounts.google.com/o/oauth2/v2/auth?..."
}
```

2. **Mở URL trong trình duyệt ẨN DANH/INCOGNITO** → Đăng nhập Google → Được redirect về `http://localhost:3000?code=...`

⚠️ **LỜI KHUYÊN QUAN TRỌNG:**
- Authorization code **CHỈ DÙNG ĐƯỢC 1 LẦN** và hết hạn sau ~10 phút
- Mở trình duyệt **chế độ ẩn danh** để tránh dùng lại code cũ
- Copy code **NGAY LẬP TỨC** từ URL
- Gọi API callback **TRONG VÒNG 1 PHÚT** sau khi lấy code

3. **Copy `code` từ URL - QUAN TRỌNG: Phải URL decode!**

**Ví dụ URL sau khi redirect:**
```
http://localhost:3000/?code=4%2F0ASc3gC0nhbgYO5EPTe0hNDbgBfYFKeWX6UetWf4tOsmXQoA56rbfrv8xOlXkagQSYZH2KA&scope=...
```

**⚠️ QUAN TRỌNG - Phải xử lý URL encoding:**
- URL có ký tự `%2F` (là dấu `/` bị encode)
- **KHÔNG** copy nguyên chuỗi `4%2F0ASc3gC...` 
- **PHẢI** decode về: `4/0ASc3gC...` (thay `%2F` thành `/`)

**Cách decode nhanh:**
1. **Cách 1 (Khuyến nghị):** Paste URL vào thanh địa chỉ trình duyệt mới → Enter → Copy lại từ thanh địa chỉ (browser tự decode)
2. **Cách 2:** Dùng tool online: https://www.urldecoder.org/ → Paste code → Click Decode
3. **Cách 3:** Trong JavaScript console của browser:
   ```javascript
   decodeURIComponent("4%2F0ASc3gC...")
   ```

**Sau khi decode, gọi API callback:**
**Sau khi decode, gọi API callback:**
```
POST http://localhost:8080/api/auth/google/callback
Content-Type: application/json

{
  "code": "4/0ASc3gC0nhbgYO5EPTe0hNDbgBfYFKeWX6UetWf4tOsmXQoA56rbfrv8xOlXkagQSYZH2KA",
  "role": "TEACHER"
}
```

**✅ Ví dụ với URL của bạn:**
```
URL gốc: code=4%2F0ASc3gC0nhbgYO5EPTe0hNDbgBfYFKeWX6UetWf4tOsmXQoA56rbfrv8xOlXkagQSYZH2KA

Code sau decode (dùng trong API): 4/0ASc3gC0nhbgYO5EPTe0hNDbgBfYFKeWX6UetWf4tOsmXQoA56rbfrv8xOlXkagQSYZH2KA
                                    ↑ (đổi %2F thành /)
```

**⚠️ Nếu gặp lỗi "invalid_grant":**
- Code đã được dùng rồi hoặc hết hạn
- LẤY CODE MỚI từ bước 1
- KHÔNG thử lại với code cũ

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "email": "teacher@gmail.com",
  "name": "Nguyen Van A",
  "userId": "123",
  "role": "TEACHER"
}
```

✅ **Copy giá trị `token`** - đây là JWT token bạn cần!

**🕐 Thời gian hết hạn:** Token có hiệu lực trong **24 giờ (1 ngày)**. Sau đó cần login lại để lấy token mới.

---

#### **Phương án 2: Login bằng Email OTP (Đơn giản hơn)**

1. **Gửi OTP đến email:**
```
POST http://localhost:8080/api/auth/request-otp
Content-Type: application/json

{
  "email": "teacher@gmail.com"
}
```

**Response:**
```json
{
  "message": "OTP đã được gửi đến email của bạn"
}
```

2. **Kiểm tra email, lấy mã OTP (6 số) và verify:**
```
POST http://localhost:8080/api/auth/verify-otp
Content-Type: application/json

{
  "email": "teacher@gmail.com",
  "otp": "123456",
  "role": "TEACHER"
}
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "email": "teacher@gmail.com",
  "name": "Nguyen Van A",
  "userId": "123",
  "role": "TEACHER"
}
```

✅ **Copy giá trị `token`** - đây là JWT token!

**🕐 Thời gian hết hạn:** Token có hiệu lực trong **24 giờ (1 ngày)**. Sau đó cần login lại để lấy token mới.

---

## 📝 Cách nhập Token vào Postman

### **Cách 1: Authorization Tab (Khuyến nghị)**

1. Mở request cần test trong Postman
2. Chọn tab **"Authorization"**
3. **Type:** chọn `Bearer Token`
4. **Token:** paste JWT token vào ô bên phải
5. Click **Send**

![Postman Authorization](https://i.imgur.com/example.png)

---

### **Cách 2: Headers (Thủ công)**

1. Chọn tab **"Headers"**
2. Thêm header mới:
   - **Key:** `Authorization`
   - **Value:** `Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...`
   - ⚠️ **Lưu ý:** Phải có từ `Bearer` + khoảng trắng + token

---

### **Cách 3: Collection Variables (Tốt nhất cho nhiều requests)**

1. Tạo Collection trong Postman
2. Click vào Collection → Tab **"Variables"**
3. Thêm variable:
   - **Variable:** `jwt_token`
   - **Initial Value:** `eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...`
   - **Current Value:** (tự động copy từ Initial Value)

4. Trong mỗi request, dùng variable:
   - Tab **Authorization** → Type: `Bearer Token`
   - Token: `{{jwt_token}}`

---

## 🧪 Danh sách API để Test

### **1. Class Management (Quản lý lớp học)**

#### Tạo lớp học (TEACHER)
```
POST http://localhost:8080/api/class/create
Authorization: Bearer {{jwt_token}}
Content-Type: application/json

{
  "className": "Lập trình Python 2024"
}
```

**Response:**
```json
{
  "classId": "1",
  "classCode": "ABC123",
  "className": "Lập trình Python 2024"
}
```

---

#### Tham gia lớp học (STUDENT)
```
POST http://localhost:8080/api/class/join
Authorization: Bearer {{jwt_token}}
Content-Type: application/json

{
  "studentName": "Nguyen Van B",
  "classCode": "ABC123"
}
```

---

#### Lấy danh sách lớp của tôi
```
GET http://localhost:8080/api/class/my-classes
Authorization: Bearer {{jwt_token}}
```

**Response:**
```json
{
  "teacherClasses": [
    {
      "classId": 1,
      "className": "Lập trình Python 2024",
      "classCode": "ABC123",
      "studentCount": 25,
      "assignmentCount": 3
    }
  ],
  "studentClasses": []
}
```

---

#### Lấy danh sách sinh viên trong lớp (TEACHER)
```
GET http://localhost:8080/api/class/ABC123/students
Authorization: Bearer {{jwt_token}}
```

---

#### Xóa lớp học (TEACHER)
```
DELETE http://localhost:8080/api/class/ABC123
Authorization: Bearer {{jwt_token}}
```

---

#### Rời khỏi lớp học (STUDENT)
```
POST http://localhost:8080/api/class/ABC123/leave
Authorization: Bearer {{jwt_token}}
```

---

### **2. Assignment Management (Quản lý bài tập)**

#### Tạo bài tập (TEACHER)
```
POST http://localhost:8080/api/assignment/create
Authorization: Bearer {{jwt_token}}
Content-Type: application/json

{
  "classCode": "ABC123",
  "title": "Bài tập 1: Hello World",
  "description": "Viết chương trình in ra Hello World",
  "deadline": "2024-12-31T23:59:59"
}
```

**Response:**
```json
{
  "assignmentId": "1",
  "assignmentCode": "XYZ789",
  "title": "Bài tập 1: Hello World",
  "repoUrl": "https://github.com/owner/repo",
  "token": "ghp_xxxxxxxxxxxx"
}
```

---

#### Lấy danh sách bài tập trong lớp
```
GET http://localhost:8080/api/assignment/class/ABC123
Authorization: Bearer {{jwt_token}}
```

**Response (TEACHER):**
```json
[
  {
    "assignmentId": "1",
    "assignmentCode": "XYZ789",
    "title": "Bài tập 1: Hello World",
    "deadline": "2024-12-31T23:59:59",
    "studentCount": 25,
    "joined": true
  }
]
```

**Response (STUDENT):**
```json
[
  {
    "assignmentId": "1",
    "assignmentCode": "XYZ789",
    "title": "Bài tập 1: Hello World",
    "deadline": "2024-12-31T23:59:59",
    "commitCount": 5,
    "lastCommitAt": "2024-12-25T10:30:00",
    "joined": true
  }
]
```

---

#### Tham gia bài tập (STUDENT)
```
POST http://localhost:8080/api/assignment/join
Authorization: Bearer {{jwt_token}}
Content-Type: application/json

{
  "assignmentCode": "XYZ789",
  "localPath": "D:/Projects/assignment-xyz789"
}
```

**Response:**
```json
{
  "repoUrl": "https://github.com/owner/repo",
  "branch": "student/nguyen-van-b",
  "token": "ghp_xxxxxxxxxxxx",
  "studentId": "2",
  "assignmentTitle": "Bài tập 1: Hello World",
  "deadline": "2024-12-31T23:59:59"
}
```

---

#### Xem danh sách nộp bài (TEACHER hoặc STUDENT)
```
GET http://localhost:8080/api/assignment/XYZ789/submissions
Authorization: Bearer {{jwt_token}}
```

**Response:**
```json
[
  {
    "studentId": 1,
    "studentName": "Nguyen Van B",
    "studentCode": "12345678",
    "email": "student@gmail.com",
    "commitCount": 5,
    "lastCommitAt": "2024-12-25T10:30:00",
    "score": 8.5
  },
  {
    "studentId": 2,
    "studentName": "Tran Thi C",
    "studentCode": "87654321",
    "email": "student2@gmail.com",
    "commitCount": 0,
    "lastCommitAt": null,
    "score": null
  }
]
```

---

#### Cập nhật điểm (TEACHER)
```
POST http://localhost:8080/api/assignment/update-score
Authorization: Bearer {{jwt_token}}
Content-Type: application/json

{
  "repoFullName": "owner/repo",
  "branchName": "student/nguyen-van-b",
  "score": 8.5
}
```

---

#### Cập nhật số lần commit (STUDENT tự động khi push)
```
POST http://localhost:8080/api/assignment/XYZ789/update-commit-count
Authorization: Bearer {{jwt_token}}
```

---

### **3. Dashboard (Thống kê)**

#### Dashboard của Teacher
```
GET http://localhost:8080/api/dashboard/teacher
Authorization: Bearer {{jwt_token}}
```

**Response:**
```json
{
  "totalStudents": 125,
  "studentsSubmitted": 80,
  "studentsNotSubmitted": 45,
  "submittedPercentage": 64.0,
  "notSubmittedPercentage": 36.0,
  "averageCommitsPerStudent": 7.5,
  "totalClasses": 5,
  "activeClasses": 3
}
```

---

#### Dashboard của Student
```
GET http://localhost:8080/api/dashboard/student
Authorization: Bearer {{jwt_token}}
```

**Response:**
```json
{
  "totalCommits": 45,
  "lastCommitAt": "2024-12-25T10:30:00",
  "totalClasses": 3,
  "activeClasses": 2
}
```

---

#### Thống kê từng lớp học
```
GET http://localhost:8080/api/dashboard/teacher/classes
Authorization: Bearer {{jwt_token}}
```

---

#### Lịch sử commit của sinh viên
```
GET http://localhost:8080/api/dashboard/student/commit-activity
Authorization: Bearer {{jwt_token}}
```

**Response:**
```json
{
  "dailyCommits": {
    "2024-12-20": 3,
    "2024-12-21": 5,
    "2024-12-22": 2,
    "2024-12-23": 0,
    "2024-12-24": 4,
    "2024-12-25": 1
  }
}
```

---

### **4. Message/Chat**

#### Gửi tin nhắn riêng tư
```
POST http://localhost:8080/api/messages/send
Authorization: Bearer {{jwt_token}}
Content-Type: application/json

{
  "receiverId": 2,
  "content": "Xin chào, bạn có thể giúp mình không?"
}
```

---

#### Lấy lịch sử chat với 1 người
```
GET http://localhost:8080/api/messages/private/2?page=0&size=50
Authorization: Bearer {{jwt_token}}
```

---

#### Lấy danh sách chat gần đây
```
GET http://localhost:8080/api/messages/recent-chats
Authorization: Bearer {{jwt_token}}
```

---

## 🔍 Troubleshooting

### ⚠️ Lỗi "invalid_grant" / "Malformed auth code" (Google OAuth)
```json
{
  "token": null,
  "name": "400 Bad Request: \"invalid_grant\", \"Malformed auth code.\""
}
```

**❌ Nguyên nhân:**
1. Authorization code từ Google **CHỈ DÙNG ĐƯỢC 1 LẦN**
2. Code đã hết hạn (thời gian hiệu lực: ~10 phút)
3. Copy sai code từ URL (thiếu ký tự hoặc thừa khoảng trắng)
4. **Code có ký tự URL encoded (`%2F`, `%3D`, ...) mà chưa decode**

**✅ Giải pháp:**

**Cách 1: Lấy code mới VÀ decode đúng cách (Khuyến nghị)**
1. Gọi lại API `/auth/google/url` để lấy URL mới
2. Mở URL trong trình duyệt **chế độ ẩn danh/incognito**
3. Đăng nhập Google → Được redirect về:
   ```
   http://localhost:3000?code=4%2F0ASc3gC...&scope=...
   ```
4. **QUAN TRỌNG - Decode URL:**
   - Paste toàn bộ URL vào thanh địa chỉ browser mới → Enter
   - Browser tự decode → Copy lại code từ thanh địa chỉ
   - Hoặc dùng tool: https://www.urldecoder.org/
   - Code đúng: `4/0ASc3gC...` (có dấu `/`, không phải `%2F`)
5. Paste code đã decode vào Postman và gửi request **NGAY** (trong vòng 1 phút)

**Cách 2: Dùng Email OTP thay thế (Dễ hơn)**
- Không cần lo về code expiration
- Xem phần "Phương án 2: Login bằng Email OTP" ở trên

**⚠️ Lưu ý quan trọng:**
- **KHÔNG** thử lại với cùng 1 code nhiều lần
- **KHÔNG** copy code từ history của trình duyệt
- Phải lấy code mới cho mỗi lần test
- Copy code chính xác, không có khoảng trắng đầu/cuối

---

### Lỗi 401 Unauthorized
- ❌ **Nguyên nhân:** Token không hợp lệ hoặc đã hết hạn
- ✅ **Giải pháp:** Login lại để lấy token mới

### Lỗi 403 Forbidden
- ❌ **Nguyên nhân:** Role không đủ quyền (VD: Student gọi API của Teacher)
- ✅ **Giải pháp:** Kiểm tra role trong token, login với đúng role

### Lỗi 404 Not Found
- ❌ **Nguyên nhân:** ClassCode hoặc AssignmentCode không tồn tại
- ✅ **Giải pháp:** Kiểm tra lại code đã nhập

### Token hết hạn
- JWT token có thời gian hết hạn: **24 giờ (1 ngày)**
- Thời gian được cấu hình trong `application.properties`: `jwt.expiration=86400000` (milliseconds)
- Sau 24 giờ, token sẽ không còn hợp lệ
- **Giải pháp:** Login lại để lấy token mới

---

## 💡 Tips & Best Practices

1. **Environment Variables trong Postman:**
   - Tạo environment với biến `base_url = http://localhost:8080`
   - Dùng `{{base_url}}/api/class/create` thay vì URL đầy đủ

2. **Pre-request Script để tự động thêm token:**
```javascript
pm.request.headers.add({
    key: 'Authorization',
    value: 'Bearer ' + pm.collectionVariables.get('jwt_token')
});
```

3. **Test Script để lưu token tự động:**
```javascript
// Trong request login
if (pm.response.code === 200) {
    var jsonData = pm.response.json();
    pm.collectionVariables.set('jwt_token', jsonData.token);
}
```

4. **Organize requests:**
   - Tạo folder: Authentication, Class, Assignment, Dashboard
   - Đặt tên request rõ ràng: `[POST] Create Class`, `[GET] My Classes`

---

## 📚 Tài liệu tham khảo

- **Swagger UI:** http://localhost:8080/swagger-ui.html (nếu có enable)
- **API Base URL:** http://localhost:8080/api
- **WebSocket:** ws://localhost:8080/ws

---

**Chúc bạn test thành công! 🚀**
