package com.spark.callgraph.web;

import com.spark.callgraph.service.EntryListService;
import com.spark.callgraph.service.EntryScanService;
import com.spark.callgraph.service.ProjectRegistry;
import com.spark.callgraph.service.dto.EntryList;
import com.spark.callgraph.service.dto.EntryList.EntryItem;
import com.spark.callgraph.service.dto.EntryScanResult;
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

import java.util.HashMap;
import java.util.LinkedHashMap;
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
    private final ProjectRegistry registry;

    public EntryListController(EntryListService entryListService,
                               EntryScanService entryScanService,
                               ProjectRegistry registry) {
        this.entryListService = entryListService;
        this.entryScanService = entryScanService;
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

    /** 自动扫描 → 返回新增候选（不自动合并，让用户决定） */
    @PostMapping("/{id}/entries/scan")
    public Map<String, Object> scanAndDiff(@PathVariable String id) {
        String path = resolvePath(id);
        EntryScanResult scanResult = entryScanService.scan(path);
        List<EntryItem> candidates = entryListService.diffScanCandidates(path, scanResult);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("candidates", candidates);
        resp.put("scanTotalGroups", scanResult.getGroups() == null ? 0 : scanResult.getGroups().size());
        resp.put("scanTotalEntries", scanResult.getGroups() == null ? 0 :
                scanResult.getGroups().stream()
                        .mapToInt(g -> g.getEntries() == null ? 0 : g.getEntries().size()).sum());
        resp.put("projectPath", path);
        log.info("[入口] 项目 {} 扫描完成：候选 {} 条", id, candidates.size());
        return resp;
    }

    /** 把候选合并进 confirmed */
    @PostMapping("/{id}/entries/merge")
    public Map<String, Object> merge(@PathVariable String id,
                                     @RequestBody List<EntryItem> candidates) {
        String path = resolvePath(id);
        int added = entryListService.mergeCandidates(path, candidates);
        Map<String, Object> resp = new HashMap<>();
        resp.put("added", added);
        return resp;
    }

    /** 手动添加一个入口（路径 B） */
    @PostMapping("/{id}/entries/add")
    public Map<String, Object> addManual(@PathVariable String id,
                                         @RequestBody Map<String, String> body) {
        String path = resolvePath(id);
        boolean added = entryListService.addManual(path,
                body.get("className"),
                body.get("methodName"),
                body.get("descriptor"));
        Map<String, Object> resp = new HashMap<>();
        resp.put("added", added);
        resp.put("list", entryListService.load(path));
        return resp;
    }

    /** 排除一个入口（confirmed → excluded） */
    @PostMapping("/{id}/entries/exclude")
    public Map<String, Object> exclude(@PathVariable String id,
                                       @RequestBody Map<String, String> body) {
        String path = resolvePath(id);
        boolean ok = entryListService.exclude(path, body.get("key"), body.get("reason"));
        Map<String, Object> resp = new HashMap<>();
        resp.put("ok", ok);
        return resp;
    }

    /** 恢复一个入口（excluded → confirmed） */
    @PostMapping("/{id}/entries/restore")
    public Map<String, Object> restore(@PathVariable String id,
                                       @RequestBody Map<String, String> body) {
        String path = resolvePath(id);
        boolean ok = entryListService.restore(path, body.get("key"));
        Map<String, Object> resp = new HashMap<>();
        resp.put("ok", ok);
        return resp;
    }

    /** 彻底删除一条 */
    @DeleteMapping("/{id}/entries")
    public Map<String, Object> delete(@PathVariable String id,
                                      @RequestParam String key) {
        String path = resolvePath(id);
        int removed = entryListService.delete(path, key);
        Map<String, Object> resp = new HashMap<>();
        resp.put("removed", removed);
        return resp;
    }
}
