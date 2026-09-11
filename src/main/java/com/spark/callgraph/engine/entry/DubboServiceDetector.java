package com.spark.callgraph.engine.entry;

import com.spark.callgraph.engine.ClassMetadataRegistry;
import com.spark.callgraph.engine.model.AnnotationInfo;
import com.spark.callgraph.engine.model.ClassInfo;
import com.spark.callgraph.engine.model.MethodKey;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Dubbo 入口：@DubboService/@Service（apache 与 alibaba 包名均支持）服务类的公共方法。
 */
@Component
public final class DubboServiceDetector implements EntryPointDetector {

    private static final String[] SERVICE_ANNOTATIONS = {
            "org/apache/dubbo/config/annotation/DubboService",
            "org/apache/dubbo/config/annotation/Service",
            "com/alibaba/dubbo/config/annotation/Service",
    };

    private static final int ACC_PUBLIC = 0x0001;
    private static final int ACC_STATIC = 0x0008;
    private static final int ACC_SYNTHETIC = 0x1000;
    private static final int ACC_BRIDGE = 0x0040;

    @Override
    public String type() { return "DUBBO"; }

    @Override
    public String label() { return "Dubbo 服务"; }

    @Override
    public List<EntryPoint> detect(ClassMetadataRegistry registry) {
        List<EntryPoint> out = new ArrayList<>();
        for (ClassInfo ci : registry.allClasses()) {
            if (ci.getSource() != com.spark.callgraph.engine.model.SourceType.PROJECT) continue;
            if (ci.isInterface() || ci.isAbstract()) continue;

            AnnotationInfo svc = null;
            for (String ann : SERVICE_ANNOTATIONS) {
                svc = ci.annotation(ann);
                if (svc != null) break;
            }
            if (svc == null) continue;

            String apiName = apiName(svc, ci);
            for (MethodKey m : ci.methodKeys()) {
                if (m.getName().startsWith("<")) continue;
                int access = ci.methodAccess(m.getName(), m.getDescriptor());
                if ((access & ACC_PUBLIC) == 0) continue;
                if ((access & (ACC_STATIC | ACC_SYNTHETIC | ACC_BRIDGE)) != 0) continue;
                out.add(new EntryPoint(type(), ci.getInternalName().replace('/', '.'),
                        m.getName(), m.getDescriptor(), "dubbo: " + apiName + "#" + m.getName()));
            }
        }
        return out;
    }

    /** 优先 interfaceClass 属性（类型描述符 Lcom/foo/Bar;），其次 interfaceName，回退实现类自身 */
    private static String apiName(AnnotationInfo svc, ClassInfo impl) {
        String typeDesc = svc.first("interfaceClass");
        if (typeDesc != null && typeDesc.startsWith("L") && typeDesc.endsWith(";")
                && !"void".equals(typeDesc)) {
            String n = typeDesc.substring(1, typeDesc.length() - 1).replace('/', '.');
            if (!"java.lang.Void".equals(n) && !"void".equals(n)) return n;
        }
        String name = svc.first("interfaceName");
        if (name != null && !name.isEmpty() && !"void".equals(name)) return name;
        return impl.getInternalName().replace('/', '.');
    }
}
