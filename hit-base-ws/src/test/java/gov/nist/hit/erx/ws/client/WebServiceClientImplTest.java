package gov.nist.hit.erx.ws.client;

import com.google.gson.Gson;
import gov.nist.hit.core.transport.exception.TransportClientException;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.ws.client.core.WebServiceTemplate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;

/**
 * Created by mcl1 on 12/18/15.
 */
public class WebServiceClientImplTest {

    private Gson gson = new Gson();

    protected WebServiceClientImpl webServiceClient = new WebServiceClientImpl();

    private HttpHeaders buildHeaders(String username, String password){
        String plainCreds = username + ":" + password;
        byte[] plainCredsBytes = plainCreds.getBytes();
        String base64Creds = DatatypeConverter.printBase64Binary(plainCredsBytes);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Basic " + base64Creds);
        headers.add(org.apache.http.HttpHeaders.CONTENT_TYPE, MediaType.TEXT_XML_VALUE);
        headers.add(org.apache.http.HttpHeaders.ACCEPT, MediaType.TEXT_XML_VALUE);
        return headers;
    }

    private HttpEntity buildRequest(String message,HttpHeaders headers){
        return new HttpEntity<>(message, headers);
    }

    @Test
    public void testHttpSend() throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        String message = "";
        String url = "http://httpbin.org/basic-auth/test/pass";
        String username = "test";
        String password = "pass";
        ResponseEntity<String> res = webServiceClient.exchange(url,HttpMethod.GET,buildRequest(message,buildHeaders(username,password)));
        BasicAuthResult result = gson.fromJson(res.getBody(), BasicAuthResult.class);
        Assert.assertTrue(result.getAuthenticated());
    }

    @Test
    public void testHttpsSend() throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        String message = "";
        String url = "https://httpbin.org/basic-auth/test/pass";
        String username = "test";
        String password = "pass";
        ResponseEntity<String> res = webServiceClient.exchange(url,HttpMethod.GET,buildRequest(message,buildHeaders(username,password)));
        BasicAuthResult result = gson.fromJson(res.getBody(), BasicAuthResult.class);
        Assert.assertTrue(result.getAuthenticated());
    }

    @Test
    public void testHttpsPost() throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        String message = "";
        String url = "https://httpbin.org/post";
        ResponseEntity<String> res = webServiceClient.exchange(url,HttpMethod.POST,buildRequest(message,new HttpHeaders()));
        Assert.assertEquals(res.getStatusCode(),HttpStatus.OK);
    }


    @Test
    public void testGet() throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        String message = "";
        String url = "https://edge.nist.gov:12080/ett/api/login";
        ResponseEntity<String> res = webServiceClient.exchange(url,HttpMethod.GET,buildRequest(message,new HttpHeaders()));
        Assert.assertEquals(res.getStatusCode(),HttpStatus.OK);
    }

    @Test
    public void testHttpsPost2(){
        String message = "";
        String url = "https://192.168.0.101:8443/hit-base-tool/api/wss/erx/surescript/message";
        ResponseEntity<String> res = null;
        try {
            res = webServiceClient.exchange(url, HttpMethod.POST,buildRequest(message,new HttpHeaders()));
        } catch (Exception e) {
            e.printStackTrace();
        }
        //if(res!=null) {
            Assert.assertEquals(res.getStatusCode(), HttpStatus.OK);
        //} else Assert.assertFalse(true);*/
    }

    /*@Test(expected = org.springframework.web.client.HttpClientErrorException.class)
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
    }*/


    @Test
    public void testHandshake() throws IOException {
        int port = 8443;


        System.setProperty("javax.net.ssl.trustStore","/ssh/erx.key");

        System.setProperty("javax.net.ssl.trustStorePassword","tomcat");

        String hostname = "192.168.0.101";

        SSLSocketFactory factory = HttpsURLConnection
                .getDefaultSSLSocketFactory();

        System.out.println("Creating a SSL Socket For "+hostname+" on port "+port);

        SSLSocket socket = (SSLSocket) factory.createSocket(hostname, port);

/**
 * Starts an SSL handshake on this connection. Common reasons include a
 * need to use new encryption keys, to change cipher suites, or to
 * initiate a new session. To force complete reauthentication, the
 * current session could be invalidated before starting this handshake.
 * If data has already been sent on the connection, it continues to flow
 * during this handshake. When the handshake completes, this will be
 * signaled with an event. This method is synchronous for the initial
 * handshake on a connection and returns when the negotiated handshake
 * is complete. Some protocols may not support multiple handshakes on an
 * existing socket and may throw an IOException.
 */

        socket.startHandshake();
        System.out.println("Handshaking Complete");

/**
 * Retrieve the server's certificate chain
 *
 * Returns the identity of the peer which was established as part of
 * defining the session. Note: This method can be used only when using
 * certificate-based cipher suites; using it with non-certificate-based
 * cipher suites, such as Kerberos, will throw an
 * SSLPeerUnverifiedException.
 *
 *
 * Returns: an ordered array of peer certificates, with the peer's own
 * certificate first followed by any certificate authorities.
 */
        Certificate[] serverCerts = socket.getSession().getPeerCertificates();
        System.out.println("Retreived Server's Certificate Chain");

        System.out.println(serverCerts.length + "Certifcates Foundnnn");
        for (int i = 0; i < serverCerts.length; i++) {
            Certificate myCert = serverCerts[i];
            System.out.println("====Certificate:" + (i+1) + "====");
            System.out.println("-Public Key-n" + myCert.getPublicKey());
            System.out.println("-Certificate Type-n " + myCert.getType());

            System.out.println();
        }

        socket.close();
    }
}