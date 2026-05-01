package com.example.auto_git_be.service;

import com.example.auto_git_be.dto.assignment.AssignmentTaskCreateRequest;
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

    public void deleteRepository(String repoFullName) throws IOException {
        GitHub gh = getGitHub();
        GHRepository repo = gh.getRepository(repoFullName);
        repo.delete();
    }

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

    public String getToken() {
        return githubToken;
    }

    public void createMultipleFilesInRepo(String repoFullName, String branchName, List<AssignmentTaskCreateRequest> tasks) throws IOException {
        GitHub gh = getGitHub();
        GHRepository repo = gh.getRepository(repoFullName);

        GHBranch branch = repo.getBranch(branchName);
        String latestCommitSha = branch.getSHA1();

        GHTreeBuilder treeBuilder = repo.createTree().baseTree(latestCommitSha);

        for (int i = 0; i < tasks.size(); i++) {
            AssignmentTaskCreateRequest task = tasks.get(i);
            int orderNo = task.getOrderNo() != null ? task.getOrderNo() : (i + 1);
            String taskFileName = "task" + orderNo + ".cpp";

            String taskContent = "\n#include <iostream>\nusing namespace std;\n\nint main() {\n    // Your code here\n    return 0;\n}\n";

            treeBuilder.add(taskFileName, taskContent, false);
        }

        GHTree newTree = treeBuilder.create();

        GHCommit newCommit = repo.createCommit()
                .message("Add all assignment tasks")
                .tree(newTree.getSha())
                .parent(latestCommitSha)
                .create();
        repo.getRef("heads/" + branchName).updateTo(newCommit.getSHA1());
    }

    public String pushStudentCode(String repoName, String branchName, String filePath, String sourceCode, String commitMessage) throws IOException {
        String repoFullName = repoName.replace("https://github.com/", "");

        GitHub gh = getGitHub();
        GHRepository repo = gh.getRepository(repoFullName);

        try {
            GHContent existingFile = repo.getFileContent(filePath, branchName);
            GHContentUpdateResponse response = existingFile.update(sourceCode, commitMessage, branchName);
            return response.getCommit().getSHA1();

        } catch (GHFileNotFoundException e) {
            GHContentUpdateResponse response = repo.createContent()
                    .branch(branchName)
                    .path(filePath)
                    .content(sourceCode)
                    .message(commitMessage)
                    .commit();

            return response.getCommit().getSHA1();
        }
    }

    public void deleteFileInRepo(String repoFullName, String filePath, String branchName) throws IOException {
        try {
            GitHub gh = getGitHub();
            GHRepository repo = gh.getRepository(repoFullName);

            GHContent content = repo.getFileContent(filePath, branchName);
            content.delete("Delete " + filePath, branchName);
        } catch (GHFileNotFoundException e) {
            // File doesn't exist, no need to delete
        }
    }
}
