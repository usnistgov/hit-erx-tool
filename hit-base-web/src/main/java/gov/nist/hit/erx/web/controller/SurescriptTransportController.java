package gov.nist.hit.erx.web.controller;


import gov.nist.hit.core.domain.*;
import gov.nist.hit.core.service.exception.TestStepException;
import gov.nist.hit.core.service.exception.UserNotFoundException;
import gov.nist.hit.core.transport.exception.TransportClientException;
import gov.nist.hit.core.xml.domain.XMLTestContext;
import gov.nist.hit.impl.EdiMessageParser;
import gov.nist.hit.impl.XMLMessageEditor;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

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
@RequestMapping("/transport/erx/surescript")
public class SurescriptTransportController extends TransportController {

    private final static String DOMAIN = "erx";
    private final static String PROTOCOL = "surescript";


    @Transactional
    @RequestMapping(value = "/configs", method = RequestMethod.POST)
    public TransportConfig configs(HttpSession session, HttpServletRequest request)
            throws UserNotFoundException {
        return configs(session, request, PROTOCOL, DOMAIN);
    }

    @Transactional
    @RequestMapping(value = "/startListener", method = RequestMethod.POST)
    public boolean startListener(@RequestBody TransportRequest request, HttpSession session)
            throws UserNotFoundException {
        return startListener(request, session, PROTOCOL, DOMAIN);
    }

    @Transactional
    @RequestMapping(value = "/stopListener", method = RequestMethod.POST)
    public boolean stopListener(@RequestBody TransportRequest request, HttpSession session)
            throws UserNotFoundException {
        return stopListener(request, session, PROTOCOL, DOMAIN);
    }


    @RequestMapping(value = "/searchTransaction", method = RequestMethod.POST)
    public Transaction searchTransaction(@RequestBody TransportRequest request) {
        Map<String, String> criteria = new HashMap<String, String>();
        criteria.put("username", request.getConfig().get("username"));
        criteria.put("password", request.getConfig().get("password"));
        return searchTransaction(criteria);
    }

    @Transactional()
    @RequestMapping(value = "/populateMessage", method = RequestMethod.POST)
    public TransportResponse populateMessage(@RequestBody TransportRequest request, HttpSession session) {
        return doPopulateMessage(request, session);
    }

    @Transactional()
    @RequestMapping(value = "/send", method = RequestMethod.POST)
    public Transaction send(@RequestBody TransportRequest request, HttpSession session) throws TransportClientException {
        logger.info("Sending message  with user id=" + request.getUserId() + " and test step with id="
                + request.getTestStepId());
        try {
            Long testStepId = request.getTestStepId();
            TestStep testStep = testStepService.findOne(testStepId);
            if (testStep == null)
                throw new TestStepException("Unknown test step with id=" + testStepId);
            Message toBeParsedMessage = new Message();
            toBeParsedMessage.setContent(request.getMessage());
            EdiMessageParser ediMessageParser = new EdiMessageParser();
            ArrayList<String> dataToRead = new ArrayList<>();
            String messageIDField = "UIB-030-01";
            dataToRead.add(messageIDField);
            Map<String, String> dataRead = ediMessageParser.readInMessage(toBeParsedMessage, dataToRead, testStep.getTestContext());
            if (dataRead.containsKey(messageIDField)) {
                dataRead.put("/Message/Header/MessageID", dataRead.get(messageIDField));
                dataRead.remove(messageIDField);
            }
            String message = addEnveloppe(toBeParsedMessage.getContent(), dataRead);
            return send(request, message, testStep, session);
        } catch (Exception e1) {
            logger.error(e1.toString());
            throw new TransportClientException("Failed to send the message." + e1.getMessage());
        }
    }

    private String addEnveloppe(String message, Map<String, String> dataToReplace) throws Exception {
        String wrappedMessage = "";
        Message XMLMessage = new Message();
        XMLMessage.setContent("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<Message xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" version=\"010\" release=\"006\" xmlns=\"http://www.ncpdp.org/schema/SCRIPT\">\n" +
                "    <Header>\n" +
                "        <To Qualifier=\"P\">RECIPIENT_ID</To>\n" +
                "        <From Qualifier=\"D\">SENDER_ID</From>\n" +
                "        <MessageID>90927</MessageID>\n" +
                "        <SentTime>2015-11-20T14:15:23</SentTime>\n" +
                "    </Header>\n" +
                "    <Body>" +
                "<EDIFACTMessage>\n" +
                "    </EDIFACTMessage>" +
                "</Body>\n" +
                "</Message>");
        dataToReplace.put("/Message/Body/EDIFACTMessage", Base64.getEncoder().encodeToString(message.getBytes()));
        XMLMessageEditor xmlMessageEditor = new XMLMessageEditor();
        wrappedMessage = xmlMessageEditor.replaceInMessage(XMLMessage, (HashMap) dataToReplace, new XMLTestContext());
        return wrappedMessage;
    }
}
