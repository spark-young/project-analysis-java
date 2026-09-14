package com.spark.callgraph.report;

import com.spark.callgraph.engine.model.CallGraph;
import com.spark.callgraph.engine.model.GraphEdge;
import com.spark.callgraph.engine.model.GraphMethod;
import com.spark.callgraph.engine.model.MethodKey;
import com.spark.callgraph.engine.model.SourceType;
import com.spark.callgraph.service.NoiseRuleService;
import com.spark.callgraph.service.dto.AnalysisResult;
import com.spark.callgraph.service.dto.MethodCaller;
import com.spark.callgraph.service.dto.MethodFrequency;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Excel 报告：总览 + 每个入口方法一个 Sheet（树形层级缩进平铺）。
 */
@Service
public class ExcelReportGenerator {

    private static final int MAX_SHEETS = 200;
    private static final String[] HEADERS = {"层级", "方法标识", "来源", "调用方式", "行号", "备注"};

    private final NoiseRuleService noiseRuleService;

    public ExcelReportGenerator(NoiseRuleService noiseRuleService) {
        this.noiseRuleService = noiseRuleService;
    }

    public byte[] generate(AnalysisResult result) throws IOException {
        return generate(result, "ALL", null);
    }

    /**
     * 生成 Excel。methodFrequency 区块按来源筛选 + 启用的样板规则过滤（全局+项目级合并）。
     *
     * @param sourceFilter ALL/PROJECT/DEPENDENCY/EXTERNAL
     * @param projectPath  项目路径（传了就合并项目级噪声规则；null 只查全局）
     */
    public byte[] generate(AnalysisResult result, String sourceFilter, String projectPath) throws IOException {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(200)) {
            CellStyle headerStyle = headerStyle(wb);
            CellStyle rootStyle = rootStyle(wb);

            // 预计算：来源筛选 + 样板规则分离
            List<MethodFrequency> allFreq = result.getMethodFrequency() == null
                    ? Collections.emptyList() : result.getMethodFrequency();
            List<MethodFrequency> kept = new ArrayList<>();
            List<MethodFrequency> noiseRemoved = new ArrayList<>();
            for (MethodFrequency mf : allFreq) {
                if (sourceFilter != null && !sourceFilter.isEmpty()
                        && !"ALL".equalsIgnoreCase(sourceFilter)) {
                    if (!sourceFilter.equalsIgnoreCase(mf.getSource())) continue;
                }
                if (noiseRuleService.isNoise(mf.getMethod(), mf.getSource(), projectPath)) {
                    noiseRemoved.add(mf);
                } else {
                    kept.add(mf);
                }
            }

            writeOverview(wb, result, headerStyle, sourceFilter, kept, noiseRemoved);
            writeFrequencySheet(wb, kept, headerStyle, "方法调用分析");
            writeFilteredOutSheet(wb, noiseRemoved, headerStyle, projectPath);

            writeRootSheets(wb, result, headerStyle, rootStyle);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    // ------------------------------------------------------------------
    // 项目级导出：跨多入口聚合，生成 总览 / 方法调用分析 / 被过滤 / 各入口调用链 Sheet
    // ------------------------------------------------------------------

    public byte[] generateProject(List<AnalysisResult> results, String sourceFilter, String projectPath)
            throws IOException {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(200)) {
            CellStyle headerStyle = headerStyle(wb);
            CellStyle rootStyle = rootStyle(wb);

            // 跨入口聚合频率（口径同单入口 collectGraphStats：入边数 = 被调次数）
            List<MethodFrequency> allFreq = aggregateFrequency(results);

            List<MethodFrequency> kept = new ArrayList<>();
            List<MethodFrequency> noiseRemoved = new ArrayList<>();
            for (MethodFrequency mf : allFreq) {
                if (sourceFilter != null && !sourceFilter.isEmpty()
                        && !"ALL".equalsIgnoreCase(sourceFilter)) {
                    if (!sourceFilter.equalsIgnoreCase(mf.getSource())) continue;
                }
                if (noiseRuleService.isNoise(mf.getMethod(), mf.getSource(), projectPath)) {
                    noiseRemoved.add(mf);
                } else {
                    kept.add(mf);
                }
            }

            writeProjectOverview(wb, results, headerStyle, sourceFilter, kept, noiseRemoved);
            writeFrequencySheet(wb, kept, headerStyle, "方法调用分析");
            writeFilteredOutSheet(wb, noiseRemoved, headerStyle, projectPath);
            writeProjectRootSheets(wb, results, headerStyle, rootStyle);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    /** 跨入口聚合方法被调次数：key=MethodKey，被调次数=跨全部图的入边数之和 */
    private List<MethodFrequency> aggregateFrequency(List<AnalysisResult> results) {
        Map<MethodKey, FreqAgg> agg = new HashMap<>();
        for (AnalysisResult r : results) {
            CallGraph g = r.getGraph();
            if (g == null) continue;
            for (GraphEdge edge : g.getEdges()) {
                GraphMethod target = g.getMethods().get(edge.getTo());
                GraphMethod caller = g.getMethods().get(edge.getFrom());
                if (target == null) continue;
                MethodKey key = target.toKey();
                FreqAgg a = agg.computeIfAbsent(key, k -> new FreqAgg());
                if (a.source == null) a.source = target.getSource();
                a.callCount++;
                if (caller != null) {
                    MethodCaller mc = new MethodCaller();
                    mc.setCaller(caller.getDisplay());
                    mc.setLine(edge.getLine());
                    a.callers.add(mc);
                }
            }
        }
        List<MethodFrequency> out = new ArrayList<>(agg.size());
        for (Map.Entry<MethodKey, FreqAgg> e : agg.entrySet()) {
            MethodFrequency f = new MethodFrequency();
            f.setMethod(e.getKey().getIdentifier());
            f.setSource(e.getValue().source.name());
            f.setCallCount(e.getValue().callCount);
            f.setCallers(e.getValue().callers);
            out.add(f);
        }
        out.sort((a, b) -> {
            int c = Integer.compare(b.getCallCount(), a.getCallCount());
            return c != 0 ? c : a.getMethod().compareTo(b.getMethod());
        });
        return out;
    }

    /** 项目级总览：项目信息 + 跨入口汇总 + 每入口一行明细 */
    private void writeProjectOverview(SXSSFWorkbook wb, List<AnalysisResult> results, CellStyle headerStyle,
                                      String sourceFilter, List<MethodFrequency> kept,
                                      List<MethodFrequency> noiseRemoved) {
        Sheet sheet = wb.createSheet("总览");
        int r = 0;
        r = titleRow(sheet, r, "Java 调用链分析报告（项目级 · " + results.size() + " 个交易入口）", headerStyle);
        r = kv(sheet, r, "生成时间", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        r = kv(sheet, r, "项目路径", results.get(0).getProjectPath());
        r = kv(sheet, r, "项目名称", results.get(0).getProjectName() == null
                ? "" : results.get(0).getProjectName());
        r = kv(sheet, r, "入口方法数", String.valueOf(results.size()));

        long totalNodes = 0;
        long projectMethods = 0, depMethods = 0, extMethods = 0;
        int truncated = 0;
        for (AnalysisResult res : results) {
            AnalysisResult.Stats s = res.getStats();
            if (s == null) continue;
            totalNodes += s.getTotalNodes();
            projectMethods += s.getProjectMethods();
            depMethods += s.getDependencyMethods();
            extMethods += s.getExternalMethods();
            if (s.isTruncated()) truncated++;
        }
        r = kv(sheet, r, "调用链总节点数（跨入口）", String.valueOf(totalNodes));
        r = kv(sheet, r, "项目方法数（跨入口）", String.valueOf(projectMethods));
        r = kv(sheet, r, "依赖方法数（跨入口）", String.valueOf(depMethods));
        r = kv(sheet, r, "外部方法数（跨入口）", String.valueOf(extMethods));
        r = kv(sheet, r, "存在截断的入口数", String.valueOf(truncated));
        r = kv(sheet, r, "方法调用分析", "项目级聚合（同一方法被多次入口调用时次数累加），见「方法调用分析」/「被过滤方法」Sheet");
        r = kv(sheet, r, "来源筛选", "ALL".equalsIgnoreCase(sourceFilter) ? "全部来源" : sourceFilter);
        r = kv(sheet, r, "保留方法数", String.valueOf(kept.size()));
        r = kv(sheet, r, "被样板规则过滤", String.valueOf(noiseRemoved.size()));

        r = titleRow(sheet, r, "入口明细", headerStyle);
        Row head = sheet.createRow(r++);
        String[] cols = {"序号", "入口方法", "项目方法", "依赖方法", "外部方法", "节点数", "耗时(ms)", "截断"};
        for (int i = 0; i < cols.length; i++) {
            head.createCell(i).setCellValue(cols[i]);
            head.getCell(i).setCellStyle(headerStyle);
        }
        int seq = 1;
        for (AnalysisResult res : results) {
            AnalysisResult.Stats s = res.getStats();
            String method = (res.getClassName() == null ? "" : res.getClassName())
                    + (res.getMethodName() == null ? "" : "#" + res.getMethodName());
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(seq++);
            row.createCell(1).setCellValue(method);
            row.createCell(2).setCellValue(s == null ? 0 : s.getProjectMethods());
            row.createCell(3).setCellValue(s == null ? 0 : s.getDependencyMethods());
            row.createCell(4).setCellValue(s == null ? 0 : s.getExternalMethods());
            row.createCell(5).setCellValue(s == null ? 0 : s.getTotalNodes());
            row.createCell(6).setCellValue(s == null ? 0 : s.getDurationMs());
            row.createCell(7).setCellValue((s != null && s.isTruncated()) ? "是" : "");
        }
        sheet.setColumnWidth(0, 6 * 256);
        sheet.setColumnWidth(1, 90 * 256);
        for (int i = 2; i < cols.length; i++) sheet.setColumnWidth(i, 12 * 256);
        sheet.createFreezePane(0, 2);
    }

    /** 项目级调用链 Sheet：跨入口给每个根方法写一个 Sheet（共用去重命名，避免跨入口同名冲突） */
    private void writeProjectRootSheets(SXSSFWorkbook wb, List<AnalysisResult> results,
                                        CellStyle headerStyle, CellStyle rootStyle) {
        Set<String> used = new HashSet<>();
        int sheetCount = 0;
        for (AnalysisResult res : results) {
            CallGraph g = res.getGraph();
            if (g == null) continue;
            for (int rootId : g.getRoots()) {
                if (sheetCount >= MAX_SHEETS) break;
                String name = sheetName(g.getMethods().get(rootId), used);
                writeMethodSheet(wb.createSheet(name), g, rootId, headerStyle, rootStyle);
                sheetCount++;
            }
            if (sheetCount >= MAX_SHEETS) break;
        }
        if (sheetCount >= MAX_SHEETS) {
            Sheet overview = wb.getSheet("总览");
            int row = overview.getLastRowNum() + 1;
            overview.createRow(row).createCell(0)
                    .setCellValue("入口方法超过 " + MAX_SHEETS + " 个，仅导出前 " + MAX_SHEETS + " 个（建议按具体方法分析）");
        }
    }

    /** 跨入口频率聚合收集器 */
    private static final class FreqAgg {
        final List<MethodCaller> callers = new ArrayList<>();
        int callCount;
        SourceType source;
    }

    // ------------------------------------------------------------------
    // 总览
    // ------------------------------------------------------------------

    private void writeOverview(SXSSFWorkbook wb, AnalysisResult result, CellStyle headerStyle,
                               String sourceFilter, List<MethodFrequency> kept, List<MethodFrequency> noiseRemoved) {
        Sheet sheet = wb.createSheet("总览");
        int r = 0;
        r = titleRow(sheet, r, "Java 方法调用链分析报告", headerStyle);
        r = kv(sheet, r, "分析时间", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        r = kv(sheet, r, "项目路径", result.getProjectPath());
        r = kv(sheet, r, "项目名称", result.getProjectName());
        r = kv(sheet, r, "项目布局", result.getLayoutType());
        r = kv(sheet, r, "入口类", result.getClassName());
        r = kv(sheet, r, "入口方法", result.getMethodName() == null
                ? "（整个类，每个方法一个 Sheet）" : result.getMethodName());
        r = kv(sheet, r, "入口方法数", String.valueOf(result.getStats().getEntryCount()));
        r = kv(sheet, r, "调用链总节点数", String.valueOf(result.getStats().getTotalNodes()));
        r = kv(sheet, r, "项目方法数", String.valueOf(result.getStats().getProjectMethods()));
        r = kv(sheet, r, "依赖方法数", String.valueOf(result.getStats().getDependencyMethods()));
        r = kv(sheet, r, "外部方法数", String.valueOf(result.getStats().getExternalMethods()));
        r = kv(sheet, r, "是否截断", result.getStats().isTruncated() ? "是（深度/节点上限）" : "否");
        r = kv(sheet, r, "分析耗时(ms)", String.valueOf(result.getStats().getDurationMs()));
        r = kv(sheet, r, "JDK 方法", "按配置排除，不出现在报告中");

        // 方法调用次数分析汇总（不再列明细，明细见独立 Sheet）
        r = titleRow(sheet, r, "方法调用次数分析", headerStyle);
        r = kv(sheet, r, "来源筛选", "ALL".equalsIgnoreCase(sourceFilter) ? "全部来源" : sourceFilter);
        r = kv(sheet, r, "保留方法数", String.valueOf(kept.size()) + "（详见「方法调用分析」Sheet）");
        r = kv(sheet, r, "被样板规则过滤", String.valueOf(noiseRemoved.size()) + "（详见「被过滤方法」Sheet）");
        if (!kept.isEmpty()) {
            int totalCalls = 0;
            for (MethodFrequency mf : kept) totalCalls += mf.getCallCount();
            r = kv(sheet, r, "保留方法总被调次数", String.valueOf(totalCalls));
            r = kv(sheet, r, "最高被调方法", kept.get(0).getMethod() + "（" + kept.get(0).getCallCount() + " 次）");
        }

        if (!result.getUnresolvedDependencies().isEmpty()) {
            r = titleRow(sheet, r, "未解析依赖", headerStyle);
            for (String dep : result.getUnresolvedDependencies()) {
                r = kv(sheet, r, "", dep);
            }
        }
        if (!result.getWarnings().isEmpty()) {
            r = titleRow(sheet, r, "告警", headerStyle);
            for (String w : result.getWarnings()) {
                r = kv(sheet, r, "", w);
            }
        }
        r = titleRow(sheet, r, "说明", headerStyle);
        r = kv(sheet, r, "", "每个入口方法一个 Sheet；层级列表示树形调用结构；方法标识为 全限定类名#方法名(参数类型)，构造器为 #<init>；调用方式含虚调用/静态/接口/构造/lambda/接口实现分派。");
        sheet.setColumnWidth(0, 22 * 256);
        sheet.setColumnWidth(1, 110 * 256);
    }

    /**
     * 按来源筛选 + 启用的样板规则过滤方法频次列表。
     */
    private List<MethodFrequency> filterFrequency(List<MethodFrequency> all, String sourceFilter, String projectPath) {
        List<MethodFrequency> out = new ArrayList<>();
        for (MethodFrequency mf : all) {
            String src = mf.getSource();
            // 来源筛选
            if (sourceFilter != null && !sourceFilter.isEmpty()
                    && !"ALL".equalsIgnoreCase(sourceFilter)) {
                if (!sourceFilter.equalsIgnoreCase(src)) continue;
            }
            // 样板规则过滤（传入完整方法标识，内部解析类名/方法名/参数个数）
            if (noiseRuleService.isNoise(mf.getMethod(), src, projectPath)) continue;
            out.add(mf);
        }
        return out;
    }

    /**
     * 独立 Sheet：方法调用分析（保留的方法，完整列表）。
     */
    private void writeFrequencySheet(SXSSFWorkbook wb, List<MethodFrequency> kept,
                                     CellStyle headerStyle, String sheetName) {
        if (kept == null || kept.isEmpty()) return;
        Sheet sheet = wb.createSheet(sheetName);
        int r = 0;
        r = titleRow(sheet, r, "方法调用次数分析（共 " + kept.size() + " 个方法）", headerStyle);
        Row head = sheet.createRow(r++);
        head.createCell(0).setCellValue("排名");
        head.createCell(1).setCellValue("方法标识");
        head.createCell(2).setCellValue("来源");
        head.createCell(3).setCellValue("被调次数");
        head.createCell(4).setCellValue("主要调用方（展示前20个）");
        for (int i = 0; i <= 4; i++) {
            head.getCell(i).setCellStyle(headerStyle);
        }
        int rank = 1;
        for (MethodFrequency mf : kept) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(rank++);
            row.createCell(1).setCellValue(mf.getMethod());
            row.createCell(2).setCellValue(mf.getSource());
            row.createCell(3).setCellValue(mf.getCallCount());
            StringBuilder callers = new StringBuilder();
            List<MethodCaller> cs = mf.getCallers();
            if (cs != null) {
                int limit = Math.min(cs.size(), 20);
                for (int k = 0; k < limit; k++) {
                    if (k > 0) callers.append("\n");
                    callers.append(cs.get(k).getCaller());
                    int ln = cs.get(k).getLine();
                    if (ln > 0) callers.append("  L").append(ln);
                }
                if (cs.size() > 20) {
                    callers.append("\n… 另有 ").append(cs.size() - 20).append(" 处调用，完整列表请在页面点“下载”按钮导出");
                }
            }
            row.createCell(4).setCellValue(callers.toString());
        }
        sheet.setColumnWidth(0, 8 * 256);
        sheet.setColumnWidth(1, 90 * 256);
        sheet.setColumnWidth(2, 12 * 256);
        sheet.setColumnWidth(3, 12 * 256);
        sheet.setColumnWidth(4, 100 * 256);
        sheet.createFreezePane(0, 2);
    }

    /**
     * 写入"被过滤方法"Sheet：列出命中样板规则的方法，供用户检查是否有误杀。
     * 最后一列显示命中的规则名称。
     */
    private void writeFilteredOutSheet(SXSSFWorkbook wb, List<MethodFrequency> noiseRemoved,
                                       CellStyle headerStyle, String projectPath) {
        if (noiseRemoved == null || noiseRemoved.isEmpty()) return;
        Sheet sheet = wb.createSheet("被过滤方法");
        int r = 0;
        r = titleRow(sheet, r, "被样板规则过滤的方法（共 " + noiseRemoved.size() + " 个）", headerStyle);
        r = kv(sheet, r, "说明", "以下方法因命中启用的样板过滤规则而未出现在「方法调用分析」Sheet 中，"
                + "请检查是否存在业务方法被误过滤的情况。");
        Row head = sheet.createRow(r++);
        head.createCell(0).setCellValue("排名");
        head.createCell(1).setCellValue("方法标识");
        head.createCell(2).setCellValue("来源");
        head.createCell(3).setCellValue("被调次数");
        head.createCell(4).setCellValue("主要调用方（最多20个）");
        head.createCell(5).setCellValue("命中规则");
        for (int i = 0; i <= 5; i++) {
            head.getCell(i).setCellStyle(headerStyle);
        }
        int rank = 1;
        for (MethodFrequency mf : noiseRemoved) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(rank++);
            row.createCell(1).setCellValue(mf.getMethod());
            row.createCell(2).setCellValue(mf.getSource());
            row.createCell(3).setCellValue(mf.getCallCount());
            StringBuilder callers = new StringBuilder();
            List<MethodCaller> cs = mf.getCallers();
            if (cs != null) {
                for (int k = 0; k < cs.size(); k++) {
                    if (k > 0) callers.append("\n");
                    callers.append(cs.get(k).getCaller());
                    int ln = cs.get(k).getLine();
                    if (ln > 0) callers.append("  L").append(ln);
                }
            }
            row.createCell(4).setCellValue(callers.toString());
            row.createCell(5).setCellValue(
                    noiseRuleService.getMatchedRule(mf.getMethod(), mf.getSource(), projectPath));
        }
        sheet.setColumnWidth(0, 8 * 256);
        sheet.setColumnWidth(1, 90 * 256);
        sheet.setColumnWidth(2, 12 * 256);
        sheet.setColumnWidth(3, 12 * 256);
        sheet.setColumnWidth(4, 100 * 256);
        sheet.setColumnWidth(5, 30 * 256);
        sheet.createFreezePane(0, 3);
    }

    // ------------------------------------------------------------------
    // 方法 Sheet（图版：每个入口方法一个 Sheet，DFS 平铺去重节点）
    // ------------------------------------------------------------------

    private void writeRootSheets(SXSSFWorkbook wb, AnalysisResult result,
                                 CellStyle headerStyle, CellStyle rootStyle) {
        CallGraph g = result.getGraph();
        List<Integer> roots = g.getRoots();
        Set<String> usedSheetNames = new HashSet<>();
        int sheetCount = 0;
        for (int rootId : roots) {
            if (sheetCount >= MAX_SHEETS) break;
            String name = sheetName(g.getMethods().get(rootId), usedSheetNames);
            Sheet sheet = wb.createSheet(name);
            writeMethodSheet(sheet, g, rootId, headerStyle, rootStyle);
            sheetCount++;
        }
        if (roots.size() > MAX_SHEETS) {
            Sheet overview = wb.getSheetAt(0);
            int r = overview.getLastRowNum() + 1;
            overview.createRow(r).createCell(0)
                    .setCellValue("入口方法超过 " + MAX_SHEETS + " 个，仅导出前 " + MAX_SHEETS + " 个（建议按具体方法分析）");
        }
    }

    private void writeMethodSheet(Sheet sheet, CallGraph graph, int rootId,
                                  CellStyle headerStyle, CellStyle rootStyle) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < HEADERS.length; i++) {
            Cell c = header.createCell(i);
            c.setCellValue(HEADERS[i]);
            c.setCellStyle(headerStyle);
        }
        int[] rowIdx = {1};
        boolean[] visited = new boolean[graph.getMethods().size()];
        writeNode(sheet, graph, rootId, null, 0, rowIdx, rootStyle, visited);
        sheet.createFreezePane(0, 1);
        sheet.setColumnWidth(0, 6 * 256);
        sheet.setColumnWidth(1, 80 * 256);
        sheet.setColumnWidth(2, 8 * 256);
        sheet.setColumnWidth(3, 14 * 256);
        sheet.setColumnWidth(4, 6 * 256);
        sheet.setColumnWidth(5, 22 * 256);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, HEADERS.length - 1));
    }

    private void writeNode(Sheet sheet, CallGraph graph, int methodId, GraphEdge parentEdge,
                           int level, int[] rowIdx, CellStyle rootStyle, boolean[] visited) {
        if (visited[methodId]) return;   // 去重：每个方法仅在入口链中展开一次，避免重复/栈溢出
        visited[methodId] = true;
        GraphMethod m = graph.getMethods().get(methodId);
        Row row = sheet.createRow(rowIdx[0]++);
        List<String> remarks = new ArrayList<>();
        if (m.isCycle()) remarks.add("环：已出现在上层路径");
        if (m.getSource() == SourceType.EXTERNAL) remarks.add("外部：类不在类路径");

        row.createCell(0).setCellValue(level);
        Cell methodCell = row.createCell(1);
        methodCell.setCellValue(m.getDisplay());
        if (level == 0) methodCell.setCellStyle(rootStyle);
        row.createCell(2).setCellValue(m.getSource().getLabel());
        row.createCell(3).setCellValue(parentEdge == null ? "入口" : parentEdge.getInvoke().getLabel());
        row.createCell(4).setCellValue(parentEdge != null && parentEdge.getLine() > 0
                ? String.valueOf(parentEdge.getLine()) : "");
        row.createCell(5).setCellValue(String.join("；", remarks));

        for (GraphEdge e : graph.edgesOf(methodId)) {
            writeNode(sheet, graph, e.getTo(), e, level + 1, rowIdx, rootStyle, visited);
        }
    }

    private String sheetName(GraphMethod m, Set<String> used) {
        String owner = m.getOwner();
        int lastSlash = owner.lastIndexOf('/');
        String simple = lastSlash >= 0 ? owner.substring(lastSlash + 1) : owner;
        String base = simple + "." + m.getName();
        String safe = WorkbookUtil.createSafeSheetName(base);
        if (safe.length() > 31) safe = safe.substring(0, 31);
        String name = safe;
        int i = 2;
        while (!used.add(name)) {
            String suffix = "(" + i++ + ")";
            name = safe.substring(0, Math.min(safe.length(), 31 - suffix.length())) + suffix;
        }
        return name;
    }

    private int titleRow(Sheet sheet, int r, String text, CellStyle style) {
        Row row = sheet.createRow(r);
        Cell c = row.createCell(0);
        c.setCellValue(text);
        c.setCellStyle(style);
        return r + 1;
    }

    private int kv(Sheet sheet, int r, String key, String value) {
        Row row = sheet.createRow(r);
        row.createCell(0).setCellValue(key);
        row.createCell(1).setCellValue(value == null ? "" : value);
        return r + 1;
    }

    private CellStyle headerStyle(SXSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle rootStyle(SXSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }
}
