import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.sql.SQLException;
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


                String clientKey = receivePacket.getAddress().toString() + ":" + receivePacket.getPort();

                if (!clients.containsKey(clientKey)) {
                    clients.put(clientKey, new ClientInfo(receivePacket.getAddress(), receivePacket.getPort()));
                    System.out.println("New client registered: " + clientKey);
                    DatabaseManager.saveClient(receivePacket.getAddress().toString().substring(1), receivePacket.getPort());
                }

                handleMessage(receivePacket);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handleMessage(DatagramPacket receivePacket) throws IOException, SQLException {
        String clientMessage = new String(receivePacket.getData(), 0, receivePacket.getLength());
        String[] parts = clientMessage.split(":", 5);

        String targetIP, connectionMessage, senderLogin;
        int targetPort;
        ClientInfo targetClient;
        switch (parts[0]) {
            case "PING":
                String responseMessage = "PONG";
                sendMessage(receivePacket.getAddress(), receivePacket.getPort(), responseMessage);
                System.out.println("Sent response: PONG");
                break;

            case "CONNECT_REQUEST":
                targetIP = parts[1];
                targetPort = Integer.parseInt(parts[2]);
                senderLogin = parts[3];

                connectionMessage = "CONNECT_REQUEST:" + receivePacket.getAddress() + ":" + receivePacket.getPort() + ":" + senderLogin;

                targetClient = findClient(targetIP, targetPort);
                if (targetClient != null) {
                    sendMessage(targetClient, connectionMessage);
                    System.out.printf("Forwarded connect request from %s to %s:%d\n",
                            receivePacket.getAddress() + ":" + receivePacket.getPort(), targetIP, targetPort);
                } else {
                    System.out.printf("Target client %s:%d not found\n", targetIP, targetPort);
                    sendMessage(receivePacket.getAddress(), receivePacket.getPort(), "NO_SUCH_USER");
                }
                break;
            case "CONNECT_ACCEPTED":
                targetIP = parts[1];
                targetPort = Integer.parseInt(parts[2]);

                connectionMessage = "CONNECT_ACCEPTED:" + receivePacket.getAddress() + ":" + receivePacket.getPort();

                targetClient = findClient(targetIP, targetPort);
                if (targetClient != null) {
                    sendMessage(targetClient, connectionMessage);
                    System.out.printf("Forwarded connect accept from %s to %s:%d\n",
                            receivePacket.getAddress() + ":" + receivePacket.getPort(), targetIP, targetPort);
                } else {
                    System.out.printf("Target client %s:%d not found\n", targetIP, targetPort);
                }
                break;
            case "CONNECT_DENIED":
                targetIP = parts[1];
                targetPort = Integer.parseInt(parts[2]);

                connectionMessage = "CONNECT_DENIED:" + receivePacket.getAddress() + ":" + receivePacket.getPort();

                targetClient = findClient(targetIP, targetPort);
                if (targetClient != null) {
                    sendMessage(targetClient, connectionMessage);
                    System.out.printf("Forwarded connect denied from %s to %s:%d\n",
                            receivePacket.getAddress() + ":" + receivePacket.getPort(), targetIP, targetPort);
                } else {
                    System.out.printf("Target client %s:%d not found\n", targetIP, targetPort);
                }
                break;

            case "SEND_TO":
                String sendToTargetIP = parts[1];
                int sendToTargetPort = Integer.parseInt(parts[2]);
                String command = parts[3];

                String message = "EXECUTE:" + receivePacket.getAddress() + ":" + receivePacket.getPort() + ":" + command;
                ClientInfo sendToClient = findClient(sendToTargetIP, sendToTargetPort);
                if (sendToClient != null) {
                    System.out.println("Sending execute to client: " + sendToClient);
                    sendMessage(sendToClient, message);
                } else {
                    System.out.println("Target client not found.");
                }
                break;

            case "RESPONSE_TO":
                String responseToTargetIP = parts[1].substring(1);
                int responseToTargetPort = Integer.parseInt(parts[2]);
                String responseCommand = parts[3];
                String response = parts[4];

                ClientInfo responseToClient = findClient(responseToTargetIP, responseToTargetPort);
                if (responseToClient != null) {
                    System.out.println("Sending response to client: " + responseToClient);
                    DatabaseManager.saveCommand(receivePacket.getAddress().toString().substring(1), receivePacket.getPort(),
                            responseToTargetIP, responseToTargetPort, responseCommand, response);
                    sendMessage(responseToClient, response);
                } else {
                    System.out.println("Target client not found: " + responseToTargetIP + ":" + responseToTargetPort);
                }
                break;

            case "LOGIN":
                String login = parts[1];
                String password = parts[2];

                if (DatabaseManager.isValidUser(login, password)) {
                    sendMessage(receivePacket.getAddress(), receivePacket.getPort(), "LOGIN_SUCCESS");
                } else {
                    sendMessage(receivePacket.getAddress(), receivePacket.getPort(), "LOGIN_FAILED");
                }
                break;

            case "REGISTER":
                String registerLogin = parts[1];
                String registerPassword = parts[2];

                if (DatabaseManager.registerUser(registerLogin, registerPassword)) {
                    sendMessage(receivePacket.getAddress(), receivePacket.getPort(), "REGISTER_SUCCESS");
                } else {
                    sendMessage(receivePacket.getAddress(), receivePacket.getPort(), "REGISTER_FAILED");
                }
                break;

            default:
                System.out.printf("Command \"%s\" not found\n", clientMessage);
                break;
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
