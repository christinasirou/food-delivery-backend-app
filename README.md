# Food Delivery Distributed System

A Java-based distributed system for managing food delivery stores with client and manager applications.

## Quick Start

### Prerequisites
- Java JDK 11 or later installed
- Java added to system PATH

### Running the System

1. **First time or after code changes:**
   ```cmd
   comp_start.bat
   ```
   - Compiles all Java classes
   - Starts all services
   - Launches Manager and Client applications

2. **After compilation (faster):**
   ```cmd
   start.bat
   ```
   - Just starts the system (no compilation)

3. **Stop the system:**
   ```cmd
   stop.bat
   ```
   - Stops all running services

## System Architecture

The system consists of 5 main components:

- **Reducer** (port 7000) - Aggregates search results from workers
- **Master** (port 5000) - Central coordinator, handles client/manager requests
- **Workers** (ports 6000, 6001, 6002) - Process store data and handle operations
- **Manager App** - For store management (register stores, update products, view sales)
- **Client App** - For customers (search stores, make purchases, rate stores)

## Configuration

### Environment File (.env)
The system uses a `.env` file for configuration. Edit this file to change settings:

```env
# Network Configuration
WORKER_IP=192.168.1.121
MASTER_PORT=5000
REDUCER_PORT=7000
MASTER_REDUCER_PORT=5002
WORKER_PORT_1=6000
WORKER_PORT_2=6001
WORKER_PORT_3=6002

# JSON Store Paths (for Manager to register stores)
STORE_1_PATH=C:\Users\PATH_TO_YOUR_PROJECT\src\data\store1.json
STORE_2_PATH=C:\Users\PATH_TO_YOUR_PROJECT\src\data\store2.json
STORE_3_PATH=C:\Users\PATH_TO_YOUR_PROJECT\src\data\store3.json
STORE_4_PATH=C:\Users\PATH_TO_YOUR_PROJECT\src\data\store4.json
STORE_5_PATH=C:\Users\PATH_TO_YOUR_PROJECT\src\data\store5.json

# Project Paths
JSON_LIB_PATH=lib/json-20210307.jar
```

### JSON Library
- Place the JSON library file in the `lib/` directory
- Update `JSON_LIB_PATH` in `.env` if using a different library name

## How to Use

### Manager Application
1. Run `comp_start.bat` or `start.bat`
2. In the Manager window, choose option 1: "Register new store"
3. Enter the full path to a JSON store file (use paths from `.env` file):
   ```
   C:\Users\lenovo-pc\Desktop\Projects\food_delivery_java_distributed_system\src\data\store1.json
   ```
4. Use other Manager options to:
   - Update existing products
   - Add new products
   - View sales reports by product, category, or type

### Client Application
1. Run `comp_start.bat` or `start.bat`
2. In the Client window:
   - First, set your location (latitude/longitude)
   - Search for stores by category, price range, and rating
   - Purchase products
   - Rate stores
   - View all available stores

## Project Structure

```
food_delivery_java_distributed_system/
├── .env                          # Configuration file
├── config.txt                    # Worker configuration
├── comp_start.bat               # Compile + Start
├── start.bat                    # Start only
├── stop.bat                     # Stop all services
├── lib/
│   └── json-20210307.jar        # JSON library
├── src/
│   ├── client/                  # Client application
│   ├── common/                  # Shared classes
│   ├── data/                    # Store JSON files
│   ├── manager/                 # Manager application
│   ├── master/                  # Master node
│   ├── worker/                  # Worker nodes
│   └── reducer/                 # Reducer service
└── bin/                         # Compiled classes (auto-generated)
```

## Features

### Manager Features
- Register new stores from JSON files
- Update product information (price, quantity)
- Add new products to stores
- Remove products from stores
- View sales reports by product
- View sales reports by food category
- View sales reports by product type

### Client Features
- Set location and find nearby stores (within 5km)
- Search stores by food category, price range, and rating
- Purchase products
- Rate stores
- View all available stores with detailed information

## Notes

- All services must be running for the system to work properly
- The system automatically distributes stores across workers based on store name hashing
- Client location is required before searching for stores
- Store registration requires valid JSON files with proper structure
