package com.spark.callgraph.engine.entry;

import com.spark.callgraph.engine.ClassMetadataRegistry;
import com.spark.callgraph.engine.model.ClassInfo;
import com.spark.callgraph.engine.model.MethodKey;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * ElasticJob 入口：实现 SimpleJob 接口（v2 dangdang / v3 shardingsphere 命名空间）的 execute 方法。
 */
@Component
public final class ElasticJobDetector implements EntryPointDetector {

    private static final String[] JOB_INTERFACES = {
            "com/dangdang/ddframe/job/api/simple/SimpleJob",
            "org/apache/shardingsphere/elasticjob/simple/job/SimpleJob",
    };

    @Override
    public String type() { return "ELASTIC_JOB"; }

    @Override
    public String label() { return "ElasticJob 作业"; }

    @Override
    public List<EntryPoint> detect(ClassMetadataRegistry registry) {
        List<EntryPoint> out = new ArrayList<>();
        for (ClassInfo ci : registry.allClasses()) {
            if (ci.getSource() != com.spark.callgraph.engine.model.SourceType.PROJECT) continue;
            if (ci.isInterface() || ci.isAbstract()) continue;

            boolean isJob = false;
            for (String itf : JOB_INTERFACES) {
                if (registry.isSubtypeOf(ci.getInternalName(), itf)) {
                    isJob = true;
                    break;
                }
            }
            if (!isJob) continue;

            String fqcn = ci.getInternalName().replace('/', '.');
            for (MethodKey m : ci.methodKeys()) {
                if (!m.getName().equals("execute")) continue;
                if (!ci.isMethodConcrete(m.getName(), m.getDescriptor())) continue;
                out.add(new EntryPoint(type(), fqcn, m.getName(), m.getDescriptor(),
                        "job: " + fqcn + "#execute"));
            }
        }
        return out;
    }
}
