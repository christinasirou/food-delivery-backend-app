package common;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

public class Store implements Serializable {
    private String storeName;
    private double latitude;
    private double longitude;
    private String foodCategory;
    private int stars;
    private int noOfVotes;
    private String storeLogo;
    private List<Product> products;
    private String priceCategory;
    private double totalSales;
    private UUID requestId;

    public Store(String storeName, double latitude, double longitude, String foodCategory,
                 int stars, int noOfVotes, String storeLogo, List<Product> products, String priceCategory) {
        this.storeName = storeName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.foodCategory = foodCategory;
        this.stars = stars;
        this.noOfVotes = noOfVotes;
        this.storeLogo = storeLogo;
        this.products = products;
        this.priceCategory = priceCategory;
        this.totalSales = 0;
        this.requestId = null;
    }

    // Getters and Setters
    public double getTotalSales(double amount) { return totalSales; }
    public synchronized void addToTotalSales(double amount) {
        this.totalSales += amount;
    }

    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getFoodCategory() { return foodCategory; }
    public void setFoodCategory(String foodCategory) { this.foodCategory = foodCategory; }

    public int getStars() { return stars; }
    public void setStars(int stars) { this.stars = stars; }

    public int getNoOfVotes() { return noOfVotes; }
    public void setNoOfVotes(int noOfVotes) { this.noOfVotes = noOfVotes; }

    public String getStoreLogo() { return storeLogo; }
    public void setStoreLogo(String storeLogo) { this.storeLogo = storeLogo; }

    public List<Product> getProducts() { return products; }
    public void setProducts(List<Product> products) { this.products = products; }

    public String getPriceCategory() { return priceCategory; }
    public void setPriceCategory(String priceCategory) { this.priceCategory = priceCategory; }

    public UUID getRequestId() { return requestId; }
    public void setRequestId(UUID requestId) { this.requestId = requestId; }

    public void updateStars(int newRating) {
        int currentTotal = this.stars * this.noOfVotes;
        int newTotal = currentTotal + newRating;
        noOfVotes += 1;
        this.stars = newTotal / noOfVotes;
    }

    // Product management methods
    public void addProduct(Product product) {
        products.add(product);
        updatePriceCategory();
    }

    private void updatePriceCategory() {
        double totalPrice = 0;
        for (Product product : products) {
            totalPrice += product.getPrice();
        }
        double averagePrice = totalPrice / products.size();

        if (averagePrice <= 5) {
            priceCategory = "$";
        } else if (averagePrice <= 15) {
            priceCategory = "$$";
        } else {
            priceCategory = "$$$";
        }
    }
}