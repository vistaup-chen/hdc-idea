package com.deveco.hdcidea;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects the current HarmonyOS app's bundle name and main ability name.
 *
 * In a standard HarmonyOS project (created by DevEco Studio):
 *   - Bundle name lives in: AppScope/app.json5  →  "app": { "bundleName": "..." }
 *   - Ability name lives in: <module>/src/main/module.json5  →  "abilities"[0]."name"
 *
 * The project may contain multiple modules; we collect the bundle name from
 * app.json5 (project-level) and the main ability from any entry-module's
 * module.json5.
 */
public class ProjectDetector {

    private static final Pattern BUNDLE_NAME_PATTERN =
            Pattern.compile("\"bundleName\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern ABILITY_NAME_PATTERN =
            Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    /**
     * Holds the detected app identity.
     */
    public static class AppIdentity {
        public final String bundleName;
        @Nullable
        public final String abilityName;

        public AppIdentity(String bundleName, @Nullable String abilityName) {
            this.bundleName = bundleName;
            this.abilityName = abilityName;
        }

        /**
         * Returns the start target in "bundleName/abilityName" format.
         */
        @Nullable
        public String getStartTarget() {
            if (abilityName != null) {
                return bundleName + "/" + abilityName;
            }
            return null;
        }
    }

    /**
     * Detects the app identity for the given project.
     *
     * @param project current project
     * @return detected identity, or null if not found
     */
    @Nullable
    public static AppIdentity detect(Project project) {
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir == null) {
            return null;
        }
        String basePath = baseDir.getPath();

        String bundleName = null;
        String abilityName = null;

        // 1. Find bundle name from AppScope/app.json5
        VirtualFile appJson5 = baseDir.findFileByRelativePath("AppScope/app.json5");
        if (appJson5 != null && appJson5.exists()) {
            try {
                String content = new String(appJson5.contentsToByteArray(), StandardCharsets.UTF_8);
                bundleName = extractBundleName(content);
            } catch (IOException e) {
                // ignore
            }
        }

        // 2. Fallback: search any app.json5 under the project
        if (bundleName == null) {
            bundleName = searchBundleName(basePath);
        }

        // 3. Find ability name from any module's src/main/module.json5
        abilityName = findAbilityName(basePath, baseDir);

        if (bundleName == null) {
            return null;
        }
        return new AppIdentity(bundleName, abilityName);
    }

    /**
     * Searches the file system for an app.json5 containing a bundleName.
     */
    @Nullable
    private static String searchBundleName(String basePath) {
        final String[] found = {null};
        try {
            Files.walkFileTree(Paths.get(basePath), java.util.EnumSet.noneOf(java.nio.file.FileVisitOption.class), 4,
                    new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            if (file.getFileName().toString().equals("app.json5")) {
                                try {
                                    String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                                    String bn = extractBundleName(content);
                                    if (bn != null) {
                                        found[0] = bn;
                                        return FileVisitResult.TERMINATE;
                                    }
                                } catch (IOException e) {
                                    // ignore
                                }
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(Path file, IOException exc) {
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException e) {
            // ignore
        }
        return found[0];
    }

    /**
     * Finds the first ability name from any module's src/main/module.json5.
     */
    @Nullable
    private static String findAbilityName(String basePath, VirtualFile baseDir) {
        // First try: look in known module directories via VFS
        String ability = null;
        try {
            List<Path> moduleFiles = new ArrayList<>();
            Files.walkFileTree(Paths.get(basePath), java.util.EnumSet.noneOf(java.nio.file.FileVisitOption.class), 6,
                    new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            String name = file.getFileName().toString();
                            if (name.equals("module.json5") || name.equals("module.json")) {
                                String pathStr = file.toString().replace('\\', '/');
                                if (pathStr.contains("/src/main/")) {
                                    moduleFiles.add(file);
                                }
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(Path file, IOException exc) {
                            return FileVisitResult.CONTINUE;
                        }
                    });
            // Prefer files in an "entry" module, then take the first one
            Path best = null;
            for (Path p : moduleFiles) {
                String pathStr = p.toString().replace('\\', '/');
                if (pathStr.contains("/entry/")) {
                    best = p;
                    break;
                }
            }
            if (best == null && !moduleFiles.isEmpty()) {
                best = moduleFiles.get(0);
            }
            if (best != null) {
                String content = new String(Files.readAllBytes(best), StandardCharsets.UTF_8);
                ability = extractAbilityName(content);
            }
        } catch (IOException e) {
            // ignore
        }
        return ability;
    }

    @Nullable
    private static String extractBundleName(String content) {
        Matcher m = BUNDLE_NAME_PATTERN.matcher(content);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    @Nullable
    private static String extractAbilityName(String content) {
        int abilitiesIdx = content.indexOf("\"abilities\"");
        if (abilitiesIdx < 0) {
            return null;
        }
        Matcher m = ABILITY_NAME_PATTERN.matcher(content.substring(abilitiesIdx));
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }
}
