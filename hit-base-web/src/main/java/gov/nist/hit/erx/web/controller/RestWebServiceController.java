package gov.nist.hit.erx.web.controller;

import com.google.gson.Gson;
import gov.nist.hit.MessageIdFinder;
import gov.nist.hit.MessageTypeFinder;
import gov.nist.hit.core.domain.TestContext;
import gov.nist.hit.core.domain.Transaction;
import gov.nist.hit.core.domain.TransportRequest;
import gov.nist.hit.core.repo.MessageRepository;
import gov.nist.hit.core.repo.TestContextRepository;
import gov.nist.hit.core.service.TransactionService;
import gov.nist.hit.core.service.TransportMessageService;
import gov.nist.hit.core.service.exception.MessageParserException;
import gov.nist.hit.core.transport.exception.TransportClientException;
import gov.nist.hit.erx.ws.client.Message;
import gov.nist.hit.impl.EdiMessageEditor;
import gov.nist.hit.impl.XMLMessageEditor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by mcl1 on 1/13/16.
 */
@RestController
@Controller
@RequestMapping("/ws/erx/rest")
public class RestWebServiceController {

    static final Logger logger = LoggerFactory.getLogger(RestWebServiceController.class);
    @Autowired
    protected TransactionService transactionService;

    @Autowired
    protected TransportMessageService transportMessageService;

    @Autowired
    protected MessageRepository messageRepository;

    @Autowired
    protected TestContextRepository testContextRepository;


    @Transactional()
    @RequestMapping(value = "/message", method = RequestMethod.POST)
    public String message(@RequestBody TransportRequest request) throws TransportClientException, MessageParserException {
        //TODO check auth
        Gson gson = new Gson();
        String responseMessage = "";
        String jsonRequest = gson.toJson(request);
        Message received = gson.fromJson(jsonRequest,Message.class);
        logger.info("Message received : " + jsonRequest);
        //TODO modify the response message
        Map<String, String> criteria = new HashMap<>();
        criteria.put("username", received.getConfig().getUsername());
        criteria.put("password", received.getConfig().getPassword());
        Transaction transaction = new Transaction();
        transaction.setProperties(criteria);
        transaction.setIncoming(received.getMessage());
        Long messageId = transportMessageService.findMessageIdByProperties(criteria);
        gov.nist.hit.core.domain.Message message;
        if(messageId!=null) {
             message = messageRepository.getOne(messageId);
            if (message != null) {
                TestContext testContext = testContextRepository.findOneByMessageId(messageId);
                MessageTypeFinder messageTypeFinder = MessageTypeFinder.getInstance();
                if(testContext.getFormat().toLowerCase().equals("edi")) {
                    EdiMessageEditor ediMessageEditor = new EdiMessageEditor();
                    try {
                        String receivedMessageType = messageTypeFinder.findEdiMessageType(received.getMessage());
                        HashMap<String, String> replaceTokens = new HashMap<>();
                        if(receivedMessageType.equals("chgres")||receivedMessageType.equals("refres")){
                            SimpleDateFormat simpleDateFormat = new SimpleDateFormat();
                            simpleDateFormat.applyPattern("yyyymmdd");
                            replaceTokens.put("UIB-080-01", simpleDateFormat.format(new Date()));
                            simpleDateFormat.applyPattern("HHmmss");
                            replaceTokens.put("UIB-080-02", simpleDateFormat.format(new Date()));
                        }
                        replaceTokens.put("UIH-020",receivedMessageType);
                        responseMessage = ediMessageEditor.replaceInMessage(message, replaceTokens, testContext);
                        logger.info("Generated response message : " + responseMessage);
                    } catch (Exception e) {
                        responseMessage = e.getMessage();
                        e.printStackTrace();
                    }
                    transaction.setOutgoing(responseMessage);
                } else if(testContext.getFormat().toLowerCase().equals("xml")){
                    try {
                        String receivedMessageType = messageTypeFinder.findXmlMessageType(received.getMessage());
                        XMLMessageEditor xmlMessageEditor = new XMLMessageEditor();
                        HashMap<String, String> replaceTokens = new HashMap<>();
                        SimpleDateFormat simpleDateFormat = new SimpleDateFormat();
                        simpleDateFormat.applyPattern("yyyy-MM-dd'T'HH:mm:ss");
                        replaceTokens.put("SentTime", simpleDateFormat.format(new Date()));
                        responseMessage = xmlMessageEditor.replaceInMessage(message,replaceTokens,testContext);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    throw new MessageParserException("Message with id "+messageId+" must be either EDI or XML ("+testContext.getFormat()+" found instead)");
                }
            } else {
                throw new TransportClientException("Message with id "+messageId+" not found");
            }
        } else {
            throw new TransportClientException("Message id not found for criteria "+criteria.toString());
        }
        transactionService.save(transaction);
        return responseMessage;
    }



    @Transactional()
    @RequestMapping(value = "/test", method = RequestMethod.GET)
    public String test(@RequestParam String username) {
        //TODO check auth
        String password = "pass";
        return "hello " + username;
    }

}
