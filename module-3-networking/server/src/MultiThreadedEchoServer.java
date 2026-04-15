import java.io.*;
import java.net.*;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

public class MultiThreadedEchoServer {

    @SuppressWarnings("InfiniteLoopStatement")
    public static void main(String[] args) {
        // Wrap ServerSocket in try-with-resources to ensure it's closed properly
        try (ServerSocket serverSocket = new ServerSocket(12345)) {
            System.out.println("HTTP API server running on port 12345");
            System.out.println("Try: http://localhost:12345/expenses");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress());

                Thread clientHandler = new Thread(new ClientHandler(clientSocket));
                clientHandler.start();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    // Inner class that handles each client in its own thread
    static class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private final ExpenseDao expenseDao = new ExpenseDao();   // database access

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try (Socket clientSocket = this.clientSocket;
                 BufferedReader in = new BufferedReader(
                         new InputStreamReader(clientSocket.getInputStream()));
                 PrintWriter out = new PrintWriter(
                         clientSocket.getOutputStream(), true)) {

                String requestLine = in.readLine();
                if (requestLine == null) {
                    return;  // socket will be closed automatically by try-with-resources
                }
                System.out.println("Request: " + requestLine);

                int contentLength = -1;
                String header;
                while ((header = in.readLine()) != null && !header.isEmpty()) {
                    if (header.toLowerCase().startsWith("content-length:")) {
                        String[] parts = header.split(":");
                        if (parts.length > 1) {
                            contentLength = Integer.parseInt(parts[1].trim());
                        }
                    }
                }

                String[] parts = requestLine.split(" ");
                if (parts.length < 2) {
                    sendErrorResponse(out, 400, "Bad Request");
                    return;
                }
                String method = parts[0];
                String fullPath = parts[1];

                String path = fullPath;
                String queryString = "";
                int queryIndex = fullPath.indexOf('?');
                if (queryIndex != -1) {
                    path = fullPath.substring(0, queryIndex);
                    queryString = fullPath.substring(queryIndex + 1);
                }

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

                System.out.println("Client disconnected: " + clientSocket.getInetAddress());
            } catch (IOException e) {
                System.out.println("Error handling client: " + e.getMessage());
            }
        }

        // -------------------- GET all (with optional category filter) --------------------
        private void handleGetExpenses(PrintWriter out, Map<String, String> queryParams) {
            String categoryFilter = queryParams.get("category");
            List<Expense> all = expenseDao.getAllExpenses();

            List<Expense> result;
            if (categoryFilter != null && !categoryFilter.isEmpty()) {
                result = new ArrayList<>();
                for (Expense e : all) {
                    if (categoryFilter.equals(e.getCategory())) {
                        result.add(e);
                    }
                }
            } else {
                result = all;
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

        // -------------------- GET by ID --------------------
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

            Expense expense = expenseDao.getExpenseById(id);
            if (expense == null) {
                sendErrorResponse(out, 404, "Expense not found");
                return;
            }

            JSONObject json = new JSONObject();
            json.put("id", expense.getId());
            json.put("name", expense.getName());
            json.put("amount", expense.getAmount());
            json.put("category", expense.getCategory());
            String responseBody = json.toString();

            out.println("HTTP/1.1 200 OK");
            out.println("Content-Type: application/json");
            out.println("Content-Length: " + responseBody.length());
            out.println();
            out.println(responseBody);
        }

        // -------------------- POST create new expense --------------------
        private void handlePostExpenses(BufferedReader in, PrintWriter out, int contentLength) throws IOException {
            if (contentLength <= 0) {
                sendErrorResponse(out, 400, "Bad Request: Missing body");
                return;
            }

            char[] bodyChars = new char[contentLength];
            int bytesRead = in.read(bodyChars, 0, contentLength);
            String body = new String(bodyChars, 0, bytesRead);
            System.out.println("Received body: " + body);

            JSONObject json;
            try {
                json = new JSONObject(body);
            } catch (JSONException e) {
                sendErrorResponse(out, 400, "Bad Request: Invalid JSON");
                return;
            }

            String name, category;
            double amount;
            try {
                name = json.getString("name");
                category = json.getString("category");
                amount = json.getDouble("amount");
            } catch (JSONException e) {
                sendErrorResponse(out, 400, "Bad Request: Missing required field: " + e.getMessage());
                return;
            }

            String validationError = validateExpenseData(name, category, amount);
            if (validationError != null) {
                sendErrorResponse(out, 400, "Bad Request: " + validationError);
                return;
            }

            // Create Expense object without ID (database will generate it)
            Expense newExpense = new Expense(0, name, amount, category);
            Expense created = expenseDao.insertExpense(newExpense);
            if (created == null) {
                sendErrorResponse(out, 500, "Internal Server Error: Could not create expense");
                return;
            }

            JSONObject responseJson = new JSONObject();
            responseJson.put("id", created.getId());
            responseJson.put("name", created.getName());
            responseJson.put("amount", created.getAmount());
            responseJson.put("category", created.getCategory());
            String responseBody = responseJson.toString();

            out.println("HTTP/1.1 201 Created");
            out.println("Content-Type: application/json");
            out.println("Content-Length: " + responseBody.length());
            out.println();
            out.println(responseBody);
        }

        // -------------------- PUT update expense --------------------
        private void handlePutExpenseById(BufferedReader in, PrintWriter out, String path, int contentLength) throws IOException {
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

            if (contentLength <= 0) {
                sendErrorResponse(out, 400, "Bad Request: Missing body");
                return;
            }
            char[] bodyChars = new char[contentLength];
            int bytesRead = in.read(bodyChars, 0, contentLength);
            String body = new String(bodyChars, 0, bytesRead);
            System.out.println("PUT body: " + body);

            JSONObject json;
            try {
                json = new JSONObject(body);
            } catch (JSONException e) {
                sendErrorResponse(out, 400, "Bad Request: Invalid JSON");
                return;
            }

            String name, category;
            double amount;
            try {
                name = json.getString("name");
                category = json.getString("category");
                amount = json.getDouble("amount");
            } catch (JSONException e) {
                sendErrorResponse(out, 400, "Bad Request: Missing required field: " + e.getMessage());
                return;
            }

            String validationError = validateExpenseData(name, category, amount);
            if (validationError != null) {
                sendErrorResponse(out, 400, "Bad Request: " + validationError);
                return;
            }

            Expense updatedExpense = new Expense(id, name, amount, category);
            boolean success = expenseDao.updateExpense(id, updatedExpense);
            if (!success) {
                sendErrorResponse(out, 404, "Expense not found");
                return;
            }

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
        }

        // -------------------- DELETE expense --------------------
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

            Expense deleted = expenseDao.getExpenseById(id);
            if (deleted == null) {
                sendErrorResponse(out, 404, "Expense not found");
                return;
            }

            boolean removed = expenseDao.deleteExpense(id);
            if (!removed) {
                sendErrorResponse(out, 500, "Internal Server Error: Could not delete expense");
                return;
            }

            JSONObject json = new JSONObject();
            json.put("id", deleted.getId());
            json.put("name", deleted.getName());
            json.put("amount", deleted.getAmount());
            json.put("category", deleted.getCategory());
            String responseBody = json.toString();

            out.println("HTTP/1.1 200 OK");
            out.println("Content-Type: application/json");
            out.println("Content-Length: " + responseBody.length());
            out.println();
            out.println(responseBody);
        }

        // -------------------- Helper: validate expense data --------------------
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
            return null;
        }

        // -------------------- Helper: parse query string --------------------
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
                    queryParams.put(keyValue[0], "");
                }
            }
            return queryParams;
        }

        // -------------------- Helper: send error response --------------------
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