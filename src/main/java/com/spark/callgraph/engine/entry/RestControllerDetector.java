package com.spark.callgraph.engine.entry;

import com.spark.callgraph.engine.ClassMetadataRegistry;
import com.spark.callgraph.engine.model.AnnotationInfo;
import com.spark.callgraph.engine.model.ClassInfo;
import com.spark.callgraph.engine.model.MethodKey;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * REST 入口：@RestController/@Controller 类中带映射注解的方法。
 * 路径 = 类级 @RequestMapping + 方法级映射注解（value 或 path 属性）。
 */
@Component
public final class RestControllerDetector implements EntryPointDetector {

    private static final String REST_CONTROLLER = "org/springframework/web/bind/annotation/RestController";
    private static final String CONTROLLER = "org/springframework/web/bind/annotation/Controller";
    private static final String REQUEST_MAPPING = "org/springframework/web/bind/annotation/RequestMapping";

    private static final String[][] METHOD_MAPPINGS = {
            {"GET", "org/springframework/web/bind/annotation/GetMapping"},
            {"POST", "org/springframework/web/bind/annotation/PostMapping"},
            {"PUT", "org/springframework/web/bind/annotation/PutMapping"},
            {"DELETE", "org/springframework/web/bind/annotation/DeleteMapping"},
            {"PATCH", "org/springframework/web/bind/annotation/PatchMapping"},
    };

    private static final int ACC_PUBLIC = 0x0001;
    private static final int ACC_SYNTHETIC = 0x1000;
    private static final int ACC_BRIDGE = 0x0040;

    @Override
    public String type() { return "REST"; }

    @Override
    public String label() { return "REST 接口"; }

    @Override
    public List<EntryPoint> detect(ClassMetadataRegistry registry) {
        List<EntryPoint> out = new ArrayList<>();
        for (ClassInfo ci : registry.allClasses()) {
            if (ci.getSource() != com.spark.callgraph.engine.model.SourceType.PROJECT) continue;
            if (ci.isInterface() || ci.isAbstract()) continue;
            if (ci.annotation(REST_CONTROLLER) == null && ci.annotation(CONTROLLER) == null) continue;

            String basePath = mappingPath(ci.annotation(REQUEST_MAPPING));
            for (MethodKey m : ci.methodKeys()) {
                if (m.getName().startsWith("<")) continue;
                int access = ci.methodAccess(m.getName(), m.getDescriptor());
                if ((access & (ACC_SYNTHETIC | ACC_BRIDGE)) != 0) continue;

                AnnotationInfo mapping = null;
                String httpMethod = null;
                for (String[] mm : METHOD_MAPPINGS) {
                    AnnotationInfo info = ci.methodAnnotation(m.getName(), m.getDescriptor(), mm[1]);
                    if (info != null) {
                        mapping = info;
                        httpMethod = mm[0];
                        break;
                    }
                }
                if (mapping == null) {
                    mapping = ci.methodAnnotation(m.getName(), m.getDescriptor(), REQUEST_MAPPING);
                    if (mapping == null) continue;
                    httpMethod = mapping.first("method"); // 无 method 属性 → ANY
                }
                String path = joinPath(basePath, mappingPath(mapping));
                String display = (httpMethod == null ? "ANY" : httpMethod) + " " + path;
                out.add(new EntryPoint(type(), ci.getInternalName().replace('/', '.'),
                        m.getName(), m.getDescriptor(), display));
            }
        }
        return out;
    }

    /** 映射注解的路径属性：value 优先，path 次之；数组取首个 */
    private static String mappingPath(AnnotationInfo mapping) {
        if (mapping == null) return "";
        String v = mapping.first("value");
        if (v == null || v.isEmpty()) v = mapping.first("path");
        return v == null ? "" : v;
    }

    private static String joinPath(String base, String method) {
        String b = base == null ? "" : base;
        String m = method == null ? "" : method;
        if (b.isEmpty() && m.isEmpty()) return "/";
        if (b.isEmpty()) return normalize(m);
        if (m.isEmpty()) return normalize(b);
        return normalize(b) + (m.startsWith("/") ? m : "/" + m);
    }

    private static String normalize(String p) {
        if (p == null || p.isEmpty()) return "/";
        if (!p.startsWith("/")) p = "/" + p;
        while (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }
}
