package com.spark.projectanalysis.service.dto;

import java.util.ArrayList;
import java.util.List;

/** 入口扫描结果：按类型分组的入口清单 */
public class EntryScanResult {

    private String projectPath;
    private String projectName;
    private List<Group> groups = new ArrayList<>();

    public static class Group {
        private String type;
        private String label;
        private List<EntryDto> entries = new ArrayList<>();

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public List<EntryDto> getEntries() { return entries; }
        public void setEntries(List<EntryDto> entries) { this.entries = entries; }
    }

    public static class EntryDto {
        private String className;
        private String methodName;
        private String methodDescriptor;
        private String display;

        public String getClassName() { return className; }
        public void setClassName(String className) { this.className = className; }
        public String getMethodName() { return methodName; }
        public void setMethodName(String methodName) { this.methodName = methodName; }
        public String getMethodDescriptor() { return methodDescriptor; }
        public void setMethodDescriptor(String methodDescriptor) { this.methodDescriptor = methodDescriptor; }
        public String getDisplay() { return display; }
        public void setDisplay(String display) { this.display = display; }
    }

    public String getProjectPath() { return projectPath; }
    public void setProjectPath(String projectPath) { this.projectPath = projectPath; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public List<Group> getGroups() { return groups; }
    public void setGroups(List<Group> groups) { this.groups = groups; }
}
