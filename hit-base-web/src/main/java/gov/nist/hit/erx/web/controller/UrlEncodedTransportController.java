package gov.nist.hit.erx.web.controller;


import gov.nist.hit.core.api.SessionContext;
import gov.nist.hit.core.domain.*;
import gov.nist.hit.core.service.exception.TestStepException;
import gov.nist.hit.core.service.exception.UserNotFoundException;
import gov.nist.hit.core.transport.exception.TransportClientException;
import gov.nist.hit.erx.ws.client.utils.MessageUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;


/**
 * Created by mcl1 on 12/16/15.
 */

@RestController
@Controller
@RequestMapping("/transport/erx/urlencoded")
public class UrlEncodedTransportController extends TransportController {

    static final Logger logger = LoggerFactory.getLogger(UrlEncodedTransportController.class);

    private final static String DOMAIN = "erx";
    private final static String PROTOCOL = "urlencoded";

    @RequestMapping(value = "/configs", method = RequestMethod.POST)
    public TransportConfig configs(HttpSession session, HttpServletRequest request)
            throws UserNotFoundException {
        return configs(session, request, PROTOCOL, DOMAIN);
    }

    @RequestMapping(value = "/startListener", method = RequestMethod.POST)
    public boolean startListener(@RequestBody TransportRequest request, HttpSession session)
            throws UserNotFoundException {
        return startListener(request, session, PROTOCOL, DOMAIN);
    }

    @RequestMapping(value = "/stopListener", method = RequestMethod.POST)
    public boolean stopListener(@RequestBody TransportRequest request, HttpSession session)
            throws UserNotFoundException {
        return stopListener(request, session, PROTOCOL, DOMAIN);
    }

    @RequestMapping(value = "/searchTransaction", method = RequestMethod.POST)
    public Transaction searchTransaction(@RequestBody TransportRequest request) {
        logger.info("Searching transaction...");
        Map<String, String> criteria = new HashMap<String, String>();
        criteria.put("username", request.getConfig().get("username"));
        criteria.put("password", request.getConfig().get("password"));
        return searchTransaction(criteria);
    }

    @RequestMapping(value = "/populateMessage", method = RequestMethod.POST)
    public TransportResponse populateMessage(@RequestBody TransportRequest request, HttpSession session) {
        return super.populateMessage(request, session);
    }

    @RequestMapping(value = "/send", method = RequestMethod.POST)
    public Transaction send(@RequestBody TransportRequest request, HttpSession session) throws TransportClientException {
        logger.info("Sending message  with user id=" + SessionContext.getCurrentUserId(session) + " and test step with id="
                + request.getTestStepId());
        Long testStepId = request.getTestStepId();
        TestStep testStep = testStepService.findOne(testStepId);
        if (testStep == null)
            throw new TestStepException("Unknown test step with id=" + testStepId);
        String outgoingMessage = null;
        try {
            outgoingMessage = MessageUtils.encodeMedHistory(request.getMessage());
            StringBuilder body = new StringBuilder();
            body.append("request=");
            body.append(outgoingMessage);
            request.getConfig().put("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE);
            String incoming = send(request,body.toString());
            Long userId = SessionContext.getCurrentUserId(session);
            String decodedMessage = decodeIncomingMessage(incoming,testStep,userId);
            return saveTransaction(userId,testStep,decodedMessage,outgoingMessage);
        } catch (UnsupportedEncodingException e) {
            logger.error("Unable to encode outgoing message: "+request.getMessage());
            e.printStackTrace();
        }
        return null;
    }

//    @Override
//    public String send(TransportRequest request,String message) throws TransportClientException {
//        try {
//            logger.info("Message formatted to be sent : "+message);
//            String incoming = webServiceClient.send(message, request.getConfig().get("username"), request.getConfig().get("password"), request.getConfig().get("endpoint"));
//            logger.info("Response received : "+incoming);
//            return incoming;
//        } catch (Exception e1) {
//            logger.error(e1.toString());
//            throw new TransportClientException("Failed to send the message." + e1.getMessage());
//        }
//    }

    public String decodeIncomingMessage(String incomingMessage,TestStep testStep,Long accountId){
        String decodedMessage = null;
        try {
            if(incomingMessage.startsWith("request=")){
                incomingMessage = incomingMessage.substring("request=".length());
            }
            decodedMessage = MessageUtils.decodeMedHistory(incomingMessage);
            //Read data in the received message
            TestStep nextTestStep = testStepUtils.findNext(testStep);
            if (nextTestStep != null) {
                Message message = new Message();
                message.setContent(decodedMessage);
                mappingUtils.readDatasFromMessage(message, nextTestStep, testCaseExecutionUtils.initTestCaseExecution(accountId, nextTestStep));
            }
            return decodedMessage;
        } catch (UnsupportedEncodingException e) {
            logger.error("Unable to decode the incoming message (UnsupportedEncodingException): "+incomingMessage);
            e.printStackTrace();
        }
        return incomingMessage;
    }
}
