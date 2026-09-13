package com.spark.callgraph.web;

import com.spark.callgraph.report.ExcelReportGenerator;
import com.spark.callgraph.service.AnalysisService;
import com.spark.callgraph.service.EntryScanService;
import com.spark.callgraph.service.GitPrepareService;
import com.spark.callgraph.service.dto.AnalyzeRequest;
import com.spark.callgraph.service.dto.AnalysisResult;
import com.spark.callgraph.service.dto.EntryScanResult;
import com.spark.callgraph.service.dto.GitPrepareRequest;
import com.spark.callgraph.service.dto.GitPrepareStatus;
import com.spark.callgraph.service.dto.ProjectInfo;
import com.spark.callgraph.service.dto.ScanRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;

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

    public AnalysisController(AnalysisService analysisService, ExcelReportGenerator excelReportGenerator,
                              EntryScanService entryScanService, GitPrepareService gitPrepareService) {
        this.analysisService = analysisService;
        this.excelReportGenerator = excelReportGenerator;
        this.entryScanService = entryScanService;
        this.gitPrepareService = gitPrepareService;
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

    @PostMapping("/analyze")
    public AnalysisResult analyze(@RequestBody AnalyzeRequest req) {
        return analysisService.analyze(req);
    }

    @PostMapping("/scan/entries")
    public EntryScanResult scanEntries(@RequestBody ScanRequest req) {
        return entryScanService.scan(req == null ? null : req.getProjectPath());
    }

    @PostMapping("/git/prepare")
    public GitPrepareStatus gitPrepare(@RequestBody GitPrepareRequest req) {
        return gitPrepareService.prepare(req);
    }

    @GetMapping("/git/prepare/{jobId}")
    public GitPrepareStatus gitPrepareStatus(@org.springframework.web.bind.annotation.PathVariable String jobId) {
        return gitPrepareService.status(jobId);
    }

    @PostMapping("/report/excel")
    public ResponseEntity<byte[]> excel(@RequestBody AnalyzeRequest req) throws IOException {
        AnalysisResult result = analysisService.analyze(req);
        String srcFilter = req.getFreqSourceFilter() == null ? "ALL" : req.getFreqSourceFilter();
        byte[] bytes = excelReportGenerator.generate(result, srcFilter);
        StringBuilder name = new StringBuilder("callgraph_")
                .append(result.getClassName() == null
                        ? "entries_" + result.getRoots().size()
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
}
