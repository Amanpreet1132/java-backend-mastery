import java.io.*;
import java.net.*;

public class JsonServer {
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(8080);
        System.out.println("JSON server running on port 8080");

        while (true) {
            Socket clientSocket = serverSocket.accept();
            System.out.println("Client connected");

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(
                    clientSocket.getOutputStream(), true);

            // 1. Read the request line
            String requestLine = in.readLine();
            System.out.println("Request: " + requestLine);


            // After reading requestLine
            String method = null;
            String path = null;
            if (requestLine != null) {
                String[] parts = requestLine.split(" ");
                if (parts.length >= 2) {
                    method = parts[0];
                    path = parts[1];
                }
            }

// Read headers and find Content-Length
            int contentLength = 0;
            String header;
            while ((header = in.readLine()) != null && !header.isEmpty()) {
                if (header.toLowerCase().startsWith("content-length:")) {
                    String[] headerParts = header.split(":");
                    contentLength = Integer.parseInt(headerParts[1].trim());
                }
            }

            String responseBody = "";



            if ("GET".equals(method) && path.startsWith("/expense/")) {
                String idPart = path.substring("/expense/".length());
                if ("1".equals(idPart)){
                    responseBody = "{\"id\":1,\"name\":\"Coffee\",\"amount\":5.50,\"category\":\"Food\"}";

                } else if ("2".equals(idPart)){
                    responseBody = "{\"id\":2,\"name\":\"Movie\",\"amount\":12.00,\"category\":\"Entertainment\"}";
                } else {
                    responseBody = "{\"error\":\"Expense not found\"}";
                }
            } else if ("GET".equals(method) && "/expenses".equals(path)) {
                // Return expenses list (same as before)
                responseBody = "["
                        + "{\"id\":1,\"name\":\"Coffee\",\"amount\":5.50,\"category\":\"Food\"},"
                        + "{\"id\":2,\"name\":\"Movie\",\"amount\":12.00,\"category\":\"Entertainment\"}"
                        + "]";
            } else if ("POST".equals(method) && "/expenses".equals(path)) {
                // Read the body
                char[] bodyChars = new char[contentLength];
                in.read(bodyChars, 0, contentLength);
                String body = new String(bodyChars);
                System.out.println("New expense: " + body);

                // For now, just acknowledge
                responseBody = "{\"status\":\"creaaaaaaaaaaaaaaaaaaated\"}";
            } else {
                responseBody = "{\"error\":\"Not fouuuuund\"}";
            }

// Send response (same as before)
            out.println("HTTP/1.1 200 OK");
            out.println("Content-Type: application/json");
            out.println("Content-Length: " + responseBody.length());
            out.println();
            out.println(responseBody);


            clientSocket.close();
        }
    }
}
