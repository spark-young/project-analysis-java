package com.dangdang.ddframe.job.api.simple;

import com.dangdang.ddframe.job.api.ElasticJob;
import com.dangdang.ddframe.job.api.ShardingContext;

/** 测试桩：Elastic-Job v2 简单作业接口。 */
public interface SimpleJob extends ElasticJob {
    void execute(ShardingContext shardingContext);
}
