package com.demo;

public class Util {
    public static long now() {
        return System.currentTimeMillis();
    }

    public static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
