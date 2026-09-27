package com.entropylab.mock;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Verification test for M11.3 — Mock Filename Generator Utility.
 * Tests formatting, sanitization, filesystem safety, and collision-free rapid generation.
 */
public class MockFileNamingVerification {

    private static final Pattern ILLEGAL_CHARS = Pattern.compile("[\\\\/:*?\"<>|]");

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================");
        System.out.println("   Mock File Naming Verification (M11.3)");
        System.out.println("==================================================");

        // 1. Standard Path Test
        System.out.println("\n[TEST 1] Testing standard path sanitization...");
        String sample1 = "/github/users/octocat";
        String file1 = MockFileNaming.generateFileName(sample1);
        System.out.println("  Input: '" + sample1 + "' -> Output: '" + file1 + "'");
        if (!file1.startsWith("github_users_octocat_") || !file1.endsWith(".json")) {
            throw new AssertionError("Expected format github_users_octocat_<timestamp>.json! Got: " + file1);
        }
        assertFilesystemSafe(file1);
        System.out.println("  Standard path: PASS");

        // 2. Query Parameters and Special Characters
        System.out.println("\n[TEST 2] Testing query parameters and special characters...");
        String sample2 = "/api/v2/search?category=books&limit=10&sort=desc#anchor";
        String file2 = MockFileNaming.generateFileName(sample2);
        System.out.println("  Input: '" + sample2 + "' -> Output: '" + file2 + "'");
        if (!file2.startsWith("api_v2_search_category_books_limit_10_sort_desc_anchor_") || !file2.endsWith(".json")) {
            throw new AssertionError("Special characters improperly sanitized! Got: " + file2);
        }
        assertFilesystemSafe(file2);
        System.out.println("  Query parameters and special characters: PASS");

        // 3. Leading / Trailing / Consecutive Slashes
        System.out.println("\n[TEST 3] Testing consecutive slashes and symbols...");
        String sample3 = "///v1///catalog//items///";
        String file3 = MockFileNaming.generateFileName(sample3);
        System.out.println("  Input: '" + sample3 + "' -> Output: '" + file3 + "'");
        if (!file3.startsWith("v1_catalog_items_") || !file3.endsWith(".json")) {
            throw new AssertionError("Leading/trailing slashes not cleaned! Got: " + file3);
        }
        assertFilesystemSafe(file3);
        System.out.println("  Consecutive slashes trimmed: PASS");

        // 4. Edge Cases: Root, Empty, Null, Whitespace
        System.out.println("\n[TEST 4] Testing edge cases (root, empty, null)...");
        String rootFile = MockFileNaming.generateFileName("/");
        System.out.println("  Input: '/' -> Output: '" + rootFile + "'");
        if (!rootFile.startsWith("root_") || !rootFile.endsWith(".json")) {
            throw new AssertionError("Root slash failed! Got: " + rootFile);
        }
        assertFilesystemSafe(rootFile);

        String nullFile = MockFileNaming.generateFileName(null);
        System.out.println("  Input: null -> Output: '" + nullFile + "'");
        if (!nullFile.startsWith("mock_") || !nullFile.endsWith(".json")) {
            throw new AssertionError("Null path failed! Got: " + nullFile);
        }
        assertFilesystemSafe(nullFile);

        String emptyFile = MockFileNaming.generateFileName("   ");
        System.out.println("  Input: '   ' -> Output: '" + emptyFile + "'");
        if (!emptyFile.startsWith("mock_") || !emptyFile.endsWith(".json")) {
            throw new AssertionError("Blank path failed! Got: " + emptyFile);
        }
        assertFilesystemSafe(emptyFile);
        System.out.println("  Edge cases: PASS");

        // 5. Rapid Concurrent Collision Test
        System.out.println("\n[TEST 5] Testing collision resistance across 1000 rapid concurrent calls...");
        int totalRequests = 1000;
        int threadCount = 10;
        Set<String> generatedNames = ConcurrentHashMap.newKeySet();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalRequests);

        for (int i = 0; i < totalRequests; i++) {
            final int id = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    String name = MockFileNaming.generateFileName("/api/rapid/test/" + (id % 5));
                    generatedNames.add(name);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Fire all threads simultaneously
        startLatch.countDown();
        if (!doneLatch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Timed out waiting for concurrent filename generation!");
        }
        executor.shutdown();

        System.out.println("  Generated filenames count: " + generatedNames.size() + " / " + totalRequests);
        if (generatedNames.size() != totalRequests) {
            throw new AssertionError("Collision detected! Expected " + totalRequests + " unique filenames, but got: " + generatedNames.size());
        }
        System.out.println("  Zero collisions across rapid multi-threaded calls - PASS");

        System.out.println("\n==================================================");
        System.out.println("   All M11.3 Mock File Naming tests PASSED!");
        System.out.println("==================================================");
    }

    private static void assertFilesystemSafe(String filename) {
        if (ILLEGAL_CHARS.matcher(filename).find()) {
            throw new AssertionError("Filename contains illegal filesystem characters: " + filename);
        }
        try {
            Path.of(filename);
        } catch (Exception e) {
            throw new AssertionError("Filename cannot be parsed as valid Path: " + filename, e);
        }
    }
}
