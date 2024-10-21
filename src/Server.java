import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

public class Server {
    private static final int PORT = 12345;
    private static final String SERVER_IP = "192.168.0.100";


    private static Map<String, ClientInfo> clients = new HashMap<>();
    private static DatagramSocket serverSocket;

    public static void main(String[] args) {
        try {
            InetAddress serverAddress = InetAddress.getByName(SERVER_IP);
            serverSocket = new DatagramSocket(PORT, serverAddress);
            System.out.println("Server started at " + SERVER_IP + ":" + PORT + ", waiting for commands...");

            byte[] receiveBuffer = new byte[1024];

            while (true) {
                DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                serverSocket.receive(receivePacket);

                String clientMessage = new String(receivePacket.getData(), 0, receivePacket.getLength());
                String clientKey = receivePacket.getAddress().toString() + ":" + receivePacket.getPort();

                if (!clients.containsKey(clientKey)) {
                    clients.put(clientKey, new ClientInfo(receivePacket.getAddress(), receivePacket.getPort()));
                    System.out.println("New client registered: " + clientKey);
                    DatabaseManager.saveClient(receivePacket.getAddress().toString().substring(1), receivePacket.getPort());
                }

                // Обробка команд
                if (clientMessage.equals("PING")) {
                    String responseMessage = "PONG";
                    sendMessage(receivePacket.getAddress(), receivePacket.getPort(), responseMessage);
                    System.out.println("Sent response: PONG");
                } else if (clientMessage.startsWith("SEND_TO:")) {
                    String[] parts = clientMessage.split(":");
                    String targetIP = parts[1];
                    int targetPort = Integer.parseInt(parts[2]);
                    String command = parts[3];

                    String message = "%s:%s:%d:%s".formatted("EXECUTE", receivePacket.getAddress(), receivePacket.getPort(), command);
                    ClientInfo targetClient = findClient(targetIP, targetPort);
                    if (targetClient != null) {
                        System.out.println("Sending execute to client: " + targetClient);
                        sendMessage(targetClient, message);
                    } else {
                        System.out.println("Target client not found.");
                    }
                } else if (clientMessage.startsWith("RESPONSE_TO:")) {
                    String[] parts = clientMessage.split(":", 5);
                    String targetIP = parts[1];
                    int targetPort = Integer.parseInt(parts[2]);
                    String command = parts[3];
                    String response = parts[4];
                    ClientInfo targetClient = findClient(targetIP, targetPort);
                    if (targetClient != null) {
                        System.out.println("Sending response to client: " + targetClient);
                        DatabaseManager.saveCommand(receivePacket.getAddress().toString().substring(1), receivePacket.getPort(),
                                targetIP, targetPort, command, response);
                        sendMessage(targetClient, response);
                    } else {
                        System.out.println("Target client not found.");
                    }
                } else {
                    System.out.printf("Command \"%s\" not found\n", clientMessage);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static ClientInfo findClient(String ip, int port) {
        ip = "/" + ip;
        for (String key : clients.keySet()) {
            ClientInfo clientInfo = clients.get(key);
            if (clientInfo.getAddress().toString().equals(ip) && clientInfo.getPort() == port) {
                System.out.printf("Found: %s:%d\n", clientInfo.getAddress(), clientInfo.getPort());
                return clientInfo;
            }
        }
        return null;
    }

    private static void sendMessage(ClientInfo clientInfo, String message) throws IOException {
        byte[] sendBuffer = message.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length,
                clientInfo.getAddress(), clientInfo.getPort());
        serverSocket.send(sendPacket);
    }

    private static void sendMessage(InetAddress inetAddress, int port, String message) throws IOException {
        byte[] sendBuffer = message.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length,
                inetAddress, port);
        serverSocket.send(sendPacket);
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
        return "Client:" + address + ":" + port;
    }
}
