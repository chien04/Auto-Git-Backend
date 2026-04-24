package com.example.auto_git_be.utils;

public final class Constant {
    private Constant() {}
    public static final Long AI_ID = 999999999L;

    public static final String SYSTEM_PROMPT_TUTOR = """
        Nhiệm vụ của bạn là hỗ trợ sinh viên hiểu sâu về lập trình.
        
        NGUYÊN TẮC QUAN TRỌNG:
        1. Hãy chỉ ra vị trí logic sai hoặc dòng code có vấn đề.
        3. Giải thích các khái niệm kỹ thuật một cách dễ hiểu .
        4. Nếu code của sinh viên đã tốt, hãy đưa ra các lời khuyên để tối ưu (Refactor) hoặc cải thiện hiệu năng.
            QUY TẮC ĐỊNH DẠNG (FORMATTING):
                    - BẮT BUỘC sử dụng Markdown cho mọi câu trả lời.
                    - Nếu có viết mã nguồn, BẮT BUỘC phải đặt trong cặp 3 dấu backticks (```) và GHI RÕ TÊN NGÔN NGỮ để hệ thống render màu sắc.
                    - Tuyệt đối không trả về plain text cho code.
            
                    Ví dụ đúng:
                    ```java
                    public void example() { }
                    ```
            
                    Ví dụ đúng:
                    ```cpp
                    int main() { return 0; }
                    ```
                    ""\";
        
        """;

    public static final String SYSTEM_PROMPT_TEACHER = """
        Nhiệm vụ của bạn là phân tích bài làm của sinh viên dựa trên các tiêu chí kỹ thuật.
        
        NGUYÊN TẮC ĐÁNH GIÁ:
        1. Kiểm tra tính đúng đắn của thuật toán và các trường hợp biên (edge cases).
        2. Đánh giá phong cách lập trình (Clean Code, cách đặt tên biến, cấu trúc hàm).
        3. Phát hiện các dấu hiệu bất thường hoặc gian lận (nếu code quá giống các mẫu có sẵn trên mạng).
        5. Phản hồi bằng phong cách chuyên nghiệp, khách quan và nghiêm túc.
            QUY TẮC ĐỊNH DẠNG (FORMATTING):
                    - BẮT BUỘC sử dụng Markdown cho mọi câu trả lời.
                    - Nếu có viết mã nguồn, BẮT BUỘC phải đặt trong cặp 3 dấu backticks (```) và GHI RÕ TÊN NGÔN NGỮ để hệ thống render màu sắc.
                    - Tuyệt đối không trả về plain text cho code.
            
                    Ví dụ đúng:
                    ```java
                    public void example() { }
                    ```
            
                    Ví dụ đúng:
                    ```cpp
                    int main() { return 0; }
                    ```
                    ""\";
        """;


    public static final String WORKFLOW_GITHUB_ACTION = """
name: Auto Grading - Multiple Exercises

on:
  push:
    branches:
      - 'student-*'

jobs:
  test-and-grade:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      # Step 0: Download and extract test cases from MinIO
      - name: Download test cases
        run: |
          echo "Downloading test cases from backend (multi-task)..."
          BACKEND_URL="{{BACKEND_URL}}"
          ASSIGNMENT_CODE="{{ASSIGNMENT_CODE}}"
          mkdir -p testcases

          # Tải danh sách tasks
          RESPONSE=$(curl -s "$BACKEND_URL/api/test-cases/$ASSIGNMENT_CODE/download-urls")
          TASK_LINES=$(echo "$RESPONSE" | jq -r '.tasks | sort_by(.orderNo)[] | select(.downloadUrl != null and .downloadUrl != "") | "\\(.orderNo)|\\(.downloadUrl)"')
          if [ -n "$TASK_LINES" ]; then
            echo "Detected multi-task testcases"
            while IFS='|' read -r TASK_NO TASK_URL; do
              [ -z "$TASK_URL" ] && continue
              ZIP_FILE="task_${TASK_NO}.zip"
              TMP_DIR=".__task_${TASK_NO}"

              echo "Downloading task $TASK_NO"
              curl -L -f -o "$ZIP_FILE" "$TASK_URL"
              if [ $? -ne 0 ]; then
                echo "Failed to download testcase ZIP for task $TASK_NO"
                exit 1
              fi

              rm -rf "$TMP_DIR" && mkdir -p "$TMP_DIR"
              unzip -q "$ZIP_FILE" -d "$TMP_DIR"
              if [ $? -ne 0 ]; then
                echo "Failed to extract $ZIP_FILE"
                exit 1
              fi

              TARGET_DIR="testcases/ex${TASK_NO}"
              rm -rf "$TARGET_DIR" && mkdir -p "$TARGET_DIR"

              if compgen -G "$TMP_DIR/testcases/ex*" > /dev/null; then
                SOURCE_DIR=$(find "$TMP_DIR/testcases" -mindepth 1 -maxdepth 1 -type d | head -n 1)
                cp -R "$SOURCE_DIR/." "$TARGET_DIR/"
              elif [ -d "$TMP_DIR/testcases" ]; then
                cp -R "$TMP_DIR/testcases/." "$TARGET_DIR/"
              elif compgen -G "$TMP_DIR/input*.txt" > /dev/null || compgen -G "$TMP_DIR/output*.txt" > /dev/null; then
                cp -R "$TMP_DIR/." "$TARGET_DIR/"
              elif [ $(find "$TMP_DIR" -mindepth 1 -maxdepth 1 -type d | wc -l) -eq 1 ]; then
                SOURCE_DIR=$(find "$TMP_DIR" -mindepth 1 -maxdepth 1 -type d | head -n 1)
                cp -R "$SOURCE_DIR/." "$TARGET_DIR/"
              else
                echo "Unsupported testcase zip structure for task $TASK_NO"
                exit 1
              fi

              rm -rf "$TMP_DIR" "$ZIP_FILE"
            done <<< "$TASK_LINES"
          fi

          echo "Extracted test cases:"
          ls -la testcases/

      # Step 1: Detect language and exercises from testcases
      - name: Detect exercises and language
        id: detect
        run: |
          EXERCISES=""
          if [ -d "testcases" ]; then
            for dir in testcases/ex*; do
              [ -d "$dir" ] || continue
              exercise=$(basename "$dir")
              if [ -z "$EXERCISES" ]; then EXERCISES="$exercise"; else EXERCISES="$EXERCISES,$exercise"; fi
            done
          fi

          if [ -z "$EXERCISES" ]; then echo "No exercises found"; exit 1; fi

          LANG=""
          for file in ex*.cpp; do [ -f "$file" ] && LANG="cpp" && break; done
          if [ -z "$LANG" ]; then for file in ex*.java; do [ -f "$file" ] && LANG="java" && break; done; fi
          if [ -z "$LANG" ]; then for file in ex*.py; do [ -f "$file" ] && LANG="python" && break; done; fi
          if [ -z "$LANG" ]; then for file in ex*.c; do [ -f "$file" ] && LANG="c" && break; done; fi
          if [ -z "$LANG" ]; then for file in ex*.js; do [ -f "$file" ] && LANG="javascript" && break; done; fi
          if [ -z "$LANG" ]; then for file in ex*.ts; do [ -f "$file" ] && LANG="typescript" && break; done; fi
          if [ -z "$LANG" ]; then LANG="none"; fi

          echo "language=$LANG" >> $GITHUB_OUTPUT
          echo "exercises=$EXERCISES" >> $GITHUB_OUTPUT
          echo "Detected Lang: $LANG | Exercises: $EXERCISES"

      # Step 2: Setup environments
      - name: Setup Java
        if: steps.detect.outputs.language == 'java'
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Python
        if: steps.detect.outputs.language == 'python'
        uses: actions/setup-python@v5
        with:
          python-version: '3.x'

      - name: Setup Node.js
        if: steps.detect.outputs.language == 'javascript' || steps.detect.outputs.language == 'typescript'
        uses: actions/setup-node@v4
        with:
          node-version: '18.x'

      - name: Setup TypeScript
        if: steps.detect.outputs.language == 'typescript'
        run: npm install -g typescript ts-node

      # Step 3: Compile and test exercises
      - name: Compile and test exercises
        id: test
        shell: bash
        env:
          DETECTED_LANG: ${{ steps.detect.outputs.language }}
          EXERCISES: ${{ steps.detect.outputs.exercises }}
        run: |
          LANG="$DETECTED_LANG"
          TOTAL_SUM_SCORE=0
          COUNT=0

          echo "[]" > details.json
          EXERCISE_LIST=$(echo "$EXERCISES" | tr ',' ' ')

          for EXERCISE in $EXERCISE_LIST; do
            COUNT=$((COUNT + 1))
            ORDER_NO=$(echo "$EXERCISE" | sed 's/[^0-9]//g')
            [ -z "$ORDER_NO" ] && ORDER_NO=0

            echo "=========================================="
            echo "Exercise: $EXERCISE (OrderNo: $ORDER_NO)"

            CODE_FILE=""
            case "$LANG" in
              java) CODE_FILE="${EXERCISE}.java" ;;
              cpp) CODE_FILE="${EXERCISE}.cpp" ;;
              c) CODE_FILE="${EXERCISE}.c" ;;
              python) CODE_FILE="${EXERCISE}.py" ;;
              javascript) CODE_FILE="${EXERCISE}.js" ;;
              typescript) CODE_FILE="${EXERCISE}.ts" ;;
            esac

            COMPILE_ERROR=""
            COMPILED=false

            if [ ! -f "$CODE_FILE" ]; then
              COMPILE_ERROR="File not submitted"
            else
              case "$LANG" in
                java)
                  if ! javac "${EXERCISE}.java" 2> error.log; then
                    COMPILE_ERROR=$(head -n 5 error.log | tr '\\n' ' ')
                  else COMPILED=true; fi
                  ;;
                cpp)
                  if ! g++ -o "${EXERCISE}" -std=c++17 -O2 "${EXERCISE}.cpp" 2> error.log; then
                    COMPILE_ERROR=$(head -n 5 error.log | tr '\\n' ' ')
                  else COMPILED=true; fi
                  ;;
                c)
                  if ! gcc -o "${EXERCISE}" -std=c11 -O2 "${EXERCISE}.c" 2> error.log; then
                    COMPILE_ERROR=$(head -n 5 error.log | tr '\\n' ' ')
                  else COMPILED=true; fi
                  ;;
                python|javascript|typescript)
                  COMPILED=true
                  ;;
              esac
            fi

            EXERCISE_PASSED=0
            EXERCISE_TOTAL=0

            if [ "$COMPILED" = true ]; then
              TEST_DIR="testcases/${EXERCISE}"
              if [ -d "$TEST_DIR" ]; then
                for input in "$TEST_DIR"/input*.txt; do
                  [ ! -f "$input" ] && continue
                  EXERCISE_TOTAL=$((EXERCISE_TOTAL + 1))
                  
                  num=$(basename "$input" | sed 's/[^0-9]//g')
                  expected="$TEST_DIR/output${num}.txt"

                  case "$LANG" in
                    java)       timeout 5s java "${EXERCISE}" < "$input" > actual.txt 2>&1 ;;
                    cpp|c)      timeout 5s ./"${EXERCISE}" < "$input" > actual.txt 2>&1 ;;
                    python)     timeout 5s python3 "${EXERCISE}.py" < "$input" > actual.txt 2>&1 ;;
                    javascript) timeout 5s node "${EXERCISE}.js" < "$input" > actual.txt 2>&1 ;;
                    typescript) timeout 5s ts-node "${EXERCISE}.ts" < "$input" > actual.txt 2>&1 ;;
                  esac

                  if [ -f "$expected" ]; then
                    tr -d '\\r' < "$expected" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' | grep -v '^$' > expected_normalized.txt
                    tr -d '\\r' < actual.txt | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' | grep -v '^$' > actual_normalized.txt

                    if diff -w expected_normalized.txt actual_normalized.txt > /dev/null 2>&1; then
                      EXERCISE_PASSED=$((EXERCISE_PASSED + 1))
                    fi
                  fi
                done
              else
                COMPILE_ERROR="No testcases found"
              fi
            fi

            EXERCISE_SCORE=0
            if [ $EXERCISE_TOTAL -gt 0 ]; then
              EXERCISE_SCORE=$((EXERCISE_PASSED * 100 / EXERCISE_TOTAL))
            fi
            TOTAL_SUM_SCORE=$((TOTAL_SUM_SCORE + EXERCISE_SCORE))

            jq -n \
              --arg orderNo "${ORDER_NO:-0}" \
              --arg lang "$LANG" \
              --arg score "${EXERCISE_SCORE:-0}" \
              --arg pass "${EXERCISE_PASSED:-0}" \
              --arg total "${EXERCISE_TOTAL:-0}" \
              --arg compiled "$COMPILED" \
              --arg error "$COMPILE_ERROR" \
              '{
                orderNo: ($orderNo | tonumber),
                language: $lang,
                score: (($score | tonumber) / 10.0),
                pass: ($pass | tonumber),
                total: ($total | tonumber),
                status: (if $compiled == "true" then "SUCCESS" else "COMPILATION_FAILED" end),
                errorMessage: $error
              }' > new_entry.json

            # Thêm object mới vào mảng details.json
            jq --slurpfile new_entry new_entry.json '. + $new_entry' details.json > tmp.json && mv tmp.json details.json
            
            echo "Result: $EXERCISE_PASSED/$EXERCISE_TOTAL passed. Score: $((EXERCISE_SCORE / 10))"
          done

          if [ $COUNT -gt 0 ]; then
            FINAL_SCORE=$(python3 -c "print(round(($TOTAL_SUM_SCORE / $COUNT) / 10.0, 2))")
          else
            FINAL_SCORE=0
          fi

          echo "SCORE=$FINAL_SCORE" >> $GITHUB_OUTPUT
          echo "Final Average Score: $FINAL_SCORE / 10"

      # Step 4: Show summary
      - name: Show summary
        if: always()
        run: |
          echo "## Test Results" >> $GITHUB_STEP_SUMMARY
          echo "- **Final Score:** ${{ steps.test.outputs.SCORE }}/10" >> $GITHUB_STEP_SUMMARY
          echo "- **Language:** ${{ steps.detect.outputs.language }}" >> $GITHUB_STEP_SUMMARY
          echo "- **Exercises:** ${{ steps.detect.outputs.exercises }}" >> $GITHUB_STEP_SUMMARY
          echo "- **Branch:** ${{ github.ref_name }}" >> $GITHUB_STEP_SUMMARY

      # Step 5: Send score to backend (Sử dụng jq để build payload an toàn)
      - name: Send score to backend
        if: always()
        env:
          SCORE: ${{ steps.test.outputs.SCORE }}
          REPO: ${{ github.repository }}
          BRANCH: ${{ github.ref_name }}
        run: |
          BACKEND_URL="{{BACKEND_URL}}"

          if [ -z "$SCORE" ]; then SCORE=0; fi
          if [ ! -f details.json ]; then echo "[]" > details.json; fi

          echo "Building payload and sending to backend..."
          
          # Dùng jq để tạo file JSON chuẩn xác 100%, không lo vỡ format chuỗi
          jq -n \
            --arg repo "$REPO" \
            --arg branch "$BRANCH" \
            --argjson score "$SCORE" \
            --slurpfile details details.json \
            '{repoFullName: $repo, branchName: $branch, score: $score, details: $details[0]}' > payload.json

          curl -X POST "$BACKEND_URL/api/assignment/update-score" \
            -H "Content-Type: application/json" \
            -d @payload.json \
            && echo -e "\\nScore submitted successfully" \
            || echo -e "\\nFailed to submit score"
""";
}
