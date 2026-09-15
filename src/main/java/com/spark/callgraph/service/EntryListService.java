package com.spark.callgraph.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.spark.callgraph.service.dto.EntryList;
import com.spark.callgraph.service.dto.EntryList.EntryItem;
import com.spark.callgraph.service.dto.EntryScanResult;
import com.spark.callgraph.service.dto.EntryScanResult.EntryDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 项目级交易入口清单管理（Step 2）。
 * <p>
 * 数据模型：
 *   confirmed —— 主体，每次进项目先展示这些
 *   excluded  —— 已排除，留痕可捞回
 * 核心原则：已确认清单是主体，自动扫描只产出"候选"让用户决定是否合并。
 */
@Service
public class EntryListService {

    private static final Logger log = LoggerFactory.getLogger(EntryListService.class);
    private static final String FILE_NAME = "entries.json";

    private final ObjectMapper mapper;

    public EntryListService() {
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    // ------------------------------------------------------------------
    // 读写
    // ------------------------------------------------------------------

    /** 加载；文件不存在时返回空清单（非 null，方便前端直接用） */
    public EntryList load(String projectPath) {
        if (projectPath == null || projectPath.isEmpty()) return empty(projectPath);
        Path file = file(projectPath);
        if (!Files.isRegularFile(file)) return empty(projectPath);
        try {
            EntryList list = mapper.readValue(file.toFile(), EntryList.class);
            if (list.getProjectPath() == null) list.setProjectPath(projectPath);
            log.info("[入口清单] 加载成功：confirmed={}, excluded={}",
                    list.getConfirmed().size(), list.getExcluded().size());
            return list;
        } catch (Exception e) {
            log.warn("[入口清单] 加载失败，返回空：{}", e.getMessage());
            return empty(projectPath);
        }
    }

    /** 保存（原子写：先写 .tmp 再 rename） */
    public void save(String projectPath, EntryList list) {
        if (list == null) return;
        list.setProjectPath(projectPath);
        Path dir = cacheDir(projectPath);
        try {
            Files.createDirectories(dir);
            Path tmp = dir.resolve(FILE_NAME + ".tmp");
            Path target = dir.resolve(FILE_NAME);
            mapper.writeValue(tmp.toFile(), list);
            Files.move(tmp, target,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            log.info("[入口清单] 保存成功：confirmed={}, excluded={}",
                    list.getConfirmed().size(), list.getExcluded().size());
        } catch (IOException e) {
            log.error("[入口清单] 保存失败: {}", e.getMessage(), e);
            throw new RuntimeException("入口清单保存失败: " + e.getMessage(), e);
        }
    }

    /** 判断项目是否已有已确认的入口清单 */
    public boolean hasConfirmed(String projectPath) {
        EntryList list = load(projectPath);
        return !list.getConfirmed().isEmpty();
    }

    // ------------------------------------------------------------------
    // 扫描 → 候选（返回"不在 confirmed 也不在 excluded"的新增项）
    // ------------------------------------------------------------------

    /**
     * 扫描 diff 结果。
     * candidates —— 不在清单中的新增候选；
     * existed   —— 已在清单（confirmed + excluded）中的数量，用于"新增 X 个 / 已存在 Y 个"提示。
     */
    public static class ScanDiffResult {
        public final List<EntryItem> candidates;
        public final int existed;

        public ScanDiffResult(List<EntryItem> candidates, int existed) {
            this.candidates = candidates;
            this.existed = existed;
        }
    }

    /**
     * 把自动扫描结果与已保存清单对比，返回新增候选与已存在数。
     * 不修改持久化数据——候选由前端展示让用户决定是否合并。
     */
    public ScanDiffResult diffScanCandidates(String projectPath, EntryScanResult scanResult) {
        List<EntryItem> all = new ArrayList<>();
        if (scanResult != null && scanResult.getGroups() != null) {
            for (EntryScanResult.Group g : scanResult.getGroups()) {
                if (g.getEntries() == null) continue;
                for (EntryDto dto : g.getEntries()) {
                    all.add(fromDto(dto, g.getType()));
                }
            }
        }
        ScanDiffResult diff = diffItems(projectPath, all);
        log.info("[入口清单] 扫描 diff：新增候选 {} 条（已存在 {} 条）",
                diff.candidates.size(), diff.existed);
        return diff;
    }

    /**
     * 从全量扫描结果中提取类名匹配的入口方法（精确匹配优先，其次包含匹配），
     * 用于"手动扫描"场景：source 统一标记为 MANUAL，group 保留原始类型（REST/DUBBO/RULE...）。
     */
    public List<EntryItem> extractByClass(EntryScanResult scanResult, String className) {
        List<EntryItem> matched = new ArrayList<>();
        if (scanResult == null || scanResult.getGroups() == null
                || className == null || className.trim().isEmpty()) return matched;
        String needle = className.trim();
        for (EntryScanResult.Group g : scanResult.getGroups()) {
            if (g.getEntries() == null) continue;
            for (EntryDto dto : g.getEntries()) {
                String cn = dto.getClassName() == null ? "" : dto.getClassName();
                if (cn.equals(needle) || cn.contains(needle)) {
                    EntryItem item = fromDto(dto, g.getType());
                    item.setSource("MANUAL");
                    matched.add(item);
                }
            }
        }
        return matched;
    }

    /** 通用 diff：items 与已保存清单对比，返回新增候选与已存在数 */
    public ScanDiffResult diffItems(String projectPath, List<EntryItem> items) {
        EntryList saved = load(projectPath);
        Set<String> existingKeys = new HashSet<>();
        for (EntryItem e : saved.getConfirmed()) existingKeys.add(e.key());
        for (EntryItem e : saved.getExcluded()) existingKeys.add(e.key());

        List<EntryItem> candidates = new ArrayList<>();
        int existed = 0;
        if (items != null) {
            for (EntryItem item : items) {
                if (existingKeys.contains(item.key())) {
                    existed++;
                } else {
                    candidates.add(item);
                }
            }
        }
        return new ScanDiffResult(candidates, existed);
    }

    // ------------------------------------------------------------------
    // 变更操作（都 load → 修改 → save）
    // ------------------------------------------------------------------

    /** 把候选合并进 confirmed（按 key 去重），返回 {added, existed} */
    public Map<String, Integer> mergeCandidates(String projectPath, List<EntryItem> candidates) {
        Map<String, Integer> result = new HashMap<>();
        result.put("added", 0);
        result.put("existed", 0);
        if (candidates == null || candidates.isEmpty()) return result;
        EntryList list = load(projectPath);
        Set<String> keys = new HashSet<>();
        for (EntryItem e : list.getConfirmed()) keys.add(e.key());
        int added = 0, existed = 0;
        for (EntryItem c : candidates) {
            if (!keys.contains(c.key())) {
                list.getConfirmed().add(c);
                keys.add(c.key());
                added++;
            } else {
                existed++;
            }
        }
        save(projectPath, list);
        result.put("added", added);
        result.put("existed", existed);
        return result;
    }

    /** 手动添加一个入口（直接进 confirmed），返回 true 表示新增，false 表示已存在 */
    public boolean addManual(String projectPath, String className, String methodName, String descriptor) {
        EntryList list = load(projectPath);
        EntryItem item = new EntryItem(className, methodName, descriptor, "MANUAL", "MANUAL");
        for (EntryItem c : list.getConfirmed()) if (c.key().equals(item.key())) return false;
        for (EntryItem x : list.getExcluded()) if (x.key().equals(item.key())) {
            // 在 excluded 里 → 移回 confirmed
            list.getExcluded().removeIf(ex -> ex.key().equals(item.key()));
            list.getConfirmed().add(item);
            save(projectPath, list);
            return true;
        }
        list.getConfirmed().add(item);
        save(projectPath, list);
        return true;
    }

    /** 批量手动添加（直接进 confirmed），返回 {added, existed}。
     *  confirmed 中已存在 → 记为 existed；
     *  excluded 中已存在 → 移回 confirmed 记为 added（用户主动捞回） */
    public Map<String, Integer> addManualBatch(String projectPath, List<EntryItem> items) {
        Map<String, Integer> result = new HashMap<>();
        result.put("added", 0);
        result.put("existed", 0);
        if (items == null || items.isEmpty()) return result;
        EntryList list = load(projectPath);
        Set<String> confirmedKeys = new HashSet<>();
        for (EntryItem e : list.getConfirmed()) confirmedKeys.add(e.key());
        Set<String> excludedKeys = new HashSet<>();
        for (EntryItem e : list.getExcluded()) excludedKeys.add(e.key());

        int added = 0, existed = 0;
        for (EntryItem item : items) {
            if (item.getClassName() == null || item.getMethodName() == null) continue;
            if (item.getSource() == null) item.setSource("MANUAL");
            if (item.getGroup() == null) item.setGroup("MANUAL");
            String key = item.key();
            if (confirmedKeys.contains(key)) {
                existed++;
            } else if (excludedKeys.contains(key)) {
                list.getExcluded().removeIf(ex -> ex.key().equals(key));
                list.getConfirmed().add(item);
                confirmedKeys.add(key);
                excludedKeys.remove(key);
                added++;
            } else {
                list.getConfirmed().add(item);
                confirmedKeys.add(key);
                added++;
            }
        }
        save(projectPath, list);
        result.put("added", added);
        result.put("existed", existed);
        return result;
    }

    /** 排除一个入口：从 confirmed 移到 excluded */
    public boolean exclude(String projectPath, String key, String reason) {
        EntryList list = load(projectPath);
        EntryItem target = null;
        for (EntryItem e : list.getConfirmed()) {
            if (e.key().equals(key)) { target = e; break; }
        }
        if (target == null) return false;
        list.getConfirmed().remove(target);
        target.setExcludeReason(reason == null ? "用户排除" : reason);
        list.getExcluded().add(target);
        save(projectPath, list);
        return true;
    }

    /** 批量排除：从 confirmed 移到 excluded，逐条写入原因，返回实际排除条数 */
    public int excludeBatch(String projectPath, List<Map<String, String>> items) {
        if (items == null || items.isEmpty()) return 0;
        EntryList list = load(projectPath);
        int count = 0;
        for (Map<String, String> item : items) {
            String key = item == null ? null : item.get("key");
            if (key == null || key.isEmpty()) continue;
            EntryItem target = null;
            for (EntryItem e : list.getConfirmed()) {
                if (e.key().equals(key)) { target = e; break; }
            }
            if (target == null) continue;
            list.getConfirmed().remove(target);
            target.setExcludeReason(item.get("reason") == null ? "用户排除" : item.get("reason"));
            list.getExcluded().add(target);
            count++;
        }
        if (count > 0) save(projectPath, list);
        return count;
    }

    /** 恢复一个入口：从 excluded 移回 confirmed */
    public boolean restore(String projectPath, String key) {
        EntryList list = load(projectPath);
        EntryItem target = null;
        for (EntryItem e : list.getExcluded()) {
            if (e.key().equals(key)) { target = e; break; }
        }
        if (target == null) return false;
        list.getExcluded().remove(target);
        target.setExcludeReason(null);
        list.getConfirmed().add(target);
        save(projectPath, list);
        return true;
    }

    /** 彻底删除（从 confirmed 或 excluded 里移除） */
    public int delete(String projectPath, String key) {
        EntryList list = load(projectPath);
        int removed = 0;
        if (list.getConfirmed().removeIf(e -> e.key().equals(key))) removed++;
        if (list.getExcluded().removeIf(e -> e.key().equals(key))) removed++;
        if (removed > 0) save(projectPath, list);
        return removed;
    }

    /** 清空所有 */
    public void clear(String projectPath) {
        save(projectPath, empty(projectPath));
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    private static EntryList empty(String projectPath) {
        EntryList e = new EntryList();
        e.setProjectPath(projectPath);
        return e;
    }

    private static EntryItem fromDto(EntryDto dto, String group) {
        return new EntryItem(
                dto.getClassName(),
                dto.getMethodName(),
                dto.getMethodDescriptor(),
                "AUTO_SCAN",
                group
        );
    }

    private static Path cacheDir(String projectPath) {
        return Paths.get(projectPath, ".callgraph");
    }

    private static Path file(String projectPath) {
        return cacheDir(projectPath).resolve(FILE_NAME);
    }
}
