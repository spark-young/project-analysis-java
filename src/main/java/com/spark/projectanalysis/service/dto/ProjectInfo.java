package com.spark.projectanalysis.service.dto;

import java.util.ArrayList;
import java.util.List;

/** 项目布局信息（/api/project/info 响应） */
public class ProjectInfo {
    private String path;
    private String name;
    private String layoutType;
    private String layoutLabel;
    private int projectClassCount;
    private int dependencyJarCount;
    private List<String> warnings = new ArrayList<>();

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLayoutType() { return layoutType; }
    public void setLayoutType(String layoutType) { this.layoutType = layoutType; }
    public String getLayoutLabel() { return layoutLabel; }
    public void setLayoutLabel(String layoutLabel) { this.layoutLabel = layoutLabel; }
    public int getProjectClassCount() { return projectClassCount; }
    public void setProjectClassCount(int projectClassCount) { this.projectClassCount = projectClassCount; }
    public int getDependencyJarCount() { return dependencyJarCount; }
    public void setDependencyJarCount(int dependencyJarCount) { this.dependencyJarCount = dependencyJarCount; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
}
