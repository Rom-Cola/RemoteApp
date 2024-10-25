import com.google.gson.Gson;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class Server {
    private static final int PORT = 12345;
    private static final String SERVER_IP = "192.168.0.100";
    private static Gson gson = new Gson();


    private static Map<String, ClientInfo> clients = new HashMap<>();
    private static DatagramSocket serverSocket;

    public static void main(String[] args) {
        try {
            InetAddress serverAddress = InetAddress.getByName(SERVER_IP);
            serverSocket = new DatagramSocket(PORT, serverAddress);
            log("Server", "Server started at " + SERVER_IP + ":" + PORT + ", waiting for commands...");

            byte[] receiveBuffer = new byte[1024];

            while (true) {
                DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                serverSocket.receive(receivePacket);


                String clientKey = receivePacket.getAddress().toString() + ":" + receivePacket.getPort();

                if (!clients.containsKey(clientKey)) {
                    clients.put(clientKey, new ClientInfo(receivePacket.getAddress(), receivePacket.getPort()));
                    log("Server", "New client registered: " + clientKey);
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
        if(clientMessage.startsWith("RESPONSE_TO")) {
            handleResponse(clientMessage, receivePacket.getAddress().toString(), receivePacket.getPort());
            return;
        }
        Message message = gson.fromJson(clientMessage, Message.class);

        switch (message.getType()) {

            case "LOGIN":
                if (DatabaseManager.isValidUser(message.getMessage(), message.getSecondMessage())) {
                    sendMessage(receivePacket.getAddress(), receivePacket.getPort(), gson.toJson(new Message("LOGIN_SUCCESS")));
                } else {
                    sendMessage(receivePacket.getAddress(), receivePacket.getPort(), gson.toJson(new Message("LOGIN_DENIED")));
                }
                break;


            case "PING":
                sendMessage(receivePacket.getAddress(), receivePacket.getPort(),
                        gson.toJson(new Message("PONG")));
                log("Server", "Sent response PONG to %s:%d".formatted(receivePacket.getAddress().toString(), receivePacket.getPort()));
                break;

            case "CONNECT_REQUEST":
                String targetIP = message.getTargetIP();
                int targetPort = message.getTargetPort();
                String senderLogin = message.getSenderLogin();

                String connectionMessage = gson.toJson(new Message("CONNECT_REQUEST",
                        receivePacket.getAddress().toString(),
                        receivePacket.getPort(),
                        senderLogin,
                        null));

                ClientInfo targetClient = findClient(targetIP, targetPort);
                if (targetClient != null) {
                    sendMessage(targetClient, connectionMessage);
                    log("Server", "Forwarded connect request from %s:%d to %s:%d".formatted(receivePacket.getAddress().toString(), receivePacket.getPort(), targetIP, targetPort));
                } else {
                    log("Server", "Target client %s:%d not found".formatted(targetIP, targetPort));
                    sendMessage(receivePacket.getAddress(), receivePacket.getPort(),
                            gson.toJson(new Message("NO_SUCH_USER")));
                }
                break;

            case "CONNECT_ACCEPTED":
                String acceptTargetIP = message.getTargetIP().substring(1);
                int acceptTargetPort = message.getTargetPort();
                String acceptMessage = gson.toJson(new Message("CONNECT_ACCEPTED",
                        receivePacket.getAddress().toString(),
                        receivePacket.getPort(),
                        message.getSenderLogin(),
                        null));

                ClientInfo acceptClient = findClient(acceptTargetIP, acceptTargetPort);
                if (acceptClient != null) {
                    sendMessage(acceptClient, acceptMessage);
                    log("Server", "Forwarded connect accept from %s to %s:%d"
                            .formatted(receivePacket.getAddress() + ":" + receivePacket.getPort(), acceptTargetIP, acceptTargetPort));

                } else {
                    log("Server", "Target client %s:%d not found".formatted(acceptTargetPort, acceptTargetPort));
                }
                break;

            case "CONNECT_DENIED":
                String denyTargetIP = message.getTargetIP();
                int denyTargetPort = message.getTargetPort();
                String denyMessage = gson.toJson(new Message("CONNECT_DENIED",
                        receivePacket.getAddress().toString(),
                        receivePacket.getPort(),
                        message.getSenderLogin(),
                        null));

                ClientInfo denyClient = findClient(denyTargetIP, denyTargetPort);
                if (denyClient != null) {
                    sendMessage(denyClient, denyMessage);
                    log("Server", "Forwarded connect denied from %s to %s:%d"
                            .formatted(receivePacket.getAddress() + ":" + receivePacket.getPort(), denyTargetIP, denyTargetPort));
                } else {
                    log("Server", "Target client %s:%d not found".formatted(denyTargetIP, denyTargetPort));
                }
                break;

            case "SEND_TO":
                String sendToTargetIP = message.getTargetIP();
                int sendToTargetPort = message.getTargetPort();
                String command = message.getMessage();

                String execMessage = gson.toJson(new Message("EXECUTE",
                        receivePacket.getAddress().toString(),
                        receivePacket.getPort(),
                        message.getSenderLogin(),
                        command));

                ClientInfo clientToSend = findClient(sendToTargetIP, sendToTargetPort);
                if (clientToSend != null) {
                    log("Server", "Sending execute from %s:%d to client %s".formatted(receivePacket.getAddress().toString(), receivePacket.getPort(), clientToSend));
                    sendMessage(clientToSend, execMessage);
                } else {
                    log("Server", "Target client %s:%d not found".formatted(sendToTargetIP, sendToTargetPort));
                }
                break;

            case "RESPONSE_TO":
//                String responseToTargetIP = message.getTargetIP();
//                int responseToTargetPort = message.getTargetPort();
//                String responseCommand = message.getMessage();
//                String response = message.getMessage();
//
//                ClientInfo responseToClient = findClient(responseToTargetIP, responseToTargetPort);
//                if (responseToClient != null) {
//                    System.out.println("Sending response to client: " + responseToClient);
//                    // Зберігаємо результат виконання команди в базі даних
//                    DatabaseManager.saveCommand(receivePacket.getAddress().toString(), receivePacket.getPort(), responseToTargetIP, responseToTargetPort, responseCommand, response);
//                    sendMessage(responseToClient, response);
//                } else {
//                    System.out.println("Target client for response not found.");
//                }
//                break;

            default:
                log("Server", "Unknown message type %s from %s:%d: ".formatted(message.getType(), receivePacket.getAddress().toString(), receivePacket.getPort()));
                System.out.println();
                break;
        }
    }


    private static ClientInfo findClient(String ip, int port) {
        ip = "/" + ip;
        for (String key : clients.keySet()) {
            ClientInfo clientInfo = clients.get(key);
            if (clientInfo.getAddress().toString().equals(ip) && clientInfo.getPort() == port) {
                return clientInfo;
            }
        }
        return null;
    }

    private static void handleResponse(String message, String senderIP, int senderPort) throws SQLException, IOException {
        String[] parts = message.split(":", 5);
        String targetIP = parts[1];
        int targetPort = Integer.parseInt(parts[2]);
        String responseCommand = parts[3];
        String response = parts[4];

        ClientInfo clientToResponse = findClient(targetIP, targetPort);
        if (clientToResponse != null) {
            log("Server", "Sending response from %s:%d to client %s".formatted(targetIP, targetPort, clientToResponse));
            DatabaseManager.saveCommand(senderIP.substring(1), senderPort,
                    targetIP, targetPort, responseCommand, response);
            sendMessage(clientToResponse, response);
        } else {
            log("Server", "Target client %s:%d not found".formatted(targetIP, targetPort));
        }
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
    private static void log(String speaker, String message) {
        LocalDateTime currentDateTime = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String formattedDateTime = "[" + currentDateTime.format(formatter) + "]";
        System.out.print(formattedDateTime);
        System.out.printf(" %s: %s\n", speaker, message);
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
        return address.toString().substring(1) + ":" + port;
    }
}
