package com.demo;

public class EnglishGreeter implements Greeter {
    @Override
    public String greet(Model m) {
        Util.trim(m.getId());
        return "hello";
    }
}
