package com.demo.lib;

public class GreeterService {
    public String serve(LibModel m) {
        return decorate(m.getName());
    }

    public String decorate(String s) {
        return "[" + s + "]";
    }
}
