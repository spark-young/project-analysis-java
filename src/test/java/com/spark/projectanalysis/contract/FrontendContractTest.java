package com.spark.projectanalysis.contract;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * 把前端 Node 契约测试（{@code src/test/js/contract.test.js}）纳入 {@code mvn test}。
 *
 * <p>背景（QA 实证）：前端签名/噪声契约测试是纯 Node 脚本，原先只有 2 个后端 JUnit 读同一份
 * 夹具，前端 {@code Sig} 路径完全没有构建保护、日常 {@code mvn test} 不会执行它。本类用
 * {@link ProcessBuilder} 驱动 {@code node} 跑脚本，使构建强制校验前端路径。</p>
 *
 * <p>行为约定：
 * <ul>
 *   <li>node 可用 → 执行脚本，UTF-8 捕获输出，退出码非 0 即 {@code fail()}（并附命令与输出）；</li>
 *   <li>node 不可用（部署机可能无 Node）→ 用 {@link Assumptions#assumeTrue} **优雅跳过**，
 *       绝不让 {@code mvn test} 因缺 node 而失败；</li>
 *   <li>探测顺序：{@code -Dnode.executable=<路径>} → 环境变量 {@code NODE} → PATH 上的 {@code node}；</li>
 *   <li>脚本路径默认相对项目 basedir；可用 {@code -Dfrontend.contract.script=<路径>} 覆盖（便于反向验证）。</li>
 * </ul>
 *
 * <p>不改任何后端/前端源码与夹具，不引入新 Maven 插件。子进程的 {@code CALLGRAPH_HOME} 指向临时目录，
 * 绝不触碰真实 {@code D:\.callgraph}。</p>
 */
class FrontendContractTest {

    private static final int TIMEOUT_SECONDS = 120;

    @Test
    void frontendSigContract_matchesBackendFixture() throws Exception {
        String node = resolveNodeExecutable();
        Assumptions.assumeTrue(node != null,
                "未检测到可用 node（PATH / -Dnode.executable / 环境变量 NODE 均不可用）——跳过前端契约测试；"
                        + "如需强制运行请设置 -Dnode.executable=<node 可执行文件路径>");

        Path script = resolveScript();
        if (script == null || !Files.isRegularFile(script)) {
            fail("未找到前端契约脚本（basedir=" + projectBasedir() + "）: " + script);
        }

        List<String> command = new ArrayList<>();
        command.add(node);
        command.add(script.toString());

        // 输出重定向到临时文件，避免管道缓冲导致的死锁；UTF-8 读取
        Path outFile = Files.createTempFile("frontend-contract-", ".log");
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(projectBasedir().toFile());
        pb.redirectErrorStream(true);
        pb.redirectOutput(outFile.toFile());
        // 隔离兜底：子进程 callgraph home 指向临时目录
        File tempHome = Files.createTempDirectory("frontend-contract-home").toFile();
        pb.environment().put("CALLGRAPH_HOME", tempHome.getAbsolutePath());

        Process process = pb.start();
        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            fail("前端 Node 契约测试超时（>" + TIMEOUT_SECONDS + "s）。命令=" + command
                    + "\n输出:\n" + readUtf8(outFile));
        }

        int exit = process.exitValue();
        String output = readUtf8(outFile);
        // 透传到 surefire 日志，证明确实执行了 node（而非被跳过）
        System.out.println("[FrontendContractTest] node=" + node + " 命令=" + command);
        System.out.println("[FrontendContractTest] 退出码=" + exit);
        System.out.println("[FrontendContractTest] 输出:\n" + output);
        if (exit != 0) {
            fail("前端 Node 契约测试失败，退出码=" + exit + "。命令=" + command + "\n输出:\n" + output);
        }
    }

    /** 脚本路径：-Dfrontend.contract.script 覆盖 → 否则 basedir/src/test/js/contract.test.js */
    private static Path resolveScript() {
        String override = System.getProperty("frontend.contract.script");
        if (override != null && !override.isBlank()) {
            return Paths.get(override).toAbsolutePath();
        }
        return projectBasedir().resolve("src/test/js/contract.test.js");
    }

    /** 项目 basedir：优先 surefire 注入的 basedir，退回 user.dir */
    private static Path projectBasedir() {
        String basedir = System.getProperty("basedir");
        if (basedir == null || basedir.isBlank()) {
            basedir = System.getProperty("user.dir");
        }
        return Paths.get(basedir).toAbsolutePath();
    }

    /** node 可执行文件：-Dnode.executable → 环境变量 NODE → PATH 上的 node；均不可用返回 null */
    private static String resolveNodeExecutable() {
        String prop = System.getProperty("node.executable");
        if (prop != null && !prop.isBlank()) {
            return prop;
        }
        String env = System.getenv("NODE");
        if (env != null && !env.isBlank()) {
            return env;
        }
        if (canExecute("node")) {
            return "node";
        }
        return null;
    }

    /** 探测命令是否可执行：能跑 --version 且退出码为 0 */
    private static boolean canExecute(String exe) {
        try {
            Path outFile = Files.createTempFile("node-probe-", ".log");
            Process p = new ProcessBuilder(exe, "--version")
                    .redirectErrorStream(true)
                    .redirectOutput(outFile.toFile())
                    .start();
            boolean finished = p.waitFor(15, TimeUnit.SECONDS);
            return finished && p.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String readUtf8(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "<读取输出失败: " + e.getMessage() + ">";
        }
    }
}
