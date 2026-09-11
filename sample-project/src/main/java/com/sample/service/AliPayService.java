package com.sample.service;

import com.sample.model.Order;
import org.springframework.stereotype.Service;

@Service
public class AliPayService implements PaymentService {

    @Override
    public String pay(Order order) {
        return "ali-" + order.getAmount();
    }
}
