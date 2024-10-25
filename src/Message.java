public class Message {
    private String type;
    private String targetIP;
    private int targetPort;
    private String senderLogin;
    private String message1;
    private String message2;

    public Message(String type, String targetIP, int targetPort, String senderLogin, String message1) {
        this.type = type;
        this.targetIP = targetIP;
        this.senderLogin = senderLogin;
        this.targetPort = targetPort;
        this.message1 = message1;
    }

    public Message(String type) {
        this.type = type;
    }

    public Message(String type, String message1, String message2){
        this.type = type;
        this.message1 = message1;
        this.message2 = message2;
    }
    public Message(String type, String targetIP, int targetPort){
        this.type = type;
        this.targetIP = targetIP;
        this.targetPort = targetPort;
    }


    public String getType() {
        return type;
    }

    public String getTargetIP() {
        return targetIP;
    }

    public int getTargetPort() {
        return targetPort;
    }

    public String getSenderLogin() {
        return senderLogin;
    }

    public String getMessage() {
        return message1;
    }

    public String getSecondMessage() {
        return message2;
    }
}
