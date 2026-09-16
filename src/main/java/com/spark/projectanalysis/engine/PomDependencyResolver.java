package com.spark.projectanalysis.engine;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 尽力而为的 pom.xml 依赖解析：
 * 父 pom 继承（.m2 或 relativePath）、属性替换、dependencyManagement（含 BOM import）、
 * 解析不到的依赖记入 unresolved，不影响其余分析。
 * 注意：本工具仅读取 XML，不执行任何 Maven 插件/脚本。
 */
public final class PomDependencyResolver {

    private PomDependencyResolver() {}

    public static List<Path> resolve(Path projectDir, Path repo, List<String> warnings) {
        List<Path> jars = new ArrayList<>();
        try {
            Path pom = projectDir.resolve("pom.xml");
            if (!Files.isRegularFile(pom)) {
                warnings.add("未找到 pom.xml，依赖未解析。");
                return jars;
            }
            List<String> unresolved = new ArrayList<>();
            List<Document> chain = new ArrayList<>(); // 子在前，父在后
            loadChain(pom, repo, chain, warnings, 0, new ArrayList<>());

            if (chain.isEmpty()) return jars;

            // 属性合并（子覆盖父）+ 内建属性
            Map<String, String> props = new HashMap<>();
            for (int i = chain.size() - 1; i >= 0; i--) {
                props.putAll(collectProperties(chain.get(i)));
            }
            Document child = chain.get(0);
            props.put("project.version", text(child, "version"));
            props.put("project.groupId", firstNonEmpty(text(child, "groupId"), text(child, "parent/groupId")));
            props.put("project.artifactId", text(child, "artifactId"));
            if (chain.size() > 1) {
                Document parent = chain.get(1);
                props.put("project.parent.version", text(parent, "version"));
                props.put("project.parent.groupId", text(parent, "groupId"));
                props.put("project.parent.artifactId", text(parent, "artifactId"));
            }

            // dependencyManagement：链上 pom + BOM import（先声明者优先）
            Map<String, String> dm = new LinkedHashMap<>();
            for (Document doc : chain) {
                mergeDependencyManagement(doc, props, repo, dm, warnings, new ArrayList<>());
            }

            // 依赖收集：子覆盖父（g:a 去重）
            Map<String, Element> deps = new LinkedHashMap<>();
            for (Document doc : chain) {
                for (Element dep : childrenOf(doc, "dependencies", "dependency")) {
                    String g = substitute(text(dep, "groupId"), props);
                    String a = substitute(text(dep, "artifactId"), props);
                    if (g == null || a == null) continue;
                    deps.put(g + ":" + a, dep); // 子在前，父的被覆盖
                }
            }

            for (Map.Entry<String, Element> e : deps.entrySet()) {
                Element dep = e.getValue();
                String scope = text(dep, "scope");
                if ("test".equals(scope)) continue;
                String g = substitute(text(dep, "groupId"), props);
                String a = substitute(text(dep, "artifactId"), props);
                String v = substitute(text(dep, "version"), props);
                if (v == null || v.isEmpty()) {
                    v = dm.get(g + ":" + a);
                }
                if (v == null || v.isEmpty()) {
                    String rel = text(dep, "relativePath");
                    warnings.add("依赖版本无法解析（可能来自 profile 或范围版本）: " + g + ":" + a + (rel != null ? "" : ""));
                    unresolved.add(g + ":" + a + ":?");
                    continue;
                }
                if (v.startsWith("[") || v.startsWith("(")) {
                    warnings.add("版本范围暂不支持，已跳过: " + g + ":" + a + ":" + v);
                    continue;
                }
                Path jar = repo.resolve(g.replace('.', '/')).resolve(a).resolve(v).resolve(a + "-" + v + ".jar");
                if (Files.isRegularFile(jar)) {
                    jars.add(jar);
                } else {
                    unresolved.add(g + ":" + a + ":" + v);
                }
            }
            if (!unresolved.isEmpty()) {
                warnings.add("以下依赖在本地仓库未找到（相关调用将标记为外部方法）: "
                        + String.join(", ", unresolved));
            }
        } catch (Exception e) {
            warnings.add("pom 依赖解析失败: " + e.getMessage());
        }
        return jars;
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    private static void loadChain(Path pom, Path repo, List<Document> chain,
                                  List<String> warnings, int depth, List<Path> visited) {
        if (pom == null || depth > 10 || !Files.isRegularFile(pom) || visited.contains(pom)) return;
        visited.add(pom);
        Document doc = parse(pom);
        if (doc == null) {
            warnings.add("pom 解析失败: " + pom);
            return;
        }
        chain.add(doc);
        String pg = text(doc, "parent/groupId");
        String pa = text(doc, "parent/artifactId");
        String pv = text(doc, "parent/version");
        if (pg == null || pa == null || pv == null) return;
        pv = substitute(pv, collectProperties(doc));
        Path parentPom = repo.resolve(pg.replace('.', '/')).resolve(pa).resolve(pv).resolve(pa + "-" + pv + ".pom");
        if (!Files.isRegularFile(parentPom)) {
            // 尝试 relativePath（多模块聚合工程）
            String rel = text(doc, "parent/relativePath");
            Path relPath = pom.getParent() != null ? pom.getParent().resolve(rel != null ? rel : "../pom.xml").normalize() : null;
            if (relPath != null && Files.isRegularFile(relPath)) {
                parentPom = relPath;
            } else {
                warnings.add("父 pom 未找到（继承信息不完整）: " + pg + ":" + pa + ":" + pv);
                return;
            }
        }
        loadChain(parentPom, repo, chain, warnings, depth + 1, visited);
    }

    private static void mergeDependencyManagement(Document doc, Map<String, String> props,
                                                  Path repo, Map<String, String> dm,
                                                  List<String> warnings, List<String> visitedBoms) {
        for (Element dep : childrenOf(doc, "dependencyManagement", "dependencies", "dependency")) {
            String g = substitute(text(dep, "groupId"), props);
            String a = substitute(text(dep, "artifactId"), props);
            String v = substitute(text(dep, "version"), props);
            String type = text(dep, "type");
            String scope = text(dep, "scope");
            if (g == null || a == null) continue;
            if ("pom".equals(type) && "import".equals(scope) && v != null) {
                String key = g + ":" + a + ":" + v;
                if (v.startsWith("[") || v.startsWith("(") || visitedBoms.contains(key)) continue;
                visitedBoms.add(key);
                Path bomPom = repo.resolve(g.replace('.', '/')).resolve(a).resolve(v).resolve(a + "-" + v + ".pom");
                if (!Files.isRegularFile(bomPom)) {
                    warnings.add("BOM 未找到: " + key);
                    continue;
                }
                Document bom = parse(bomPom);
                if (bom != null) {
                    mergeDependencyManagement(bom, props, repo, dm, warnings, visitedBoms);
                }
            } else if (v != null) {
                dm.putIfAbsent(g + ":" + a, v);
            }
        }
    }

    private static Map<String, String> collectProperties(Document doc) {
        Map<String, String> out = new HashMap<>();
        Element props = firstChild(doc.getDocumentElement(), "properties");
        if (props == null) return out;
        NodeList children = props.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element) {
                Element e = (Element) children.item(i);
                out.put(e.getTagName(), e.getTextContent().trim());
            }
        }
        return out;
    }

    private static List<Element> childrenOf(Document doc, String... path) {
        List<Element> out = new ArrayList<>();
        List<Element> current = new ArrayList<>();
        current.add(doc.getDocumentElement());
        for (String tag : path) {
            List<Element> next = new ArrayList<>();
            for (Element e : current) {
                NodeList children = e.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    if (children.item(i) instanceof Element
                            && ((Element) children.item(i)).getTagName().equals(tag)) {
                        next.add((Element) children.item(i));
                    }
                }
            }
            current = next;
        }
        out.addAll(current);
        return out;
    }

    /** 依 path 逐层取第一个匹配元素的文本，如 "parent/version" */
    private static String text(Document doc, String path) {
        return text(doc.getDocumentElement(), path);
    }

    private static String text(Element element, String path) {
        Element cur = element;
        for (String tag : path.split("/")) {
            if (cur == null) return null;
            cur = firstChild(cur, tag);
        }
        return cur != null ? cur.getTextContent().trim() : null;
    }

    private static Element firstChild(Element element, String tag) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element
                    && ((Element) children.item(i)).getTagName().equals(tag)) {
                return (Element) children.item(i);
            }
        }
        return null;
    }

    private static String firstNonEmpty(String a, String b) {
        return a != null && !a.isEmpty() ? a : b;
    }

    private static String substitute(String s, Map<String, String> props) {
        if (s == null) return null;
        String prev = null;
        int guard = 0;
        while (!s.equals(prev) && guard++ < 5) {
            prev = s;
            for (Map.Entry<String, String> e : props.entrySet()) {
                if (e.getValue() != null) {
                    s = s.replace("${" + e.getKey() + "}", e.getValue());
                }
            }
        }
        return s;
    }

    /** XXE 安全的 DOM 解析（拒绝 DTD / 外部实体） */
    private static Document parse(Path pom) {
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
            return b.parse(pom.toFile());
        } catch (Exception e) {
            return null;
        }
    }
}
