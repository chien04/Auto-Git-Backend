package com.example.auto_git_be.service;

import org.kohsuke.github.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class GitHubService {

    @Value("${github.app.token}")
    private String githubToken;

    @Value("${github.organization:}")
    private String githubOrganization;
    
    @Value("${backend.url}")
    private String backendUrl;

    private GitHub github;

    private GitHub getGitHub() throws IOException {
        if (github == null) {
            github = new GitHubBuilder().withOAuthToken(githubToken).build();
        }
        return github;
    }

    public GHRepository createRepository(String repoName, String description) throws IOException {
        GitHub gh = getGitHub();
        
        GHCreateRepositoryBuilder builder;
        
        if (githubOrganization != null && !githubOrganization.isEmpty()) {
            GHOrganization org = gh.getOrganization(githubOrganization);
            builder = org.createRepository(repoName);
        } else {
            builder = gh.createRepository(repoName);
        }
        
        return builder
                .description(description)
                .private_(false)
                .autoInit(true)
                .create();
    }

    public void createBranch(String repoFullName, String branchName, String fromBranch) throws IOException {
        GitHub gh = getGitHub();
        GHRepository repo = gh.getRepository(repoFullName);
        
        // Get the SHA of the source branch
        GHBranch sourceBranch = repo.getBranch(fromBranch);
        String sha = sourceBranch.getSHA1();
        
        // Create new branch
        repo.createRef("refs/heads/" + branchName, sha);
    }

    /**
     * Generate a fine-grained personal access token for a student
     * Note: This requires GitHub App installation and proper permissions
     * For simplicity, this example uses a pre-generated token approach
     */
    public String generateStudentToken(String repoFullName, String branchName) throws IOException {
        // In production, you would:
        // 1. Use GitHub App to generate installation access tokens
        // 2. Or use OAuth Apps to create tokens on behalf of users
        // 3. Or pre-generate fine-grained tokens with specific permissions
        
        // For this implementation, we'll return the main token
        // but in production you should implement proper token generation
        // with limited scope (only push access to specific branch)
        
        return githubToken;
    }

    /**
     * Get all branches in a repository
     */
    public List<String> getBranches(String repoFullName) throws IOException {
        GitHub gh = getGitHub();
        GHRepository repo = gh.getRepository(repoFullName);
        
        List<String> branches = new ArrayList<>();
        for (GHBranch branch : repo.getBranches().values()) {
            branches.add(branch.getName());
        }
        
        return branches;
    }

    /**
     * Get repository information
     */
    public GHRepository getRepository(String repoFullName) throws IOException {
        GitHub gh = getGitHub();
        return gh.getRepository(repoFullName);
    }

    /**
     * Delete a repository (for testing or cleanup)
     */
    public void deleteRepository(String repoFullName) throws IOException {
        GitHub gh = getGitHub();
        GHRepository repo = gh.getRepository(repoFullName);
        repo.delete();
    }

    /**
     * Add a collaborator to the repository
     */
    public void addCollaborator(String repoFullName, String username, GHOrganization.Permission permission) throws IOException {
        GitHub gh = getGitHub();
        GHRepository repo = gh.getRepository(repoFullName);
        repo.addCollaborators(gh.getUser(username));
    }

    /**
     * Protect a branch (prevent force push, etc.)
     */
    public void protectBranch(String repoFullName, String branchName) throws IOException {
        GitHub gh = getGitHub();
        GHRepository repo = gh.getRepository(repoFullName);
        GHBranch branch = repo.getBranch(branchName);
        
        // Enable branch protection
        branch.enableProtection()
              .includeAdmins(false)
              .restrictPushAccess()
              .enable();
    }

    /**
     * Get commit count for a branch
     */
    public int getCommitCount(String repoFullName, String branchName) throws IOException {
        GitHub gh = getGitHub();
        GHRepository repo = gh.getRepository(repoFullName);
        
        PagedIterable<GHCommit> commits = repo.queryCommits()
                .from(branchName)
                .list();
        
        int count = 0;
        for (GHCommit commit : commits) {
            count++;
        }
        
        return count;
    }

    /**
     * Get the repository owner (username or organization)
     */
    public String getRepoOwner() throws IOException {
        GitHub gh = getGitHub();
        
        if (githubOrganization != null && !githubOrganization.isEmpty()) {
            return githubOrganization;
        }
        
        return gh.getMyself().getLogin();
    }
    
    /**
     * Delete a branch
     */
    public void deleteBranch(String repoFullName, String branchName) throws IOException {
        GitHub gh = getGitHub();
        GHRepository repo = gh.getRepository(repoFullName);
        
        // Delete the branch
        GHRef ref = repo.getRef("heads/" + branchName);
        ref.delete();
    }

    /**
     * Get commits for a specific branch
     */
    public List<GHCommit> getCommits(String repoFullName, String branchName) throws IOException {
        try {
            GitHub gh = getGitHub();
            
            // Check rate limit
            GHRateLimit rateLimit = gh.getRateLimit();
            
            if (rateLimit.getCore().getRemaining() < 10) {
                throw new IOException("GitHub API rate limit exceeded. Reset at: " + rateLimit.getCore().getResetDate());
            }
            
            GHRepository repo = gh.getRepository(repoFullName);
            
            // Check if branch exists
            GHBranch branch = null;
            try {
                branch = repo.getBranch(branchName);
            } catch (Exception e) {
                throw new IOException("Branch '" + branchName + "' not found in repository.");
            }
            
            List<GHCommit> commitList = new ArrayList<>();
            
            try {
                // Method 1: Try with queryCommits (might fail with 403 on some repos)
                PagedIterable<GHCommit> commits = repo.queryCommits()
                        .from(branchName)
                        .list();
                
                int count = 0;
                for (GHCommit commit : commits) {
                    if (count >= 50) break;
                    commitList.add(commit);
                    count++;
                }
                
            } catch (Exception e) {
                // Method 2: Fallback to listCommits
                PagedIterable<GHCommit> commits = repo.listCommits();
                
                int count = 0;
                for (GHCommit commit : commits) {
                    // Filter by branch
                    try {
                        if (commit.getSHA1().equals(branch.getSHA1()) || 
                            repo.getBranch(branchName).getSHA1().startsWith(commit.getSHA1().substring(0, 7))) {
                            if (count >= 50) break;
                            commitList.add(commit);
                            count++;
                        }
                    } catch (Exception ex) {
                        // Skip this commit
                    }
                }
            }
            
            return commitList;
            
        } catch (IOException e) {
            throw e;
        }
    }

    /**
     * Get commit URL
     */
    public String getCommitUrl(String repoFullName, String commitSha) throws IOException {
        GitHub gh = getGitHub();
        GHRepository repo = gh.getRepository(repoFullName);
        return repo.getHtmlUrl().toString() + "/commit/" + commitSha;
    }

    public String getToken() {
        return githubToken;
    }

    public void createWorkflowFile(String repoFullName, String branchName, String assignmentCode) throws IOException {
        String workflowContent = """
name: Auto Grading - Multiple Exercises

on:
  push:
    branches:
      - main
      - master
      - teacher
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
          echo "Downloading test cases from backend..."
          BACKEND_URL="%s"
          ASSIGNMENT_CODE="%s"
          
          # Get presigned download URL from backend
          RESPONSE=$(curl -s "$BACKEND_URL/api/test-cases/$ASSIGNMENT_CODE/download-url")
          DOWNLOAD_URL=$(echo "$RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin)['downloadUrl'])" 2>/dev/null)
          
          if [ -z "$DOWNLOAD_URL" ]; then
            echo "Failed to get download URL from backend"
            echo "Response: $RESPONSE"
            exit 1
          fi
          
          echo "Got download URL (expires in 10 minutes)"
          
          # Download ZIP file from MinIO via presigned URL
          curl -L -f -o testcases.zip "$DOWNLOAD_URL"
          
          if [ $? -ne 0 ]; then
            echo "Failed to download test cases"
            exit 1
          fi
          
          echo "Downloaded testcases.zip"
          
          # Extract ZIP file
          unzip -q testcases.zip
          
          if [ $? -ne 0 ]; then
            echo "Failed to extract test cases"
            exit 1
          fi
          
          echo "Extracted test cases"
          ls -la testcases/
      
      # Step 1: Detect language and exercises from testcases
      - name: Detect exercises and language
        id: detect
        run: |
          echo "Detecting exercises from testcases folder..."
          
          # First, find all exercises from testcases folder
          EXERCISES=""
          if [ -d "testcases" ]; then
            for dir in testcases/ex*; do
              [ -d "$dir" ] || continue
              exercise=$(basename "$dir")
              if [ -z "$EXERCISES" ]; then
                EXERCISES="$exercise"
              else
                EXERCISES="$EXERCISES,$exercise"
              fi
            done
          fi
          
          if [ -z "$EXERCISES" ]; then
            echo "No exercises found in testcases folder"
            exit 1
          fi
          
          echo "Found exercises: $EXERCISES"
          
          # Now detect language from submitted code files
          LANG=""
          
          # Check for C++ exercises
          for file in ex*.cpp; do
            [ -f "$file" ] || continue
            LANG="cpp"
            break
          done
          
          # Check for Java exercises
          if [ -z "$LANG" ]; then
            for file in ex*.java; do
              [ -f "$file" ] || continue
              LANG="java"
              break
            done
          fi
          
          # Check for Python exercises
          if [ -z "$LANG" ]; then
            for file in ex*.py; do
              [ -f "$file" ] || continue
              LANG="python"
              break
            done
          fi
          
          # Check for C exercises
          if [ -z "$LANG" ]; then
            for file in ex*.c; do
              [ -f "$file" ] || continue
              LANG="c"
              break
            done
          fi
          
          # Check for JavaScript exercises
          if [ -z "$LANG" ]; then
            for file in ex*.js; do
              [ -f "$file" ] || continue
              LANG="javascript"
              break
            done
          fi
          
          # Check for TypeScript exercises
          if [ -z "$LANG" ]; then
            for file in ex*.ts; do
              [ -f "$file" ] || continue
              LANG="typescript"
              break
            done
          fi
          
          if [ -z "$LANG" ]; then
            echo "No code files found, but will check test cases"
            LANG="none"
          fi
          
          echo "language=$LANG" >> $GITHUB_OUTPUT
          echo "exercises=$EXERCISES" >> $GITHUB_OUTPUT
          echo "Detected: $LANG"
          echo "Exercises: $EXERCISES"
      
      # Step 2: Setup environment based on language
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
      
      - name: Setup Node.js (JavaScript/TypeScript)
        if: steps.detect.outputs.language == 'javascript' || steps.detect.outputs.language == 'typescript'
        uses: actions/setup-node@v4
        with:
          node-version: '18.x'
      
      - name: Setup TypeScript
        if: steps.detect.outputs.language == 'typescript'
        run: npm install -g typescript ts-node
      
      - name: Setup C/C++
        if: steps.detect.outputs.language == 'cpp' || steps.detect.outputs.language == 'c'
        run: |
          gcc --version
          g++ --version
      
      # Step 3: Compile and test ALL exercises
      - name: Compile and test exercises
        id: test
        env:
          DETECTED_LANG: ${{ steps.detect.outputs.language }}
          EXERCISES: ${{ steps.detect.outputs.exercises }}
        run: |
          LANG="$DETECTED_LANG"
          TOTAL_SCORE=0
          TOTAL_EXERCISES=0
          PASSED_EXERCISES=0
          
          echo "Testing exercises: $EXERCISES"
          echo ""
          
          # Split exercises by comma (convert comma to space for bash iteration)
          EXERCISE_LIST=$(echo "$EXERCISES" | tr ',' ' ')
          
          for EXERCISE in $EXERCISE_LIST; do
            TOTAL_EXERCISES=$((TOTAL_EXERCISES + 1))
            echo "=========================================="
            echo "Exercise: $EXERCISE"
            echo "=========================================="
            
            # Check if code file exists
            CODE_FILE=""
            case "$LANG" in
              java) CODE_FILE="${EXERCISE}.java" ;;
              cpp) CODE_FILE="${EXERCISE}.cpp" ;;
              c) CODE_FILE="${EXERCISE}.c" ;;
              python) CODE_FILE="${EXERCISE}.py" ;;
              javascript) CODE_FILE="${EXERCISE}.js" ;;
              typescript) CODE_FILE="${EXERCISE}.ts" ;;
            esac
            
            if [ ! -f "$CODE_FILE" ]; then
              echo "File not submitted: $CODE_FILE"
              echo "$EXERCISE Results: 0/0 passed (Score: 0/100) - NOT SUBMITTED"
              echo ""
              continue
            fi
            
            # Compile the exercise
            COMPILED=false
            case "$LANG" in
              java)
                if javac "${EXERCISE}.java" 2>&1; then
                  COMPILED=true
                  echo "Compiled: ${EXERCISE}.java"
                else
                  echo "Compilation failed: ${EXERCISE}.java"
                fi
                ;;
              cpp)
                if g++ -o "${EXERCISE}" -std=c++17 -O2 "${EXERCISE}.cpp" 2>&1; then
                  COMPILED=true
                  echo "Compiled: ${EXERCISE}.cpp"
                else
                  echo "Compilation failed: ${EXERCISE}.cpp"
                fi
                ;;
              c)
                if gcc -o "${EXERCISE}" -std=c11 -O2 "${EXERCISE}.c" 2>&1; then
                  COMPILED=true
                  echo "Compiled: ${EXERCISE}.c"
                else
                  echo "Compilation failed: ${EXERCISE}.c"
                fi
                ;;
              python|javascript|typescript)
                COMPILED=true
                echo "No compilation needed for $LANG"
                ;;
            esac
            
            if [ "$COMPILED" = false ]; then
              echo "Skipping tests for $EXERCISE (compilation failed)"
              echo ""
              continue
            fi
            
            # Run test cases for this exercise
            TEST_DIR="testcases/${EXERCISE}"
            
            if [ ! -d "$TEST_DIR" ]; then
              echo "No test cases found for $EXERCISE (expected: $TEST_DIR/)"
              echo ""
              continue
            fi
            
            EXERCISE_PASSED=0
            EXERCISE_TOTAL=0
            
            echo "Running test cases for $EXERCISE..."
            
            for input in "$TEST_DIR"/input*.txt; do
              [ ! -f "$input" ] && continue
              EXERCISE_TOTAL=$((EXERCISE_TOTAL + 1))
              
              # Extract test number
              filename=$(basename "$input")
              num=$(echo "$filename" | grep -oP '(?<=input)\\d+')
              expected="$TEST_DIR/output${num}.txt"
              
              echo "Test Case #$num"
              
              # Execute based on language
              timeout 5s bash -c "
                case '$LANG' in
                  java)
                    java ${EXERCISE} < '$input' > actual.txt 2>&1
                    ;;
                  cpp|c)
                    ./${EXERCISE} < '$input' > actual.txt 2>&1
                    ;;
                  python)
                    python3 ${EXERCISE}.py < '$input' > actual.txt 2>&1
                    ;;
                  javascript)
                    node ${EXERCISE}.js < '$input' > actual.txt 2>&1
                    ;;
                  typescript)
                    ts-node ${EXERCISE}.ts < '$input' > actual.txt 2>&1
                    ;;
                esac
              " || { echo "FAILED (timeout or error)"; continue; }
              
              # Check if expected output file exists
              if [ ! -f "$expected" ]; then
                echo "No expected output file: $expected"
                continue
              fi
              
              # Simpler normalization: just remove carriage returns and trim trailing whitespace
              tr -d '\\r' < "$expected" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' | grep -v '^$' > expected_normalized.txt
              tr -d '\\r' < actual.txt | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' | grep -v '^$' > actual_normalized.txt
              
              # Compare
              if diff -w expected_normalized.txt actual_normalized.txt > /dev/null 2>&1; then
                echo "PASSED"
                EXERCISE_PASSED=$((EXERCISE_PASSED + 1))
              else
                echo "FAILED (output mismatch)"
                echo "   Expected: $(head -n 1 expected_normalized.txt)"
                echo "   Actual:   $(head -n 1 actual_normalized.txt)"
              fi
            done
            
            # Calculate exercise score
            if [ $EXERCISE_TOTAL -gt 0 ]; then
              EXERCISE_SCORE=$((EXERCISE_PASSED * 100 / EXERCISE_TOTAL))
              TOTAL_SCORE=$((TOTAL_SCORE + EXERCISE_SCORE))
              echo ""
              echo "$EXERCISE Results: $EXERCISE_PASSED/$EXERCISE_TOTAL passed (Score: $EXERCISE_SCORE/100)"
              
              if [ $EXERCISE_PASSED -eq $EXERCISE_TOTAL ]; then
                PASSED_EXERCISES=$((PASSED_EXERCISES + 1))
              fi
            fi
            echo ""
          done
          
          # Calculate final score (average across all exercises)
          if [ $TOTAL_EXERCISES -gt 0 ]; then
            FINAL_SCORE=$((TOTAL_SCORE / TOTAL_EXERCISES))
          else
            FINAL_SCORE=0
          fi
          
          echo "TOTAL_EXERCISES=$TOTAL_EXERCISES" >> $GITHUB_OUTPUT
          echo "PASSED_EXERCISES=$PASSED_EXERCISES" >> $GITHUB_OUTPUT
          echo "SCORE=$FINAL_SCORE" >> $GITHUB_OUTPUT
          
          echo "=========================================="
          echo "Final Results"
          echo "=========================================="
          echo "Exercises Completed: $PASSED_EXERCISES/$TOTAL_EXERCISES"
          echo "Average Score: $FINAL_SCORE/100"
      
      # Step 4: Show summary
      - name: Show summary
        if: always()
        run: |
          echo "## Test Results" >> $GITHUB_STEP_SUMMARY
          echo "" >> $GITHUB_STEP_SUMMARY
          echo "- **Final Score:** ${{ steps.test.outputs.SCORE }}/100" >> $GITHUB_STEP_SUMMARY
          echo "- **Exercises Completed:** ${{ steps.test.outputs.PASSED_EXERCISES }}/${{ steps.test.outputs.TOTAL_EXERCISES }}" >> $GITHUB_STEP_SUMMARY
          echo "- **Language:** ${{ steps.detect.outputs.language }}" >> $GITHUB_STEP_SUMMARY
          echo "- **Exercises:** ${{ steps.detect.outputs.exercises }}" >> $GITHUB_STEP_SUMMARY
          echo "- **Branch:** ${{ github.ref_name }}" >> $GITHUB_STEP_SUMMARY
      
      # Step 5: Send score to backend
      - name: Send score to backend
        if: steps.test.outputs.SCORE != ''
        continue-on-error: true
        env:
          SCORE: ${{ steps.test.outputs.SCORE }}
          TOTAL_EX: ${{ steps.test.outputs.TOTAL_EXERCISES }}
          PASSED_EX: ${{ steps.test.outputs.PASSED_EXERCISES }}
          LANG: ${{ steps.detect.outputs.language }}
          REPO: ${{ github.repository }}
          BRANCH: ${{ github.ref_name }}
        run: |
          BACKEND_URL="%s"
          
          echo "Sending score to backend..."
          echo "   URL: $BACKEND_URL"
          echo "   Score: $SCORE/100"
          echo "   Language: $LANG"
          
          curl -X POST "$BACKEND_URL/api/assignment/update-score" \\
            -H "Content-Type: application/json" \\
            -d "{
              \\"repoFullName\\": \\"$REPO\\",
              \\"branchName\\": \\"$BRANCH\\",
              \\"score\\": $SCORE,
              \\"passedTests\\": $PASSED_EX,
              \\"totalTests\\": $TOTAL_EX,
              \\"language\\": \\"$LANG\\"
            }" \\
            && echo "Score submitted successfully" \\
            || echo "Failed to submit score (backend may be offline)"
""";
        
        workflowContent = String.format(workflowContent, backendUrl, assignmentCode, backendUrl);
        
        createFileInRepo(repoFullName, ".github/workflows/auto-grading.yml", workflowContent, branchName);
    }
    
    /**
     * Create sample test cases for multiple exercises - DEPRECATED
     * Now test cases are stored in MinIO, not in GitHub repository
     * Use TestCaseService.createTestCasesForAssignment() instead
     */
    @Deprecated
    public void createSampleTestCases(String repoFullName, String branchName) throws IOException {
        // Create all files in one commit for speed
        java.util.Map<String, String> files = new java.util.LinkedHashMap<>();
        
        // Exercise 1: Simple Addition (a + b)
        files.put("test-cases/ex1/input1.txt", "5 10");
        files.put("test-cases/ex1/output1.txt", "15");
        files.put("test-cases/ex1/input2.txt", "-100 50");
        files.put("test-cases/ex1/output2.txt", "-50");
        files.put("test-cases/ex1/input3.txt", "0 0");
        files.put("test-cases/ex1/output3.txt", "0");
        
        // Exercise 2: Find Maximum (max of two numbers)
        files.put("test-cases/ex2/input1.txt", "5 10");
        files.put("test-cases/ex2/output1.txt", "10");
        files.put("test-cases/ex2/input2.txt", "-100 50");
        files.put("test-cases/ex2/output2.txt", "50");
        files.put("test-cases/ex2/input3.txt", "42 42");
        files.put("test-cases/ex2/output3.txt", "42");
        
        // Exercise 3: Factorial (n!)
        files.put("test-cases/ex3/input1.txt", "5");
        files.put("test-cases/ex3/output1.txt", "120");
        files.put("test-cases/ex3/input2.txt", "0");
        files.put("test-cases/ex3/output2.txt", "1");
        files.put("test-cases/ex3/input3.txt", "10");
        files.put("test-cases/ex3/output3.txt", "3628800");
        
        // Create README with exercise descriptions
        String readme = """
# Assignment Test Cases

This assignment contains 3 exercises. Create files: ex1.cpp, ex2.cpp, ex3.cpp (or .java, .py)

## Exercise 1: Simple Addition
Input: Two integers a and b
Output: Sum a + b

## Exercise 2: Find Maximum
Input: Two integers a and b
Output: The larger number

## Exercise 3: Factorial
Input: Integer n (0 ≤ n ≤ 12)
Output: n! (factorial of n)

Note: 0! = 1
""";
        files.put("README_EXERCISES.md", readme);
        
        // Batch create all files in one commit (much faster!)
        createMultipleFilesInRepo(repoFullName, files, branchName, 
            "Add test cases for 3 exercises");
    }

    private void createFileInRepo(String repoFullName, String filePath, String content, String branchName) throws IOException {
        try {
            GitHub gh = getGitHub();
            GHRepository repo = gh.getRepository(repoFullName);

            repo.createContent()
                .branch(branchName)
                .path(filePath)
                .content(content)
                .message("Add " + filePath)
                .commit();
        } catch (Exception e) {
            throw e;
        }
    }
    
    /**
     * Batch create multiple files in one commit using GitHub Database API
     * Process: Get Ref → Create Blobs → Create Tree → Create Commit → Update Ref
     */
    public void createMultipleFilesInRepo(String repoFullName, java.util.Map<String, String> fileContents, 
                                          String branchName, String commitMessage) throws IOException {
        try {
            GitHub gh = getGitHub();
            GHRepository repo = gh.getRepository(repoFullName);
            
            // Step 1: Get Reference (current commit SHA)
            String ref = "refs/heads/" + branchName;
            var refObj = repo.getRef(ref.replace("refs/", ""));
            String baseCommitSha = refObj.getObject().getSha();
            
            // Get base tree
            var baseCommit = repo.getCommit(baseCommitSha);
            String baseTreeSha = baseCommit.getTree().getSha();
            
            // Step 2: Create Tree with all files
            var treeBuilder = repo.createTree().baseTree(baseTreeSha);
            
            for (java.util.Map.Entry<String, String> entry : fileContents.entrySet()) {
                String path = entry.getKey();
                String content = entry.getValue();
                
                // Add file content directly as byte array (not as blob SHA)
                // This ensures content is stored correctly
                byte[] contentBytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                treeBuilder.add(path, contentBytes, false); // false = normal file (100644)
            }
            
            // Step 3: Create the tree
            var newTree = treeBuilder.create();
            
            // Step 4: Create commit with new tree
            var newCommit = repo.createCommit()
                .message(commitMessage)
                .tree(newTree.getSha())
                .parent(baseCommitSha)
                .create();
            
            // Step 5: Update branch reference to new commit
            refObj.updateTo(newCommit.getSHA1());
            
        } catch (Exception e) {
            throw new IOException("Failed to create multiple files: " + e.getMessage(), e);
        }
    }
}
