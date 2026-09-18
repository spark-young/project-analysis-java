package com.spark.projectanalysis.web;

import com.spark.projectanalysis.report.ExcelReportGenerator;
import com.spark.projectanalysis.service.AnalysisCacheService;
import com.spark.projectanalysis.service.AnalysisException;
import com.spark.projectanalysis.service.AnalysisService;
import com.spark.projectanalysis.service.BatchAnalyzeService;
import com.spark.projectanalysis.service.GitPrepareService;
import com.spark.projectanalysis.service.JavacCompileService;
import com.spark.projectanalysis.service.MavenCompileService;
import com.spark.projectanalysis.service.dto.AnalyzeRequest;
import com.spark.projectanalysis.service.dto.AnalysisResult;
import com.spark.projectanalysis.service.dto.BatchAnalyzeStatus;
import com.spark.projectanalysis.service.dto.BatchJobStartResponse;
import com.spark.projectanalysis.service.dto.EntryVerifyResult;
import com.spark.projectanalysis.service.dto.GitPrepareRequest;
import com.spark.projectanalysis.service.dto.GitPrepareStatus;
import com.spark.projectanalysis.service.dto.ProjectExcelRequest;
import com.spark.projectanalysis.service.dto.ProjectInfo;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * REST API。默认仅监听 127.0.0.1（见 application.yml），内网本机使用。
 */
@RestController
@RequestMapping("/api")
public class AnalysisController {

    private final AnalysisService analysisService;
    private final ExcelReportGenerator excelReportGenerator;
    private final GitPrepareService gitPrepareService;
    private final BatchAnalyzeService batchAnalyzeService;
    private final AnalysisCacheService cacheService;

    public AnalysisController(AnalysisService analysisService, ExcelReportGenerator excelReportGenerator,
                              GitPrepareService gitPrepareService,
                              BatchAnalyzeService batchAnalyzeService, AnalysisCacheService cacheService) {
        this.analysisService = analysisService;
        this.excelReportGenerator = excelReportGenerator;
        this.gitPrepareService = gitPrepareService;
        this.batchAnalyzeService = batchAnalyzeService;
        this.cacheService = cacheService;
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
    public EntryVerifyResult verify(@RequestParam("path") String path,
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
    public BatchJobStartResponse analyzeBatch(@RequestBody AnalyzeRequest req) {
        String jobId = batchAnalyzeService.startAsync(req);
        BatchJobStartResponse resp = new BatchJobStartResponse();
        resp.setJobId(jobId);
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
        StringBuilder name = new StringBuilder("callgraph_");
        String projectName = result.getProjectName();
        if (projectName != null && !projectName.isEmpty()) {
            name.append(projectName.replaceAll("[^\\w\\u4e00-\\u9fa5.-]", "_")).append("_");
        }
        name.append(result.getClassName() == null
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

    /** 项目级 Excel 导出：按缓存文件名加载全部入口，聚合生成一个多 Sheet 工作簿 */
    @PostMapping("/report/excel-project")
    public ResponseEntity<byte[]> excelProject(@RequestBody ProjectExcelRequest req) throws IOException {
        List<AnalysisResult> results = new ArrayList<>();
        if (req.getCacheFiles() != null) {
            for (String f : req.getCacheFiles()) {
                if (f == null || f.isEmpty()) continue;
                Optional<AnalysisResult> r = cacheService.loadByFileName(req.getProjectPath(), f);
                r.ifPresent(results::add);
            }
        }
        if (results.isEmpty()) {
            throw new AnalysisException(HttpStatus.BAD_REQUEST,
                    "没有可用于导出的入口缓存，请先执行批量分析并等待全量加载完成");
        }
        String srcFilter = req.getFreqSourceFilter() == null ? "ALL" : req.getFreqSourceFilter();
        byte[] bytes = excelReportGenerator.generateProject(results, srcFilter, req.getProjectPath());
        StringBuilder name = new StringBuilder("callgraph_project");
        String projectName = results.get(0).getProjectName();
        if (projectName != null && !projectName.isEmpty()) {
            name.append("_").append(projectName.replaceAll("[^\\w\\u4e00-\\u9fa5.-]", "_"));
        }
        name.append("_entries_").append(results.size()).append(".xlsx");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + URLEncoder.encode(name.toString(), "UTF-8"))
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }
}
