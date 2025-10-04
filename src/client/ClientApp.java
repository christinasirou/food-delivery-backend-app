package client;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import common.ConfigLoader;

public class ClientApp {
    static boolean firsttime = true ;
    static int choice;
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java ClientApp <master-ip>");
            return;
        }

        String masterIP = args[0];
        System.out.println("Connecting to Master at: " + masterIP);

        int masterPort = ConfigLoader.getInt("MASTER_PORT", 5000);
        try (
                Socket socket = new Socket(masterIP, masterPort);
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                Scanner scanner = new Scanner(System.in)
        ) {
            // Menu loop
            while (true) {
                if(firsttime){
                    choice = 0;
                    firsttime = false;
                }else {
                    System.out.println("\n--- Customer Menu ---");
                    System.out.println("Stores near by:");
                    System.out.println("1. Search Stores");
                    System.out.println("2. Purchase Product");
                    System.out.println("3. Rate Store");
                    System.out.println("4. List all stores");
                    System.out.println("5. Exit");
                    System.out.print("Choice: ");
                    choice = Integer.parseInt(scanner.nextLine());
                }

                switch (choice) {
                    case 0 -> {
                        System.out.println("ENTER latitude");
                        String latitude  = scanner.nextLine();
                        System.out.println("ENTER longitude");
                        String longitude  = scanner.nextLine();

                        Map<String, Double> location = new HashMap<>();
                        location.put("latitude", Double.valueOf(latitude));
                        location.put("longitude", Double.valueOf(longitude));
                        System.out.println("Shops near you");
                        out.writeObject("show_stores");
                        out.flush();
                        out.writeObject(location);
                        out.flush();
                        try {
                            Object result = in.readObject();
                            if (result instanceof List<?> list) {
                                if (list.isEmpty()) {
                                    System.out.println("No stores Near By.");
                                } else {
                                    System.out.println("All Near By Stores:");
                                    for (Object o : list) {
                                        if (o instanceof Map<?, ?> store) {
                                            @SuppressWarnings("unchecked")
                                            Map<String, Object> storeMap = (Map<String, Object>) store;
                                            System.out.println("- " + storeMap.get("storeName") +
                                                    " (" + storeMap.get("foodCategory") + ")" +
                                                    " - Rating: " + storeMap.get("stars") + "/5" +
                                                    " - Price Category: " + storeMap.get("priceCategory"));

                                            @SuppressWarnings("unchecked")
                                            List<Map<String, Object>> products = (List<Map<String, Object>>) storeMap.get("products");
                                            if (products != null && !products.isEmpty()) {
                                                System.out.println("  Products:");
                                                for (Map<String, Object> product : products) {
                                                    System.out.println("    - " + product.get("name") +
                                                            " (" + product.get("type") + ")" +
                                                            " - Price: $" + product.get("price") +
                                                            " - Available: " + product.get("availableAmount"));
                                                }
                                            }
                                        }
                                    }
                                    System.out.println("Total stores: " + list.size());
                                }
                            } else {
                                System.out.println("Error: Received invalid response format from server");
                            }
                        } catch (Exception e) {
                            System.err.println("Error while retrieving stores: " + e.getMessage());
                        }

                    }
                    case 1 -> {
                        System.out.print("Enter food category: ");
                        String foodCategory = scanner.nextLine();
                        System.out.print("Enter stars: ");
                        int stars = Integer.parseInt(scanner.nextLine());
                        System.out.print("Enter price category: ");
                        String priceCategory = scanner.nextLine();

                        Map<String, Object> searchData = new HashMap<>();
                        searchData.put("foodCategory", foodCategory);
                        searchData.put("stars", stars);
                        searchData.put("priceCategory", priceCategory);

                        out.writeObject("search");
                        out.writeObject(searchData);
                        out.flush();

                        Object response = in.readObject();
                        System.out.println("Response: " + response);
                    }

                    case 2 -> {
                        System.out.println("--- Purchase Product ---");
                        System.out.print("Enter store name: ");
                        String storeName = scanner.nextLine();
                        System.out.print("Enter product name: ");
                        String productName = scanner.nextLine();
                        System.out.print("Enter quantity to purchase: ");
                        int quantity = Integer.parseInt(scanner.nextLine());

                        Map<String, Object> purchaseData = new HashMap<>();
                        purchaseData.put("storeName", storeName);
                        purchaseData.put("productName", productName);
                        purchaseData.put("quantity", quantity);

                        out.writeObject("purchase");
                        out.writeObject(purchaseData);
                        out.flush();

                        Object response = in.readObject();
                        System.out.println("Response: " + response);
                    }

                    case 3 -> {
                        System.out.println("--- Rate Store ---");
                        System.out.print("Enter store name: ");
                        String storeName = scanner.nextLine();
                        System.out.print("Enter stars (1-5): ");
                        int stars = Integer.parseInt(scanner.nextLine());

                        Map<String, Object> rateData = new HashMap<>();
                        rateData.put("storeName", storeName);
                        rateData.put("stars", stars);

                        out.writeObject("rate");
                        out.writeObject(rateData);
                        out.flush();

                        Object response = in.readObject();
                        System.out.println("Response: " + response);
                    }

                    case 4 -> {
                        out.writeObject("get_all_stores");
                        out.writeObject("none");
                        out.flush();

                        try {
                            Object result = in.readObject();
                            if (result instanceof List<?> list) {
                                if (list.isEmpty()) {
                                    System.out.println("No stores available in the system.");
                                } else {
                                    System.out.println("All Available Stores:");
                                    for (Object o : list) {
                                        if (o instanceof Map<?, ?> store) {
                                            @SuppressWarnings("unchecked")
                                            Map<String, Object> storeMap = (Map<String, Object>) store;
                                            System.out.println("- " + storeMap.get("storeName") +
                                                    " (" + storeMap.get("foodCategory") + ")" +
                                                    " - Rating: " + storeMap.get("stars") + "/5" +
                                                    " - Price Category: " + storeMap.get("priceCategory"));

                                            @SuppressWarnings("unchecked")
                                            List<Map<String, Object>> products = (List<Map<String, Object>>) storeMap.get("products");
                                            if (products != null && !products.isEmpty()) {
                                                System.out.println("  Products:");
                                                for (Map<String, Object> product : products) {
                                                    System.out.println("    - " + product.get("name") +
                                                            " (" + product.get("type") + ")" +
                                                            " - Price: $" + product.get("price") +
                                                            " - Available: " + product.get("availableAmount"));
                                                }
                                            }
                                        }
                                    }
                                    System.out.println("Total stores: " + list.size());
                                }
                            } else {
                                System.out.println("Error: Received invalid response format from server");
                            }
                        } catch (Exception e) {
                            System.err.println("Error while retrieving stores: " + e.getMessage());
                        }
                    }

                    case 5 -> {
                        out.writeObject("exit");
                        out.writeObject("no");
                        out.flush();
                        System.out.println("Exiting CustomerApp.");
                        return;
                    }

                    default -> System.out.println("Invalid option");
                }

                out.writeObject("yes");
                out.flush();
            }

        } catch (Exception e) {
            System.err.println("Client error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
