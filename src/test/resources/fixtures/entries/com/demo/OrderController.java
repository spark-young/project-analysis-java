package com.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @GetMapping("/list")
    public String list() {
        return "[]";
    }

    @PostMapping
    public String place(String order) {
        return "ok";
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
    public String remove(long id) {
        return "ok";
    }

    /** 无映射注解 —— 不应成为 REST 入口 */
    public String helper() {
        return "internal";
    }
}
