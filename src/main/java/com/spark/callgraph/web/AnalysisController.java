package com.spark.callgraph.web;

import com.spark.callgraph.report.ExcelReportGenerator;
import com.spark.callgraph.service.AnalysisCacheService;
import com.spark.callgraph.service.AnalysisException;
import com.spark.callgraph.service.AnalysisService;
import com.spark.callgraph.service.BatchAnalyzeService;
import com.spark.callgraph.service.EntryScanService;
import com.spark.callgraph.service.GitPrepareService;
import com.spark.callgraph.service.JavacCompileService;
import com.spark.callgraph.service.MavenCompileService;
import com.spark.callgraph.service.dto.AnalyzeRequest;
import com.spark.callgraph.service.dto.AnalysisResult;
import com.spark.callgraph.service.dto.BatchAnalyzeStatus;
import com.spark.callgraph.service.dto.EntryScanResult;
import com.spark.callgraph.service.dto.EntryScanStatus;
import com.spark.callgraph.service.dto.GitPrepareRequest;
import com.spark.callgraph.service.dto.GitPrepareStatus;
import com.spark.callgraph.service.dto.ProjectInfo;
import com.spark.callgraph.service.dto.ScanRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * REST API。默认仅监听 127.0.0.1（见 application.yml），内网本机使用。
 */
@RestController
@RequestMapping("/api")
public class AnalysisController {

    private final AnalysisService analysisService;
    private final ExcelReportGenerator excelReportGenerator;
    private final EntryScanService entryScanService;
    private final GitPrepareService gitPrepareService;
    private final MavenCompileService mavenCompileService;
    private final JavacCompileService javacCompileService;
    private final BatchAnalyzeService batchAnalyzeService;
    private final AnalysisCacheService cacheService;

    public AnalysisController(AnalysisService analysisService, ExcelReportGenerator excelReportGenerator,
                              EntryScanService entryScanService, GitPrepareService gitPrepareService,
                              MavenCompileService mavenCompileService, JavacCompileService javacCompileService,
                              BatchAnalyzeService batchAnalyzeService, AnalysisCacheService cacheService) {
        this.analysisService = analysisService;
        this.excelReportGenerator = excelReportGenerator;
        this.entryScanService = entryScanService;
        this.gitPrepareService = gitPrepareService;
        this.mavenCompileService = mavenCompileService;
        this.javacCompileService = javacCompileService;
        this.batchAnalyzeService = batchAnalyzeService;
        this.cacheService = cacheService;
    }

    @GetMapping("/defaults")
    public AnalyzeRequest defaults() {
        return analysisService.demoDefaults();
    }

    @GetMapping("/project/info")
    public ProjectInfo info(@RequestParam("path") String path) {
        return analysisService.projectInfo(path);
    }

    @GetMapping("/classes/search")
    public List<String> search(@RequestParam("path") String path,
                                @RequestParam(value = "q", required = false, defaultValue = "") String q) {
        return analysisService.searchClasses(path, q);
    }

    @GetMapping("/classes/methods")
    public List<Map<String, String>> methods(@RequestParam("path") String path,
                                              @RequestParam("class") String cls) {
        return analysisService.getMethods(path, cls);
    }

    @GetMapping("/classes/verify")
    public Map<String, Object> verify(@RequestParam("path") String path,
                                      @RequestParam("class") String cls,
                                      @RequestParam(value = "method", required = false) String method,
                                      @RequestParam(value = "descriptor", required = false, defaultValue = "") String descriptor) {
        return analysisService.verifyEntry(path, cls, method, descriptor);
    }

    @PostMapping("/analyze")
    public AnalysisResult analyze(@RequestBody AnalyzeRequest req) {
        return analysisService.analyze(req);
    }

    /** 按交易入口清单批量分析（异步）：返回 jobId，前端轮询进度 */
    @PostMapping("/analyze/batch")
    public Map<String, String> analyzeBatch(@RequestBody AnalyzeRequest req) {
        String jobId = batchAnalyzeService.startAsync(req);
        Map<String, String> resp = new HashMap<>();
        resp.put("jobId", jobId);
        return resp;
    }

    /** 查询批量分析进度（DONE 时返回完整结果） */
    @GetMapping("/analyze/batch/progress/{jobId}")
    public BatchAnalyzeStatus analyzeBatchProgress(@PathVariable String jobId) {
        BatchAnalyzeStatus status = batchAnalyzeService.status(jobId);
        if (status == null) {
            throw new AnalysisException(HttpStatus.NOT_FOUND, "任务不存在或已过期: " + jobId);
        }
        return status;
    }

    @PostMapping("/scan/entries")
    public EntryScanResult scanEntries(@RequestBody ScanRequest req) {
        String path = req == null ? null : req.getProjectPath();
        if (path != null && !path.trim().isEmpty()) {
            compileIfNeeded(Paths.get(path.trim()), false);
        }
        return entryScanService.scan(path);
    }

    /** 异步启动入口扫描，返回 jobId */
    @PostMapping("/scan/entries/async")
    public Map<String, String> scanEntriesAsync(@RequestBody ScanRequest req) {
        String path = req == null ? null : req.getProjectPath();
        if (path != null && !path.trim().isEmpty()) {
            compileIfNeeded(Paths.get(path.trim()), false);
        }
        String jobId = entryScanService.startAsync(path);
        Map<String, String> result = new HashMap<>();
        result.put("jobId", jobId);
        return result;
    }

    /** 查询扫描进度 */
    @GetMapping("/scan/entries/progress/{jobId}")
    public EntryScanStatus scanEntriesProgress(@PathVariable String jobId) {
        EntryScanStatus status = entryScanService.status(jobId);
        if (status == null) {
            throw new AnalysisException(HttpStatus.NOT_FOUND, "任务不存在或已过期: " + jobId);
        }
        return status;
    }

    /** 单独的编译接口：前端"重新分析"可先手动触发编译；force=true 时做 clean compile */
    @PostMapping("/ensure-compile")
    public String ensureCompile(@RequestBody ScanRequest req,
                                @RequestParam(value = "force", required = false, defaultValue = "false") boolean force) {
        String path = req == null ? null : req.getProjectPath();
        if (path == null || path.trim().isEmpty()) {
            return "路径不能为空";
        }
        compileIfNeeded(Paths.get(path.trim()), force);
        return "ok";
    }

    /**
     * 自动探测项目类型并按需编译（Maven / javac / 已有产物）。
     * force=true 时强制 clean compile（用于"重新分析"场景）。
     */
    private void compileIfNeeded(Path root, boolean force) {
        try {
            if (!force) {
                // 非强制：已有产物就跳过
                if (findExistingArtifacts(root) != null) {
                    return;
                }
            }
            // Maven 项目
            if (Files.exists(root.resolve("pom.xml"))) {
                MavenCompileService.CompileResult r = force
                        ? mavenCompileService.compileClean(root)
                        : mavenCompileService.compile(root);
                if (!r.isSuccess()) {
                    throw new AnalysisException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                            (force ? "clean compile" : "Maven 编译") + "失败：\n" + r.getOutputTail());
                }
                return;
            }
            // 普通 Java 源码 → javac（没有 clean 概念，强制时直接覆盖 build/）
            boolean hasJava;
            try (Stream<Path> walk = Files.walk(root, 100)) {
                hasJava = walk.anyMatch(p ->
                        Files.isRegularFile(p) && p.getFileName().toString().endsWith(".java"));
            }
            if (hasJava) {
                Path buildRoot = root.resolve("build");
                Files.createDirectories(buildRoot);
                JavacCompileService.CompileResult r = javacCompileService.compile(root, buildRoot);
                if (!r.isSuccess()) {
                    throw new AnalysisException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                            "javac 编译失败：\n" + r.getOutputTail());
                }
            }
            // Gradle 等其他构建工具 → 静默跳过（用户应手动编译）
        } catch (AnalysisException e) {
            throw e;
        } catch (Exception e) {
            throw new AnalysisException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "编译探测失败：" + e.getMessage());
        }
    }

    /** 查找已有的编译产物目录：存在且有 .class 文件才认为有效 */
    private Path findExistingArtifacts(Path root) {
        Path[] candidates = new Path[] {
                root.resolve("target").resolve("classes"),
                root.resolve("build").resolve("classes"),
                root.resolve("bin"),
                root.resolve("build")
        };
        for (Path c : candidates) {
            if (Files.isDirectory(c) && hasClassFiles(c)) return c;
        }
        try (Stream<Path> walk = Files.walk(root, 3)) {
            return walk.filter(Files::isDirectory)
                    .filter(p -> {
                        String s = p.toString().replace('\\', '/');
                        return s.endsWith("/target/classes")
                                || s.endsWith("/build/classes")
                                || s.endsWith("/out/production");
                    })
                    .filter(this::hasClassFiles)
                    .findFirst().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /** 目录或子目录下是否有 .class 文件 */
    private boolean hasClassFiles(Path dir) {
        try (Stream<Path> walk = Files.walk(dir, 50)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName() != null
                            && p.getFileName().toString().endsWith(".class"))
                    .findFirst().isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    @PostMapping("/git/prepare")
    public GitPrepareStatus gitPrepare(@RequestBody GitPrepareRequest req) {
        return gitPrepareService.prepare(req);
    }

    @GetMapping("/git/prepare/{jobId}")
    public GitPrepareStatus gitPrepareStatus(@org.springframework.web.bind.annotation.PathVariable String jobId) {
        return gitPrepareService.status(jobId);
    }

    /** 查找最近一个未过期的 Git 任务（前端刷新后恢复进度条） */
    @GetMapping("/git/latest")
    public GitPrepareStatus gitLatest() {
        return gitPrepareService.latest();
    }

    @PostMapping("/report/excel")
    public ResponseEntity<byte[]> excel(@RequestBody AnalyzeRequest req) throws IOException {
        AnalysisResult result = null;
        // 批量分析展开某入口时：按缓存文件名直接加载，避免重新分析
        if (req.getCacheFileName() != null && !req.getCacheFileName().isEmpty()) {
            result = cacheService.loadByFileName(req.getProjectPath(), req.getCacheFileName()).orElse(null);
        }
        // 优先复用最近一次分析结果，避免重复分析导致客户端超时断开
        if (result == null) {
            result = analysisService.getLastResult(req.getProjectPath());
        }
        if (result == null) {
            result = analysisService.analyze(req);
        }
        String srcFilter = req.getFreqSourceFilter() == null ? "ALL" : req.getFreqSourceFilter();
        byte[] bytes = excelReportGenerator.generate(result, srcFilter, req.getProjectPath());
        StringBuilder name = new StringBuilder("callgraph_")
                .append(result.getClassName() == null
                        ? "entries_" + result.getGraph().getRoots().size()
                        : result.getClassName().replaceAll("[^\\w.]", "_"));
        if (result.getMethodName() != null) {
            name.append("_").append(result.getMethodName().replaceAll("[^\\w]", "_"));
        }
        name.append(".xlsx");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + URLEncoder.encode(name.toString(), "UTF-8"))
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    /** 导出某一个方法的完整调用方列表为 CSV */
    @GetMapping("/method-callers")
    public ResponseEntity<byte[]> exportMethodCallers(
            @RequestParam String method,
            @RequestParam(value = "source", required = false, defaultValue = "ALL") String sourceFilter) throws IOException {
        AnalysisResult result = analysisService.getLastResult(null);
        if (result == null) {
            throw new AnalysisException(org.springframework.http.HttpStatus.BAD_REQUEST, "请先执行一次分析");
        }
        List<com.spark.callgraph.service.dto.MethodFrequency> all = result.getMethodFrequency();
        if (all == null) {
            throw new AnalysisException(org.springframework.http.HttpStatus.BAD_REQUEST, "无方法调用次数数据");
        }
        com.spark.callgraph.service.dto.MethodFrequency target = null;
        for (com.spark.callgraph.service.dto.MethodFrequency mf : all) {
            if (mf.getMethod().equals(method)) {
                if (sourceFilter != null && !"ALL".equalsIgnoreCase(sourceFilter)
                        && !sourceFilter.equalsIgnoreCase(mf.getSource())) {
                    continue;
                }
                target = mf;
                break;
            }
        }
        if (target == null) {
            throw new AnalysisException(org.springframework.http.HttpStatus.NOT_FOUND, "方法未找到: " + method);
        }
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("调用方方法,行号\n");
        if (target.getCallers() != null) {
            for (com.spark.callgraph.service.dto.MethodCaller c : target.getCallers()) {
                csv.append("\"").append(c.getCaller().replace("\"", "\"\"")).append("\"")
                        .append(",").append(c.getLine() > 0 ? c.getLine() : "").append("\n");
            }
        }
        String safeName = method.replaceAll("[^\\w]", "_");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + URLEncoder.encode(safeName + "_callers.csv", "UTF-8"))
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv.toString().getBytes("UTF-8"));
    }
}
