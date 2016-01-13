package gov.nist.hit.erx.ws.client;

/**
 * Created by mcl1 on 1/13/16.
 */
public class MessageConfig {
    private String username;
    private String password;

    public MessageConfig(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
