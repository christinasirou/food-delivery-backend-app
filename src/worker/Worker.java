package worker;
import java.io.*;
import java.net.*;
import java.util.*;
import common.*;
import common.ConfigLoader;

public class Worker {

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java Worker <master_ip> <port>");
            return;
        }

        String masterIP = args[0];
        int port = Integer.parseInt(args[1]);

        try {
            // Get the worker's local IP address
            String localIP = InetAddress.getLocalHost().getHostAddress();
            System.out.println("Worker's local IP: " + localIP);

            try (ServerSocket serverSocket = new ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"))) {
                System.out.println("Worker started on port " + port);
                System.out.println("Connected to Master at " + masterIP);

                while (true) {
                    Socket socket = serverSocket.accept();
                    new Thread(new WorkerHandler(socket, port, masterIP, localIP)).start();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class WorkerHandler implements Runnable {
    private final Socket socket;
    private final int port;
    private final String masterIP;
    private final String localIP;
    private static final int REDUCER_PORT = ConfigLoader.getInt("REDUCER_PORT", 7000);
    private static final Map<String, Store> storeMap = new HashMap<>();

    private static Store getStore(String name) {
        return storeMap.get(name);
    }

    public WorkerHandler(Socket socket, int port, String masterIP, String localIP) {
        this.socket = socket;
        this.port = port;
        this.masterIP = masterIP;
        this.localIP = localIP;
    }

    @Override
    public void run() {
        try (
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())
        ) {
            String command = (String) in.readObject();
            Object request = in.readObject();

            switch (command) {
                // Manager actions
                case "register" -> {
                    Store store = (Store) request;
                    if (!storeMap.containsKey(store.getStoreName())) {
                        synchronized (storeMap) {
                            storeMap.put(store.getStoreName(), store);
                        }
                        out.writeObject("Store registered: " + store.getStoreName());
                    } else {
                        out.writeObject("Store already exists: " + store.getStoreName());
                    }
                }

                case "update" -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> updates = (Map<String, Object>) request;
                    String storeName = (String) updates.get("storeName");
                    String productName = (String) updates.get("productName");

                    Store store = getStore(storeName);
                    if (store == null) {
                        out.writeObject("Error: Store not found");
                        break;
                    }

                    // Find the product in the store
                    Product product = null;
                    for (Product p : store.getProducts()) {
                        if (p.getProductName().equals(productName)) {
                            product = p;
                            break;
                        }
                    }

                    if (product == null) {
                        out.writeObject("Error: Product not found in store");
                        break;
                    }

                    // Update product attributes
                    if (updates.containsKey("price")) {
                        product.setPrice((Double) updates.get("price"));

                    }
                    if (updates.containsKey("quantity")) {
                        product.setAvailableAmount((Integer) updates.get("quantity"));
                    }

                    // If this is a removal request
                    if (updates.containsKey("remove") && updates.get("remove").equals(true)) {
                        product.setActive(false);
                        out.writeObject("Product deactivated successfully");
                    } else {
                        out.writeObject("Product updated successfully");
                    }
                }

                case "add_product" -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> productInfo = (Map<String, Object>) request;
                    String storeName = (String) productInfo.get("storeName");
                    String productName = (String) productInfo.get("productName");
                    String productImage = (String) productInfo.get("productImage");
                    String productType = (String) productInfo.get("productType");
                    int availableAmount = (Integer) productInfo.get("availableAmount");
                    double price = (Double) productInfo.get("price");

                    Store store = getStore(storeName);
                    if (store == null) {
                        out.writeObject("Error: Store not found");
                        break;
                    }

                    // Check if product already exists
                    for (Product p : store.getProducts()) {
                        if (p.getProductName().equals(productName)) {
                            out.writeObject("Error: Product already exists in store");
                            break;
                        }
                    }

                    // Create and add new product
                    Product newProduct = new Product(productName, productType, productImage,availableAmount, price);
                    store.addProduct(newProduct);
                    out.writeObject("Product added successfully");
                }

                case "sales_by_product" -> {
                    String storeName = (String) request;
                    Store store = getStore(storeName);
                    Map<String, Object> products;
                    if (store == null) {
                        out.writeObject("Error: Store not found");
                    }
                    synchronized (store) {
                        products = new HashMap<>();
                        products.put(storeName, store.getProducts());
                    }
                    out.writeObject(products);
                }

                case "sales_by_food_category" -> {
                    String category = (String) request;
                    Map<String, Double> result = new HashMap<>();
                    for (Store store : storeMap.values()) {
                        if (store.getFoodCategory().equals(category)) {
                            double storeTotal = 0.0;
                            for (Product p : store.getProducts()) {
                                if (p.isActive()) {
                                    storeTotal += p.getUnitsSold() * p.getPrice();
                                }
                            }
                            result.put(store.getStoreName(), storeTotal);
                        }
                    }
                    out.writeObject(result);
                }

                case "sales_by_product_type" -> {
                    String type = (String) request;
                    Map<String, Double> result = new HashMap<>();
                    for (Store store : storeMap.values()) {
                        double storeCategoryTotal = 0.0;
                        for (Product p : store.getProducts()) {
                            if (p.isActive() && p.getProductType().equals(type)) {
                                storeCategoryTotal += p.getUnitsSold() * p.getPrice();
                            }
                        }
                        if (storeCategoryTotal > 0) {
                            result.put(store.getStoreName(), storeCategoryTotal);
                        }
                    }
                    out.writeObject(result);
                }


                // Client actions
                case "search" -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> filters = (Map<String, Object>) request;
                    String category = (String) filters.get("foodCategory");
                    String priceCategory = (String) filters.get("priceCategory");
                    int stars = (Integer) filters.get("stars");
                    List<Store> matched = new ArrayList<>();

                    // First filter stores by category, price, and stars
                    for (Store s : storeMap.values()) {
                        if (s.getFoodCategory().equals(category) &&
                                s.getPriceCategory().equals(priceCategory) &&
                                s.getStars() >= stars) {
                            matched.add(s);
                        }
                    }

                    Map<String, Object> requestData = new HashMap<>();
                    System.out.println("Worker " + localIP + ":" + port + " sending result for UUID: " + filters.get("requestId") + " with " + matched.size() + " stores");
                    requestData.put("requestId", filters.get("requestId"));
                    requestData.put("stores", matched);
                    requestData.put("categories", List.of(category));
                    requestData.put("price", priceCategory);
                    requestData.put("stars", stars);

                    System.out.println("IN WORKER " + localIP + ":" + port + " - INPUT REDUCER STORES:" + requestData.values());
                    // Send to Reducer
                    try (Socket reducerSocket = new Socket(masterIP, REDUCER_PORT);
                         ObjectOutputStream reducerOut = new ObjectOutputStream(reducerSocket.getOutputStream())) {
                        reducerOut.writeObject(requestData);
                        reducerOut.flush();
                        out.writeObject("ACK");
                        out.flush();
                    }
                }

                case "purchase" -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> purchaseInfo = (Map<String, Object>) request;
                    String storeName = (String) purchaseInfo.get("storeName");
                    String productName = (String) purchaseInfo.get("productName");
                    int quantity = (Integer) purchaseInfo.get("quantity");

                    Store store = getStore(storeName);
                    if (store == null) {
                        out.writeObject("Error: Store not found");
                        break;
                    }

                    synchronized (store) {
                        Product product = null;
                        for (Product p : store.getProducts()) {
                            if (p.getProductName().equals(productName) && p.isActive()) {
                                product = p;
                                break;
                            }
                        }

                        if (product == null) {
                            out.writeObject("Error: Product not found in store");
                        } else if (product.getAvailableAmount() < quantity) {
                            out.writeObject("Error: Not enough stock available");
                        } else {
                            product.setAvailableAmount(product.getAvailableAmount() - quantity);
                            product.setUnitsSold(product.getUnitsSold() + quantity);
                            store.addToTotalSales(quantity * product.getPrice());
                            out.writeObject("Purchase successful: " + quantity + "x " + productName);
                        }
                    }
                }

                case "rate" -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> rateInfo = (Map<String, Object>) request;
                    String storeName = (String) rateInfo.get("storeName");
                    int stars = (Integer) rateInfo.get("stars");

                    Store store = getStore(storeName);
                    if (store == null) {
                        out.writeObject("Error: Store not found");
                        break;
                    }

                    synchronized (store) {
                        store.updateStars(stars);
                        out.writeObject("Rating successful for store: " + storeName);
                    }
                }

                case "get_all_stores" -> {
                    List<Store> stores;
                    synchronized (storeMap) {
                        stores = new ArrayList<>(storeMap.values());
                    }
                    out.writeObject(stores);
                }

                default -> {
                    out.writeObject("ERROR - Unknown command");
                }
            }
            out.flush();
            System.out.println("Worker on port " + port + " processed request");

        } catch (Exception e) {
            System.err.println("Worker error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
}
