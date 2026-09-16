package com.spark.projectanalysis.engine;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地 Maven 仓库定位（顺序）：
 * 1. 系统属性 callgraph.maven.repo / 环境变量 CALLGRAPH_M2_REPO
 * 2. 用户 settings.xml（~/.m2/settings.xml）的 &lt;localRepository&gt;
 * 3. Maven 安装目录 conf/settings.xml 的 &lt;localRepository&gt;（通过 PATH 上的 mvn 或 MAVEN_HOME/M2_HOME 定位）
 * 4. 默认 ~/.m2/repository
 */
public final class MavenRepoLocator {

    private static volatile Path cached;

    private MavenRepoLocator() {}

    public static Path detect() {
        Path result = cached;
        if (result != null) return result;
        synchronized (MavenRepoLocator.class) {
            if (cached == null) {
                cached = doDetect();
            }
            return cached;
        }
    }

    private static Path doDetect() {
        // 1. 显式覆盖
        String override = System.getProperty("callgraph.maven.repo");
        if (override == null || override.trim().isEmpty()) {
            override = System.getenv("CALLGRAPH_M2_REPO");
        }
        if (override != null && !override.trim().isEmpty()) {
            return Paths.get(override.trim());
        }

        // 2. 用户 settings.xml
        Path userHome = Paths.get(System.getProperty("user.home"));
        Path p = localRepositoryFrom(userHome.resolve(".m2").resolve("settings.xml"));
        if (p != null) return p;

        // 3. Maven 安装目录的 conf/settings.xml
        Path mavenHome = mavenHomeFromPath();
        if (mavenHome == null) {
            String env = System.getenv("MAVEN_HOME");
            if (env == null || env.trim().isEmpty()) env = System.getenv("M2_HOME");
            if (env != null && !env.trim().isEmpty()) mavenHome = Paths.get(env.trim());
        }
        if (mavenHome != null) {
            p = localRepositoryFrom(mavenHome.resolve("conf").resolve("settings.xml"));
            if (p != null) return p;
        }

        // 4. 默认
        return userHome.resolve(".m2").resolve("repository");
    }

    /** 解析 settings.xml 的 localRepository；无有效配置返回 null */
    static Path localRepositoryFrom(Path settingsXml) {
        if (settingsXml == null || !Files.isRegularFile(settingsXml)) return null;
        Document doc = parse(settingsXml);
        if (doc == null) return null;
        NodeList nodes = doc.getElementsByTagName("localRepository");
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element) {
                String value = nodes.item(i).getTextContent().trim();
                if (!value.isEmpty() && !value.startsWith("${")) {
                    return Paths.get(value);
                }
            }
        }
        return null;
    }

    /** 从 PATH 定位 mvn 可执行文件并推导 Maven 安装目录 */
    private static Path mavenHomeFromPath() {
        try {
            String finder = System.getProperty("os.name", "").toLowerCase().contains("win")
                    ? "where" : "which";
            Process process = new ProcessBuilder(finder, "mvn").redirectErrorStream(true).start();
            String firstLine;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                firstLine = reader.readLine();
            }
            process.waitFor();
            if (firstLine == null || firstLine.trim().isEmpty()) return null;
            Path bin = Paths.get(firstLine.trim());
            // .../bin/mvn(.cmd) -> Maven 根目录
            Path parent = bin.getParent();
            return parent != null ? parent.getParent() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** XXE 安全的 DOM 解析（拒绝 DTD / 外部实体） */
    private static Document parse(Path xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            f.setExpandEntityReferences(false);
            f.setNamespaceAware(false);
            DocumentBuilder b = f.newDocumentBuilder();
            return b.parse(xml.toFile());
        } catch (Exception e) {
            return null;
        }
    }
}
