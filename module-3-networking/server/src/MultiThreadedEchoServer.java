import java.io.*;
import java.net.*;
import java.util.*;

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
            try (PrintWriter fileOut = new PrintWriter(new FileWriter(DATA_FILE))) {
                for (Expense e : expenses) {
                    String line = String.format(
                            "{\"id\":%d,\"name\":\"%s\",\"amount\":%.2f,\"category\":\"%s\"}",
                            e.getId(), e.getName(), e.getAmount(), e.getCategory()
                    );
                    fileOut.println(line);
                }
            } catch (IOException e) {
                System.out.println("Error saving to file: " + e.getMessage());
            }
        }
    }

    private static String parseJsonValue(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start == -1) return "";
        start += search.length();
        while (start < json.length() && json.charAt(start) <= ' ') start++;
        if (json.charAt(start) == '"') {
            int end = json.indexOf('"', start + 1);
            return json.substring(start + 1, end);
        } else {
            int end = json.indexOf(',', start);
            if (end == -1) end = json.indexOf('}', start);
            return json.substring(start, end).trim();
        }
    }

    private static void loadFromFile() {
        File file = new File(DATA_FILE);
        if (!file.exists()) {
            // No saved data – create sample data
            expenses = new ArrayList<>();
            nextId = 1;
            expenses.add(new Expense(nextId++, "Coffee", 5.50, "Food"));
            expenses.add(new Expense(nextId++, "Movie", 12.00, "Entertainment"));
            expenses.add(new Expense(nextId++, "Uber", 24.50, "Transport"));
            saveToFile(); // save sample data so next start loads them
            return;
        }

        List<Expense> loaded = new ArrayList<>();
        int maxId = 0;

        try (BufferedReader fileReader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = fileReader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                String idStr = parseJsonValue(line, "id");
                String name = parseJsonValue(line, "name");
                String amountStr = parseJsonValue(line, "amount");
                String category = parseJsonValue(line, "category");

                if (idStr.isEmpty() || name.isEmpty() || amountStr.isEmpty() || category.isEmpty()) {
                    System.out.println("Skipping malformed line: " + line);
                    continue;
                }

                int id = Integer.parseInt(idStr);
                double amount = Double.parseDouble(amountStr);

                loaded.add(new Expense(id, name, amount, category));
                if (id > maxId) maxId = id;
            }
        } catch (IOException e) {
            System.out.println("Error loading from file: " + e.getMessage());
            // Fall back to sample data on error
            loaded.clear();
            loaded.add(new Expense(1, "Coffee", 5.50, "Food"));
            loaded.add(new Expense(2, "Movie", 12.00, "Entertainment"));
            loaded.add(new Expense(3, "Uber", 24.50, "Transport"));
            maxId = 3;
        }

        // Replace the static list with the loaded data
        synchronized (MultiThreadedEchoServer.class) {
            expenses = loaded;
            nextId = maxId + 1;
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
            // Determine which expenses to include
            List<Expense> result = new ArrayList<>();
            String categoryFilter = queryParams.get("category");

            synchronized (MultiThreadedEchoServer.class) {
                if (categoryFilter != null && !categoryFilter.isEmpty()) {
                    // Filter by category (case‑sensitive)
                    for (Expense e : expenses) {
                        if (categoryFilter.equals(e.getCategory())) {
                            result.add(e);
                        }
                    }
                } else {
                    // No filter – return all
                    result.addAll(expenses);
                }
            }

            // Build JSON array using the result list
            StringBuilder json = new StringBuilder();
            json.append("[\n");
            for (int i = 0; i < result.size(); i++) {
                Expense e = result.get(i);
                json.append("  {")
                        .append("\"id\":").append(e.getId()).append(",")
                        .append("\"name\":\"").append(e.getName()).append("\",")
                        .append("\"amount\":").append(e.getAmount()).append(",")
                        .append("\"category\":\"").append(e.getCategory()).append("\"")
                        .append("}");
                if (i < result.size() - 1) {
                    json.append(",");
                }
                json.append("\n");
            }
            json.append("]");

            String responseBody = json.toString();

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

            // Read the request body
            char[] bodyChars = new char[contentLength];
            int bytesRead = in.read(bodyChars, 0, contentLength);
            String body = new String(bodyChars, 0, bytesRead);
            System.out.println("Received body: " + body);

            // Very primitive JSON parsing (for learning only)
            String name = extractJsonValue(body, "name");
            String category = extractJsonValue(body, "category");
            double amount;
            try {
                amount = Double.parseDouble(extractJsonValue(body, "amount"));
            } catch (NumberFormatException e) {
                sendErrorResponse(out, 400, "Bad Request: Invalid amount");
                return;
            }

            // Validate the data
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
            String responseBody = String.format(
                    "{\"id\":%d,\"name\":\"%s\",\"amount\":%.2f,\"category\":\"%s\"}",
                    newId, name, amount, category);

            out.println("HTTP/1.1 201 Created");
            out.println("Content-Type: application/json");
            out.println("Content-Length: " + responseBody.length());
            out.println();
            out.println(responseBody);
        }

        // Helper to extract a value from a simple JSON string (very limited)
        private String extractJsonValue(String json, String key) {
            String search = "\"" + key + "\":";
            int start = json.indexOf(search);
            if (start == -1) return "";
            start += search.length();
            // Skip whitespace
            while (start < json.length() && json.charAt(start) <= ' ') start++;
            if (json.charAt(start) == '"') {
                int end = json.indexOf('"', start + 1);
                return json.substring(start + 1, end);
            } else {
                int end = json.indexOf(',', start);
                if (end == -1) end = json.indexOf('}', start);
                return json.substring(start, end).trim();
            }
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
            String idStr = pathParts[2];
            int id;
            try {
                id = Integer.parseInt(idStr);
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

            String responseBody = String.format(
                    "{\"id\":%d,\"name\":\"%s\",\"amount\":%.2f,\"category\":\"%s\"}",
                    found.getId(), found.getName(), found.getAmount(), found.getCategory()
            );

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
            // 1. Extract ID from path (e.g., "/expenses/2" -> "2")
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

            // 2. Read the request body (must be present)
            if (contentLength <= 0) {
                sendErrorResponse(out, 400, "Bad Request: Missing body");
                return;
            }
            char[] bodyChars = new char[contentLength];
            int bytesRead = in.read(bodyChars, 0, contentLength);
            String body = new String(bodyChars, 0, bytesRead);
            System.out.println("PUT body: " + body);

            // 3. Parse the JSON to get updated fields
            String name = extractJsonValue(body, "name");
            String category = extractJsonValue(body, "category");
            double amount;
            try {
                amount = Double.parseDouble(extractJsonValue(body, "amount"));
            } catch (NumberFormatException e) {
                sendErrorResponse(out, 400, "Bad Request: Invalid amount");
                return;
            }

            String validationError = validateExpenseData(name, category, amount);
            if (validationError != null) {
                sendErrorResponse(out, 400, "Bad Request: " + validationError);
                return;
            }

            // 4. Thread‑safe update: find and replace the expense
            boolean updated = false;
            synchronized (MultiThreadedEchoServer.class) {
                for (int i = 0; i < expenses.size(); i++) {
                    Expense e = expenses.get(i);
                    if (e.getId() == id) {
                        // Create a new Expense object with the same ID but updated fields
                        Expense updatedExpense = new Expense(id, name, amount, category);
                        expenses.set(i, updatedExpense);
                        saveToFile();
                        updated = true;


                        // Build the response JSON (the updated expense)
                        String responseBody = String.format(
                                "{\"id\":%d,\"name\":\"%s\",\"amount\":%.2f,\"category\":\"%s\"}",
                                id, name, amount, category);

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