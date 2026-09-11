package com.demo;

public abstract class AbstractBase {
    public abstract String describe();

    public String format() {
        return describe() + "!";
    }
}
