import java.sql.*;
import java.sql.Connection;

public class DatabaseManager {
    private static final String URL = "jdbc:postgresql://localhost:5432/RemoteApp";
    private static final String USER = "postgres";
    private static final String PASSWORD = "postgres";

    private static Connection connection;

    static {
        try {
            connection = DriverManager.getConnection(URL, USER, PASSWORD);
            System.out.println("Connection to PostgreSQL established!");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void saveClient(String ip, int port) throws SQLException {
        String query = "INSERT INTO clients (ip_address, port) VALUES (?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, ip);
            statement.setInt(2, port);
            statement.executeUpdate();
        }
    }


    public static void saveCommand(String senderIp, int senderPort, String receiverIp, int receiverPort, String command, String result) throws SQLException {
        String query = "INSERT INTO commands (sender_ip, sender_port, receiver_ip, receiver_port, command, result) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, senderIp);
            statement.setInt(2, senderPort);
            statement.setString(3, receiverIp);
            statement.setInt(4, receiverPort);
            statement.setString(5, command);
            statement.setString(6, result);
            statement.executeUpdate();
        }
    }
    public static boolean isValidUser(String login, String password) {
        String query = "SELECT COUNT(*) FROM users WHERE login = ? AND password = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, login);
            statement.setString(2, password);
            ResultSet rs = statement.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean registerUser(String login, String password) {
        String query = "INSERT INTO users (login, password) VALUES (?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, login);
            statement.setString(2, password);
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Attempt to register an existing login");
            return false;
        }
    }


}
