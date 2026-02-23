import java.io.*;
import java.net.*;

public class SimpleServer {
    public static void main(String[] args) throws IOException {
        // 1. Choose a port number (like a door)
        int port = 1234;

        // 2. Create a ServerSocket that will listen on that port
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Server is listening on port " + port);

        // 3. Wait for a client to connect
        //    The program will pause here until someone connects
        Socket clientSocket = serverSocket.accept();
        System.out.println("Client connected!");

        // 4. Once connected, we can get an output stream to send data to the client
        //    PrintWriter lets us write text easily
        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

        // 5. Send a message
        out.println("Hello from the server!");

        // 6. Close everything (good practice)
        out.close();
        clientSocket.close();
        serverSocket.close();
    }
}