import com.google.gson.Gson;

import java.io.IOException;
import java.net.*;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class Server {
    // Параметри для налаштування сервера
    private static final int PORT = 12345; // Порт для прослуховування сервером
    private static final String SERVER_IP = "192.168.0.100"; // IP-адреса сервера
    private static final Gson gson = new Gson(); // JSON-бібліотека для роботи з об’єктами
    private static final Map<String, ClientInfo> clients = new HashMap<>(); // Карта для зберігання клієнтів
    private static final DatagramSocket serverSocket;

    static {
        // Ініціалізація сокету для сервера
        try {
            serverSocket = new DatagramSocket(PORT, InetAddress.getByName(SERVER_IP));
        } catch (SocketException | UnknownHostException e) {
            throw new RuntimeException(e); // Обробка помилки при створенні сокету
        }
    }

    public static void main(String[] args) {
        try {
            log("Server", "Server started at " + SERVER_IP + ":" + PORT + ", waiting for commands...");

            byte[] receiveBuffer = new byte[1024]; // Буфер для отримання даних від клієнтів

            while (true) {
                DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                serverSocket.receive(receivePacket); // Отримання пакету від клієнта

                // Створення ключа для клієнта на основі його IP та порту
                String clientKey = receivePacket.getAddress().toString() + ":" + receivePacket.getPort();

                // Реєстрація нового клієнта, якщо його ще немає в списку
                if (!clients.containsKey(clientKey)) {
                    clients.put(clientKey, new ClientInfo(receivePacket.getAddress(), receivePacket.getPort()));
                    log("Server", "New client registered: " + clientKey);
                    DatabaseManager.saveClient(receivePacket.getAddress().toString().substring(1), receivePacket.getPort());
                }

                handleMessage(receivePacket); // Обробка повідомлення від клієнта
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Метод обробки повідомлення від клієнта
    private static void handleMessage(DatagramPacket receivePacket)  {
        String clientMessage = new String(receivePacket.getData(), 0, receivePacket.getLength());

        // Обробка типу повідомлення "RESPONSE_TO" окремо
        if(clientMessage.startsWith("RESPONSE_TO")) {
            handleResponse(clientMessage, receivePacket.getAddress().toString(), receivePacket.getPort());
            return;
        }

        // Декодування JSON-повідомлення
        Message message = gson.fromJson(clientMessage, Message.class);

        switch (message.getType()) {
            case "LOGIN":
                // Обробка запиту на логін
                if (DatabaseManager.isValidUser(message.getMessage(), message.getSecondMessage())) {
                    sendMessage(receivePacket.getAddress(), receivePacket.getPort(), gson.toJson(new Message("LOGIN_SUCCESS")));
                } else {
                    sendMessage(receivePacket.getAddress(), receivePacket.getPort(), gson.toJson(new Message("LOGIN_DENIED")));
                }
                break;

            case "REGISTER":
                // Обробка запиту на реєстрацію
                if (DatabaseManager.registerUser(message.getMessage(), message.getSecondMessage())) {
                    sendMessage(receivePacket.getAddress(), receivePacket.getPort(), gson.toJson(new Message("REGISTER_SUCCESS")));
                } else {
                    sendMessage(receivePacket.getAddress(), receivePacket.getPort(), gson.toJson(new Message("REGISTER_FAILED")));
                }
                break;

            case "PING":
                // Відповідь на PING-запит
                sendMessage(receivePacket.getAddress(), receivePacket.getPort(),
                        gson.toJson(new Message("PONG")));
                log("Server", "Sent response PONG to %s:%d".formatted(receivePacket.getAddress().toString(), receivePacket.getPort()));
                break;

            case "CONNECT_REQUEST":
                // Обробка запиту на підключення до іншого клієнта
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
                // Обробка повідомлення про прийняття запиту на підключення
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
                // Обробка повідомлення про відхилення запиту на підключення
                String denyTargetIP = message.getTargetIP().substring(1);
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
                // Обробка запиту на виконання команди
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

            default:
                log("Server", "Unknown message type %s from %s:%d: ".formatted(message.getType(), receivePacket.getAddress().toString(), receivePacket.getPort()));
                break;
        }
    }

    // Метод для пошуку клієнта за IP та портом
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

    // Метод для обробки відповіді клієнта
    private static void handleResponse(String message, String senderIP, int senderPort) {
        String[] parts = message.split(":", 5);
        String targetIP = parts[1];
        int targetPort = Integer.parseInt(parts[2]);
        String responseCommand = parts[3];
        String response = parts[4];

        ClientInfo clientToResponse = findClient(targetIP, targetPort);
        if (clientToResponse != null) {
            log("Server", "Sending response from %s:%d to client %s".formatted(targetIP, targetPort, clientToResponse));
            try {
                DatabaseManager.saveCommand(senderIP.substring(1), senderPort,
                        targetIP, targetPort, responseCommand, response);
                if (!sendMessage(clientToResponse, response)) {
                    logErr("Server", "Cannot send response from %s:%d to client %s".formatted(targetIP, targetPort, clientToResponse));
                }
            } catch (SQLException e) {
                logErr("Server", "Cannot save command to DB " + e);
            }
        } else {
            log("Server", "Target client %s:%d not found".formatted(targetIP, targetPort));
        }
    }

    // Метод для надсилання повідомлення клієнту за IP та портом
    private static boolean sendMessage(InetAddress clientIP, int clientPort, String message) {
        try {
            byte[] sendBuffer = message.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, clientIP, clientPort);
            serverSocket.send(sendPacket);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Метод для надсилання повідомлення клієнту, використовуючи об'єкт ClientInfo
    private static boolean sendMessage(ClientInfo client, String message) {
        return sendMessage(client.getAddress(), client.getPort(), message);
    }

    // Метод для логування подій
    private static void log(String tag, String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        System.out.printf("%s [%s]: %s%n", timestamp, tag, message);
    }

    // Метод для логування помилок
    private static void logErr(String tag, String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        System.err.printf("%s [%s]: %s%n", timestamp, tag, message);
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
