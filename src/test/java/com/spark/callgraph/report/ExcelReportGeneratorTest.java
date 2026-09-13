package com.spark.callgraph.report;

import com.spark.callgraph.engine.CallGraphBuilder;
import com.spark.callgraph.engine.ClassMetadataRegistry;
import com.spark.callgraph.engine.model.CallNode;
import com.spark.callgraph.engine.model.MethodKey;
import com.spark.callgraph.engine.model.SourceType;
import com.spark.callgraph.service.NoiseRuleService;
import com.spark.callgraph.service.dto.AnalysisResult;
import com.spark.callgraph.testsupport.Fixtures;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExcelReportGeneratorTest {

    static ClassMetadataRegistry registry;
    static ExcelReportGenerator generator = new ExcelReportGenerator(new NoiseRuleService());

    @BeforeAll
    static void setUp() throws Exception {
        Path libJar = Fixtures.compileToJar("lib");
        Path demoClasses = Fixtures.compileToDir("demo", libJar);
        registry = ClassMetadataRegistry.builder()
                .addClassesDir(demoClasses, SourceType.PROJECT)
                .addJar(libJar, SourceType.DEPENDENCY)
                .build();
    }

    private AnalysisResult buildResult(CallNode... trees) {
        AnalysisResult result = new AnalysisResult();
        result.setProjectPath("/tmp/demo");
        result.setProjectName("demo");
        result.setLayoutType("MAVEN");
        result.setClassName("com.demo.OrderService");
        result.setMethodName(trees.length == 1 ? trees[0].getMethod().getName() : null);
        List<CallNode> roots = new ArrayList<>();
        int total = 0;
        for (CallNode t : trees) {
            roots.add(t);
            total += count(t);
        }
        result.setRoots(roots);
        result.getStats().setEntryCount(trees.length);
        result.getStats().setTotalNodes(total);
        return result;
    }

    private static int count(CallNode node) {
        int c = 1;
        for (CallNode child : node.getChildren()) c += count(child);
        return c;
    }

    private CallNode placeTree() {
        return new CallGraphBuilder(registry)
                .build(MethodKey.of("com/demo/OrderService", "place", "()V"), 20, 100000);
    }

    @Test
    void test_excelStructure() throws IOException {
        AnalysisResult result = buildResult(placeTree());
        byte[] bytes = generator.generate(result);

        assertTrue(bytes.length > 0);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            // 总览 + 方法 Sheet
            assertNotNull(wb.getSheet("总览"));
            assertNotNull(wb.getSheet("OrderService.place"));

            Sheet sheet = wb.getSheet("OrderService.place");
            Row header = sheet.getRow(0);
            assertEquals("层级", header.getCell(0).getStringCellValue());
            assertEquals("方法标识", header.getCell(1).getStringCellValue());
            assertEquals("来源", header.getCell(2).getStringCellValue());
            assertEquals("调用方式", header.getCell(3).getStringCellValue());
            assertEquals("行号", header.getCell(4).getStringCellValue());
            assertEquals("备注", header.getCell(5).getStringCellValue());
            assertNull(header.getCell(6), "应为 6 列，不再有第 7 列");

            // 行数 = 节点数（不含表头）
            int rows = sheet.getLastRowNum(); // 0-based，含表头
            assertEquals(count(placeTree()) + 1, rows + 1);

            // 首行为入口方法：全限定类名#方法名(参数) 格式
            Row first = sheet.getRow(1);
            assertEquals(0, (int) first.getCell(0).getNumericCellValue());
            assertEquals("com.demo.OrderService#place()", first.getCell(1).getStringCellValue());
            assertEquals("项目", first.getCell(2).getStringCellValue());
            assertEquals("入口", first.getCell(3).getStringCellValue());

            // 所有行：方法标识均为 全限定类名#方法 格式，且无缩进空白
            boolean hasNow = false, hasInterface = false;
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String id = row.getCell(1).getStringCellValue();
                assertTrue(id.indexOf('#') > 0, "方法标识应含 #：" + id);
                assertTrue(id.startsWith("com.demo."), "方法标识应以全限定类名开头：" + id);
                assertFalse(id.startsWith(" "), "方法标识不应有缩进空白：" + id);
                if (id.equals("com.demo.Util#now()")) {
                    hasNow = true;
                }
                if ("接口".equals(row.getCell(3).getStringCellValue())) {
                    hasInterface = true;
                }
            }
            assertTrue(hasNow, "应包含 com.demo.Util#now()（项目静态方法）");
            assertTrue(hasInterface, "应包含接口分派行");

            // Sheet 名不超过 31 字符
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                assertTrue(wb.getSheetName(i).length() <= 31);
            }
        }
    }

    @Test
    void test_sheetNameDedup_and_longName() throws IOException {
        // 两个同名方法根 + 一个超长类名
        CallNode t1 = new CallGraphBuilder(registry)
                .build(MethodKey.of("com/demo/OrderService", "pay", "(I)V"), 2, 1000);
        CallNode t2 = new CallGraphBuilder(registry)
                .build(MethodKey.of("com/demo/OrderService", "pay", "(Ljava/lang/String;)V"), 2, 1000);
        CallNode longName = new CallNode(
                MethodKey.of("com/demo/AVeryVeryVeryVeryVeryVeryVeryLongServiceClassNameForTest", "doSomething", "()V"),
                SourceType.PROJECT, null, -1);
        AnalysisResult result = buildResult(t1, t2, longName);

        byte[] bytes = generator.generate(result);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            List<String> names = new ArrayList<>();
            for (int i = 0; i < wb.getNumberOfSheets(); i++) names.add(wb.getSheetName(i));
            assertTrue(names.contains("OrderService.pay"));
            assertTrue(names.contains("OrderService.pay(2)"), "重名 Sheet 应加序号后缀");
            assertTrue(names.stream().anyMatch(n -> n.startsWith("AVeryVeryVeryVeryVeryVeryVe")),
                    "超长类名应截断");
            for (String n : names) assertTrue(n.length() <= 31);
        }
    }

    @Test
    void test_overviewContainsStats() throws IOException {
        AnalysisResult result = buildResult(placeTree());
        result.getWarnings().add("测试告警");
        byte[] bytes = generator.generate(result);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet overview = wb.getSheet("总览");
            assertNotNull(overview);
            StringBuilder all = new StringBuilder();
            for (int r = 0; r <= overview.getLastRowNum(); r++) {
                Row row = overview.getRow(r);
                if (row == null) continue;
                for (int c = 0; c < 2; c++) {
                    if (row.getCell(c) != null) all.append(row.getCell(c).getStringCellValue()).append(' ');
                }
            }
            String text = all.toString();
            assertTrue(text.contains("/tmp/demo"));
            assertTrue(text.contains("com.demo.OrderService"));
            assertTrue(text.contains("测试告警"));
            assertTrue(text.contains("JDK"));
        }
    }
}
