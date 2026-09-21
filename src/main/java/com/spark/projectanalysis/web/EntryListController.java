package com.spark.projectanalysis.web;

import com.spark.projectanalysis.service.ClassMetadataService;
import com.spark.projectanalysis.service.EntryListService;
import com.spark.projectanalysis.service.EntryScanService;
import com.spark.projectanalysis.service.ProjectRegistry;
import com.spark.projectanalysis.service.dto.EntryAddBatchResponse;
import com.spark.projectanalysis.service.dto.EntryAddResponse;
import com.spark.projectanalysis.service.dto.EntryExcludedResponse;
import com.spark.projectanalysis.service.dto.EntryList;
import com.spark.projectanalysis.service.dto.EntryList.EntryItem;
import com.spark.projectanalysis.service.dto.EntryMergeResponse;
import com.spark.projectanalysis.service.dto.EntryOkResponse;
import com.spark.projectanalysis.service.dto.EntryRemovedResponse;
import com.spark.projectanalysis.service.dto.EntryScanDiffResponse;
import com.spark.projectanalysis.service.dto.EntryScanManualResponse;
import com.spark.projectanalysis.service.dto.EntryScanResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 项目级交易入口清单管理（Step 2）。
 * <p>
 * 核心接口：
 *   GET    /api/projects/{id}/entries              → 取已保存清单（confirmed + excluded）
 *   POST   /api/projects/{id}/entries/scan         → 自动扫描 → 返回候选（不自动合并）
 *   POST   /api/projects/{id}/entries/merge        → 把候选合并进 confirmed
 *   POST   /api/projects/{id}/entries/add          → 手动添加一个入口
 *   PUT    /api/projects/{id}/entries/exclude      → 排除一个入口（confirmed → excluded）
 *   PUT    /api/projects/{id}/entries/restore      → 恢复一个入口（excluded → confirmed）
 *   DELETE /api/projects/{id}/entries              → 彻底删除一条
 */
@RestController
@RequestMapping("/api/projects")
public class EntryListController {

    private static final Logger log = LoggerFactory.getLogger(EntryListController.class);

    private final EntryListService entryListService;
    private final EntryScanService entryScanService;
    private final ClassMetadataService classMetadataService;
    private final ProjectRegistry registry;

    public EntryListController(EntryListService entryListService,
                               EntryScanService entryScanService,
                               ClassMetadataService classMetadataService,
                               ProjectRegistry registry) {
        this.entryListService = entryListService;
        this.entryScanService = entryScanService;
        this.classMetadataService = classMetadataService;
        this.registry = registry;
    }

    private String resolvePath(String id) {
        ProjectRegistry.RegisteredProject p = registry.get(id);
        if (p == null) {
            throw new RuntimeException("项目不存在: " + id);
        }
        return p.projectPath;
    }

    /** 取已保存清单 */
    @GetMapping("/{id}/entries")
    public EntryList getEntries(@PathVariable String id) {
        return entryListService.load(resolvePath(id));
    }

    /** 自动扫描 → 返回新增候选 + 已存在数（不自动合并，让用户决定）。可传 profileId 指定扫描方案 */
    @PostMapping("/{id}/entries/scan")
    public EntryScanDiffResponse scanAndDiff(@PathVariable String id,
                                             @RequestBody(required = false) Map<String, String> body) {
        String path = resolvePath(id);
        String profileId = body == null ? null : body.get("profileId");
        EntryScanResult scanResult = (profileId != null && !profileId.isEmpty())
                ? entryScanService.scan(path, profileId)
                : entryScanService.scan(path);
        EntryListService.ScanDiffResult diff = entryListService.diffScanCandidates(path, scanResult);

        EntryScanDiffResponse resp = new EntryScanDiffResponse();
        resp.setCandidates(diff.candidates);
        resp.setExisted(diff.existed);
        resp.setScanTotalGroups(scanResult.getGroups() == null ? 0 : scanResult.getGroups().size());
        resp.setScanTotalEntries(scanResult.getGroups() == null ? 0 :
                scanResult.getGroups().stream()
                        .mapToInt(g -> g.getEntries() == null ? 0 : g.getEntries().size()).sum());
        resp.setProjectPath(path);
        log.info("[入口] 项目 {} 扫描完成：候选 {} 条，已存在 {} 条",
                id, diff.candidates.size(), diff.existed);
        return resp;
    }

    /** 把候选合并进 confirmed，返回 {added, existed} */
    @PostMapping("/{id}/entries/merge")
    public EntryMergeResponse merge(@PathVariable String id,
                                    @RequestBody List<EntryItem> candidates) {
        String path = resolvePath(id);
        Map<String, Integer> r = entryListService.mergeCandidates(path, candidates);
        EntryMergeResponse resp = new EntryMergeResponse();
        resp.setAdded(r.get("added"));
        resp.setExisted(r.get("existed"));
        return resp;
    }

    /**
     * 手动扫描：把用户填的「类名 / 包名」下面的全部方法枚举为候选，供勾选加入清单。
     * <p>
     * 与「自动扫描」不同，这里**不套用扫描策略**——手动添加的语义是"用户已经知道要哪些方法"，
     * 所以只做枚举（排除构造器与编译器合成方法），是否算入口由用户勾选决定。
     * 包名只列本层类，不递归子包（产品约定）。
     */
    @PostMapping("/{id}/entries/scan-manual")
    public EntryScanManualResponse scanManual(@PathVariable String id,
                                              @RequestBody(required = false) Map<String, String> body) {
        String path = resolvePath(id);
        String query = body == null ? null : body.get("className");
        String methodName = body == null ? null : body.get("methodName");

        ClassMetadataService.MethodQuery found = classMetadataService.methodsUnder(path, query);
        String needle = methodName == null ? "" : methodName.trim();
        List<EntryItem> matched = new ArrayList<>();
        for (ClassMetadataService.MethodRef m : found.getMethods()) {
            if (!needle.isEmpty() && !needle.equals(m.getMethodName())) continue;
            matched.add(EntryListService.manualEntry(m.getClassName(), m.getMethodName(), m.getDescriptor()));
        }
        EntryListService.ScanDiffResult diff = entryListService.diffItems(path, matched);

        EntryScanManualResponse resp = new EntryScanManualResponse();
        resp.setCandidates(diff.candidates);
        resp.setExisted(diff.existed);
        resp.setTotal(matched.size());
        resp.setMode(found.getMode());
        resp.setResolvedName(found.getResolvedName());
        resp.setTruncated(found.isTruncated());
        resp.setProjectPath(path);
        log.info("[入口] 手动扫描 {}「{}」({})：匹配 {} 条，新增候选 {} 条，已存在 {} 条",
                id, found.getResolvedName(), found.getMode(),
                matched.size(), diff.candidates.size(), diff.existed);
        return resp;
    }

    /** 批量手动添加（直接进 confirmed），返回 {added, existed, list} */
    @PostMapping("/{id}/entries/add-batch")
    public EntryAddBatchResponse addManualBatch(@PathVariable String id,
                                                @RequestBody List<EntryItem> items) {
        String path = resolvePath(id);
        Map<String, Integer> r = entryListService.addManualBatch(path, items);
        EntryAddBatchResponse resp = new EntryAddBatchResponse();
        resp.setAdded(r.get("added"));
        resp.setExisted(r.get("existed"));
        resp.setList(entryListService.load(path));
        return resp;
    }

    /** 手动添加一个入口（路径 B） */
    @PostMapping("/{id}/entries/add")
    public EntryAddResponse addManual(@PathVariable String id,
                                      @RequestBody Map<String, String> body) {
        String path = resolvePath(id);
        boolean added = entryListService.addManual(path,
                body.get("className"),
                body.get("methodName"),
                body.get("descriptor"));
        EntryAddResponse resp = new EntryAddResponse();
        resp.setAdded(added);
        resp.setList(entryListService.load(path));
        return resp;
    }

    /** 排除一个入口（confirmed → excluded） */
    @PostMapping("/{id}/entries/exclude")
    public EntryOkResponse exclude(@PathVariable String id,
                                   @RequestBody Map<String, String> body) {
        String path = resolvePath(id);
        boolean ok = entryListService.exclude(path, body.get("key"), body.get("reason"));
        EntryOkResponse resp = new EntryOkResponse();
        resp.setOk(ok);
        return resp;
    }

    /** 批量排除（confirmed → excluded），body 为 [{key, reason}, ...] */
    @PostMapping("/{id}/entries/exclude/batch")
    public EntryExcludedResponse excludeBatch(@PathVariable String id,
                                              @RequestBody List<Map<String, String>> items) {
        String path = resolvePath(id);
        int excluded = entryListService.excludeBatch(path, items);
        EntryExcludedResponse resp = new EntryExcludedResponse();
        resp.setExcluded(excluded);
        return resp;
    }

    /** 恢复一个入口（excluded → confirmed） */
    @PostMapping("/{id}/entries/restore")
    public EntryOkResponse restore(@PathVariable String id,
                                   @RequestBody Map<String, String> body) {
        String path = resolvePath(id);
        boolean ok = entryListService.restore(path, body.get("key"));
        EntryOkResponse resp = new EntryOkResponse();
        resp.setOk(ok);
        return resp;
    }

    /** 彻底删除一条 */
    @DeleteMapping("/{id}/entries")
    public EntryRemovedResponse delete(@PathVariable String id,
                                       @RequestParam String key) {
        String path = resolvePath(id);
        int removed = entryListService.delete(path, key);
        EntryRemovedResponse resp = new EntryRemovedResponse();
        resp.setRemoved(removed);
        return resp;
    }
}
