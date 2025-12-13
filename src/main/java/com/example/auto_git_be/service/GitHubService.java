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
            System.out.println("GitHub API Rate Limit - Remaining: " + rateLimit.getCore().getRemaining() + "/" + rateLimit.getCore().getLimit());
            
            if (rateLimit.getCore().getRemaining() < 10) {
                throw new IOException("GitHub API rate limit exceeded. Reset at: " + rateLimit.getCore().getResetDate());
            }
            
            GHRepository repo = gh.getRepository(repoFullName);
            System.out.println("Getting commits for repo: " + repoFullName + ", branch: " + branchName);
            
            // Check if branch exists
            GHBranch branch = null;
            try {
                branch = repo.getBranch(branchName);
                System.out.println("Branch found: " + branch.getName() + ", SHA: " + branch.getSHA1());
            } catch (Exception e) {
                System.out.println("Branch '" + branchName + "' not found, listing all branches...");
                for (GHBranch b : repo.getBranches().values()) {
                    System.out.println("  Available branch: " + b.getName());
                }
                throw new IOException("Branch '" + branchName + "' not found in repository. Available branches listed above.");
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
                
                System.out.println("Successfully retrieved " + count + " commits using queryCommits");
                
            } catch (Exception e) {
                System.err.println("queryCommits failed, trying listCommits: " + e.getMessage());
                
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
                
                System.out.println("Retrieved " + count + " commits using listCommits fallback");
            }
            
            if (commitList.isEmpty()) {
                System.out.println("No commits found for branch: " + branchName);
            } else {
                System.out.println("Sample commit: " + commitList.get(0).getSHA1() + " - " + commitList.get(0).getCommitShortInfo().getMessage());
            }
            
            return commitList;
            
        } catch (IOException e) {
            System.err.println("Error getting commits: " + e.getMessage());
            e.printStackTrace();
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
}
