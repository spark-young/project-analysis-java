package com.demo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OrderService {
    private final Greeter greeter = new EnglishGreeter();
    private final Repo repo = new DbRepo();

    public void place() {
        Model m = new Model();
        validate(m);
        repo.save(m);
        greeter.greet(m);
        long t = Util.now();
        String s = Util.trim("x");
        String s2 = Util.trim("y");
        Map<String, String> map = new HashMap<>();
        map.put("a", "b");
        Math.abs(-1);
        pay("cash");
        pay(1);
        List<Model> list = new ArrayList<>();
        list.add(m);
        list.forEach(x -> log(x));
    }

    public void validate(Model m) {
        Util.trim(m.getId());
    }

    public void log(Model m) {
        System.out.println(m);
    }

    public void pay(String type) {
        Util.trim(type);
    }

    public void pay(int count) {
        Util.now();
    }

    public void loop() {
        loop();
    }

    public void ping() {
        pong();
    }

    public void pong() {
        ping();
    }

    public String describe(AbstractBase base) {
        return base.format();
    }

    public void direct(DbRepo dbRepo) {
        dbRepo.save(new Model());
    }

    public void withLib(com.demo.lib.GreeterService lib) {
        com.demo.lib.LibModel lm = new com.demo.lib.LibModel();
        lib.serve(lm);
    }

    public void methodRef() {
        List<Model> list = new ArrayList<>();
        list.add(new Model());
        list.forEach(this::log);
    }
}
