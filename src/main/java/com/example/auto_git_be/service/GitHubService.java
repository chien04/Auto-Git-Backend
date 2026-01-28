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

    /**
     * Initialize GitHub connection
     */
    private GitHub getGitHub() throws IOException {
        if (github == null) {
            github = new GitHubBuilder().withOAuthToken(githubToken).build();
        }
        return github;
    }

    /**
     * Create a new repository for a class
     */
    public GHRepository createRepository(String repoName, String description) throws IOException {
        GitHub gh = getGitHub();
        
        GHCreateRepositoryBuilder builder;
        
        if (githubOrganization != null && !githubOrganization.isEmpty()) {
            // Create in organization
            GHOrganization org = gh.getOrganization(githubOrganization);
            builder = org.createRepository(repoName);
        } else {
            // Create in personal
            builder = gh.createRepository(repoName);
        }
        
        return builder
                .description(description)
                .private_(false) // Set to true if you want private repos
                .autoInit(true)  // Initialize with README
                .create();
    }

    /**
     * Create a branch
     */
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

    /**
     * Get GitHub token
     */
    public String getToken() {
        return githubToken;
    }
    
    /**
     * Create workflow file automatically in repository
     */
    public void createWorkflowFile(String repoFullName, String branchName) throws IOException {
        String workflowContent = """
name: Auto Grading

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
        uses: actions/checkout@v3
      
      - name: Setup Java
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Setup Python
        uses: actions/setup-python@v4
        with:
          python-version: '3.11'
      
      - name: Setup C/C++
        run: |
          sudo apt-get update
          sudo apt-get install -y gcc g++ build-essential
      
      - name: Setup Node.js
        uses: actions/setup-node@v3
        with:
          node-version: '18'
      
      - name: Detect and compile code
        id: compile
        continue-on-error: true
        run: |
          echo "Detecting code files..."
          
          if [ -f "Main.java" ] || [ -f "Solution.java" ]; then
            echo "LANGUAGE=java" >> $GITHUB_OUTPUT
            javac *.java || exit 1
          elif [ -f "main.cpp" ] || [ -f "solution.cpp" ]; then
            echo "LANGUAGE=cpp" >> $GITHUB_OUTPUT
            g++ -o solution -std=c++17 -O2 *.cpp || exit 1
          elif [ -f "main.c" ] || [ -f "solution.c" ]; then
            echo "LANGUAGE=c" >> $GITHUB_OUTPUT
            gcc -o solution -std=c11 -O2 *.c -lm || exit 1
          elif [ -f "main.py" ] || [ -f "solution.py" ]; then
            echo "LANGUAGE=python" >> $GITHUB_OUTPUT
          elif [ -f "main.js" ] || [ -f "solution.js" ]; then
            echo "LANGUAGE=javascript" >> $GITHUB_OUTPUT
          else
            echo "LANGUAGE=none" >> $GITHUB_OUTPUT
          fi
      
      - name: Run test cases
        id: test
        if: steps.compile.outputs.LANGUAGE != 'none'
        run: |
          TOTAL_TESTS=0
          PASSED_TESTS=0
          
          if [ ! -d "test-cases" ]; then
            echo "No test-cases folder"
            echo "TOTAL_TESTS=0" >> $GITHUB_OUTPUT
            echo "PASSED_TESTS=0" >> $GITHUB_OUTPUT
            echo "SCORE=0" >> $GITHUB_OUTPUT
            exit 0
          fi
          
          for input in test-cases/input*.txt; do
            [ ! -f "$input" ] && continue
            TOTAL_TESTS=$((TOTAL_TESTS + 1))
            num=$(basename "$input" | sed 's/input\\([0-9]*\\)\\.txt/\\1/')
            expected="test-cases/output${num}.txt"
            
            echo "Test Case #$num"
            
            LANG="${{ steps.compile.outputs.LANGUAGE }}"
            
            timeout 5s bash -c "
              if [ '$LANG' = 'java' ]; then
                java Main < '$input' > actual.txt 2>&1
              elif [ '$LANG' = 'cpp' ] || [ '$LANG' = 'c' ]; then
                ./solution < '$input' > actual.txt 2>&1
              elif [ '$LANG' = 'python' ]; then
                python3 main.py < '$input' > actual.txt 2>&1
              elif [ '$LANG' = 'javascript' ]; then
                node main.js < '$input' > actual.txt 2>&1
              fi
            " || { echo "FAILED"; continue; }
            
            if diff -ZB "$expected" actual.txt > /dev/null 2>&1; then
              echo "PASSED"
              PASSED_TESTS=$((PASSED_TESTS + 1))
            else
              echo "FAILED"
            fi
          done
          
          if [ $TOTAL_TESTS -gt 0 ]; then
            SCORE=$((PASSED_TESTS * 100 / TOTAL_TESTS))
          else
            SCORE=0
          fi
          
          echo "TOTAL_TESTS=$TOTAL_TESTS" >> $GITHUB_OUTPUT
          echo "PASSED_TESTS=$PASSED_TESTS" >> $GITHUB_OUTPUT
          echo "SCORE=$SCORE" >> $GITHUB_OUTPUT
      
      - name: Show summary
        if: always()
        run: |
          echo "## 📊 Test Results" >> $GITHUB_STEP_SUMMARY
          echo "" >> $GITHUB_STEP_SUMMARY
          echo "- **Score:** ${{ steps.test.outputs.SCORE }}/100" >> $GITHUB_STEP_SUMMARY
          echo "- **Tests Passed:** ${{ steps.test.outputs.PASSED_TESTS }}/${{ steps.test.outputs.TOTAL_TESTS }}" >> $GITHUB_STEP_SUMMARY
          echo "- **Language:** ${{ steps.compile.outputs.LANGUAGE }}" >> $GITHUB_STEP_SUMMARY
      
      - name: Send score to backend
        if: steps.test.outputs.SCORE != ''
        continue-on-error: true
        run: |
          BACKEND_URL="%s"
          
          SCORE="${{ steps.test.outputs.SCORE }}"
          PASSED="${{ steps.test.outputs.PASSED_TESTS }}"
          TOTAL="${{ steps.test.outputs.TOTAL_TESTS }}"
          
          echo "📤 Sending score to $BACKEND_URL..."
          
          curl -X POST "$BACKEND_URL/api/assignment/update-score" \\
            -H "Content-Type: application/json" \\
            -d "{
              \\"repoFullName\\": \\"${{ github.repository }}\\",
              \\"branchName\\": \\"${{ github.ref_name }}\\",
              \\"score\\": $SCORE,
              \\"passedTests\\": $PASSED,
              \\"totalTests\\": $TOTAL
            }" \\
            && echo "✅ Score submitted successfully" \\
            || echo "❌ Failed to submit score (backend may be offline)"
""";
        
        // Inject backend URL into workflow
        workflowContent = String.format(workflowContent, backendUrl);
        
        createFileInRepo(repoFullName, ".github/workflows/auto-grading.yml", workflowContent, branchName);
    }
    
    /**
     * Create sample test cases
     */
    public void createSampleTestCases(String repoFullName, String branchName) throws IOException {
        // Sample test case 1
        createFileInRepo(repoFullName, "test-cases/input1.txt", "5 10", branchName);
        createFileInRepo(repoFullName, "test-cases/output1.txt", "15", branchName);
        
        // Sample test case 2
        createFileInRepo(repoFullName, "test-cases/input2.txt", "-100 50", branchName);
        createFileInRepo(repoFullName, "test-cases/output2.txt", "-50", branchName);
        
        // Sample test case 3
        createFileInRepo(repoFullName, "test-cases/input3.txt", "0 0", branchName);
        createFileInRepo(repoFullName, "test-cases/output3.txt", "0", branchName);
    }
    
    /**
     * Create a file in repository via GitHub API
     * Note: GitHub API will handle base64 encoding automatically
     */
    private void createFileInRepo(String repoFullName, String filePath, String content, String branchName) throws IOException {
        try {
            GitHub gh = getGitHub();
            GHRepository repo = gh.getRepository(repoFullName);
            
            // Create file - GitHub API handles encoding automatically
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
}
