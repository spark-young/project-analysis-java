package com.spark.projectanalysis.service;

import com.jcraft.jsch.Session;
import com.spark.projectanalysis.util.CredentialRedactor;
import com.spark.projectanalysis.service.dto.GitRefs;
import com.spark.projectanalysis.service.dto.GitSwitchResult;
import com.spark.projectanalysis.service.dto.RemoteStatus;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JGit 克隆（纯 Java，无需服务器安装 git 命令）。
 * <p>
 * 认证策略：
 * - SSH URL（git@host:xxx 或 ssh://host/xxx）→ 自动转 HTTPS（GitHub/GitLab/Gitee/GitHub-Enterprise 通用规则），
 *   配合 Token 走 HTTPS 协议，彻底绕开 known_hosts/SSH key 问题。
 * - 如用户坚持用 SSH（内网 GitLab 有已配置的 SSH key），则启用 StrictHostKeyChecking=no 兜底，
 *   不再因 known_hosts 指纹冲突报错。
 * - 公开仓库不传 Token 也可 clone（HTTP 匿名）。
 */
@Service
public class GitCloneService {

    /** 每次 clone/pull 时通过 ThreadLocal 注入的进度回调 */
    private final ThreadLocal<java.util.function.BiConsumer<Integer, String>> progressCb = new ThreadLocal<>();

    /** 开始 clone/pull 前设置进度回调（git 输出里的 Receiving/Resolving/Writing 百分比会实时回调） */
    public void beginProgress(java.util.function.BiConsumer<Integer, String> cb) {
        progressCb.set(cb);
    }

    /** clone/pull 完成后清理回调，避免 ThreadLocal 泄漏 */
    public void endProgress() {
        progressCb.remove();
    }

    /**
     * 列出远端仓库的分支与 Tag（用 git ls-remote，无需本地工作区）。
     * 网络失败/无远端时返回空列表（不抛异常阻断 UI）。
     */
    public GitRefs listRefs(String repoUrl, String token, String username) throws Exception {
        GitRefs refs = new GitRefs();
        String authed = authedUrl(repoUrl, token, username);
        try {
            String out = git(new String[]{"ls-remote", "--symref", "--heads", "--tags", authed}, repoUrl);
            Set<String> branches = new LinkedHashSet<>();
            Set<String> tags = new LinkedHashSet<>();
            for (String raw : out.split("\n")) {
                String line = raw.trim();
                if (line.isEmpty()) continue;
                // symref 行：ref: refs/heads/master\tHEAD
                if (line.startsWith("ref:")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 2 && parts[1].startsWith("refs/heads/")) {
                        refs.setDefaultBranch(parts[1].substring("refs/heads/".length()));
                    }
                    continue;
                }
                int tab = line.indexOf('\t');
                if (tab < 0) continue;
                String refName = line.substring(tab + 1);
                if (refName.startsWith("refs/heads/")) {
                    branches.add(refName.substring("refs/heads/".length()));
                } else if (refName.startsWith("refs/tags/")) {
                    String name = refName.substring("refs/tags/".length());
                    if (name.endsWith("^{}")) name = name.substring(0, name.length() - 3);
                    tags.add(name);
                }
            }
            refs.setBranches(new ArrayList<>(branches));
            refs.setTags(new ArrayList<>(tags));
            if (refs.getDefaultBranch() == null) {
                if (branches.contains("main")) refs.setDefaultBranch("main");
                else if (branches.contains("master")) refs.setDefaultBranch("master");
                else if (!branches.isEmpty()) refs.setDefaultBranch(branches.iterator().next());
            }
        } catch (Exception e) {
            refs.setBranches(new ArrayList<>());
            refs.setTags(new ArrayList<>());
        }
        return refs;
    }

    /**
     * 比较本地 HEAD 与远端指定引用的 SHA，判断本地是否落后于远端。
     * 网络失败/引用不存在时返回 UNKNOWN 并附 hint。
     */
    public RemoteStatus checkRemoteUpdate(Path repoDir, String repoUrl, String ref, String refType,
            String token, String username) throws Exception {
        RemoteStatus rs = new RemoteStatus();
        rs.setCheckedAt(System.currentTimeMillis());
        String authed = authedUrl(repoUrl, token, username);
        try {
            String localSha = git(new String[]{"-C", repoDir.toString(), "rev-parse", "HEAD"}, repoUrl).trim();
            rs.setLocalSha(localSha);
            String remoteSha = remoteShaOf(authed, repoUrl, ref, refType);
            if (remoteSha == null || remoteSha.isEmpty()) {
                rs.setStatus("UNKNOWN");
                rs.setHint("远端不存在引用：" + ref);
                return rs;
            }
            rs.setRemoteSha(remoteSha);
            rs.setStatus(remoteSha.equals(localSha) ? "UP_TO_DATE" : "BEHIND");
        } catch (Exception e) {
            rs.setStatus("UNKNOWN");
            rs.setHint(e.getMessage());
        }
        return rs;
    }

    /**
     * 切换到指定分支或 Tag。
     * 流程：脏工作区先 git stash（含未跟踪文件）→ fetch → checkout → stash pop。
     * 冲突时保留 stash（改动不丢失）并置 conflict 标记。
     */
    public GitSwitchResult switchRef(Path repoDir, String repoUrl, String target, String targetType,
            String token, String username, BiConsumer<Integer, String> progress) throws Exception {
        GitSwitchResult result = new GitSwitchResult();
        result.setRef(target);
        result.setRefType(targetType);
        String authed = authedUrl(repoUrl, token, username);
        String dir = repoDir.toString();

        // 1) 脏检查 + stash
        String status = git(new String[]{"-C", dir, "status", "--porcelain"}, repoUrl);
        boolean dirty = status != null && !status.trim().isEmpty();
        if (dirty) {
            git(new String[]{"-C", dir, "stash", "push", "-u", "-m", "callgraph-autoswitch"}, repoUrl);
            result.setStashed(true);
        }

        // 2) fetch + checkout；失败时恢复 stash
        try {
            if ("TAG".equalsIgnoreCase(targetType)) {
                git(new String[]{"-C", dir, "fetch", "--tags", "--prune", authed}, repoUrl, progress);
                git(new String[]{"-C", dir, "checkout", "tags/" + target}, repoUrl);
            } else {
                git(new String[]{"-C", dir, "fetch", "--tags", "--prune", authed,
                        "refs/heads/" + target + ":refs/remotes/origin/" + target}, repoUrl, progress);
                if (hasLocalBranch(repoDir, target, repoUrl)) {
                    git(new String[]{"-C", dir, "checkout", target}, repoUrl);
                    git(new String[]{"-C", dir, "merge", "--ff-only", "origin/" + target}, repoUrl);
                } else {
                    git(new String[]{"-C", dir, "checkout", "-b", target, "origin/" + target}, repoUrl);
                }
            }
        } catch (Exception e) {
            if (result.isStashed()) {
                try {
                    git(new String[]{"-C", dir, "stash", "pop"}, repoUrl);
                } catch (Exception ignore) {
                    // stash 保留在栈上，改动未丢失
                }
            }
            throw e;
        }

        // 3) stash pop
        if (result.isStashed()) {
            try {
                git(new String[]{"-C", dir, "stash", "pop"}, repoUrl);
                result.setStashPopped(true);
            } catch (IOException e) {
                String msg = e.getMessage();
                if (msg != null && msg.contains("CONFLICT")) {
                    result.setConflict(true);
                } else {
                    throw e;
                }
            }
        }
        result.setOutput("OK");
        return result;
    }

    /** 本地是否已存在指定分支 */
    private boolean hasLocalBranch(Path repoDir, String branch, String repoUrl)
            throws IOException, InterruptedException {
        try {
            git(new String[]{"-C", repoDir.toString(), "rev-parse", "--verify", "refs/heads/" + branch}, repoUrl);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** 取远端指定引用的 SHA（Tag 优先取 peel 后的提交点，兼容轻量/附注标签） */
    private String remoteShaOf(String authedUrl, String originalUrl, String ref, String refType)
            throws IOException, InterruptedException {
        List<String> args = new ArrayList<>();
        args.add("ls-remote");
        args.add(authedUrl);
        if ("TAG".equalsIgnoreCase(refType)) {
            args.add("refs/tags/" + ref);
            args.add("refs/tags/" + ref + "^{}");
        } else {
            args.add("refs/heads/" + ref);
        }
        String out = git(args.toArray(new String[0]), originalUrl);
        String sha = null;
        for (String raw : out.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            sha = line.split("\\s+")[0];
        }
        return sha;
    }

    /** 组装带认证的 URL：清洗 → SSH 转 HTTPS → 注入 token（仅本次命令用，不落盘） */
    private String authedUrl(String repoUrl, String token, String username) {
        String url = sanitizeUrl(repoUrl);
        String converted = sshToHttps(url);
        if (converted != null) {
            url = sanitizeUrl(converted);
        }
        if (token != null && !token.trim().isEmpty()) {
            String user = (username != null && !username.trim().isEmpty())
                    ? username.trim() : defaultUsername(url);
            url = withAuth(url, user, token.trim());
        }
        return url;
    }

    /** git@host:group/repo.git → https://host/group/repo.git */
    private static final Pattern SSH_SCP_PATTERN = Pattern.compile(
            "^git@([^:]+):(.+?)(?:\\.git)?$", Pattern.CASE_INSENSITIVE);

    /** ssh://[user@]host[:port]/path → https://host/path */
    private static final Pattern SSH_URL_PATTERN = Pattern.compile(
            "^ssh://(?:[^/@]*@)?([^:/]+)(?::\\d+)?/(.+?)(?:\\.git)?$", Pattern.CASE_INSENSITIVE);

    /** 需要走系统代理的公共仓库域名白名单（命中则注入代理；其余一律直连，避免污染内网仓库） */
    private static final Pattern EXTERNAL_HOST_PATTERN = Pattern.compile(
            "(^|\\.)(github\\.com|githubusercontent\\.com|gitlab\\.com|bitbucket\\.org|gitee\\.com)$",
            Pattern.CASE_INSENSITIVE);

    /** 内网/本机直连段，命中则不注入代理 */
    private static final Pattern INTERNAL_IP_PATTERN = Pattern.compile(
            "^(localhost|127\\.\\d+\\.\\d+\\.\\d+|10\\.\\d+\\.\\d+\\.\\d+|"
            + "192\\.168\\.\\d+\\.\\d+|172\\.(1[6-9]|2\\d|3[01])\\.\\d+\\.\\d+)$");

    /** 缓存的系统代理，首次探测后复用（避免每次 git 调用都读注册表） */
    private volatile String systemProxy = "";

    /** TUN/虚拟网卡模式下没有系统代理注册表项，但本机起监听端口的本地代理客户端（Clash/v2ray 等）常见的监听端口 */
    private static final int[] TUN_PROXY_PORTS = { 7890, 10809, 10808, 1080, 8888, 7897 };

    /** 缓存"可行性"欠佳的本地代理探测结果，避免每次 git 调用都做 socket 探测 */
    private volatile String tunProxy = "";

    /** 网络瞬时断流错误特征：命中则自动重试（大仓库经代理/不稳定线路下载极易中途被掐断） */
    private static final String[] TRANSIENT_ERR_MARKERS = {
            "early EOF",
            "index-pack failed",
            "unexpected disconnect",
            "RPC failed",
            "connection reset",
            "recv failure",
            "terminated packet",
            "Could not read from remote repository",
            // TLS 层连接被重置/中断（常见于代理与线路抖动）
            "ssl_read",
            "ssl_write",
            "connection was reset",
            "errno 10054",
            "errno 10053",
            "errno 10060"
    };

    /** clone 失败的瞬时断流重试次数 */
    private static final int CLONE_MAX_ATTEMPTS = 3;

    /** 瞬时断流重试退避间隔(毫秒) */
    private static final long CLONE_RETRY_BACKOFF_MS = 3000;

    /** 克隆仓库到目标目录。 */
    public void clone(String repoUrl, String branch, String token, String username, Path targetDir)
            throws Exception {
        String url = sanitizeUrl(repoUrl);

        // 1) SSH → HTTPS 自动转换（覆盖绝大多数"用户只知道 SSH 地址但有 Token"的场景）
        String converted = sshToHttps(url);
        if (converted != null) {
            url = converted;
        }

        // 再清洗一次：正则转换可能残留末尾冒号/斜杠/空格
        url = sanitizeUrl(url);

        CloneCommand cmd = Git.cloneRepository()
                .setURI(url)
                .setDirectory(targetDir.toFile());

        if (branch != null && !branch.trim().isEmpty()) {
            cmd.setBranch("refs/heads/" + branch.trim());
        }

        // 2) Token 走 HTTPS 用户名密码认证
        if (token != null && !token.trim().isEmpty()) {
            String user = username != null && !username.trim().isEmpty()
                    ? username.trim()
                    : defaultUsername(url);
            cmd.setCredentialsProvider(new UsernamePasswordCredentialsProvider(user, token.trim()));
        }

        // 3) StrictHostKeyChecking=no 兜底（万一转换失败 / 内网 GitLab 必须 SSH）
        cmd.setTransportConfigCallback(transport -> {
            if (transport instanceof SshTransport) {
                SshTransport ssh = (SshTransport) transport;
                ssh.setSshSessionFactory(new JschConfigSessionFactory() {
                    @Override
                    protected void configure(OpenSshConfig.Host hc, Session session) {
                        session.setConfig("StrictHostKeyChecking", "no");
                        session.setConfig("UserKnownHostsFile", "/dev/null");
                    }
                });
            }
        });

        try (Git ignored = cmd.call()) {
            // try-with-resources 关闭即仓库可用
        }
    }

    /**
     * 固定目录 + 增量拉取：在本机工作根目录下，按仓库地址定位一个持久子目录。
     * - 目录尚不存在 / 未初始化 → 首次 git clone；
     * - 目录已存在（本地已有该仓库）→ 二次 git pull 增量更新。
     * <p>
     * 用本机 git CLI（而非 JGit）执行，与用户在本地手动 git clone/pull 的体验一致。
     * 适用于"仅用于分析、本地不修改源码"的场景。Token 通过带认证的 URL 临时注入，不持久化进本地仓库配置。
     *
     * @return 仓库在本地的工作目录
     */
    public Path ensureLocal(String repoUrl, String branch, String token, String username, Path workRoot)
            throws Exception {
        String url = sanitizeUrl(repoUrl);
        String converted = sshToHttps(url);
        if (converted != null) {
            url = sanitizeUrl(converted);
        }

        Path repoDir = workRoot.resolve(repoDirName(url));
        boolean existing = Files.isDirectory(repoDir.resolve(".git"));

        // 组装带认证的 URL（仅用于本次 git 命令，不写入仓库 config）
        String authed = url;
        if (token != null && !token.trim().isEmpty()) {
            String user = username != null && !username.trim().isEmpty()
                    ? username.trim() : defaultUsername(url);
            authed = withAuth(url, user, token.trim());
        }

        if (existing) {
            pullWithRetry(repoDir, url, authed, branch);
        } else {
            cloneWithRetry(repoDir, url, authed, branch);
        }
        return repoDir;
    }

    /** 增量拉取（带瞬时断流自动重试），与 cloneWithRetry 共用重试特征。 */
    private void pullWithRetry(Path repoDir, String originalUrl, String authedUrl, String branch)
            throws IOException, InterruptedException {
        IOException last = null;
        for (int attempt = 1; attempt <= CLONE_MAX_ATTEMPTS; attempt++) {
            try {
                String output = git(new String[]{"-C", repoDir.toString(), "pull", "--ff-only",
                        authedUrl, safeBranch(branch)}, originalUrl);
                if (output.toLowerCase().contains("fatal")) {
                    // git pull 非零退出码已在 git() 抛出；这里兜底处理输出里含 fatal 但进程成功的情况
                    throw new IOException("增量拉取失败：\n" + CredentialRedactor.redact(output.trim()));
                }
                return; // 成功
            } catch (IOException e) {
                if (!looksLikeTransient(e.getMessage()) || attempt >= CLONE_MAX_ATTEMPTS) {
                    throw e;
                }
                last = e;
                Thread.sleep(CLONE_RETRY_BACKOFF_MS * attempt);
            }
        }
        throw new IOException("增量拉取连续失败（已重试 " + CLONE_MAX_ATTEMPTS + " 次，疑似网络不稳定）：\n"
                + (last == null ? "" : last.getMessage()));
    }

    /**
     * 首次 clone（带瞬时断流自动重试）。
     * 代理/不稳定线路下载大仓库时 git 常报 early EOF / index-pack failed 等传输中断错误，
     * 这类是瞬时网络抖动，不是仓库或认证问题。命中特征时清空半成品目录重试，最多 CLONE_MAX_ATTEMPTS 次。
     */
    private void cloneWithRetry(Path repoDir, String originalUrl, String authedUrl, String branch)
            throws IOException, InterruptedException {
        IOException last = null;
        for (int attempt = 1; attempt <= CLONE_MAX_ATTEMPTS; attempt++) {
            // 每次重试前清空上一次残留的半成品目录
            if (Files.exists(repoDir)) {
                deleteQuietly(repoDir);
            }
            List<String> cmd = new ArrayList<>();
            cmd.add("clone");
            cmd.add("--progress");
            if (branch != null && !branch.trim().isEmpty()) {
                cmd.add("-b");
                cmd.add(branch.trim());
            }
            cmd.add(authedUrl);
            cmd.add(repoDir.toString());
            try {
                String output = git(cmd.toArray(new String[0]), originalUrl);
                if (output.toLowerCase().contains("fatal")
                        && repoDir.toFile().listFiles() == null) {
                    deleteQuietly(repoDir);
                    throw new IOException("克隆失败：\n" + CredentialRedactor.redact(output.trim()));
                }
                return; // 成功
            } catch (IOException e) {
                if (!looksLikeTransient(e.getMessage()) || attempt >= CLONE_MAX_ATTEMPTS) {
                    throw e; // 非瞬时错误或已用尽重试，直接抛
                }
                last = e;
                // 等待后重试：给对方/网络恢复时间
                Thread.sleep(CLONE_RETRY_BACKOFF_MS * attempt);
            }
        }
        throw new IOException("克隆连续失败（已重试 " + CLONE_MAX_ATTEMPTS + " 次，疑似网络不稳定）：\n"
                + (last == null ? "" : last.getMessage()));
    }

    /** 判断错误是否为网络瞬时断流（命中任一特征即可触发重试） */
    private static boolean looksLikeTransient(String message) {
        if (message == null || message.isEmpty()) return false;
        String m = message.toLowerCase();
        for (String marker : TRANSIENT_ERR_MARKERS) {
            if (m.contains(marker.toLowerCase())) return true;
        }
        return false;
    }

    /**
     * 由清洗后的仓库 URL 派生固定的本地目录名（同 URL → 同目录，实现缓存复用）。
     * 统一剥掉末尾的 .git 与斜杠，保证 "foo/bar.git" 与 "foo/bar" 视为同一个仓库，
     * 复用同一缓存目录，避免因 URL 写法不同而重复 clone。
     */
    private static String repoDirName(String url) {
        String s = stripDotGit(sanitizeUrl(url));
        // 去 scheme（http://、ssh:// 等）
        String ns = s.replaceFirst("^[a-zA-Z][a-zA-Z0-9+.-]*://", "");
        ns = ns.replaceAll("[^a-zA-Z0-9._-]", "_");
        // 去尾部下划线（由尾部 "/" 或 "." 转来）
        while (ns.endsWith("_")) ns = ns.substring(0, ns.length() - 1);
        if (ns.length() > 60) ns = ns.substring(ns.length() - 60);
        return ns + "-" + shortHash(s);
    }

    private static String shortHash(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                sb.append(String.format("%02x", d[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    /** 将 user:token 信息注入 URL 的认证段：https://host → https://user:token@host */
    private static String withAuth(String url, String user, String token) {
        Matcher m = Pattern.compile("^(https?://)").matcher(url);
        if (!m.find()) return url;
        String rest = url.substring(m.end());
        return m.group(1) + user + ":" + token + "@" + rest;
    }

    private static String safeBranch(String branch) {
        return (branch == null || branch.trim().isEmpty()) ? "HEAD" : branch.trim();
    }

    /** 执行本机 git 命令，返回合并后的 stdout+stderr；非零退出码抛异常。 */
    /** 执行本机 git 命令；自动取 ThreadLocal 里的进度回调（如果有） */
    private String git(String[] args) throws IOException, InterruptedException {
        return git(args, null);
    }

    private String git(String[] args, String url) throws IOException, InterruptedException {
        return git(args, url, progressCb.get());
    }

    /** 执行本机 git 命令；url 非空时注入代理；progressCallback 非空时实时回调 git 输出中的进度。 */
    private String git(String[] args, String url, java.util.function.BiConsumer<Integer, String> progressCallback)
            throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        String proxy = proxyFor(url);
        if (proxy != null) {
            cmd.add("-c");
            cmd.add("http.proxy=" + proxy);
            cmd.add("-c");
            cmd.add("https.proxy=" + proxy);
        }
        for (String a : args) cmd.add(a);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().put("GIT_TERMINAL_PROMPT", "0");
        pb.environment().put("GIT_ASKPASS", "echo");
        pb.environment().put("GIT_SSH_COMMAND",
                "ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o ConnectTimeout=15");
        pb.environment().put("GIT_SSL_NO_VERIFY", "1");
        pb.redirectErrorStream(true);
        Process p = pb.start();

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        StringBuilder lineBuf = new StringBuilder();  // 用于实时解析进度
        int n;
        try (java.io.InputStream in = p.getInputStream()) {
            while ((n = in.read(chunk)) != -1) {
                buf.write(chunk, 0, n);
                if (progressCallback != null) {
                    // 实时解析：git 进度用 \r 分隔，stderr 里可能是逐字节来的
                    String piece = new String(chunk, 0, n, StandardCharsets.UTF_8);
                    for (char c : piece.toCharArray()) {
                        if (c == '\r' || c == '\n') {
                            String line = lineBuf.toString().trim();
                            lineBuf.setLength(0);
                            if (!line.isEmpty()) parseAndReportProgress(line, progressCallback);
                        } else {
                            lineBuf.append(c);
                        }
                    }
                }
            }
        }
        int code = p.waitFor();
        String output = new String(buf.toByteArray(), StandardCharsets.UTF_8);
        if (code != 0) {
            // 脱敏：git 报错输出里常带 authed URL（含 token），禁止回显到异常信息（OPT-10）
            throw new IOException(CredentialRedactor.redact(
                    (output.trim() + "\n(退出码 " + code + ")").trim()));
        }
        return output;
    }

    /** 从 git 输出行里解析进度百分比（Receiving objects 45% / Resolving deltas 34% / Writing objects 12%） */
    private static void parseAndReportProgress(String line, java.util.function.BiConsumer<Integer, String> cb) {
        try {
            // 匹配 "Receiving objects:  45% (1234/2742), ..."
            // 匹配 "Resolving deltas:   34% (456/1345)"
            // 匹配 "Writing objects:   12% (100/800)"
            java.util.regex.Matcher m = PROGRESS_PATTERN.matcher(line);
            if (m.find()) {
                String phase = m.group(1);   // Receiving / Resolving / Writing
                int pct = Integer.parseInt(m.group(2));  // 百分比数字
                cb.accept(pct, phase + " " + pct + "%");
            }
        } catch (Exception ignored) {}
    }

    /** git clone/fetch 进度解析正则 */
    private static final java.util.regex.Pattern PROGRESS_PATTERN =
            java.util.regex.Pattern.compile("(Receiving|Resolving|Writing)\\s+\\w+:\\s*(\\d{1,3})%");

    /**
     * 为给定的仓库 URL 决定是否注入代理、注入哪个代理。
     * - 只有命中外网公共仓库白名单（github/githubusercontent/gitlab/bitbucket/gitee）才可能走代理；
     * - 内网 IP / 本机 / 内网域名一律直连（返回 null）；
     * - 系统开启代理（ProxyEnable=1）且 URL 是外网时，返回系统 ProxyServer。
     *
     * @return 形如 host:port 的代理地址；不需要代理返回 null
     */
    private String proxyFor(String url) {
        if (url == null || url.trim().isEmpty()) return null;
        String host = hostOf(url);
        if (host == null) return null;
        // 内网 IP / 本机：直连，不走代理
        if (INTERNAL_IP_PATTERN.matcher(host).matches()) return null;
        // 非公共仓库白名单的域名（含内网域名/公司域名）：直连，避免污染
        if (!EXTERNAL_HOST_PATTERN.matcher(host).find()) return null;
        // 1) 系统代理开启 → 用系统 ProxyServer
        if (systemProxyEnabled()) {
            String proxy = systemProxy();
            return (proxy == null || proxy.trim().isEmpty()) ? null : proxy.trim();
        }
        // 2) TUN/虚拟网卡模式兜底：系统代理开关关闭（ProxyEnable=0），
        //    但本机可能正运行 Clash/v2ray 等客户端并在本地端口监听。
        //    探测常见代理端口，命中则注入，让 "网页能开但 git 连不上" 也能走代理。
        return tunProxyFallback();
    }

    /**
     * 探测本机是否存在监听中的本地代理端口（TUN 模式常用客户端）。
     * 结果缓存，避免每次 git 调用都建 socket。
     *
     * @return "127.0.0.1:port"；没有可用的返回 null
     */
    private String tunProxyFallback() {
        if (tunProxy.equals("__none__")) return null;
        if (!tunProxy.isEmpty()) return tunProxy;
        synchronized (this) {
            if (!tunProxy.isEmpty()) {
                return tunProxy.equals("__none__") ? null : tunProxy;
            }
            for (int port : TUN_PROXY_PORTS) {
                if (portOpen("127.0.0.1", port)) {
                    String p = "127.0.0.1:" + port;
                    tunProxy = p;
                    return p;
                }
            }
            tunProxy = "__none__";
            return null;
        }
    }

    /** 尝试与 host:port 建立 TCP 连接，判断端口是否被监听。 */
    private static boolean portOpen(String host, int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 400);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** 从 URL 中提取 host 部分 */
    private static String hostOf(String url) {
        Matcher m = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*://([^/?#]+)").matcher(url);
        if (m.find()) {
            String h = m.group(1);
            // 去掉 user:pass@ 前缀
            int at = h.lastIndexOf('@');
            if (at >= 0) h = h.substring(at + 1);
            // 去掉端口
            int colon = h.indexOf(':');
            if (colon >= 0) h = h.substring(0, colon);
            return h.trim();
        }
        return null;
    }

    private boolean systemProxyEnabled() {
        return readProxy("ProxyEnable").trim().equals("1");
    }

    private String systemProxy() {
        if (!systemProxy.isEmpty()) return systemProxy;
        synchronized (this) {
            if (systemProxy.isEmpty()) {
                String s = readProxy("ProxyServer").trim();
                // 移除可能的自动代理脚本占位 ""
                if (!s.isEmpty() && !s.equals("\"\"") && !s.equals("\"\"")) {
                    systemProxy = s;
                }
            }
            return systemProxy;
        }
    }

    /**
     * 读取 Windows 系统当前用户代理注册表项（Internet Settings）。
     * 依次尝试 HKCU、HKLM；用 reg query 命令实现，跨 JVM 无需额外依赖。
     */
    private static String readProxy(String valueName) {
        String[] roots = {
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
                "HKLM\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings"
        };
        for (String root : roots) {
            String v = regQuery(root, valueName);
            if (v != null) return v;
        }
        return "";
    }

    private static String regQuery(String key, String valueName) {
        try {
            ProcessBuilder pb = new ProcessBuilder("reg", "query", key, "/v", valueName);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int n;
            try (java.io.InputStream in = p.getInputStream()) {
                while ((n = in.read(chunk)) != -1) buf.write(chunk, 0, n);
            }
            if (p.waitFor() != 0) return null;
            String out = new String(buf.toByteArray(), StandardCharsets.UTF_8);
            // 形如 "    ProxyServer    REG_SZ    127.0.0.1:10809"
            Matcher m = Pattern.compile(valueName + "\\s+REG_\\w+\\s+(\\S+)").matcher(out);
            if (m.find()) return m.group(1).trim();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null || !Files.exists(dir)) return;
        try {
            try (java.util.stream.Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignore) { }
                });
            }
        } catch (IOException ignore) { }
    }

    /**
     * 尝试将 SSH 风格 URL 转成 HTTPS 等价形式。
     * 支持 git@host:group/repo、git@host:22/group/repo、ssh://git@host:port/group/repo、ssh://host/group/repo。
     *
     * @return HTTPS 形式；若不是 SSH 风格则返回 null
     */
    static String sshToHttps(String url) {
        if (url == null) return null;
        String s = sanitizeUrl(url);

        // 形式一：git@github.com:spark-young/spark-live-classroom.git
        Matcher m = SSH_SCP_PATTERN.matcher(s);
        if (m.matches()) {
            return "https://" + m.group(1) + "/" + stripDotGit(m.group(2));
        }

        // 形式二：ssh://git@github.com:22/spark-young/spark-live-classroom.git
        m = SSH_URL_PATTERN.matcher(s);
        if (m.matches()) {
            return "https://" + m.group(1) + "/" + stripDotGit(m.group(2));
        }

        return null;
    }

    /** 去掉末尾的 .git（GitHub/GitLab 都不需要） */
    private static String stripDotGit(String path) {
        String p = path.trim();
        if (p.endsWith(".git")) {
            p = p.substring(0, p.length() - 4);
        }
        return p;
    }

    /**
     * URL 防御性清洗：
     * - 去首尾空白
     * - 去首尾反引号/引号/【】( )等包装符（复制粘贴 Markdown/聊天里的 ``url`` 常有）
     * - 去末尾残留的冒号、斜杠（复制粘贴常见问题，如 "foo/bar.git:"）
     * - 去末尾的逗号、分号
     */
    private static String sanitizeUrl(String url) {
        if (url == null) return "";
        String s = url.trim();
        // 1) 去掉首尾的包装字符：反引号、单双引号、( ) [ ] < > { } 、
        while (s.length() > 1) {
            char head = s.charAt(0);
            if (head == '`' || head == '\'' || head == '"' || head == '(' || head == '['
                    || head == '<' || head == '{') {
                s = s.substring(1);
            } else {
                break;
            }
        }
        while (s.length() > 1) {
            char tail = s.charAt(s.length() - 1);
            if (tail == '`' || tail == '\'' || tail == '"' || tail == ')' || tail == ']'
                    || tail == '>' || tail == '}') {
                s = s.substring(0, s.length() - 1);
            } else {
                break;
            }
        }
        s = s.trim();
        // 2) 循环去末尾脏字符（可能连续多个，如 ".git: "）
        while (!s.isEmpty()) {
            char last = s.charAt(s.length() - 1);
            if (last == ':' || last == '/' || last == '\\' || last == ',' || last == ';'
                    || last == ' ' || last == '\t') {
                s = s.substring(0, s.length() - 1);
            } else {
                break;
            }
        }
        return s;
    }

    /** Token 认证默认用户名：GitHub PAT 用户名任意（用 token）；GitLab PAT 惯例 oauth2 */
    static String defaultUsername(String url) {
        return url != null && url.contains("github.com") ? "token" : "oauth2";
    }
}
