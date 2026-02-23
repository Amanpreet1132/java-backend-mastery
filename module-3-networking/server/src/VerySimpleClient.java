import java.io.*;
import java.net.*;

public class VerySimpleClient {
    public static void main(String[] args) throws IOException {
        // 1. Connect to the server at localhost, port 1234
        Socket socket = new Socket("localhost", 1234);

        // 2. Get an input stream to read data from the server
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        // 3. Read a line of text sent by the server
        String message = in.readLine();
        System.out.println("Client received: " + message);

        // 4. Close the socket
        socket.close();
    }
}