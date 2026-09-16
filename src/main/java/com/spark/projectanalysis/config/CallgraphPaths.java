package com.spark.projectanalysis.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 工具数据目录统一管理。
 *
 * 默认放在 D:\.callgraph，避免占用 C 盘空间。
 * 可通过系统属性 callgraph.home 或环境变量 CALLGRAPH_HOME 覆盖。
 * 若 D 盘不可用，自动回退到用户主目录下的 .callgraph。
 */
public final class CallgraphPaths {

    private static final String DIR_NAME = ".callgraph";

    private static volatile Path cachedHome;

    private CallgraphPaths() {
    }

    /**
     * 获取 callgraph 数据根目录（如 D:\.callgraph），不存在则创建。
     */
    public static Path getHome() {
        if (cachedHome != null) {
            return cachedHome;
        }
        synchronized (CallgraphPaths.class) {
            if (cachedHome != null) {
                return cachedHome;
            }
            Path home = resolveHome();
            try {
                Files.createDirectories(home);
            } catch (IOException ignore) {
                // 创建失败时回退到用户目录
                Path fallback = Paths.get(System.getProperty("user.home"), DIR_NAME);
                try {
                    Files.createDirectories(fallback);
                    home = fallback;
                } catch (IOException e) {
                    // 实在不行用临时目录
                    home = Paths.get(System.getProperty("java.io.tmpdir"), DIR_NAME);
                    try {
                        Files.createDirectories(home);
                    } catch (IOException ignored) {
                    }
                }
            }
            cachedHome = home;
            return home;
        }
    }

    private static Path resolveHome() {
        // 1) 系统属性 / 环境变量显式指定
        String override = System.getProperty("callgraph.home");
        if (override == null || override.trim().isEmpty()) {
            override = System.getenv("CALLGRAPH_HOME");
        }
        if (override != null && !override.trim().isEmpty()) {
            return Paths.get(override.trim());
        }
        // 2) 默认 D:\.callgraph（仅 Windows；非 Windows 上 "D:" 会被当成相对目录名）
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            Path dDrive = Paths.get("D:", DIR_NAME);
            if (isWritable(dDrive)) {
                return dDrive;
            }
        }
        // 3) 回退到用户主目录
        return Paths.get(System.getProperty("user.home"), DIR_NAME);
    }

    /** 探测目录是否可写：已存在就认为可写（避免不必要的探测文件），不存在才尝试创建 */
    private static boolean isWritable(Path dir) {
        // 目录已存在且是目录 → 直接信任，不再探测（沙箱环境下探测会被误判）
        if (Files.isDirectory(dir)) {
            return true;
        }
        // 不存在才尝试创建 + 探测
        try {
            Files.createDirectories(dir);
            Path probe = Files.createTempFile(dir, ".probe", null);
            Files.delete(probe);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** 规则文件路径：<home>/noise-rules.json */
    public static Path noiseRulesFile() {
        return getHome().resolve("noise-rules.json");
    }

    /** Git 工作目录：<home>/workspaces */
    public static Path workspacesDir() {
        return getHome().resolve("workspaces");
    }
}
