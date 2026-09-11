package com.sample.service;

import com.sample.model.Order;
import org.springframework.stereotype.Service;

@Service
public class WechatPayService implements PaymentService {

    @Override
    public String pay(Order order) {
        return "wechat-" + order.getAmount();
    }
}
