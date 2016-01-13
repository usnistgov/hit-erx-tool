package gov.nist.hit.erx.ws.client;

/**
 * Created by mcl1 on 1/13/16.
 */
public class Message {
    private MessageConfig config;
    private String message;

    public Message(String message, String username, String password) {
        this.message = message;
        this.config = new MessageConfig(username, password);
    }

    public MessageConfig getConfig() {
        return config;
    }

    public String getMessage() {
        return message;
    }
}
