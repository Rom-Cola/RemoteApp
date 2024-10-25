import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.DatagramSocket;

public class ClientApp {
    public static void main(String[] args) {
        try {
            Client client = new Client();

            client.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
