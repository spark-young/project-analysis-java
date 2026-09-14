package com.spark.callgraph.service.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 项目级已确认的交易入口清单（持久化于 <项目>/.callgraph/entries.json）。
 * 三层模型：confirmed（主体，进来先展示）+ excluded（已排除）+ candidates（临时候选，扫描产生未合并）。
 */
public class EntryList {

    private int version = 1;
    private String projectPath;

    /** 主体：已确认的交易入口，每次进项目先展示这些 */
    private List<EntryItem> confirmed = new ArrayList<>();

    /** 已排除的入口（不删除，留痕可捞回） */
    private List<EntryItem> excluded = new ArrayList<>();

    /** 最后一次自动扫描的时间（epoch millis） */
    private long lastScanMs;

    public EntryList() {}

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getProjectPath() { return projectPath; }
    public void setProjectPath(String projectPath) { this.projectPath = projectPath; }
    public List<EntryItem> getConfirmed() { return confirmed; }
    public void setConfirmed(List<EntryItem> confirmed) { this.confirmed = confirmed; }
    public List<EntryItem> getExcluded() { return excluded; }
    public void setExcluded(List<EntryItem> excluded) { this.excluded = excluded; }
    public long getLastScanMs() { return lastScanMs; }
    public void setLastScanMs(long lastScanMs) { this.lastScanMs = lastScanMs; }

    // ------------------------------------------------------------------
    /** 清单中单个入口条目 */
    public static class EntryItem {
        /** 全限定类名 */
        private String className;
        /** 方法名 */
        private String methodName;
        /** 方法描述符，如 (Ljava/lang/String;)V */
        private String descriptor;
        /** 来源：AUTO_SCAN（自动扫描）/ MANUAL（手动添加） */
        private String source;
        /** 分组标签：REST / DUBBO / ELASTIC_JOB / MAIN / MANUAL */
        private String group;
        /** 排除原因（仅 excluded 列表里有） */
        private String excludeReason;

        public EntryItem() {}

        public EntryItem(String className, String methodName, String descriptor,
                         String source, String group) {
            this.className = className;
            this.methodName = methodName;
            this.descriptor = descriptor;
            this.source = source;
            this.group = group;
        }

        /** 生成唯一 key（className + methodName + descriptor） */
        public String key() {
            return className + "#" + methodName + "#" + (descriptor == null ? "" : descriptor);
        }

        public String getClassName() { return className; }
        public void setClassName(String className) { this.className = className; }
        public String getMethodName() { return methodName; }
        public void setMethodName(String methodName) { this.methodName = methodName; }
        public String getDescriptor() { return descriptor; }
        public void setDescriptor(String descriptor) { this.descriptor = descriptor; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public String getGroup() { return group; }
        public void setGroup(String group) { this.group = group; }
        public String getExcludeReason() { return excludeReason; }
        public void setExcludeReason(String excludeReason) { this.excludeReason = excludeReason; }
    }
}
