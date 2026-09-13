# 方法被调频次 / 调用方统计 + 统计口径修正

## Context（为什么做）

用户在页面统计数字上提了两点诉求：

1. **统计口径修正**：当前"项目方法 / 依赖方法 / 外部方法"三个指标是**按节点重复计数**（遍历调用树每个节点各 +1，见 `AnalysisService.count()`）。用户希望"方法数"是**去重后的独立方法数**——方法被多少个节点重复展开过不重要，重要的是项目里**实际涉及多少种方法**。
2. **新增"方法被调频次 + 调用方"能力**：用户改/升级/调整某个方法前，想先看到"这个方法被调用了多少次、分别在哪几个地方（哪个上层方法、哪个行）被调"。希望页面能展示**高频被调方法排行**（按被调次数排序），点某个方法查看它的调用方列表。

配套修复（已完成并入本次说明）：此前 `CallGraphBuilder.buildRoots` 所有入口共享同一节点预算，导致"只有第一个方法有链、其余被截断"，已改为每入口独立预算。

## 现状代码事实（已确认）

- 调用树节点：`engine/model/CallNode.java` → `getMethod()`(MethodKey)、`getSource()`(SourceType)、`getInvokeType()`、`getLine()`、`getChildren()`、`isTruncated()`。
- `MethodKey`：owner+name+descriptor，`equals/hashCode` 已实现；`getIdentifier()` 返回 `com.foo.Bar#pay(String)` 全限定标识。
- 分析编排：`service/AnalysisService.analyze()` → `fillStats(result, trees, durationMs)` 填 stats；`count()` 目前按节点重复计数。
- 结果 DTO：`service/dto/AnalysisResult.java`，含 `roots` + `stats(entryCount/totalNodes/projectMethods/dependencyMethods/externalMethods/truncated/durationMs)`。
- 前端：`static/app.js` `renderStats()` 渲染 6 个指标 chip；`static/index.html` 第 120-123 行有 `tree-controls`（全部展开/收起按钮），第 126 行 `#statsBar`。整棵 `roots` 树已回传前端（全局搜索遍历它是既有可行路径）。
- Excel：`report/ExcelReportGenerator.java` 总览 sheet 直接读 `result.getStats()` 的这几个字段。

## 实现方案

### 1) 后端统计重构 —— `service/AnalysisService.java`

用**一次 DFS** 同时完成：总节点计数、去重独立方法数、被调次数（入度）、调用方采集。

新增常量：
```java
private static final int DEFAULT_METHOD_TOP_N = 20;  // 排行展示上限
private static final int CALLER_CAPTURE_LIMIT  = 20; // 每方法调用方捕获上限
```

新增私有静态内部类（服务内部收集用，延迟到 DTO 构建再转 String，避免逐边拼字符串）：
```java
private static final class MethodAgg {
    final List<CallerInfo> callers = new ArrayList<>();
    int  callCount;      // 入度：被调次数（根方法为 0，不进排行）
    SourceType source;   // 首次出现时的来源（owner 固定，source 稳定）
}
private static final class CallerInfo {
    final MethodKey caller;
    final int line;          // 调用处行号，未知 -1
}
```

改写 `fillStats`（签名不变）：遍历每棵树时 `collectStats(root, null, stats, agg)`。每次遇到子节点：`child.callCount++`，若 `callers.size()<CALLER_CAPTURE_LIMIT` 则记录 `(父方法, child.getLine())`。统计完成后：
- **去重口径**：遍历 `agg.values()` 按 `source` 分三类累加 → `stats.setProjectMethods/DependencyMethods/ExternalMethods`。
- **排行**：`agg` 中 `callCount>0` 的项，按 callCount 降序、同次数按 `getIdentifier()` 升序稳定排，`limit(DEFAULT_METHOD_TOP_N)` → `result.setMethodFrequency(...)`。

`totalNodes` 保持按节点计数不变（语义即"总节点数"）。根方法 callCount=0 不进排行。

`collectStats` 递归：
```java
private void collectStats(CallNode node, MethodKey caller, Stats stats, Map<MethodKey, MethodAgg> agg) {
    stats.setTotalNodes(stats.getTotalNodes() + 1);
    if (node.isTruncated()) stats.setTruncated(true);
    MethodAgg a = agg.computeIfAbsent(node.getMethod(), k -> new MethodAgg());
    if (a.source == null) a.source = node.getSource();
    if (caller != null) {
        a.callCount++;
        if (a.callers.size() < CALLER_CAPTURE_LIMIT)
            a.callers.add(new CallerInfo(caller, node.getLine()));
    }
    for (CallNode c : node.getChildren()) collectStats(c, node.getMethod(), stats, agg);
}
```

### 2) DTO 扩展

`AnalysisResult.java`：
- `Stats` 字段名**全部保持不变**（只是语义变去重），`ExcelReportGenerator` 总览 sheet 自动获得修正口径，**零改动**。
- 顶层新增 `private List<MethodFrequency> methodFrequency = new ArrayList<>();` + getter/setter。

新增 DTO（`service/dto/` 下两个文件）：
- `MethodFrequency.java`：`method`(String identifier) / `source`(String) / `callCount`(int) / `callers`(List\<MethodCaller\>)。
- `MethodCaller.java`：`caller`(String identifier) / `line`(int)。

调用方用 `String identifier` 而非整个 `MethodKey`——避免新增字段序列化超大持久树结构（当前树已 100 万+ 节点回传，top 20 的字符串列表体积可忽略）。

### 3) 前端 —— `static/index.html` + `static/app.js`

- `index.html` 第 120-123 行 `tree-controls` 里加按钮（在"全部收起"旁）：
  `<button id="btnTopMethods" type="button" class="btn small" disabled>高频方法 (Top 20)</button>`
- `index.html` 第 126 行 `#statsBar` 下方加隐藏面板：
  `<div id="topMethodsPanel" class="card" hidden><div id="topMethodsList"></div></div>`（复用现有 `.card` 浅色样式）。
- `app.js` els 注册表（约 39 行）追加 `topMethodsPanel` / `topMethodsList` / `btnTopMethods`。
- 在 `renderResult` 调 `renderStats(result.stats)` 处旁，调 `renderTopMethods(result)`：有 `methodFrequency` 数据时启用按钮。
- 新增 `renderTopMethods`：遍历 `result.methodFrequency`，每项渲染"`method` 被调 `callCount` 次 + source 徽标"；点击行展开 `callers` 子列表（`caller` : `line`）。交互复刻现有全局搜索的 chip/panel 模式。
- 新增 `btnTopMethods` 点击切换 `topMethodsPanel.hidden`。
- 现有 `renderStats` 结构不变，6 个 chip 标题不变，仅数值变为去重口径。

### 4) Excel 影响面

- 口径修正：零改动，总览 sheet 自动读去重值。
- 方法频次上榜：**一期不加**，后续可在 `ExcelReportGenerator` 总览 sheet 加"高频被调方法"段（读 `result.getMethodFrequency()`）。

### 5) 测试

新增 `test/service/AnalysisStatsTest.java`（复用既有 Fixtures 编译 demo 工程 → registry → analyze）：
1. **去重计数正确**：独立重算遍历收集去重 MethodKey 集合，断言 `stats.projectMethods` 等于独立集合 PROJECT 数，且 `< stats.totalNodes`。
2. **同父去重不虚增**：`Util.trim` 在 place 同级被去重为单节点，断言其 callCount 不等于重复虚高值。
3. **入度/调用方正确**：对已知方法（如 `Util.now`）断言 callCount 等于树中该方法的子节点个数，callers 非空且 ≤ CAP，且每个 caller 在独立重算的调用方集合内。
4. **调用方行号**：断言某 caller 的 line 等于对应 CallNode.getLine()。
5. **Top N 限额**：`methodFrequency.size() <= 20`，元素 callCount>0 且降序、同次数 identifier 稳定序。

扩展 `test/web/AnalysisControllerTest.java`：
- `test_analyzeMethod_returnsTree` 增加 `jsonPath("$.stats.projectMethods")` 为去重值（≤ totalNodes）；`jsonPath("$.methodFrequency").isNotEmpty()`。
- 新增用例：`$.methodFrequency[0].callCount >= $.methodFrequency[1].callCount`（降序）与字段存在性。

## 验证

1. `mvn -B test` → 全量 82+ 新增用例绿。
2. `mvn -B package -DskipTests` 打包。
3. `java -jar target\call-graph-analyzer.jar` 启动，本地路径/自分析。
4. 页面核对：6 指标中"项目方法"改为独立去重数（≤ 原重复值）；右上角"高频方法"按钮可点，面板展示 top 20 排行，点击某项展开调用方 + 行号。
5. Excel 导出：总览 sheet 的方法计数已是去重口径。