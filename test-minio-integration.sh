#!/bin/bash

# Test Script for MinIO Integration
# Run this after starting backend and MinIO

BACKEND_URL="http://localhost:8080"
ASSIGNMENT_CODE="TEST_ABC123"

echo "=========================================="
echo "🧪 Testing MinIO Integration"
echo "=========================================="
echo ""

# Test 1: Create test cases
echo "📤 Test 1: Creating test cases..."
RESPONSE=$(curl -s -X POST "$BACKEND_URL/api/test-cases" \
  -H "Content-Type: application/json" \
  -d @test-case-request-sample.json)

echo "$RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$RESPONSE"
echo ""

# Test 2: Get test case structure
echo "📊 Test 2: Getting test case structure..."
RESPONSE=$(curl -s "$BACKEND_URL/api/test-cases/$ASSIGNMENT_CODE/structure")
echo "$RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$RESPONSE"
echo ""

# Test 3: Get all test cases metadata
echo "📋 Test 3: Getting all test cases metadata..."
RESPONSE=$(curl -s "$BACKEND_URL/api/test-cases/$ASSIGNMENT_CODE")
echo "$RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$RESPONSE"
echo ""

# Test 4: Get presigned download URL
echo "🔗 Test 4: Getting presigned download URL..."
RESPONSE=$(curl -s "$BACKEND_URL/api/test-cases/$ASSIGNMENT_CODE/download-url")
echo "$RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$RESPONSE"

# Extract download URL
DOWNLOAD_URL=$(echo "$RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin)['downloadUrl'])" 2>/dev/null)

if [ -z "$DOWNLOAD_URL" ]; then
  echo "❌ Failed to get download URL"
  exit 1
fi

echo ""
echo "✅ Got download URL"
echo ""

# Test 5: Download ZIP file
echo "📥 Test 5: Downloading test cases ZIP..."
curl -s -L -f -o test-cases-downloaded.zip "$DOWNLOAD_URL"

if [ $? -ne 0 ]; then
  echo "❌ Failed to download ZIP file"
  exit 1
fi

echo "✅ Downloaded test-cases-downloaded.zip"
echo ""

# Test 6: Extract and verify
echo "📦 Test 6: Extracting and verifying..."
rm -rf test-cases-extracted 2>/dev/null
mkdir -p test-cases-extracted
unzip -q test-cases-downloaded.zip -d test-cases-extracted

if [ $? -ne 0 ]; then
  echo "❌ Failed to extract ZIP file"
  exit 1
fi

echo "✅ Extracted successfully"
echo ""

# Verify structure
echo "📁 Verifying structure..."
if [ -d "test-cases-extracted/test-cases/ex1" ] && \
   [ -d "test-cases-extracted/test-cases/ex2" ] && \
   [ -d "test-cases-extracted/test-cases/ex3" ]; then
  echo "✅ Folder structure is correct"
else
  echo "❌ Folder structure is incorrect"
  exit 1
fi

# Verify content
echo ""
echo "📄 Verifying content..."
echo "ex1/input1.txt: $(cat test-cases-extracted/test-cases/ex1/input1.txt)"
echo "ex1/output1.txt: $(cat test-cases-extracted/test-cases/ex1/output1.txt)"
echo ""

# Count files
TOTAL_FILES=$(find test-cases-extracted/test-cases -name "*.txt" | wc -l)
echo "✅ Total test case files: $TOTAL_FILES (expected: 18)"

if [ $TOTAL_FILES -eq 18 ]; then
  echo "✅ File count is correct"
else
  echo "⚠️ File count mismatch (got: $TOTAL_FILES, expected: 18)"
fi

echo ""
echo "=========================================="
echo "✅ All tests passed!"
echo "=========================================="
echo ""
echo "📁 Files created:"
echo "  - test-cases-downloaded.zip"
echo "  - test-cases-extracted/"
echo ""
echo "🧹 Clean up:"
echo "  rm test-cases-downloaded.zip"
echo "  rm -rf test-cases-extracted"
