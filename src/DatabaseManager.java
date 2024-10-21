import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

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
}
