import java.io.BufferedReader;
import java.io.InputStreamReader;

public class CommandExecutor {
    public static String executeCommand(String command) {
        StringBuilder output = new StringBuilder();
        try {
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
            return "Помилка виконання команди.";
        }
        return output.toString();
    }
}
