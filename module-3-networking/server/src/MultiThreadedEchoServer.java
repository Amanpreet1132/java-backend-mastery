import java.io.*;
import java.net.*;
import java.util.*;

public class MultiThreadedEchoServer {
    // In‑memory "database"
    private static List<Expense> expenses = new ArrayList<>();
    private static int nextId = 1;

    // Static initializer – runs once when class is loaded
    static {
        expenses.add(new Expense(nextId++, "Coffee", 5.50, "Food"));
        expenses.add(new Expense(nextId++, "Movie", 12.00, "Entertainment"));
        expenses.add(new Expense(nextId++, "Uber", 24.50, "Transport"));
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
                String path = parts[1];

                // 4. Route based on method + path
                if ("GET".equals(method) && "/expenses".equals(path)) {
                    handleGetExpenses(out);
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

        private void handleGetExpenses(PrintWriter out) {
            // Build JSON array manually
            StringBuilder json = new StringBuilder();
            json.append("[\n");
            for (int i = 0; i < expenses.size(); i++) {
                Expense e = expenses.get(i);
                json.append("  {")
                        .append("\"id\":").append(e.getId()).append(",")
                        .append("\"name\":\"").append(e.getName()).append("\",")
                        .append("\"amount\":").append(e.getAmount()).append(",")
                        .append("\"category\":\"").append(e.getCategory()).append("\"")
                        .append("}");
                if (i < expenses.size() - 1) {
                    json.append(",");
                }
                json.append("\n");
            }
            json.append("]");

            String responseBody = json.toString();

            // Send HTTP response
            out.println("HTTP/1.1 200 OK");
            out.println("Content-Type: application/json");
            out.println("Content-Length: " + responseBody.length());
            out.println(); // blank line
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
            double amount = Double.parseDouble(extractJsonValue(body, "amount"));

            // Thread‑safe addition
            int newId;
            synchronized (MultiThreadedEchoServer.class) {
                newId = nextId++;
                Expense newExpense = new Expense(newId, name, amount, category);
                expenses.add(newExpense);
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