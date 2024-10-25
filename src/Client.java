import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;

public class Client {
    private final DatagramSocket clientSocket;
    private final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    private final InetAddress serverAddress;
    private final int serverPort;
    private final Gson gson = new Gson();
    private String login;

    { // Ініціалізатор для налаштування адреси сервера і створення сокета
        try {
            serverAddress = InetAddress.getByName("192.168.0.100");
            serverPort = 12345;
            clientSocket = new DatagramSocket();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }

    public void start(){ // Основний метод для старту клієнта та спроби підключення до сервера
        while (true) {
            System.out.println("Спроба підключитись до серверу...");
            if (!connectToServer()) {// Викликає метод для підключення
                continue;
            }
            System.out.println("Підключення успішне!");
            menu(); // Запуск меню після успішного підключення
            break;
        }
    }

    private void menu() {   // Головне меню для користувача з авторизацією
        String choice;
        boolean isAuth = false;
        while (!isAuth) {
            System.out.println("=== Консольне меню ===");

            System.out.println("1. Логін");
            System.out.println("2. Реєстрація");
            System.out.println("0. Вийти");

            try {
                choice = reader.readLine();
            } catch (IOException e) {
                System.err.println("Не вийшло прочитати вхідні дані від користувача");
                throw new RuntimeException(e);
            }
            switch (choice) { // Виклик методів логіна та реєстрації, а також вихід
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
            while (!exitStatus) { // Підменю з опціями для підключення до інших клієнтів або надання підключення
                System.out.println("=== Консольне меню ===");

                System.out.println("1. Підключитись до іншого клієнта");
                System.out.println("2. Надати підключення до свого клієнта");
                System.out.println("0. Вийти");

                try {
                    choice = reader.readLine();
                } catch (IOException e) {
                    System.err.println("Не вийшло прочитати вхідні дані від користувача");
                    throw new RuntimeException(e);
                }
                switch (choice) {
                    case "1":
                        connectToAnotherClient(); // Підключення до іншого клієнта
                        break;
                    case "2":
                        allowConnectionToYourClient(); // Відкриття свого клієнта для підключення з інших
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

    private boolean connectToServer() { // Метод для підключення до сервера через надсилання повідомлення "PING"
        try {
            // Формуємо та надсилаємо PING повідомлення
            Message testMessage = new Message("PING");
            sendMessageToServer(testMessage);

            // Чекаємо на відповідь
            Message responseMessage = gson.fromJson(receiveMessageFromServer(5000), Message.class);

            // Після отримання перевіряємо та повертаємо значення
            return responseMessage.getType().equals("PONG");

        } catch (Exception e) {
            System.out.println("Не вдалося підключитися до сервера.");
            return false;
        }
    }

    private boolean login() { // Логін користувача: введення логіна і пароля, відправлення на сервер, отримання відповіді

        String login;
        String password;
        try {
            System.out.println("Введіть логін:");
            login = reader.readLine();
            System.out.println("Введіть пароль:");
            password = reader.readLine();
        } catch (IOException e) {
            System.err.println("Не вийшло прочитати вхідні дані від користувача");
            throw new RuntimeException(e);
        }

        // Формуємо та відправляємо повідомлення на спробу авторизації
        Message credentials = new Message("LOGIN", login, password);
        sendMessageToServer(credentials);

        try {
            // Отримуємо повідомлення та перевіряємо
            String serverResponse = receiveMessageFromServer(10000);
            Message responseMessage = gson.fromJson(serverResponse, Message.class);
            if (responseMessage.getType().equals("LOGIN_SUCCESS")) {
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

    private boolean register() { // Реєстрація нового користувача: введення логіна і пароля, відправлення на сервер, отримання відповіді

        String login;
        String password;
        try {
            System.out.println("Введіть новий логін:");
            login = reader.readLine();
            System.out.println("Введіть новий пароль:");
            password = reader.readLine();
        } catch (IOException e) {
            System.err.println("Не вийшло прочитати вхідні дані від користувача");
            throw new RuntimeException(e);
        }

        // Формуємо та надсилаємо повідомлення для спроби реєстрації
        Message message = new Message("REGISTER", login, password);
        sendMessageToServer(message);

        try {
            // Отримуємо повідомлення та перевіряємо
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


    private void connectToAnotherClient(){  // Метод для запиту підключення до іншого клієнта
        String targetIP;
        int targetPort;
        try {
            System.out.println("Введіть IP клієнта для підключення:");
            targetIP = reader.readLine();
            System.out.println("Введіть порт клієнта:");
            targetPort = Integer.parseInt(reader.readLine());
        } catch (IOException e) {
            System.err.println("Не вийшло прочитати вхідні дані від користувача");
            throw new RuntimeException(e);
        }

        // Надсилаємо запит на підключення до іншого клієнта
        Message message = new Message("CONNECT_REQUEST", targetIP, targetPort, login, null);
        sendMessageToServer(message);

        System.out.println("Чекаємо дозволу на підключення (30 секунд)");
        try {
            // Отримуємо відповідь та перевіряємо
            String serverResponse = receiveMessageFromServer(30_000);
            Message receivedMessage = gson.fromJson(serverResponse, Message.class);

            if (receivedMessage.getType().equals("CONNECT_DENIED")) {
                System.out.println("Підключення відхилено.");
                return;
            } else if (receivedMessage.getType().equals("CONNECT_ACCEPTED")) {
                System.out.println("Підключення підтверджено. Тепер ви можете надсилати команди.");
            } else if (receivedMessage.getType().equals("NO_SUCH_USER")) {
                System.out.println("Такого клієнту немає в системі");
                return;
            }
        } catch (IOException e) {
            System.err.println("Не вдалося отримати відповідь на запит підключення.");
            return;
        }

        // Введення команд після підтвердження підключення
        while (true) {
            String command;
            try {
                System.out.println("Введіть команду для виконання (0 to exit):");
                command = reader.readLine();
            } catch (IOException e) {
                System.err.println("Не вийшло прочитати вхідні дані від користувача");
                throw new RuntimeException(e);
            }
            if (command.equals("0")) {
                break;
            }
            // Формуємо повідомлення для виконання нашої команди іншим клієнтом
            Message executeMessage = new Message("SEND_TO", targetIP, targetPort, null, command);
            sendMessageToServer(executeMessage);
            try {
                // Очікуємо результат, та при отриманні виводимо у консоль
                String serverResponse = receiveMessageFromServer(10000);
                System.out.println("Результат виконання команди: " + serverResponse);
            } catch (IOException e) {
                System.err.println("Відповідь не прийшла, перевірте підключення та повторіть спробу");
            }
        }
    }


    private void allowConnectionToYourClient() {  // Надання дозволу на підключення до свого клієнта іншому користувачеві
         // Очікування на підключення
        System.out.println("Очікування запитів на підключення...");
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Очікуємо запитів на відключення
                String messageString = receiveMessageFromServer(10000);
                Message message = gson.fromJson(messageString, Message.class);
                if (message.getType().equals("CONNECT_REQUEST")) { // Якщо прийшло повідомлення з запитом на підлючення - перевіряємо
                    String senderIP = message.getTargetIP();
                    int senderPort = message.getTargetPort();
                    String senderLogin = message.getSenderLogin();

                    System.out.printf("Клієнт %s хоче підключитися. Підтвердити підключення? (yes/no)\n", senderLogin);
                    String userResponse = reader.readLine();
                    if (userResponse.equals("yes")) { // Якщо користувач підтверджує доступ то відсилаємо відповідне повідомлення
                        message = new Message("CONNECT_ACCEPTED", senderIP, senderPort);
                        sendMessageToServer(message);
                        System.out.println("Підключення підтверджено для клієнта " + senderLogin);
                        executeFromClient(senderIP.substring(1), senderPort);
                    } else {
                        message = new Message("CONNECT_DENIED", senderIP, senderPort);
                        sendMessageToServer(message);
                        System.out.printf("Підключення відхилено для клієнта %s\n", senderLogin);
                    }
                }
            } catch (IOException e) {
                System.out.println("Очікування запитів на підключення...");
            }
        }
    }

    private void executeFromClient(String clientIP, int clientPort){ // Виконання команди заданої від іншого клієнта та відповідь
        while (true) { // Слухаємо вхідні команди, перевіряємо чи це дозволений клієнт та виконуємо
            String messageString = null;
            try {
                messageString = receiveMessageFromServer(0);
            } catch (IOException e) {
                System.err.println("Не вийшло отримати вказівок від іншого клієнта.");
            }
            Message message = gson.fromJson(messageString, Message.class);
            String messageType = message.getType();
            String targetIP = message.getTargetIP().substring(1);
            int targetPort = message.getTargetPort();
            String command = message.getMessage();
            if (messageType.equals("EXECUTE") && targetIP.equals(clientIP) && targetPort == clientPort) {
                String result = CommandExecutor.executeCommand(command);
                System.out.println(result);
                sendMessageToServer("RESPONSE_TO:%s:%d:%s:%s".formatted(targetIP, targetPort, command, result));
            }
        }
    }

//    private void executeAndRespond(String senderIP, int senderPort, String command) throws IOException {
//        String result = CommandExecutor.executeCommand(command);
//        System.out.println(result);
//        Message message = new Message("RESPONSE_TO", senderIP, senderPort, command, result);
//        sendMessageToServer(message);
//    }



    private void sendMessageToServer(Message message) { // Відправка повідомлення на сервер у форматі JSON
        try {
            String jsonMessage = gson.toJson(message);
            byte[] sendBuffer = jsonMessage.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, serverAddress, serverPort);
            clientSocket.send(sendPacket);
        } catch (IOException e) {
            System.err.println("Не вийшло відправили повідомлення на сервер");
        }

    }

    private void sendMessageToServer(String message) { // Відправка повідомлення на сервер у форматі String
        try {
            byte[] sendBuffer = message.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, serverAddress, serverPort);
            clientSocket.send(sendPacket);
        } catch (IOException e) {
            System.err.println("Не вийшло відправили повідомлення на сервер");
        }

    }


    private String receiveMessageFromServer(int timeToWait) throws IOException { // Отримання повідомлення від сервера із зазначеним таймаутом
        byte[] receiveBuffer = new byte[1024];
        DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
        clientSocket.setSoTimeout(timeToWait);
        clientSocket.receive(receivePacket);
        return new String(receivePacket.getData(), 0, receivePacket.getLength());


    }
}
