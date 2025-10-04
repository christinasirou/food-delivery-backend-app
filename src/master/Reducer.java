package master;

import common.Store;
import common.ConfigLoader;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class Reducer {

    private static final int REDUCER_PORT = ConfigLoader.getInt("REDUCER_PORT", 7000); // Port to listen for worker requests
    private static final int MASTER_PORT = ConfigLoader.getInt("MASTER_REDUCER_PORT", 5002);  // Port to send results to the master
    private static final Map<UUID, List<Map<String, Object>>> requestBuckets = new HashMap<>();
    private static final Object lock = new Object();
    private static int EXPECTED_WORKERS;

    public static void main(String[] args) {
        try {
            // Step 1: Load configuration for worker ports
            List<Integer> workerPorts = loadWorkerPorts("config.txt");
            EXPECTED_WORKERS = workerPorts.size();
            System.out.println("Reducer starting with EXPECTED_WORKERS = " + EXPECTED_WORKERS);

            // Step 2: Start the Reducer server to listen for connections
            try (ServerSocket serverSocket = new ServerSocket(REDUCER_PORT)) {
                System.out.println("Reducer ready on port " + REDUCER_PORT + "...");

                // Step 3: Listen for incoming connections from workers
                while (true) {
                    Socket socket = serverSocket.accept();
                    Thread handlerThread = new Thread(() -> handleWorkerRequest(socket, workerPorts));
                    handlerThread.start();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Load worker ports from the configuration file
    private static List<Integer> loadWorkerPorts(String configFile) throws IOException {
        List<Integer> workerPorts = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("WORKER=")) {
                    String[] parts = line.substring(7).split(",");
                    if (parts.length == 2) {
                        workerPorts.add(Integer.parseInt(parts[1].trim()));
                    }
                }
            }
        }
        System.out.println("Loaded " + workerPorts.size() + " worker ports from config: " + workerPorts);
        return workerPorts;
    }

    // Handle each incoming worker request
    private static void handleWorkerRequest(Socket socket, List<Integer> workerPorts) {
        try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            @SuppressWarnings("unchecked")
            Map<String, Object> requestData = (Map<String, Object>) in.readObject();
            UUID requestId = (UUID) requestData.get("requestId");

            synchronized (lock) {
                requestBuckets.putIfAbsent(requestId, new ArrayList<>());
                requestBuckets.get(requestId).add(requestData);

                int currentCount = requestBuckets.get(requestId).size();
                System.out.println("Reducer received " + currentCount + " responses for UUID: " + requestId);
                System.out.println("Checking if currentCount " + currentCount + " == EXPECTED_WORKERS " + EXPECTED_WORKERS);

                if (currentCount == EXPECTED_WORKERS) {
                    // Process and send to master
                    List<Map<String, Object>> allWorkerData = requestBuckets.remove(requestId);
                    List<Store> finalResult = new ArrayList<>();

                    for (Map<String, Object> data : allWorkerData) {
                        @SuppressWarnings("unchecked")
                        List<Store> stores = (List<Store>) data.get("stores");
                        @SuppressWarnings("unchecked")
                        List<String> categories = (List<String>) data.get("categories");
                        String price = (String) data.get("price");
                        int stars = (int) data.get("stars");

                        for (Store store : stores) {
                            if (categories.contains(store.getFoodCategory())
                                    && store.getPriceCategory().equals(price)
                                    && store.getStars() >= stars) {
                                finalResult.add(store);
                            }
                        }
                    }
                    System.out.println("Final results: " + finalResult);

                    // Send final result to master
                    try (
                            Socket masterSocket = new Socket(InetAddress.getLocalHost().getHostAddress(), MASTER_PORT);
                            ObjectOutputStream masterOut = new ObjectOutputStream(masterSocket.getOutputStream())
                    ) {
                        // Create a response map containing both requestId and stores
                        Map<String, Object> responseMap = new HashMap<>();
                        responseMap.put("requestId", requestId);
                        responseMap.put("stores", finalResult);

                        masterOut.writeObject(responseMap);
                        masterOut.flush();
                        System.out.println("Reducer sent reduced result to master for UUID: " + requestId);
                    } catch (IOException e) {
                        System.err.println("Reducer failed to send to master: " + e.getMessage());
                    }
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error while processing worker request: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }
}
