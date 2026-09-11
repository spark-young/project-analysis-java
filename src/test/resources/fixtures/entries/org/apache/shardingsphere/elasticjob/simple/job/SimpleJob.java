package org.apache.shardingsphere.elasticjob.simple.job;

import org.apache.shardingsphere.elasticjob.api.ShardingContext;

/** 测试桩：ElasticJob v3（ShardingSphere）简单作业接口。 */
public interface SimpleJob {
    void execute(ShardingContext shardingContext);
}
