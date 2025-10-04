package reducer;

import java.io.*;
import java.net.*;
import java.util.*;
import common.Store;

public class Reducer {
    private static final int EXPECTED_WORKERS = 3; // Based on config.txt
    private static Map<UUID, List<Store>> searchResults = new HashMap<>();
    private static Map<UUID, Integer> responseCounts = new HashMap<>();
    private static final Object lock = new Object();

    public static void main(String[] args) {
        System.out.println("Reducer starting with EXPECTED_WORKERS = " + EXPECTED_WORKERS);
        try (ServerSocket serverSocket = new ServerSocket(7000)) {
            System.out.println("Reducer ready on port 7000...");

            while (true) {
                Socket workerSocket = serverSocket.accept();
                new Thread(() -> handleWorkerRequest(workerSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleWorkerRequest(Socket workerSocket) {
        try (
                ObjectInputStream in = new ObjectInputStream(workerSocket.getInputStream());
                ObjectOutputStream out = new ObjectOutputStream(workerSocket.getOutputStream())
        ) {
            @SuppressWarnings("unchecked")
            Map<String, Object> requestData = (Map<String, Object>) in.readObject();

            UUID requestId = (UUID) requestData.get("requestId");
            @SuppressWarnings("unchecked")
            List<Store> stores = (List<Store>) requestData.get("stores");

            synchronized (lock) {
                // Update response count
                int currentCount = responseCounts.getOrDefault(requestId, 0) + 1;
                responseCounts.put(requestId, currentCount);

                System.out.println("Reducer received response " + currentCount + " of " + EXPECTED_WORKERS +
                        " for UUID: " + requestId);

                // Merge stores into results
                if (!searchResults.containsKey(requestId)) {
                    searchResults.put(requestId, new ArrayList<>());
                }
                searchResults.get(requestId).addAll(stores);

                System.out.println("Current count for UUID " + requestId + ": " + currentCount +
                        " (EXPECTED_WORKERS = " + EXPECTED_WORKERS + ")");

                if (currentCount >= EXPECTED_WORKERS) {
                    System.out.println("All workers have responded for UUID: " + requestId);
                    // Send results to master
                    try (Socket masterSocket = new Socket("localhost", 5002);
                         ObjectOutputStream masterOut = new ObjectOutputStream(masterSocket.getOutputStream())) {

                        List<Store> finalResults = searchResults.get(requestId);
                        System.out.println("Sending final results to master for UUID: " + requestId +
                                " with " + finalResults.size() + " stores");

                        // Create response map
                        Map<String, Object> responseMap = new HashMap<>();
                        responseMap.put("requestId", requestId);
                        responseMap.put("stores", finalResults);
                        responseMap.put("price", requestData.get("price"));
                        responseMap.put("categories", requestData.get("categories"));
                        responseMap.put("stars", requestData.get("stars"));

                        masterOut.writeObject(responseMap);
                        masterOut.flush();
                        System.out.println("Reducer sent reduced result to master for UUID: " + requestId);

                        // Clean up
                        searchResults.remove(requestId);
                        responseCounts.remove(requestId);
                    }
                }
            }

            // Send acknowledgment to worker
            out.writeObject("ACK");
            out.flush();

        } catch (Exception e) {
            System.err.println("Error handling worker request: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                workerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
} 