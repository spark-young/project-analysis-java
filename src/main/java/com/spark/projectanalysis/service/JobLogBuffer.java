package com.spark.projectanalysis.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 任务日志环形缓冲：保留最近 N 行，供前端轮询展示（如 mvn 编译输出）。
 * 写入方是任务执行线程，读取方是 HTTP 线程，因此内部同步。
 */
public final class JobLogBuffer {

    /** 默认保留行数：够看清编译过程与报错，又不让轮询响应过大 */
    public static final int DEFAULT_MAX_LINES = 60;

    private final int maxLines;
    private final Deque<String> lines = new ArrayDeque<>();

    public JobLogBuffer() {
        this(DEFAULT_MAX_LINES);
    }

    public JobLogBuffer(int maxLines) {
        this.maxLines = Math.max(1, maxLines);
    }

    /** 追加一行（空白行忽略）；超过上限时丢弃最旧的行 */
    public synchronized void add(String line) {
        if (line == null) return;
        String s = line.trim();
        if (s.isEmpty()) return;
        lines.addLast(s);
        while (lines.size() > maxLines) {
            lines.removeFirst();
        }
    }

    /** 当前快照：最近 maxLines 行，按时间正序 */
    public synchronized List<String> snapshot() {
        return new ArrayList<>(lines);
    }
}
