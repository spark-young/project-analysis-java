package com.sample.model;

public class Order {
    private Long id;
    private String product;
    private int amount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getProduct() { return product; }
    public void setProduct(String product) { this.product = product; }
    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }
}
