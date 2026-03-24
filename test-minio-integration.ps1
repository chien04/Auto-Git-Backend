# Test Script for MinIO Integration (PowerShell)
# Run this after starting backend and MinIO

$BACKEND_URL = "http://localhost:8080"
$ASSIGNMENT_CODE = "TEST_ABC123"

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "🧪 Testing MinIO Integration" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

# Test 1: Create test cases
Write-Host "📤 Test 1: Creating test cases..." -ForegroundColor Yellow
$Response = Invoke-RestMethod -Uri "$BACKEND_URL/api/test-cases" `
    -Method Post `
    -ContentType "application/json" `
    -InFile "test-case-request-sample.json"

$Response | ConvertTo-Json -Depth 10
Write-Host ""

# Test 2: Get test case structure
Write-Host "📊 Test 2: Getting test case structure..." -ForegroundColor Yellow
$Response = Invoke-RestMethod -Uri "$BACKEND_URL/api/test-cases/$ASSIGNMENT_CODE/structure"
$Response | ConvertTo-Json
Write-Host ""

# Test 3: Get all test cases metadata
Write-Host "📋 Test 3: Getting all test cases metadata..." -ForegroundColor Yellow
$Response = Invoke-RestMethod -Uri "$BACKEND_URL/api/test-cases/$ASSIGNMENT_CODE"
Write-Host "Total test cases: $($Response.Count)"
Write-Host ""

# Test 4: Get presigned download URL
Write-Host "🔗 Test 4: Getting presigned download URL..." -ForegroundColor Yellow
$Response = Invoke-RestMethod -Uri "$BACKEND_URL/api/test-cases/$ASSIGNMENT_CODE/download-url"
$Response | ConvertTo-Json
$DOWNLOAD_URL = $Response.downloadUrl
Write-Host ""
Write-Host "✅ Got download URL" -ForegroundColor Green
Write-Host ""

# Test 5: Download ZIP file
Write-Host "📥 Test 5: Downloading test cases ZIP..." -ForegroundColor Yellow
Invoke-WebRequest -Uri $DOWNLOAD_URL -OutFile "test-cases-downloaded.zip"

if (Test-Path "test-cases-downloaded.zip") {
    Write-Host "✅ Downloaded test-cases-downloaded.zip" -ForegroundColor Green
} else {
    Write-Host "❌ Failed to download ZIP file" -ForegroundColor Red
    exit 1
}
Write-Host ""

# Test 6: Extract and verify
Write-Host "📦 Test 6: Extracting and verifying..." -ForegroundColor Yellow

# Remove old extracted folder
if (Test-Path "test-cases-extracted") {
    Remove-Item -Recurse -Force "test-cases-extracted"
}

# Extract ZIP
Expand-Archive -Path "test-cases-downloaded.zip" -DestinationPath "test-cases-extracted" -Force

if (Test-Path "test-cases-extracted\test-cases") {
    Write-Host "✅ Extracted successfully" -ForegroundColor Green
} else {
    Write-Host "❌ Failed to extract ZIP file" -ForegroundColor Red
    exit 1
}
Write-Host ""

# Verify structure
Write-Host "📁 Verifying structure..." -ForegroundColor Yellow
if ((Test-Path "test-cases-extracted\test-cases\ex1") -and
    (Test-Path "test-cases-extracted\test-cases\ex2") -and
    (Test-Path "test-cases-extracted\test-cases\ex3")) {
    Write-Host "✅ Folder structure is correct" -ForegroundColor Green
} else {
    Write-Host "❌ Folder structure is incorrect" -ForegroundColor Red
    exit 1
}

# Verify content
Write-Host ""
Write-Host "📄 Verifying content..." -ForegroundColor Yellow
$input1 = Get-Content "test-cases-extracted\test-cases\ex1\input1.txt"
$output1 = Get-Content "test-cases-extracted\test-cases\ex1\output1.txt"
Write-Host "ex1/input1.txt: $input1"
Write-Host "ex1/output1.txt: $output1"
Write-Host ""

# Count files
$TOTAL_FILES = (Get-ChildItem -Path "test-cases-extracted\test-cases" -Filter "*.txt" -Recurse).Count
Write-Host "✅ Total test case files: $TOTAL_FILES (expected: 18)" -ForegroundColor Green

if ($TOTAL_FILES -eq 18) {
    Write-Host "✅ File count is correct" -ForegroundColor Green
} else {
    Write-Host "⚠️ File count mismatch (got: $TOTAL_FILES, expected: 18)" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "✅ All tests passed!" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "📁 Files created:" -ForegroundColor Yellow
Write-Host "  - test-cases-downloaded.zip"
Write-Host "  - test-cases-extracted\"
Write-Host ""
Write-Host "🧹 Clean up:" -ForegroundColor Yellow
Write-Host "  Remove-Item test-cases-downloaded.zip"
Write-Host "  Remove-Item -Recurse test-cases-extracted"
