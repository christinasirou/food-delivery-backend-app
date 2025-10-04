package manager;

import java.io.*;
import java.net.*;
import java.util.*;
import common.ConfigLoader;

public class ManagerApp {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java ManagerApp <master-ip>");
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
            System.out.println("\n--- Manager Menu ---");

            while (true) {
                System.out.println("1. Register new store");
                System.out.println("2. Update existing product");
                System.out.println("3. Add new product");
                System.out.println("4. View sales by product");
                System.out.println("5. View sales by food category");
                System.out.println("6. View sales by product type");
                System.out.println("7. Exit");
                System.out.print("Choice: ");
                int choice = Integer.parseInt(scanner.nextLine());

                switch (choice) {
                    case 1 -> {
                        System.out.print("Enter path to JSON store file: ");
                        String path = scanner.nextLine();
                        // Send path to Master
                        out.writeObject("register");
                        out.writeObject(path);
                        out.flush();
                        // Wait for master to answer
                        Object response = in.readObject();
                        System.out.println(response);
                    }

                    case 2 -> {
                        System.out.print("Enter store name: ");
                        String storeName = scanner.nextLine();

                        System.out.print("Enter product name to update: ");
                        String productName = scanner.nextLine();

                        System.out.println("\nWhat would you like to do with the product?");
                        System.out.println("1. Update product");
                        System.out.println("2. Remove product from store");
                        System.out.print("Enter your choice (1-2): ");
                        int updateChoice = Integer.parseInt(scanner.nextLine());

                        Map<String, Object> updates = new HashMap<>();
                        updates.put("storeName", storeName);
                        updates.put("productName", productName);

                        if (updateChoice==1) {
                            System.out.println("\nWhich attributes do you want to update?");
                            System.out.println("1. Price");
                            System.out.println("2. Quantity");
                            System.out.print("Enter your choice (1-2): ");
                            int attrChoice = Integer.parseInt(scanner.nextLine());

                            switch (attrChoice) {
                                case 1 -> {
                                    System.out.print("Enter new price: ");
                                    updates.put("price", Double.parseDouble(scanner.nextLine()));
                                }
                                case 2 -> {
                                    System.out.print("Enter new quantity: ");
                                    updates.put("quantity", Integer.parseInt(scanner.nextLine()));
                                }
                                default -> {
                                    System.out.println("Invalid choice");
                                    continue;
                                }
                            }
                        } else {
                            updates.put("remove", true);
                        }

                        out.writeObject("update");
                        out.writeObject(updates);
                        out.flush();

                        Object response = in.readObject();
                        System.out.println(response);
                    }

                    case 3 -> {
                        System.out.print("Enter store name: ");
                        String storeName = scanner.nextLine();

                        System.out.print("Enter product name: ");
                        String productName = scanner.nextLine();

                        System.out.print("Enter product type: ");
                        String productType = scanner.nextLine();

                        System.out.print("Enter available amount: ");
                        int availableAmount = Integer.parseInt(scanner.nextLine());

                        System.out.print("Enter price: ");
                        double price = Double.parseDouble(scanner.nextLine());

                        Map<String, Object> product = new HashMap<>();
                        product.put("storeName", storeName);
                        product.put("productName", productName);
                        product.put("productType", productType);
                        product.put("availableAmount", availableAmount);
                        product.put("price", price);

                        out.writeObject("add_product");
                        out.writeObject(product);
                        out.flush();

                        Object response = in.readObject();
                        System.out.println(response);
                    }

                    case 4 -> {
                        System.out.print("Enter store name: ");
                        String storeName = scanner.nextLine();

                        out.writeObject("sales_by_product");
                        out.writeObject(storeName);
                        out.flush();

                        Object response = in.readObject();
                        System.out.println(response);
                    }

                    case 5 -> {
                        System.out.print("Enter food category: ");
                        String foodCategory = scanner.nextLine();

                        out.writeObject("sales_by_food_category");
                        out.writeObject(foodCategory);
                        out.flush();

                        Object response = in.readObject();
                        System.out.println(response);
                    }

                    case 6 -> {
                        System.out.print("Enter product type: ");
                        String productType = scanner.nextLine();

                        out.writeObject("sales_by_product_type");
                        out.writeObject(productType);
                        out.flush();

                        Object response = in.readObject();
                        System.out.println(response);
                    }

                    case 7 -> {
                        out.writeObject("exit");
                        out.writeObject("no");
                        out.flush();
                        System.out.println("Exiting ManagerApp.");
                        return;
                    }

                    default -> System.out.println("Invalid option");
                }

                out.writeObject("yes");
                out.flush();
            }

        } catch (Exception e) {
            System.err.println("Manager error: " + e.getMessage());
        }
    }
}
