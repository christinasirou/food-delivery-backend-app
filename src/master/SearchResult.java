package master;

import common.Product;
import common.Store;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class SearchResult {
    protected List<Store> results;
    protected boolean isComplete;
    private final Object lock;
    private final ObjectOutputStream clientOut;
    private final String clientAddress;
    private final Map<String, Object> searchCriteria;
    private final double clientLatitude;
    private final double clientLongitude;

    public SearchResult(ObjectOutputStream clientOut, String clientAddress, Map<String, Object> searchCriteria, double latitude, double longitude) {
        this.results = null;
        this.isComplete = false;
        this.lock = new Object();
        this.clientOut = clientOut;
        this.clientAddress = clientAddress;
        this.searchCriteria = searchCriteria;
        this.clientLatitude = latitude;
        this.clientLongitude = longitude;
    }

    public void storeResults(List<Store> results) {
        synchronized (lock) {
            System.out.println("Storing results for client " + clientAddress + " with criteria: " + searchCriteria);
            this.results = results;
            this.isComplete = true;
            lock.notifyAll();
        }
    }

    public void setResults(List<Store> results) {
        synchronized (lock) {
            System.out.println("Setting results for client " + clientAddress + " with criteria: " + searchCriteria);
            this.results = results;
            this.isComplete = true;

            // Send results directly to the client
            try {
                // Transform results into detailed maps
                List<Map<String, Object>> detailedStores = new ArrayList<>();
                for (Store store : results) {
                    Map<String, Object> storeDetails = new HashMap<>();
                    storeDetails.put("Store Name", store.getStoreName());
                    storeDetails.put("Food Category", store.getFoodCategory());
                    storeDetails.put("Price Category", store.getPriceCategory());
                    storeDetails.put("Rating", store.getStars() + " stars");
                    storeDetails.put("Location", String.format("(%.2f, %.2f)", store.getLatitude(), store.getLongitude()));

                    List<Map<String, Object>> productDetails = new ArrayList<>();
                    for (Product product : store.getProducts()) {
                        if (product.isActive()) {
                            Map<String, Object> productInfo = new HashMap<>();
                            productInfo.put("Name", product.getProductName());
                            productInfo.put("Type", product.getProductType());
                            productInfo.put("Price", String.format("$%.2f", product.getPrice()));
                            productInfo.put("Available", product.getAvailableAmount() + " units");
                            productDetails.add(productInfo);
                        }
                    }
                    storeDetails.put("Products", productDetails);
                    detailedStores.add(storeDetails);
                }

                // Create a formatted response
                StringBuilder formattedResponse = new StringBuilder();
                formattedResponse.append("\nSearch Results:\n");
                formattedResponse.append("==============\n\n");

                for (Map<String, Object> store : detailedStores) {
                    formattedResponse.append("Store: ").append(store.get("Store Name")).append("\n");
                    formattedResponse.append("Category: ").append(store.get("Food Category")).append("\n");
                    formattedResponse.append("Price Range: ").append(store.get("Price Category")).append("\n");
                    formattedResponse.append("Rating: ").append(store.get("Rating")).append("\n");
                    formattedResponse.append("Location: ").append(store.get("Location")).append("\n");

                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> products = (List<Map<String, Object>>) store.get("Products");
                    if (!products.isEmpty()) {
                        formattedResponse.append("\nAvailable Products:\n");
                        for (Map<String, Object> product : products) {
                            formattedResponse.append("  - ").append(product.get("Name"))
                                    .append(" (").append(product.get("Type")).append(")")
                                    .append(": ").append(product.get("Price"))
                                    .append(" - ").append(product.get("Available"))
                                    .append("\n");
                        }
                    }
                    formattedResponse.append("\n----------------------------------------\n\n");
                }

                if (detailedStores.isEmpty()) {
                    formattedResponse.append("No stores found matching your criteria.\n");
                }

                clientOut.writeObject(formattedResponse.toString());
                clientOut.flush();
            } catch (IOException e) {
                System.err.println("Error sending results to client " + clientAddress + ": " + e.getMessage());
            }

            lock.notifyAll();
        }
    }

    public List<Store> getResults(long timeout) throws InterruptedException {
        synchronized (lock) {
            System.out.println("Waiting for results with timeout: " + timeout + " for client " + clientAddress);
            if (!isComplete) {
                lock.wait(timeout);
            }
            System.out.println("Got results or timed out, isComplete: " + isComplete + " for client " + clientAddress);
            return results;
        }
    }
}
