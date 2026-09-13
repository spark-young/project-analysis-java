package com.spark.callgraph.report;

import com.spark.callgraph.engine.model.CallNode;
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
import java.util.HashSet;
import java.util.List;
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
        return generate(result, "ALL");
    }

    /**
     * 生成 Excel。methodFrequency 区块按来源筛选 + 启用的样板规则过滤。
     *
     * @param sourceFilter ALL/PROJECT/DEPENDENCY/EXTERNAL
     */
    public byte[] generate(AnalysisResult result, String sourceFilter) throws IOException {
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
                if (noiseRuleService.isNoise(mf.getMethod(), mf.getSource())) {
                    noiseRemoved.add(mf);
                } else {
                    kept.add(mf);
                }
            }

            writeOverview(wb, result, headerStyle, sourceFilter, kept, noiseRemoved);
            writeFrequencySheet(wb, kept, headerStyle, "方法调用分析");
            writeFilteredOutSheet(wb, noiseRemoved, headerStyle);

            Set<String> usedSheetNames = new HashSet<>();
            int sheetCount = 0;
            for (CallNode root : result.getRoots()) {
                if (sheetCount >= MAX_SHEETS) break;
                String name = sheetName(root, usedSheetNames);
                Sheet sheet = wb.createSheet(name);
                writeMethodSheet(sheet, root, headerStyle, rootStyle);
                sheetCount++;
            }
            if (result.getRoots().size() > MAX_SHEETS) {
                Sheet overview = wb.getSheetAt(0);
                int r = overview.getLastRowNum() + 1;
                overview.createRow(r).createCell(0)
                        .setCellValue("入口方法超过 " + MAX_SHEETS + " 个，仅导出前 " + MAX_SHEETS + " 个（建议按具体方法分析）");
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
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
    private List<MethodFrequency> filterFrequency(List<MethodFrequency> all, String sourceFilter) {
        List<MethodFrequency> out = new ArrayList<>();
        for (MethodFrequency mf : all) {
            String src = mf.getSource();
            // 来源筛选
            if (sourceFilter != null && !sourceFilter.isEmpty()
                    && !"ALL".equalsIgnoreCase(sourceFilter)) {
                if (!sourceFilter.equalsIgnoreCase(src)) continue;
            }
            // 样板规则过滤（传入完整方法标识，内部解析类名/方法名/参数个数）
            if (noiseRuleService.isNoise(mf.getMethod(), src)) continue;
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
    private void writeFilteredOutSheet(SXSSFWorkbook wb, List<MethodFrequency> noiseRemoved, CellStyle headerStyle) {
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
                    noiseRuleService.getMatchedRule(mf.getMethod(), mf.getSource()));
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
    // 方法 Sheet
    // ------------------------------------------------------------------

    private void writeMethodSheet(Sheet sheet, CallNode root, CellStyle headerStyle, CellStyle rootStyle) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < HEADERS.length; i++) {
            Cell c = header.createCell(i);
            c.setCellValue(HEADERS[i]);
            c.setCellStyle(headerStyle);
        }
        int[] rowIdx = {1};
        writeNode(sheet, root, 0, rowIdx, rootStyle);
        sheet.createFreezePane(0, 1);
        sheet.setColumnWidth(0, 6 * 256);
        sheet.setColumnWidth(1, 80 * 256);
        sheet.setColumnWidth(2, 8 * 256);
        sheet.setColumnWidth(3, 14 * 256);
        sheet.setColumnWidth(4, 6 * 256);
        sheet.setColumnWidth(5, 22 * 256);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, HEADERS.length - 1));
    }

    private void writeNode(Sheet sheet, CallNode node, int level, int[] rowIdx, CellStyle rootStyle) {
        Row row = sheet.createRow(rowIdx[0]++);
        List<String> remarks = new ArrayList<>();
        if (node.isCycle()) remarks.add("环：已出现在上层路径");
        if (node.isTruncated()) remarks.add("截断：深度/节点上限");
        if (node.getSource() == com.spark.callgraph.engine.model.SourceType.EXTERNAL) remarks.add("外部：类不在类路径");

        row.createCell(0).setCellValue(level);
        Cell methodCell = row.createCell(1);
        methodCell.setCellValue(node.getMethod().getIdentifier());
        if (level == 0) methodCell.setCellStyle(rootStyle);
        row.createCell(2).setCellValue(node.getSource().getLabel());
        row.createCell(3).setCellValue(node.getInvokeType() == null ? "入口" : node.getInvokeType().getLabel());
        row.createCell(4).setCellValue(node.getLine() > 0 ? String.valueOf(node.getLine()) : "");
        row.createCell(5).setCellValue(String.join("；", remarks));

        for (CallNode child : node.getChildren()) {
            writeNode(sheet, child, level + 1, rowIdx, rootStyle);
        }
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private String sheetName(CallNode root, Set<String> used) {
        String base = root.getMethod().getSimpleClassName() + "." + root.getMethod().getName();
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
