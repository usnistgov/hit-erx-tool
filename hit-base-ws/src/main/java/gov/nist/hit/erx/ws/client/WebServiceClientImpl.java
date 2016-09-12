package gov.nist.hit.erx.ws.client;

import gov.nist.hit.erx.ws.client.utils.MessageUtils;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.TrustStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import javax.xml.bind.DatatypeConverter;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

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
        if(arguments.length>=3) {
            final String username = arguments[0];
            final String password = arguments[1];
            final String endpoint = arguments[2];
            String plainCreds = username + ":" + password;
            byte[] plainCredsBytes = plainCreds.getBytes();
            String base64Creds = DatatypeConverter.printBase64Binary(plainCredsBytes);
            HttpHeaders headers = new HttpHeaders();
            headers.add("Authorization", "Basic " + base64Creds);
            if(arguments.length==4 && MediaType.parseMediaType(arguments[3])!=null){
                headers.add(org.apache.http.HttpHeaders.CONTENT_TYPE, arguments[3]);
                headers.add(org.apache.http.HttpHeaders.ACCEPT, arguments[3]);
            } else {
                headers.add(org.apache.http.HttpHeaders.CONTENT_TYPE, MediaType.TEXT_XML_VALUE);
                headers.add(org.apache.http.HttpHeaders.ACCEPT, MediaType.TEXT_XML_VALUE);
            }
            HttpEntity<String> request = new HttpEntity<>(message, headers);
            ResponseEntity<String> response = null;
            logger.info("Sending a message to " + endpoint + " ...");
            try {
                response = exchange(endpoint, HttpMethod.POST, request);
                logger.info("Message : " + message + " sent to the endpoint : " + endpoint + " with basic auth credentials : " + base64Creds);
            } catch (KeyStoreException e) {
                e.printStackTrace();
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (KeyManagementException e) {
                e.printStackTrace();
            }
            try {
                String messageReceived = MessageUtils.prettyPrint(response.getBody());
                return messageReceived;
            } catch (Exception e) {
                return response.getBody();
            }
        } else {
            logger.error("Arguments count invalid ("+arguments.length+" found, 3 required). arguments="+arguments.toString());
        }
        return null;
    }

    public ResponseEntity<String> exchange(String endpoint, HttpMethod method, HttpEntity<String> request) throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException {

        TrustStrategy acceptingTrustStrategy = new TrustStrategy() {
            @Override
            public boolean isTrusted(java.security.cert.X509Certificate[] x509Certificates, String s) throws CertificateException {
                return true;
            }
        };

        SSLContext sslContext = org.apache.http.ssl.SSLContexts.custom()
                .loadTrustMaterial(null, acceptingTrustStrategy)
                .build();

        SSLConnectionSocketFactory csf = new SSLConnectionSocketFactory(sslContext);

        CloseableHttpClient httpClient = HttpClients.custom()
                .setSSLSocketFactory(csf)
                .build();

        HttpComponentsClientHttpRequestFactory requestFactory =
                new HttpComponentsClientHttpRequestFactory();

        requestFactory.setHttpClient(httpClient);

        RestTemplate restTemplate = new RestTemplate(requestFactory);

        //HttpComponentsClientHttpRequestFactory httpComponentsClientHttpRequestFactory = new HttpComponentsAsyncClientHttpRequestFactory();
        //RestTemplate restTemplate = new RestTemplate(httpComponentsClientHttpRequestFactory);
        return restTemplate.exchange(endpoint, method, request, String.class);
    }
}
