package gov.nist.hit.erx.web.controller;

import com.google.gson.Gson;
import gov.nist.hit.core.domain.TransportRequest;
import gov.nist.hit.core.service.exception.MessageParserException;
import gov.nist.hit.core.transport.exception.TransportClientException;
import gov.nist.hit.erx.ws.client.Message;
import gov.nist.hit.utils.XMLUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.Base64;

/**
 * This software was developed at the National Institute of Standards and Technology by employees of
 * the Federal Government in the course of their official duties. Pursuant to title 17 Section 105
 * of the United States Code this software is not subject to copyright protection and is in the
 * public domain. This is an experimental system. NIST assumes no responsibility whatsoever for its
 * use by other parties, and makes no guarantees, expressed or implied, about its quality,
 * reliability, or any other characteristic. We would appreciate acknowledgement if the software is
 * used. This software can be redistributed and/or modified freely provided that any derivative
 * works bear some notice that they are derived from it, and any modified versions bear some notice
 * that they have been modified.
 * <p>
 * Created by Maxence Lefort on 3/18/16.
 */
@RestController
@Controller
@RequestMapping("/ws/erx/surescript")
public class SurescriptWebServiceController extends WebServiceController{

    @Transactional()
    @RequestMapping(value = "/message", method = RequestMethod.POST)
    public String message(@RequestBody TransportRequest request) throws TransportClientException, MessageParserException {
        Gson gson = new Gson();
        String jsonRequest = gson.toJson(request);
        Message received = gson.fromJson(jsonRequest, Message.class);
        try {
            String parsedMessage = parseEnveloppe(received.getMessage());
            received.setMessage(parsedMessage);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }
        return message(received);
    }

    private String parseEnveloppe(String wrappedMessage) throws IOException, SAXException, ParserConfigurationException {
        String message = "";
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        org.w3c.dom.Document doc = docBuilder.parse(IOUtils.toInputStream(wrappedMessage));
        String encodedEdifactMessage = XMLUtils.getNodeByNameOrXPath("/Message/Body/EDIFACTMessage", doc).getTextContent();
        message = Base64.getDecoder().decode(encodedEdifactMessage).toString();
        return message;
    }



}