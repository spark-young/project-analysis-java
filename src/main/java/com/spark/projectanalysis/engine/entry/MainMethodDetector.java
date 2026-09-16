package com.spark.projectanalysis.engine.entry;

import com.spark.projectanalysis.engine.ClassMetadataRegistry;
import com.spark.projectanalysis.engine.model.ClassInfo;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * main 入口：public static void main(String[])。
 */
@Component
public final class MainMethodDetector implements EntryPointDetector {

    private static final String MAIN_DESCRIPTOR = "([Ljava/lang/String;)V";
    private static final int ACC_PUBLIC = 0x0001;
    private static final int ACC_STATIC = 0x0008;
    private static final int ACC_INTERFACE = 0x0200;
    private static final int ACC_ENUM = 0x4000;

    @Override
    public String type() { return "MAIN"; }

    @Override
    public String label() { return "main 启动类"; }

    @Override
    public List<EntryPoint> detect(ClassMetadataRegistry registry) {
        List<EntryPoint> out = new ArrayList<>();
        for (ClassInfo ci : registry.allClasses()) {
            if (ci.getSource() != com.spark.projectanalysis.engine.model.SourceType.PROJECT) continue;
            int classAccess = ci.getAccess();
            if ((classAccess & ACC_INTERFACE) != 0 || (classAccess & ACC_ENUM) != 0) continue;
            if (!ci.hasOwnMethod("main", MAIN_DESCRIPTOR)) continue;
            int access = ci.methodAccess("main", MAIN_DESCRIPTOR);
            if ((access & ACC_PUBLIC) == 0 || (access & ACC_STATIC) == 0) continue;
            String fqcn = ci.getInternalName().replace('/', '.');
            out.add(new EntryPoint(type(), fqcn, "main", MAIN_DESCRIPTOR, "main: " + fqcn));
        }
        return out;
    }
}
