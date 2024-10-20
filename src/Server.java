import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

public class Server {
    private static final int PORT = 12345;
    private static final String SERVER_IP = "192.168.0.100"; // Ваша IP-адреса сервера

    // Зберігає інформацію про клієнтів: IP та порт
    private static Map<String, ClientInfo> clients = new HashMap<>();
    private static DatagramSocket serverSocket;

    public static void main(String[] args) {
        try {
            // Створення DatagramSocket з конкретною IP-адресою та портом
            InetAddress serverAddress = InetAddress.getByName(SERVER_IP);
            serverSocket = new DatagramSocket(PORT, serverAddress);
            System.out.println("Server started at " + SERVER_IP + ":" + PORT + ", waiting for commands...");

            byte[] receiveBuffer = new byte[1024];

            while (true) {
                DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                serverSocket.receive(receivePacket);

                String clientMessage = new String(receivePacket.getData(), 0, receivePacket.getLength());
                String clientKey = receivePacket.getAddress().toString() + ":" + receivePacket.getPort();

                // Зберігаємо клієнта в мапу, якщо він новий
                if (!clients.containsKey(clientKey)) {
                    clients.put(clientKey, new ClientInfo(receivePacket.getAddress(), receivePacket.getPort()));
                    System.out.println("New client registered: " + clientKey);
                }

                // Обробка команд
                if (clientMessage.equals("PING")) {
                    // Відправити відповідь "PONG"
                    String responseMessage = "PONG";
                    byte[] sendBuffer = responseMessage.getBytes();
                    DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length,
                            receivePacket.getAddress(), receivePacket.getPort());
                    serverSocket.send(sendPacket);
                    System.out.println("Sent response: PONG");
                } else if (clientMessage.startsWith("SEND_TO:")) {
                    String[] parts = clientMessage.split(":");
                    String targetIP = "/" + parts[1];
                    int targetPort = Integer.parseInt(parts[2]);
                    String command = parts[3];

                    // Шукаємо клієнта для відправки команди
                    ClientInfo targetClient = findClient(targetIP, targetPort);
                    if (targetClient != null) {
                        // Відправляємо команду іншому клієнту
                        byte[] sendBuffer = command.getBytes();
                        DatagramPacket sendPacket = new DatagramPacket(
                                sendBuffer, sendBuffer.length, targetClient.getAddress(), targetClient.getPort());
                        serverSocket.send(sendPacket);
                    } else {
                        System.out.println("Target client not found.");
                    }
                } else if (clientMessage.startsWith("RESPONSE_TO:")) {
                    String[] parts = clientMessage.split(":");
                    String targetIP = "/" + parts[1];
                    int targetPort = Integer.parseInt(parts[2]);
                    String command = parts[3];
                } else {
                    // Якщо це результат виконання команди, просто відображаємо його на сервері
                    System.out.println("Received result from client: " + clientMessage);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static ClientInfo findClient(String ip, int port) {
        System.out.printf("Target: %s:%d\n", ip, port);
        System.out.println("List" + clients);
        for (String key : clients.keySet()) {
            ClientInfo clientInfo = clients.get(key);
            if (clientInfo.getAddress().toString().equals(ip) && clientInfo.getPort() == port) {
                return clientInfo;
            }
        }
        return null;
    }


}

class ClientInfo {
    private final InetAddress address;
    private final int port;

    public ClientInfo(InetAddress address, int port) {
        this.address = address;
        this.port = port;
    }

    public InetAddress getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return "ClientInfo{" +
                "address=" + address +
                ", port=" + port +
                '}';
    }
}
