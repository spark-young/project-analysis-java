package com.sample.service;

import com.sample.model.Order;
import com.sample.repo.OrderRepo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepo orderRepo;
    private final PaymentService paymentService;

    public OrderService(OrderRepo orderRepo, PaymentService paymentService) {
        this.orderRepo = orderRepo;
        this.paymentService = paymentService;
    }

    public String place(Order order) {
        validate(order);                                  // 自身虚调用
        orderRepo.save(order);                            // 接口分派 -> MysqlOrderRepo
        String payResult = paymentService.pay(order);     // 接口分派 -> Ali/Wechat 两个实现
        String title = StringUtils.abbreviate(order.getProduct(), 20); // 依赖方法(commons-lang3)
        List<String> logs = new ArrayList<>();
        logs.add(title);
        logs.forEach(line -> log(line));                  // lambda
        return payResult;
    }

    public void validate(Order order) {
        if (order.getAmount() <= 0) {
            throw new IllegalArgumentException("amount <= 0");
        }
    }

    public void log(String line) {
        System.out.println(line);
    }

    public String cancel(Long orderId) {
        Order order = new Order();
        order.setId(orderId);
        return place(order);                              // 链式调用
    }
}
