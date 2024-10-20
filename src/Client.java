import java.io.BufferedReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class Client {
    private List<Connection> connections = new ArrayList<>();
    private DatagramSocket clientSocket;
    private BufferedReader reader;
    private InetAddress serverAddress;
    private int serverPort = 12345;

    public Client(DatagramSocket clientSocket, BufferedReader reader) throws Exception {
        this.clientSocket = clientSocket;
        this.reader = reader;
        this.serverAddress = InetAddress.getByName("192.168.0.100");
    }

    public void start() throws Exception {
        while (true) {
            System.out.println("Спроба підключитись до серверу...");
            if (!connectToServer()) {
                continue;
            }
            System.out.println("Підключення успішне!");
            while (true) {
                menu();
            }
        }
    }

    // Метод для відображення меню і обробки вибору користувача
    private void menu() throws Exception {
        System.out.println("=== Консольне меню ===");
        System.out.println("1. Підключитись до іншого клієнта");
        System.out.println("2. Надати підключення до свого клієнта");
        System.out.println("0. Вийти");

        String choice = reader.readLine();
        switch (choice) {
            case "1":
                connectToAnotherClient();
                break;
            case "2":
                allowConnectionToYourClient();
                break;
            case "0":
                System.out.println("Вихід з програми.");
                return; // Завершення програми
            default:
                System.out.println("Невірний вибір, спробуйте ще раз.");
        }
    }

    // Метод для спроби підключення до сервера
    public boolean connectToServer() {
        try {
            String testMessage = "PING";
            byte[] sendBuffer = testMessage.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, serverAddress, serverPort);
            clientSocket.send(sendPacket);

            byte[] receiveBuffer = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            clientSocket.setSoTimeout(5000); // Очікувати відповідь 5 секунд
            clientSocket.receive(receivePacket);

            String serverResponse = new String(receivePacket.getData(), 0, receivePacket.getLength());
            return serverResponse.equals("PONG"); // Перевірка на правильну відповідь від сервера

        } catch (Exception e) {
            System.out.println("Не вдалося підключитися до сервера.");
            return false;
        }
    }

    // Метод для підключення до іншого клієнта
    public void connectToAnotherClient() throws Exception {
        System.out.println("Введіть IP клієнта для підключення:");
        String targetIP = reader.readLine();
        System.out.println("Введіть порт клієнта:");
        int targetPort = Integer.parseInt(reader.readLine());
        System.out.println("Введіть команду для виконання:");
        String command = reader.readLine();

        String message = "SEND_TO:" + targetIP + ":" + targetPort + ":" + command;
        sendMessageToServer(message);

        String serverResponse = receiveMessageFromServer();
        System.out.println("Результат виконання команди: " + serverResponse);
    }

    // Метод для надання підключення до свого клієнта
    public void allowConnectionToYourClient() throws Exception {
        System.out.println("Очікування підключень...");
        clientSocket.setSoTimeout(0);

        while (true) {
            String clientMessage = receiveMessageFromServer();
            if (clientMessage.startsWith("EXECUTE")) {
                String[] parts = clientMessage.split(":");
                String senderIP = "/" + parts[1];
                int senderPort = Integer.parseInt(parts[2]);
                String command = parts[3];

                System.out.printf("Отримана команда від %s:%d : %s\n", senderIP, senderPort, command);

                String result = CommandExecutor.executeCommand(clientMessage);
                sendMessageToServer("RESPONSE_TO:%s:%d:%s".formatted(senderIP, senderPort, result));
            }
        }
    }

    // Метод для відправки повідомлення на сервер
    private void sendMessageToServer(String message) throws Exception {
        byte[] sendBuffer = message.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, serverAddress, serverPort);
        clientSocket.send(sendPacket);
    }

    // Метод для отримання повідомлення від сервера
    private String receiveMessageFromServer() throws Exception {
        byte[] receiveBuffer = new byte[1024];
        DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
        clientSocket.receive(receivePacket);
        return new String(receivePacket.getData(), 0, receivePacket.getLength());
    }
}
