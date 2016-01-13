package gov.nist.hit.erx.ws.client;

import com.google.gson.Gson;
import gov.nist.hit.core.transport.exception.TransportClientException;
import gov.nist.hit.core.transport.service.TransportClient;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;


/**
 * Created by mcl1 on 12/17/15.
 */
@Component
@Qualifier("WebServiceClient")
public class WebServiceClientImpl implements WebServiceClient {

    static final Logger logger = LoggerFactory
            .getLogger(WebServiceClientImpl.class);

    //private final WebServiceTemplate webServiceTemplate;

    /*public WebServiceClient(WebServiceTemplate webServiceTemplate) {
        this.webServiceTemplate = webServiceTemplate;
    }*/


    @Override
    public String send(String message, String... arguments) throws TransportClientException {
        final String username = arguments[0];
        final String password = arguments[1];
        final String endpoint = arguments[2];

        //String plainCreds = username+":"+password;
        //byte[] plainCredsBytes = plainCreds.getBytes();
        //byte[] base64CredsBytes = Base64.encodeBase64(plainCredsBytes);
        //String base64Creds = new String(base64CredsBytes);
        //HttpHeaders headers = new HttpHeaders();
        //headers.add("Content-Type","application/json");
        //headers.add("Authorization", "Basic " + base64Creds);
        //HttpEntity<String> request = new HttpEntity<>(headers);

        //RestTemplate restTemplate = new RestTemplate();
        //ResponseEntity<String> response = restTemplate.exchange(endpoint, HttpMethod.POST, request,String.class);

        Message body = new Message(message,username,password);
        Gson gson = new Gson();
        RestTemplate restTemplate = new RestTemplate();

        String requestJson = gson.toJson(body);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<String>(requestJson,headers);
        String answer = restTemplate.postForObject(endpoint, entity, String.class);

        return answer;
    }
}
