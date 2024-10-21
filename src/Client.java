import java.io.BufferedReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;

public class Client {
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
            while (menu());
            break;
        }
    }

    private boolean menu() throws Exception {
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
                return false;
            default:
                System.out.println("Невірний вибір, спробуйте ще раз.");
        }
        return true;
    }

    public boolean connectToServer() {
        try {
            String testMessage = "PING";
            byte[] sendBuffer = testMessage.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, serverAddress, serverPort);
            clientSocket.send(sendPacket);

            byte[] receiveBuffer = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            clientSocket.setSoTimeout(5000);
            clientSocket.receive(receivePacket);

            String serverResponse = new String(receivePacket.getData(), 0, receivePacket.getLength());
            return serverResponse.equals("PONG");

        } catch (Exception e) {
            System.out.println("Не вдалося підключитися до сервера.");
            return false;
        }
    }

    public void connectToAnotherClient() throws Exception {
        System.out.println("Введіть IP клієнта для підключення:");
        String targetIP = reader.readLine();
        System.out.println("Введіть порт клієнта:");
        int targetPort = Integer.parseInt(reader.readLine());
        while (true) {
            System.out.println("Введіть команду для виконання (0 to exit):");
            String command = reader.readLine();
            if (command.equals("0")) {
                break;
            }
            sendCommandToAnotherClient(targetIP, targetPort, command);
            try {
                String serverResponse = receiveMessageFromServer();
                System.out.println("Результат виконання команди: " + serverResponse);
            } catch (IOException e) {
                System.out.println("Відповідь не прийшла, перевірте підключення та повторіть спробу");
            }

        }
    }

    public void allowConnectionToYourClient() throws Exception {
        Thread listenerThread = new Thread(() -> {
            System.out.println("Очікування підключень...");
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String clientMessage = receiveMessageFromServer();
                    if (clientMessage.startsWith("EXECUTE")) {
                        String[] parts = clientMessage.split(":");
                        String senderIP = parts[1].substring(1);
                        int senderPort = Integer.parseInt(parts[2]);
                        String command = parts[3];

                        System.out.printf("Отримана команда від %s:%d : %s\n", senderIP, senderPort, command);

                        String result = CommandExecutor.executeCommand(command);
                        System.out.println(result);
                        sendMessageToServer("RESPONSE_TO:%s:%d:%s:%s".formatted(senderIP, senderPort, command, result));
                    }
                } catch (IOException e) {
                    System.out.println("Очікування підключень...");
                }
            }
        });

        listenerThread.start();

        // В основному потоці продовжуємо читати команди
        while (true) {
            System.out.println("Введіть 'exit' для завершення очікування підключень:");
            String input = reader.readLine();
            if (input.equalsIgnoreCase("exit")) {
                listenerThread.interrupt();
                listenerThread.join();
                System.out.println("Очікування підключень завершено.");
                break;
            }
        }
    }

    private void sendCommandToAnotherClient(String targetIp, int targetPort, String command) throws IOException {
        String message = "SEND_TO:" + targetIp + ":" + targetPort + ":" + command;
        sendMessageToServer(message);
    }

    private void sendMessageToServer(String message) throws IOException {
        byte[] sendBuffer = message.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, serverAddress, serverPort);
        clientSocket.send(sendPacket);
    }

    private String receiveMessageFromServer() throws IOException {
        byte[] receiveBuffer = new byte[1024];
        DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
        clientSocket.setSoTimeout(10_000);
        clientSocket.receive(receivePacket);
        return new String(receivePacket.getData(), 0, receivePacket.getLength());
    }
}
