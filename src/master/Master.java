package master;

import java.io.*;
import java.net.*;
import java.util.*;

import common.Store;
import common.StoreFileProcessor;
import common.Product;
import common.ConfigLoader;

public class Master {
    private static int[] workerPorts;
    private static String[] workerIPs;
    private static int nextWorkerIndex = 0;
    private static final int REDUCER_PORT = ConfigLoader.getInt("MASTER_REDUCER_PORT", 5002);
    private static ServerSocket reducerServerSocket;
    private static volatile boolean isRunning = true;

    // Map to store search results and their synchronization objects
    private static final Map<UUID, SearchResult> searchResults = new HashMap<>();
    private static final Object searchResultsLock = new Object();

    // Map to store client locations
    private static final Map<String, Map<String, Double>> clientLocations = new HashMap<>();
    private static final Object clientLocationsLock = new Object();

    public static void main(String[] args) throws UnknownHostException {
        loadConfig("config.txt");
        startReducerListener();

        int masterPort = ConfigLoader.getInt("MASTER_PORT", 5000);
        try (ServerSocket serverSocket = new ServerSocket(masterPort, 50, InetAddress.getByName("0.0.0.0"))) {
            System.out.println("Master ready on port " + masterPort);
            System.out.println("Master IP: " + InetAddress.getLocalHost().getHostAddress());
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            isRunning = false;
            try {
                if (reducerServerSocket != null) {
                    reducerServerSocket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void startReducerListener() {
        try {
            reducerServerSocket = new ServerSocket(REDUCER_PORT);
            new Thread(() -> {
                while (isRunning) {
                    try {
                        Socket reducerSocket = reducerServerSocket.accept();
                        new Thread(() -> handleReducerResponse(reducerSocket)).start();
                    } catch (IOException e) {
                        if (isRunning) {
                            e.printStackTrace();
                        }
                    }
                }
            }).start();
            System.out.println("Reducer listener started on port " + REDUCER_PORT);
        } catch (IOException e) {
            System.err.println("Failed to start reducer listener: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void handleReducerResponse(Socket reducerSocket) {
        try (
                ObjectInputStream in = new ObjectInputStream(reducerSocket.getInputStream())
        ) {
            Object response = in.readObject();
            System.out.println("Received response from reducer: " + response);

            List<Store> stores;
            UUID requestId = null;

            if (response instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseMap = (Map<String, Object>) response;
                requestId = (UUID) responseMap.get("requestId");
                @SuppressWarnings("unchecked")
                List<Store> storesFromMap = (List<Store>) responseMap.get("stores");
                stores = storesFromMap;
            } else if (response instanceof List) {
                @SuppressWarnings("unchecked")
                List<Store> storesFromList = (List<Store>) response;
                stores = storesFromList;
                // Get the requestId from the first store's metadata if available
                if (!stores.isEmpty() && stores.get(0) instanceof Store) {
                    requestId = stores.get(0).getRequestId();
                }
            } else {
                System.err.println("Unexpected response type from reducer: " + response.getClass().getName());
                System.err.println("Response content: " + response);
                return;
            }

            if (stores != null && requestId != null) {
                synchronized (searchResultsLock) {
                    SearchResult searchResult = searchResults.get(requestId);
                    if (searchResult != null && !searchResult.isComplete) {
                        System.out.println("Found matching search result for UUID: " + requestId + " with " + stores.size() + " stores");
                        // Just store the results without sending to client
                        searchResult.storeResults(stores);
                        searchResults.remove(requestId);
                    } else {
                        System.err.println("No matching search result found for reducer response with UUID: " + requestId);
                    }
                }
            } else {
                System.err.println("Invalid response from reducer - missing stores or requestId");
            }
        } catch (Exception e) {
            System.err.println("Error handling reducer response: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                reducerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void loadConfig(String filename) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            List<Integer> ports = new ArrayList<>();
            List<String> ips = new ArrayList<>();
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("WORKER=")) {
                    String[] parts = line.substring(7).split(",");
                    if (parts.length == 2) {
                        ips.add(parts[0].trim());
                        ports.add(Integer.parseInt(parts[1].trim()));
                    }
                }
            }
            workerIPs = ips.toArray(new String[0]);
            workerPorts = ports.stream().mapToInt(i -> i).toArray();

            if (workerIPs.length != workerPorts.length) {
                System.err.println("Error: Number of IPs and ports must match in config.txt");
                System.exit(1);
            }

            System.out.println("Loaded " + workerIPs.length + " workers from config:");
            for (int i = 0; i < workerIPs.length; i++) {
                System.out.println("Worker " + (i+1) + ": " + workerIPs[i] + ":" + workerPorts[i]);
            }
        } catch (Exception e) {
            System.err.println("Failed to load config.txt: " + e.getMessage());
            System.exit(1);
        }
    }

    private static int getWorkerIndexForStore(String storeName) {
        int hash = Math.abs(storeName.hashCode());
        return hash % workerPorts.length;
    }

    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try (
                    ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
                    ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream())
            ) {
                while (true) {
                    String command = (String) in.readObject();
                    Object userinput = in.readObject();

                    if (command.equals("exit")) {
                        System.out.println("Client action.");
                        break;
                    }

                    switch (command) {
                        case "register": {
                            String path = (String) userinput;

                            Store store = StoreFileProcessor.processStoreFile(path);
                            int workerIndex = getWorkerIndexForStore(store.getStoreName());
                            String workerIP = workerIPs[workerIndex];
                            int workerPort = workerPorts[workerIndex];

                            try (
                                    Socket wSocket = new Socket(workerIP, workerPort);
                                    ObjectOutputStream wOut = new ObjectOutputStream(wSocket.getOutputStream());
                                    ObjectInputStream wIn = new ObjectInputStream(wSocket.getInputStream())
                            ) {
                                // Send to worker
                                wOut.writeObject("register");
                                wOut.writeObject(store);
                                wOut.flush();
                                // Wait for worker's response
                                Object response = wIn.readObject();
                                out.writeObject(response);
                                out.flush();
                            }
                            break;
                        }

                        case "update", "add_product": {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> updates = (Map<String, Object>) userinput;
                            String storeName = (String) updates.get("storeName");

                            // Get the worker for this store
                            int workerIndex = getWorkerIndexForStore(storeName);
                            String workerIP = workerIPs[workerIndex];
                            int workerPort = workerPorts[workerIndex];

                            try (
                                    Socket wSocket = new Socket(workerIP, workerPort);
                                    ObjectOutputStream wOut = new ObjectOutputStream(wSocket.getOutputStream());
                                    ObjectInputStream wIn = new ObjectInputStream(wSocket.getInputStream())
                            ) {
                                wOut.writeObject(command);
                                wOut.writeObject(updates);
                                wOut.flush();

                                Object response = wIn.readObject();
                                out.writeObject(response);
                                out.flush();

                            } catch (Exception e) {
                                out.writeObject("Error updating product: " + e.getMessage());
                                out.flush();
                            }
                            break;
                        }

                        case "sales_by_product": {
                            String storeName = (String) userinput;

                            // Get the worker for this store
                            int workerIndex = getWorkerIndexForStore(storeName);
                            String workerIP = workerIPs[workerIndex];
                            int workerPort = workerPorts[workerIndex];

                            try (
                                    Socket wSocket = new Socket(workerIP, workerPort);
                                    ObjectOutputStream wOut = new ObjectOutputStream(wSocket.getOutputStream());
                                    ObjectInputStream wIn = new ObjectInputStream(wSocket.getInputStream())
                            ) {
                                wOut.writeObject("sales_by_product");
                                wOut.writeObject(storeName);
                                wOut.flush();

                                Object response = wIn.readObject();
                                if (response instanceof Map<?, ?> productsMap) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, List<Product>> storeProducts = (Map<String, List<Product>>) productsMap;

                                    // Calculate sales for each product
                                    Map<String, Double> productSales = new HashMap<>();
                                    double totalStoreSales = 0.0;

                                    for (List<Product> products : storeProducts.values()) {
                                        for (Product product : products) {
                                            double productTotal = product.getUnitsSold() * product.getPrice();
                                            productSales.put(product.getProductName(), productTotal);
                                            totalStoreSales += productTotal;
                                        }
                                    }

                                    // Create detailed sales report
                                    StringBuilder report = new StringBuilder();
                                    report.append("\nSales Report for ").append(storeName).append(":\n");
                                    report.append("----------------------------------------\n");

                                    for (Map.Entry<String, Double> entry : productSales.entrySet()) {
                                        report.append(String.format("%s: $%.2f\n",
                                                entry.getKey(), entry.getValue()));
                                    }

                                    report.append("----------------------------------------\n");
                                    report.append(String.format("Total Store Sales: $%.2f\n", totalStoreSales));

                                    out.writeObject(report.toString());
                                } else {
                                    out.writeObject("Error: Invalid response format from worker");
                                }
                                out.flush();
                            } catch (Exception e) {
                                out.writeObject("Error calculating sales: " + e.getMessage());
                                out.flush();
                            }
                            break;
                        }

                        case "sales_by_food_category": {
                            String foodCategory = (String) userinput;
                            Map<String, Double> storeSales = new HashMap<>();
                            double totalSales = 0.0;

                            for (int i = 0; i < workerIPs.length; i++) {
                                try (
                                        Socket wSocket = new Socket(workerIPs[i], workerPorts[i]);
                                        ObjectOutputStream wOut = new ObjectOutputStream(wSocket.getOutputStream());
                                        ObjectInputStream wIn = new ObjectInputStream(wSocket.getInputStream())
                                ) {
                                    wOut.writeObject("sales_by_food_category");
                                    wOut.writeObject(foodCategory);
                                    wOut.flush();

                                    Object response = wIn.readObject();
                                    if (response instanceof Map<?, ?> map) {
                                        @SuppressWarnings("unchecked")
                                        Map<String, Double> partial = (Map<String, Double>) map;
                                        for (Map.Entry<String, Double> entry : partial.entrySet()) {
                                            storeSales.merge(entry.getKey(), entry.getValue(), Double::sum);
                                            totalSales += entry.getValue();
                                        }
                                    }
                                } catch (Exception e) {
                                    System.err.println("Error contacting worker at " + workerIPs[i] + ":" + workerPorts[i] + ": " + e.getMessage());
                                }
                            }

                            StringBuilder report = new StringBuilder("\nSales by Food Category: " + foodCategory + "\n");
                            report.append("----------------------------------------\n");
                            for (Map.Entry<String, Double> entry : storeSales.entrySet()) {
                                report.append(String.format("%s: $%.2f\n", entry.getKey(), entry.getValue()));
                            }
                            report.append("----------------------------------------\n");
                            report.append(String.format("Total: $%.2f\n", totalSales));
                            out.writeObject(report.toString());
                            out.flush();
                            break;
                        }

                        case "sales_by_product_type": {
                            String productType = (String) userinput;
                            Map<String, Double> storeSales = new HashMap<>();
                            double totalSales = 0.0;

                            for (int i = 0; i < workerIPs.length; i++) {
                                try (
                                        Socket wSocket = new Socket(workerIPs[i], workerPorts[i]);
                                        ObjectOutputStream wOut = new ObjectOutputStream(wSocket.getOutputStream());
                                        ObjectInputStream wIn = new ObjectInputStream(wSocket.getInputStream())
                                ) {
                                    wOut.writeObject("sales_by_product_type");
                                    wOut.writeObject(productType);
                                    wOut.flush();

                                    Object response = wIn.readObject();
                                    if (response instanceof Map<?, ?> map) {
                                        @SuppressWarnings("unchecked")
                                        Map<String, Double> partial = (Map<String, Double>) map;
                                        for (Map.Entry<String, Double> entry : partial.entrySet()) {
                                            storeSales.merge(entry.getKey(), entry.getValue(), Double::sum);
                                            totalSales += entry.getValue();
                                        }
                                    }
                                } catch (Exception e) {
                                    System.err.println("Error contacting worker at " + workerIPs[i] + ":" + workerPorts[i] + ": " + e.getMessage());
                                }
                            }

                            StringBuilder report = new StringBuilder("\nSales by Product Type: " + productType + "\n");
                            report.append("----------------------------------------\n");
                            for (Map.Entry<String, Double> entry : storeSales.entrySet()) {
                                report.append(String.format("%s: $%.2f\n", entry.getKey(), entry.getValue()));
                            }
                            report.append("----------------------------------------\n");
                            report.append(String.format("Total: $%.2f\n", totalSales));
                            out.writeObject(report.toString());
                            out.flush();
                            break;
                        }

                        case "get_all_stores": {
                            List<Store> allStores = new ArrayList<>();
                            for (int i = 0; i < workerIPs.length; i++) {
                                try (
                                        Socket wSocket = new Socket(workerIPs[i], workerPorts[i]);
                                        ObjectOutputStream wOut = new ObjectOutputStream(wSocket.getOutputStream());
                                        ObjectInputStream wIn = new ObjectInputStream(wSocket.getInputStream())
                                ) {
                                    wOut.writeObject("get_all_stores");
                                    wOut.writeObject("none");
                                    wOut.flush();
                                    @SuppressWarnings("unchecked")
                                    List<Store> stores = (List<Store>) wIn.readObject();
                                    allStores.addAll(stores);
                                } catch (Exception e) {
                                    System.err.println("Worker not responding at " + workerIPs[i] + ":" + workerPorts[i]);
                                }
                            }

                            // Transform stores into detailed maps
                            List<Map<String, Object>> detailedStores = new ArrayList<>();
                            for (Store store : allStores) {
                                Map<String, Object> storeDetails = new HashMap<>();
                                storeDetails.put("storeName", store.getStoreName());
                                storeDetails.put("foodCategory", store.getFoodCategory());
                                storeDetails.put("priceCategory", store.getPriceCategory());
                                storeDetails.put("stars", store.getStars());
                                storeDetails.put("Latitude", store.getLatitude());
                                storeDetails.put("Longitude", store.getLongitude());

                                // Add detailed product information
                                List<Map<String, Object>> productDetails = new ArrayList<>();
                                for (Product product : store.getProducts()) {
                                    if (product.isActive()) {
                                        Map<String, Object> productInfo = new HashMap<>();
                                        productInfo.put("name", product.getProductName());
                                        productInfo.put("type", product.getProductType());
                                        productInfo.put("price", product.getPrice());
                                        productInfo.put("availableAmount", product.getAvailableAmount());
                                        productDetails.add(productInfo);
                                    }
                                }
                                storeDetails.put("products", productDetails);
                                detailedStores.add(storeDetails);
                            }

                            out.writeObject(detailedStores);
                            out.flush();
                            break;
                        }

                        case "show_stores": {
                            @SuppressWarnings("unchecked")
                            Map<String, Double> location = (Map<String, Double>) userinput;
                            double userLat = location.get("latitude");
                            double userLon = location.get("longitude");

                            // Store client location
                            String clientAddress = clientSocket.getRemoteSocketAddress().toString();
                            synchronized (clientLocationsLock) {
                                clientLocations.put(clientAddress, location);
                            }

                            List<Store> nearbyStores = new ArrayList<>();

                            for (int i = 0; i < workerIPs.length; i++) {
                                try (
                                        Socket wSocket = new Socket(workerIPs[i], workerPorts[i]);
                                        ObjectOutputStream wOut = new ObjectOutputStream(wSocket.getOutputStream());
                                        ObjectInputStream wIn = new ObjectInputStream(wSocket.getInputStream())
                                ) {
                                    wOut.writeObject("get_all_stores");
                                    wOut.writeObject("none");
                                    wOut.flush();
                                    @SuppressWarnings("unchecked")
                                    List<Store> stores = (List<Store>) wIn.readObject();
                                    for (Store store : stores) {
                                        if (isWithin5Km(userLat, userLon, store.getLatitude(), store.getLongitude())) {
                                            nearbyStores.add(store);
                                            System.out.println("found" + store.getStoreName());
                                        }
                                    }
                                    System.out.println(nearbyStores.size());
                                } catch (Exception e) {
                                    System.err.println("Worker not responding at " + workerIPs[i] + ":" + workerPorts[i]);
                                }
                            }
                            System.out.println("Found " + nearbyStores.size() + " stores within 5km of (" + userLat + ", " + userLon + ")");
                            List<Map<String, Object>> detailedStores = new ArrayList<>();
                            for (Store store : nearbyStores) {
                                Map<String, Object> storeDetails = new HashMap<>();
                                storeDetails.put("storeName", store.getStoreName());
                                storeDetails.put("foodCategory", store.getFoodCategory());
                                storeDetails.put("priceCategory", store.getPriceCategory());
                                storeDetails.put("stars", store.getStars());
                                storeDetails.put("logo", store.getStoreLogo());
                                storeDetails.put("Latitude", store.getLatitude());
                                storeDetails.put("Longitude", store.getLongitude());

                                // Add detailed product information
                                List<Map<String, Object>> productDetails = new ArrayList<>();
                                for (Product product : store.getProducts()) {
                                    if (product.isActive()) {
                                        Map<String, Object> productInfo = new HashMap<>();
                                        productInfo.put("name", product.getProductName());
                                        productInfo.put("type", product.getProductType());
                                        productInfo.put("image", product.getProductImage());
                                        productInfo.put("price", product.getPrice());
                                        productInfo.put("availableAmount", product.getAvailableAmount());
                                        productDetails.add(productInfo);
                                    }
                                }
                                storeDetails.put("products", productDetails);
                                detailedStores.add(storeDetails);
                            }

                            out.writeObject(detailedStores);
                            out.flush();
                            break;
                        }

                        case "purchase", "rate": {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> purchaseOrRateData = (Map<String, Object>) userinput;
                            String storeName = (String) purchaseOrRateData.get("storeName");

                            int workerIndex = getWorkerIndexForStore(storeName);
                            String workerIP = workerIPs[workerIndex];
                            int workerPort = workerPorts[workerIndex];

                            try (
                                    Socket wSocket = new Socket(workerIP, workerPort);
                                    ObjectOutputStream wOut = new ObjectOutputStream(wSocket.getOutputStream());
                                    ObjectInputStream wIn = new ObjectInputStream(wSocket.getInputStream())
                            ) {
                                wOut.writeObject(command);
                                wOut.writeObject(purchaseOrRateData);
                                wOut.flush();

                                Object response = wIn.readObject();
                                out.writeObject(response);
                                out.flush();

                            } catch (Exception e) {
                                out.writeObject("Error handling purchase/rate: " + e.getMessage());
                                out.flush();
                            }
                            break;
                        }

                        case "search": {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> searchData = (Map<String, Object>) userinput;
                            UUID requestId = UUID.randomUUID();
                            searchData.put("requestId", requestId);

                            String clientAddress = clientSocket.getRemoteSocketAddress().toString();
                            System.out.println("Starting search with UUID: " + requestId + " for client: " + clientAddress);

                            // Get client's location
                            Map<String, Double> clientLocation;
                            synchronized (clientLocationsLock) {
                                clientLocation = clientLocations.get(clientAddress);
                            }

                            if (clientLocation == null) {
                                out.writeObject("Error: Please set your location first using the 'show_stores' option");
                                out.flush();
                                break;
                            }

                            // Create and register search result object with client's output stream
                            SearchResult searchResult = new SearchResult(out, clientAddress, searchData,
                                    clientLocation.get("latitude"), clientLocation.get("longitude"));
                            synchronized (searchResultsLock) {
                                searchResults.put(requestId, searchResult);
                            }

                            // Send search request to all workers
                            for (int i = 0; i < workerIPs.length; i++) {
                                try (
                                        Socket wSocket = new Socket(workerIPs[i], workerPorts[i]);
                                        ObjectOutputStream wOut = new ObjectOutputStream(wSocket.getOutputStream());
                                        ObjectInputStream wIn = new ObjectInputStream(wSocket.getInputStream())
                                ) {
                                    wOut.writeObject("search");
                                    wOut.writeObject(searchData);
                                    wOut.flush();
                                    Object response = wIn.readObject();
                                    System.out.println("Worker " + workerIPs[i] + ":" + workerPorts[i] + " response for request " + requestId + " from client " + clientAddress);
                                } catch (Exception e) {
                                    System.err.println("Worker not responding at " + workerIPs[i] + ":" + workerPorts[i] + " for request " + requestId + " from client " + clientAddress);
                                }
                            }

                            try {
                                System.out.println("Waiting for reducer response for request " + requestId + " from client " + clientAddress);
                                // Wait for the reducer's response with a timeout
                                List<Store> finalResult = searchResult.getResults(30000); // timeout

                                if (finalResult == null) {
                                    System.out.println("Search timed out for request " + requestId + " from client " + clientAddress);
                                    synchronized (searchResultsLock) {
                                        searchResults.remove(requestId);
                                    }
                                    out.writeObject("Search timed out after 30 seconds");
                                    out.flush();
                                    break;
                                }

                                // Filter stores by distance
                                List<Store> nearbyStores = new ArrayList<>();
                                for (Store store : finalResult) {
                                    if (isWithin5Km(clientLocation.get("latitude"), clientLocation.get("longitude"),
                                            store.getLatitude(), store.getLongitude())) {
                                        nearbyStores.add(store);
                                        System.out.println("NEARBY STORE ADDED: " + store.getStoreName());
                                    }
                                }

                                // Update the results with only nearby stores
                                searchResult.setResults(nearbyStores);

                                System.out.println("Received results from reducer for request " + requestId + " from client " + clientAddress);
                            } catch (InterruptedException e) {
                                System.err.println("Search was interrupted for request " + requestId + " from client " + clientAddress);
                                synchronized (searchResultsLock) {
                                    searchResults.remove(requestId);
                                }
                                out.writeObject("Search was interrupted");
                                out.flush();
                            } catch (Exception e) {
                                System.err.println("Error during search for request " + requestId + " from client " + clientAddress + ": " + e.getMessage());
                                synchronized (searchResultsLock) {
                                    searchResults.remove(requestId);
                                }
                                out.writeObject("Error during search: " + e.getMessage());
                                out.flush();
                            }
                            break;
                        }

                        default: {
                            out.writeObject("Unknown command: " + command);
                            out.flush();
                        }
                    }


                    String cont = (String) in.readObject();
                    if (!cont.equalsIgnoreCase("yes")) {
                        System.out.println("Client action");
                        break;
                    }
                }
            } catch (Exception e) {
                System.err.println("Master client/manager error: " + e.getMessage());
                e.printStackTrace();
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    public static boolean isWithin5Km(double lat1, double lon1, double lat2, double lon2) {
        final int EARTH_RADIUS_KM = 6371; // Earth's radius in kilometers

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = EARTH_RADIUS_KM * c;

        return distance <= 5.0;
    }
}

