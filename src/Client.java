import java.io.BufferedReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Client {
    private DatagramSocket clientSocket;
    private BufferedReader reader;
    private InetAddress serverAddress;
    private int serverPort = 12345;
    private String login;

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
            menu();
            break;
        }
    }

    private void menu() throws Exception {
        String choice;
        boolean isAuth = false;
        while (!isAuth) {
            System.out.println("=== Консольне меню ===");

            System.out.println("1. Логін");
            System.out.println("2. Реєстрація");
            System.out.println("0. Вийти");

            choice = reader.readLine();
            switch (choice) {
                case "1":
                    if (!login()) {
                        continue;
                    }
                    isAuth = true;
                    break;
                case "2":
                    if (!register()) {
                        continue;
                    }
                    isAuth = true;
                    break;
                case "0":
                    System.out.println("Вихід з програми.");
                    return;
                default:
                    System.out.println("Невірний вибір, спробуйте ще раз.");
            }

            boolean exitStatus = false;
            while (!exitStatus) {
                System.out.println("=== Консольне меню ===");

                System.out.println("1. Підключитись до іншого клієнта");
                System.out.println("2. Надати підключення до свого клієнта");
                System.out.println("0. Вийти");

                choice = reader.readLine();
                switch (choice) {
                    case "1":
                        connectToAnotherClient();
                        break;
                    case "2":
                        allowConnectionToYourClient();
                        break;
                    case "0":
                        exitStatus = true;
                        isAuth = false;
                        System.out.println("Вихід.");
                        break;
                    default:
                        System.out.println("Невірний вибір, спробуйте ще раз.");
                }
            }
        }
    }

    private boolean connectToServer() {
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

    private boolean login() throws IOException {
        System.out.println("Введіть логін:");
        String login = reader.readLine();

        System.out.println("Введіть пароль:");
        String password = reader.readLine();

        String credentials = "LOGIN:" + login + ":" + password;
        sendMessageToServer(credentials);

        try {
            String serverResponse = receiveMessageFromServer(10000);
            if (serverResponse.equals("LOGIN_SUCCESS")) {
                System.out.println("Авторизація успішна.");
                this.login = login;
                return true;
            } else {
                System.out.println("Авторизація не вдалася.");
                return false;
            }
        } catch (IOException e) {
            System.out.println("Помилка підключення.");
            return false;
        }
    }

    private boolean register() throws IOException {
        System.out.println("Введіть новий логін:");
        String login = reader.readLine();

        System.out.println("Введіть новий пароль:");
        String password = reader.readLine();

        String credentials = "REGISTER:" + login + ":" + password;
        sendMessageToServer(credentials);

        try {
            String serverResponse = receiveMessageFromServer(10000);
            if (serverResponse.equals("REGISTER_SUCCESS")) {
                System.out.println("Реєстрація успішна.");
                this.login = login;
                return true;
            } else {
                System.out.println("Реєстрація не вдалася. Можливо, такий логін вже існує.");
                return false;
            }
        } catch (IOException e) {
            System.out.println("Помилка підключення.");
            return false;
        }
    }


    private void connectToAnotherClient() throws Exception {
        System.out.println("Введіть IP клієнта для підключення:");
        String targetIP = reader.readLine();
        System.out.println("Введіть порт клієнта:");
        int targetPort = Integer.parseInt(reader.readLine());

        String connectionRequest = "CONNECT_REQUEST:" + targetIP + ":" + targetPort + ":" + login;
        sendMessageToServer(connectionRequest);

        System.out.println("Чекаємо дозволу на підключення (30 секунд)");
        try {
            String serverResponse = receiveMessageFromServer(30_000);
            String[] parts = serverResponse.split(":", 3);

            if (parts[0].equals("CONNECT_DENIED")) {
                System.out.println("Підключення відхилено.");
                return;
            } else if (parts[0].equals("CONNECT_ACCEPTED")) {
                System.out.println("Підключення підтверджено. Тепер ви можете надсилати команди.");
            } else if (parts[0].equals("NO_SUCH_USER")) {
                System.out.println("Такого клієнту немає в системі");
            }
        } catch (IOException e) {
            System.out.println("Не вдалося отримати відповідь на запит підключення.");
            return;
        }



        // Цикл для надсилання команд
        while (true) {
            System.out.println("Введіть команду для виконання (0 to exit):");
            String command = reader.readLine();
            if (command.equals("0")) {
                break;
            }
            sendCommandToAnotherClient(targetIP, targetPort, command);
            try {
                String serverResponse = receiveMessageFromServer(10000);
                System.out.println("Результат виконання команди: " + serverResponse);
            } catch (IOException e) {
                System.out.println("Відповідь не прийшла, перевірте підключення та повторіть спробу");
            }
        }
    }


    private void allowConnectionToYourClient() throws Exception {
        Thread listenerThread = new Thread(() -> {
            System.out.println("Очікування запитів на підключення...");
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String clientMessage = receiveMessageFromServer(10000);
                    if (clientMessage.startsWith("CONNECT_REQUEST")) {
                        String[] parts = clientMessage.split(":");
                        String senderIP = parts[1].substring(1);
                        int senderPort = Integer.parseInt(parts[2]);
                        String senderLogin = parts[3];

                        System.out.printf("Клієнт %s хоче підключитися. Підтвердити підключення? (yes/no)\n", senderLogin);
                        String userResponse = reader.readLine();
                        if (userResponse.equals("yes")) {
                            sendMessageToServer("CONNECT_ACCEPTED:" + senderIP + ":" + senderPort);
                            System.out.println("Підключення підтверджено для клієнта " + senderLogin);
                            executeFromClient(senderIP, senderPort);
                        } else {
                            sendMessageToServer("CONNECT_DENIED:" + senderIP + ":" + senderPort);
                            System.out.printf("Підключення відхилено для клієнта %s\n", senderLogin);
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Очікування запитів на підключення...");
                }
            }
        });

        listenerThread.start();
        listenerThread.join();
//        while (true) {
//            System.out.println("Введіть 'exit' для завершення очікування запитів на підключення:");
//            userResponse = reader.readLine();
//            if (userResponse.equalsIgnoreCase("exit")) {
//                listenerThread.interrupt();
//                listenerThread.join();
//                System.out.println("Очікування запитів на підключення завершено.");
//                break;
//            }
//        }
    }

    private void executeFromClient(String clientIP, int clientPort){
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String clientMessage = receiveMessageFromServer(0);
                String[] parts = clientMessage.split(":", 4);
                if (clientMessage.startsWith("EXECUTE") && parts[1].equals("/" + clientIP) && Integer.parseInt(parts[2]) == clientPort) {
                    executeAndRespond(parts[1], Integer.parseInt(parts[2]), parts[3]);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void executeAndRespond(String senderIP, int senderPort, String command) throws IOException {
        String result = CommandExecutor.executeCommand(command);
        System.out.println(result);
        sendMessageToServer("RESPONSE_TO:%s:%d:%s:%s".formatted(senderIP, senderPort, command, result));
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

    private String receiveMessageFromServer(int timeToWait) throws IOException {
        byte[] receiveBuffer = new byte[1024];
        DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
        clientSocket.setSoTimeout(timeToWait);
        clientSocket.receive(receivePacket);
        return new String(receivePacket.getData(), 0, receivePacket.getLength());
    }
}
