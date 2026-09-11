package com.sample.service;

import com.sample.model.Order;

public interface PaymentService {
    String pay(Order order);
}
