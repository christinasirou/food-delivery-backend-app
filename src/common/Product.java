package common;

import java.io.Serializable;

public class Product implements Serializable {
    private String productName;
    private String productType;
    private String productImage;
    private int availableAmount;
    private int unitsSold;
    private double price;
    private boolean isActive;

    public Product(String productName, String productType,String productImage, int availableAmount, double price) {
        this.productName = productName;
        this.productType = productType;
        this.productImage = productImage;
        this.availableAmount = availableAmount;
        this.unitsSold = 0;
        this.price = price;
        this.isActive = true;
    }

    public synchronized boolean purchase(int quantity) {
        if (!isActive || quantity <= 0 || quantity > availableAmount) {
            return false;
        }
        availableAmount -= quantity;
        unitsSold += quantity;
        return true;
    }

    // Getters και Setters
    public String getProductName() { return productName; }
    public String getProductType() { return productType; }
    public String getProductImage() { return productImage; }
    public int getAvailableAmount() { return availableAmount; }
    public int getUnitsSold() { return unitsSold; }
    public double getPrice() { return price; }
    public boolean isActive() { return isActive; }

    public void setProductName(String name) { this.productName = name; }
    public void setProductType(String type) { this.productType = type; }
    public void setAvailableAmount(int amount) { this.availableAmount = amount; }
    public void setPrice(double price) { this.price = price; }
    public void setActive(boolean active) { this.isActive = active; }
    public void setUnitsSold(int unitsSold) { this.unitsSold = unitsSold; }
}