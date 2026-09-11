package com.sample.repo;

import com.sample.model.Order;
import org.springframework.stereotype.Repository;

@Repository
public class MysqlOrderRepo implements OrderRepo {

    @Override
    public void save(Order order) {
        String id = String.valueOf(order.getId());
        order.setProduct(id);
    }
}
