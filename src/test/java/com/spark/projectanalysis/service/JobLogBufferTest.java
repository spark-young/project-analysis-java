package com.spark.projectanalysis.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 任务日志环形缓冲：上限裁剪、顺序、空白忽略、快照隔离。
 * 该缓冲由任务线程写、HTTP 线程读，故行为需确定且线程安全（synchronized）。
 */
class JobLogBufferTest {

    @Test
    void test_defaultMaxLines_is60() {
        JobLogBuffer buf = new JobLogBuffer();
        for (int i = 0; i < 100; i++) {
            buf.add("line-" + i);
        }
        assertEquals(60, JobLogBuffer.DEFAULT_MAX_LINES);
        assertEquals(JobLogBuffer.DEFAULT_MAX_LINES, buf.snapshot().size());
    }

    @Test
    void test_customMax_keepsMostRecentInOrder() {
        JobLogBuffer buf = new JobLogBuffer(3);
        buf.add("a");
        buf.add("b");
        buf.add("c");
        buf.add("d");

        // 只保留最近 3 行，且按时间正序（最旧 → 最新）
        assertEquals(List.of("b", "c", "d"), buf.snapshot());
    }

    @Test
    void test_nullAndBlankLinesIgnored() {
        JobLogBuffer buf = new JobLogBuffer(5);
        buf.add(null);
        buf.add("");
        buf.add("   ");
        buf.add("\t\n");

        assertTrue(buf.snapshot().isEmpty(), "空白/null 行不应进入缓冲");
    }

    @Test
    void test_linesTrimmed() {
        JobLogBuffer buf = new JobLogBuffer(5);
        buf.add("  hello  ");
        buf.add("\tworld\n");

        assertEquals(List.of("hello", "world"), buf.snapshot());
    }

    @Test
    void test_maxLinesClampedToAtLeastOne() {
        JobLogBuffer buf = new JobLogBuffer(0);
        buf.add("x");
        buf.add("y");

        assertEquals(List.of("y"), buf.snapshot(), "maxLines<1 应被钳制为 1");
    }

    @Test
    void test_snapshotIsIndependentCopy() {
        JobLogBuffer buf = new JobLogBuffer(5);
        buf.add("x");

        List<String> snap = buf.snapshot();
        snap.add("tampered");

        assertEquals(1, buf.snapshot().size(), "外部修改快照不应影响内部缓冲");
    }
}
