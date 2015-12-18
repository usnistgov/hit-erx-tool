package gov.nist.hit.erx.ws.client;

import gov.nist.hit.core.transport.exception.TransportClientException;
import gov.nist.hit.core.transport.service.TransportClient;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.ws.client.core.WebServiceTemplate;


/**
 * Created by mcl1 on 12/17/15.
 */
public class WebServiceClient implements TransportClient {

    static final Logger logger = LoggerFactory
            .getLogger(WebServiceClient.class);

    private final WebServiceTemplate webServiceTemplate;

    public WebServiceClient(WebServiceTemplate webServiceTemplate) {
        this.webServiceTemplate = webServiceTemplate;
    }


    @Override
    public String send(String message, String... arguments) throws TransportClientException {
        final String username = (String) arguments[0];
        final String password = (String) arguments[1];
        final String endpoint = (String) arguments[2];
        String plainCreds = username+":"+password;
        byte[] plainCredsBytes = plainCreds.getBytes();
        byte[] base64CredsBytes = Base64.encodeBase64(plainCredsBytes);
        String base64Creds = new String(base64CredsBytes);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Basic " + base64Creds);
        HttpEntity<String> request = new HttpEntity<String>(headers);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.exchange(endpoint, HttpMethod.GET, request,String.class);
        return response.getBody();
    }
}
