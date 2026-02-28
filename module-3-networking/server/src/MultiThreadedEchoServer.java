import java.io.*;
import java.net.*;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

public class MultiThreadedEchoServer {
    // In‑memory "database"
    private static List<Expense> expenses = new ArrayList<>();
    private static int nextId = 1;
    private static final String DATA_FILE = "expenses.jsonl";

    // Static initializer – runs once when class is loaded
    static {
        loadFromFile();
    }

    private static void saveToFile() {
        synchronized (MultiThreadedEchoServer.class) {
            JSONArray jsonArray = new JSONArray();
            for (Expense e : expenses) {
                JSONObject obj = new JSONObject();
                obj.put("id", e.getId());
                obj.put("name", e.getName());
                obj.put("amount", e.getAmount());
                obj.put("category", e.getCategory());
                jsonArray.put(obj);
            }
            try (PrintWriter fileOut = new PrintWriter(new FileWriter(DATA_FILE))) {
                // toString(2) adds indentation for readability
                fileOut.print(jsonArray.toString(2));
            } catch (IOException e) {
                System.out.println("Error saving to file: " + e.getMessage());
            }
        }
    }



    private static void loadFromFile() {
        File file = new File(DATA_FILE);
        if (!file.exists()) {
            // No saved data – use sample data
            expenses.add(new Expense(nextId++, "Coffee", 5.50, "Food"));
            expenses.add(new Expense(nextId++, "Movie", 12.00, "Entertainment"));
            expenses.add(new Expense(nextId++, "Uber", 24.50, "Transport"));
            saveToFile();
            return;
        }

        try (BufferedReader fileReader = new BufferedReader(new FileReader(file))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = fileReader.readLine()) != null) {
                content.append(line);
            }
            JSONArray jsonArray = new JSONArray(content.toString());

            List<Expense> loaded = new ArrayList<>();
            int maxId = 0;
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                int id = obj.getInt("id");
                String name = obj.getString("name");
                double amount = obj.getDouble("amount");
                String category = obj.getString("category");
                loaded.add(new Expense(id, name, amount, category));
                if (id > maxId) maxId = id;
            }

            synchronized (MultiThreadedEchoServer.class) {
                expenses.clear();
                expenses.addAll(loaded);
                nextId = maxId + 1;
            }
        } catch (IOException | JSONException e) {
            System.out.println("Error loading from file: " + e.getMessage());
            // Fall back to sample data
            expenses.add(new Expense(nextId++, "Coffee", 5.50, "Food"));
            expenses.add(new Expense(nextId++, "Movie", 12.00, "Entertainment"));
            expenses.add(new Expense(nextId++, "Uber", 24.50, "Transport"));
        }
    }

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(12345);
        System.out.println("HTTP API server running on port 12345");
        System.out.println("Try: http://localhost:12345/expenses");

        while (true) {
            Socket clientSocket = serverSocket.accept();
            System.out.println("New client connected: " + clientSocket.getInetAddress());

            // Hand off to a new thread
            Thread clientHandler = new Thread(new ClientHandler(clientSocket));
            clientHandler.start();
        }
    }

    // Inner class that handles each client in its own thread
    static class ClientHandler implements Runnable {
        private Socket clientSocket;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try (
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(clientSocket.getInputStream()));
                    PrintWriter out = new PrintWriter(
                            clientSocket.getOutputStream(), true)
            ) {
                // 1. Read the request line
                String requestLine = in.readLine();
                if (requestLine == null) {
                    clientSocket.close();
                    return;
                }
                System.out.println("Request: " + requestLine);

                // 2. Read headers and capture Content-Length
                int contentLength = -1;
                String header;
                while ((header = in.readLine()) != null && !header.isEmpty()) {
                    if (header.toLowerCase().startsWith("content-length:")) {
                        String[] parts = header.split(":");
                        if (parts.length > 1) {
                            contentLength = Integer.parseInt(parts[1].trim());
                        }
                    }
                    // ignore other headers
                }

                // 3. Parse method and path from request line
                String[] parts = requestLine.split(" ");
                if (parts.length < 2) {
                    sendErrorResponse(out, 400, "Bad Request");
                    clientSocket.close();
                    return;
                }
                String method = parts[0];
                String fullPath = parts[1];  // may contain ? and query

                // Separate path and query string
                String path = fullPath;
                String queryString = "";
                int queryIndex = fullPath.indexOf('?');
                if (queryIndex != -1) {
                    path = fullPath.substring(0, queryIndex);
                    queryString = fullPath.substring(queryIndex + 1);
                }

                // 4. Route based on method + path
                if ("GET".equals(method) && "/expenses".equals(path)) {
                    Map<String, String> queryParams = parseQueryString(queryString);
                    handleGetExpenses(out, queryParams);
                } else if ("GET".equals(method) && path.startsWith("/expenses/")) {
                    handleGetExpenseById(out, path);
                } else if ("DELETE".equals(method) && path.startsWith("/expenses/")) {
                    handleDeleteExpenseById(out, path);
                } else if ("PUT".equals(method) && path.startsWith("/expenses/")) {
                    handlePutExpenseById(in, out, path, contentLength);
                } else if ("POST".equals(method) && "/expenses".equals(path)) {
                    handlePostExpenses(in, out, contentLength);
                } else {
                    sendErrorResponse(out, 404, "Not Found");
                }

                clientSocket.close();
                System.out.println("Client disconnected: " + clientSocket.getInetAddress());
            } catch (IOException e) {
                System.out.println("Error handling client: " + e.getMessage());
            }
        }

        private void handleGetExpenses(PrintWriter out, Map<String, String> queryParams) {
            List<Expense> result = new ArrayList<>();
            String categoryFilter = queryParams.get("category");

            synchronized (MultiThreadedEchoServer.class) {
                if (categoryFilter != null && !categoryFilter.isEmpty()) {
                    for (Expense e : expenses) {
                        if (categoryFilter.equals(e.getCategory())) {
                            result.add(e);
                        }
                    }
                } else {
                    result.addAll(expenses);
                }
            }

            JSONArray jsonArray = new JSONArray();
            for (Expense e : result) {
                JSONObject obj = new JSONObject();
                obj.put("id", e.getId());
                obj.put("name", e.getName());
                obj.put("amount", e.getAmount());
                obj.put("category", e.getCategory());
                jsonArray.put(obj);
            }
            String responseBody = jsonArray.toString();

            out.println("HTTP/1.1 200 OK");
            out.println("Content-Type: application/json");
            out.println("Content-Length: " + responseBody.length());
            out.println();
            out.println(responseBody);
        }

        private void handlePostExpenses(BufferedReader in, PrintWriter out, int contentLength) throws IOException {
            if (contentLength <= 0) {
                sendErrorResponse(out, 400, "Bad Request: Missing body");
                return;
            }

            char[] bodyChars = new char[contentLength];
            int bytesRead = in.read(bodyChars, 0, contentLength);
            String body = new String(bodyChars, 0, bytesRead);
            System.out.println("Received body: " + body);

            // Parse JSON
            JSONObject json;
            try {
                json = new JSONObject(body);
            } catch (JSONException e) {
                sendErrorResponse(out, 400, "Bad Request: Invalid JSON");
                return;
            }

            // Extract fields (they may be missing)
            String name;
            String category;
            double amount;
            try {
                name = json.getString("name");
                category = json.getString("category");
                amount = json.getDouble("amount");
                System.out.println("Using JSON library");
            } catch (JSONException e) {
                sendErrorResponse(out, 400, "Bad Request: Missing required field: " + e.getMessage());
                return;
            }

            // Validate
            String validationError = validateExpenseData(name, category, amount);
            if (validationError != null) {
                sendErrorResponse(out, 400, "Bad Request: " + validationError);
                return;
            }

            // Thread‑safe addition
            int newId;
            synchronized (MultiThreadedEchoServer.class) {
                newId = nextId++;
                Expense newExpense = new Expense(newId, name, amount, category);
                expenses.add(newExpense);
                saveToFile();
            }

            // Build response JSON
            JSONObject responseJson = new JSONObject();
            responseJson.put("id", newId);
            responseJson.put("name", name);
            responseJson.put("amount", amount);
            responseJson.put("category", category);
            String responseBody = responseJson.toString();

            out.println("HTTP/1.1 201 Created");
            out.println("Content-Type: application/json");
            out.println("Content-Length: " + responseBody.length());
            out.println();
            out.println(responseBody);
        }

        private String validateExpenseData(String name, String category, double amount) {
            if (name == null || name.trim().isEmpty()) {
                return "Name is required";
            }
            if (category == null || category.trim().isEmpty()) {
                return "Category is required";
            }
            if (amount <= 0) {
                return "Amount must be greater than 0";
            }
            return null; // null means valid
        }


        private Map<String, String> parseQueryString(String queryString) {
            Map<String, String> queryParams = new HashMap<>();
            if (queryString == null || queryString.isEmpty()) {
                return queryParams;
            }
            String[] pairs = queryString.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length == 2) {
                    queryParams.put(keyValue[0], keyValue[1]);
                } else if (keyValue.length == 1) {
                    // key with no value (e.g., "?category") – treat as empty string
                    queryParams.put(keyValue[0], "");
                }
            }
            return queryParams;
        }


        private void handleGetExpenseById(PrintWriter out, String path) {
            String[] pathParts = path.split("/");
            if (pathParts.length < 3) {
                sendErrorResponse(out, 400, "Bad Request: Invalid path");
                return;
            }
            int id;
            try {
                id = Integer.parseInt(pathParts[2]);
            } catch (NumberFormatException e) {
                sendErrorResponse(out, 400, "Bad Request: ID must be a number");
                return;
            }

            Expense found = null;
            synchronized (MultiThreadedEchoServer.class) {
                for (Expense e : expenses) {
                    if (e.getId() == id) {
                        found = e;
                        break;
                    }
                }
            }

            if (found == null) {
                sendErrorResponse(out, 404, "Expense not found");
                return;
            }

            JSONObject json = new JSONObject();
            json.put("id", found.getId());
            json.put("name", found.getName());
            json.put("amount", found.getAmount());
            json.put("category", found.getCategory());
            String responseBody = json.toString();

            out.println("HTTP/1.1 200 OK");
            out.println("Content-Type: application/json");
            out.println("Content-Length: " + responseBody.length());
            out.println();
            out.println(responseBody);
        }


        private void handleDeleteExpenseById(PrintWriter out, String path) {
            String[] pathParts = path.split("/");
            if (pathParts.length < 3) {
                sendErrorResponse(out, 400, "Bad Request: Invalid path");
                return;
            }
            int id;
            try {
                id = Integer.parseInt(pathParts[2]);
            } catch (NumberFormatException e) {
                sendErrorResponse(out, 400, "Bad Request: ID must be a number");
                return;
            }

            // Find and remove expense (thread‑safe)
            boolean removed;
            synchronized (MultiThreadedEchoServer.class) {
                // We need to find the index to remove by id
                int indexToRemove = -1;
                for (int i = 0; i < expenses.size(); i++) {
                    if (expenses.get(i).getId() == id) {
                        indexToRemove = i;

                        break;
                    }
                }
                if (indexToRemove != -1) {
                    expenses.remove(indexToRemove);
                    removed = true;
                    saveToFile();
                } else {
                    removed = false;
                }
            }

            if (!removed) {
                sendErrorResponse(out, 404, "Expense not found");
                return;
            }

            // 204 No Content – no response body
            out.println("HTTP/1.1 204 No Content");
            out.println("Content-Length: 0");
            out.println(); // blank line ends headers
        }

        private void handlePutExpenseById(BufferedReader in, PrintWriter out, String path, int contentLength) throws IOException {
            // Extract ID from path (unchanged)
            String[] pathParts = path.split("/");
            if (pathParts.length < 3) {
                sendErrorResponse(out, 400, "Bad Request: Invalid path");
                return;
            }
            int id;
            try {
                id = Integer.parseInt(pathParts[2]);
            } catch (NumberFormatException e) {
                sendErrorResponse(out, 400, "Bad Request: ID must be a number");
                return;
            }

            // Read body
            if (contentLength <= 0) {
                sendErrorResponse(out, 400, "Bad Request: Missing body");
                return;
            }
            char[] bodyChars = new char[contentLength];
            int bytesRead = in.read(bodyChars, 0, contentLength);
            String body = new String(bodyChars, 0, bytesRead);
            System.out.println("PUT body: " + body);

            // Parse JSON
            JSONObject json;
            try {
                json = new JSONObject(body);
            } catch (JSONException e) {
                sendErrorResponse(out, 400, "Bad Request: Invalid JSON");
                return;
            }

            // Extract fields
            String name;
            String category;
            double amount;
            try {
                name = json.getString("name");
                category = json.getString("category");
                amount = json.getDouble("amount");
            } catch (JSONException e) {
                sendErrorResponse(out, 400, "Bad Request: Missing required field: " + e.getMessage());
                return;
            }

            // Validate
            String validationError = validateExpenseData(name, category, amount);
            if (validationError != null) {
                sendErrorResponse(out, 400, "Bad Request: " + validationError);
                return;
            }

            // Update (synchronized block)
            boolean updated = false;
            synchronized (MultiThreadedEchoServer.class) {
                for (int i = 0; i < expenses.size(); i++) {
                    Expense e = expenses.get(i);
                    if (e.getId() == id) {
                        Expense updatedExpense = new Expense(id, name, amount, category);
                        expenses.set(i, updatedExpense);
                        updated = true;
                        saveToFile();

                        // Build response JSON
                        JSONObject responseJson = new JSONObject();
                        responseJson.put("id", id);
                        responseJson.put("name", name);
                        responseJson.put("amount", amount);
                        responseJson.put("category", category);
                        String responseBody = responseJson.toString();

                        out.println("HTTP/1.1 200 OK");
                        out.println("Content-Type: application/json");
                        out.println("Content-Length: " + responseBody.length());
                        out.println();
                        out.println(responseBody);
                        break;
                    }
                }
            }

            if (!updated) {
                sendErrorResponse(out, 404, "Expense not found");
            }
        }

        private void sendErrorResponse(PrintWriter out, int statusCode, String message) {
            String body = "{\"error\":\"" + message + "\"}";
            out.println("HTTP/1.1 " + statusCode + " " + message);
            out.println("Content-Type: application/json");
            out.println("Content-Length: " + body.length());
            out.println();
            out.println(body);
        }
    }
}