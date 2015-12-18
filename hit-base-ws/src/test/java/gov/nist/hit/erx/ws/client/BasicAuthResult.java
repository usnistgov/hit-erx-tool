package gov.nist.hit.erx.ws.client;

import javax.annotation.Generated;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * Created by mcl1 on 12/18/15.
 */

public class BasicAuthResult {

    @SerializedName("authenticated")
    @Expose
    private Boolean authenticated;
    @SerializedName("user")
    @Expose
    private String user;

    /**
     *
     * @return
     * The authenticated
     */
    public Boolean getAuthenticated() {
        return authenticated;
    }

    /**
     *
     * @param authenticated
     * The authenticated
     */
    public void setAuthenticated(Boolean authenticated) {
        this.authenticated = authenticated;
    }

    /**
     *
     * @return
     * The user
     */
    public String getUser() {
        return user;
    }

    /**
     *
     * @param user
     * The user
     */
    public void setUser(String user) {
        this.user = user;
    }

}