package gov.nist.hit.erx.ws.client;

import gov.nist.hit.erx.ws.client.utils.MessageUtils;
import org.apache.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.xml.bind.DatatypeConverter;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.io.StringWriter;

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
    public String send(String message, String... arguments) {
        final String username = arguments[0];
        final String password = arguments[1];
        final String endpoint = arguments[2];

        String plainCreds = username + ":" + password;
        byte[] plainCredsBytes = plainCreds.getBytes();
        String base64Creds = DatatypeConverter.printBase64Binary(plainCredsBytes);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Basic " + base64Creds);
        headers.add(org.apache.http.HttpHeaders.CONTENT_TYPE, MediaType.TEXT_XML_VALUE);
        headers.add(org.apache.http.HttpHeaders.ACCEPT, MediaType.TEXT_XML_VALUE);
        HttpEntity<String> request = new HttpEntity<>(message, headers);
        RestTemplate restTemplate = new RestTemplate();

        logger.info("Send to the endpoint : " + endpoint + " with basic auth credentials : " + base64Creds + " message : " + message);
        ResponseEntity<String> response = restTemplate.exchange(endpoint, HttpMethod.POST, request, String.class);
        String messageReceived = MessageUtils.prettyPrint(response.getBody());
        return messageReceived;
    }
}
