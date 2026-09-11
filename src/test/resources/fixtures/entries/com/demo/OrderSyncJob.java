package com.demo;

import com.dangdang.ddframe.job.api.ShardingContext;
import com.dangdang.ddframe.job.api.simple.SimpleJob;

public class OrderSyncJob implements SimpleJob {

    @Override
    public void execute(ShardingContext shardingContext) {
    }
}
