package gov.nist.hit.erx.ws.client;

import com.google.gson.Gson;
import gov.nist.hit.core.transport.exception.TransportClientException;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.ws.client.core.WebServiceTemplate;

/**
 * Created by mcl1 on 12/18/15.
 */
public class WebServiceClientTest {

    private Gson gson = new Gson();

    protected WebServiceClient webServiceClient = new WebServiceClientImpl();

    @Test
    public void testSend(){
        String message = "test message auth";
        String url = "http://httpbin.org/basic-auth/test/pass";
        String username = "test";
        String password = "pass";
        try {
            String res = webServiceClient.send(message,username,password,url);
            BasicAuthResult result = gson.fromJson(res, BasicAuthResult.class);
            Assert.assertTrue(result.getAuthenticated());
        } catch (TransportClientException e) {
            e.printStackTrace();
        }
    }

    @Test(expected = org.springframework.web.client.HttpClientErrorException.class)
    public void testSendBadAuth(){
        String message = "test message auth";
        String url = "http://httpbin.org/basic-auth/test/pass";
        String username = "test";
        String password = "wrong";
        try {
            String res = webServiceClient.send(message,username,password,url);
        } catch (TransportClientException e) {
            e.printStackTrace();
        }
    }

}