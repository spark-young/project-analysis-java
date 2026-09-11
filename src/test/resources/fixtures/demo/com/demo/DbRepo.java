package com.demo;

public class DbRepo implements Repo {
    @Override
    public void save(Model m) {
        Util.trim(m.getId());
    }
}
