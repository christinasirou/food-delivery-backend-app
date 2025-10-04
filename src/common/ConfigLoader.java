package common;

import java.io.*;
import java.util.*;

public class ConfigLoader {
    private static final Map<String, String> config = new HashMap<>();
    
    static {
        loadConfig();
    }
    
    private static void loadConfig() {
        try (BufferedReader reader = new BufferedReader(new FileReader(".env"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#") && line.contains("=")) {
                    String[] parts = line.split("=", 2);
                    if (parts.length == 2) {
                        config.put(parts[0].trim(), parts[1].trim());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not load .env file, using defaults");
        }
    }
    
    public static String getString(String key, String defaultValue) {
        return config.getOrDefault(key, defaultValue);
    }
    
    public static int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(config.getOrDefault(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    public static double getDouble(String key, double defaultValue) {
        try {
            return Double.parseDouble(config.getOrDefault(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
