package com.demo;

import com.demo.api.PayApi;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService(interfaceClass = PayApi.class)
public class PayRpcImpl implements PayApi {

    @Override
    public String pay(String order) {
        return "ok";
    }

    /** Dubbo 服务类的公共方法均为入口 */
    public String query(String order) {
        return "q";
    }
}
