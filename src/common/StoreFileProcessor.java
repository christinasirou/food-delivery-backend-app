package common;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class StoreFileProcessor {
    public static Store processStoreFile(String jsonFilePath) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(jsonFilePath)));
        JSONObject json = new JSONObject(content);

        // Extract store information
        String storeName = json.getString("StoreName");
        double latitude = json.getDouble("Latitude");
        double longitude = json.getDouble("Longitude");
        String foodCategory = json.getString("FoodCategory");
        int stars = json.getInt("Stars");
        int noOfVotes = json.getInt("NoOfVotes");
        String storeLogo = json.getString("StoreLogo");

        // Process products
        List<Product> products = new ArrayList<>();
        JSONArray productsArray = json.getJSONArray("Products");
        double totalPrice = 0;

        for (int i = 0; i < productsArray.length(); i++) {
            JSONObject productJson = productsArray.getJSONObject(i);
            Product product = new Product(
                    productJson.getString("ProductName"),
                    productJson.getString("ProductType"),
                    productJson.getString("ProductImage"),
                    productJson.getInt("Available Amount"),
                    productJson.getDouble("Price")
            );
            products.add(product);
            totalPrice += product.getPrice();
        }

        // Calculate price category
        double averagePrice = totalPrice / products.size();
        String priceCategory;
        if (averagePrice <= 5) {
            priceCategory = "$";
        } else if (averagePrice <= 15) {
            priceCategory = "$$";
        } else {
            priceCategory = "$$$";
        }

        // Create and return store
        return new Store(storeName, latitude, longitude, foodCategory,
                stars, noOfVotes, storeLogo, products, priceCategory);
    }

} 