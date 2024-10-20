import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.DatagramSocket;

public class ClientApp {
    public static void main(String[] args) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            DatagramSocket clientSocket = new DatagramSocket();

            Client client = new Client(clientSocket, reader);

            // Викликаємо метод для запуску меню
            client.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
