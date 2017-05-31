package gov.nist.hit.erx.web.controller;


import gov.nist.auth.hit.core.domain.TransportConfig;
import gov.nist.hit.core.api.SessionContext;
import gov.nist.hit.core.domain.*;
import gov.nist.hit.core.service.exception.TestStepException;
import gov.nist.hit.core.service.exception.UserNotFoundException;
import gov.nist.hit.core.transport.exception.TransportClientException;
import gov.nist.hit.erx.web.utils.SurescriptUtils;
import gov.nist.hit.erx.ws.client.utils.MessageUtils;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
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
        Long userId = SessionContext.getCurrentUserId(session);
        logger.info("Sending message with user ID="+userId+" and test step with id="
                + request.getTestStepId());
        try {
            Long testStepId = request.getTestStepId();
            TestStep testStep = testStepService.findOne(testStepId);
            if (testStep == null)
                throw new TestStepException("Unknown test step with id=" + testStepId);
            boolean replaceSeparators=Boolean.parseBoolean(request.getConfig().get("replaceSeparators"));
            logger.info("Cleaning message to send (replace separators: "+String.valueOf(replaceSeparators));
            String outgoingMessage = MessageUtils.cleanToSend(request.getMessage(),replaceSeparators);
            logger.info("Adding surescript enveloppe");
            String message = SurescriptUtils.addEnveloppe(outgoingMessage, testStep.getTestContext());
            String incoming = send(request,message);
            String edifact;
            logger.info("Message received: "+incoming);
            if(incoming!=null) {
                 edifact = SurescriptUtils.parseEnveloppe(incoming);
                logger.info("Successfully parsed the surescript enveloppe: "+incoming);
            } else {
                edifact = "";
            }
            parseIncomingMessage(edifact,testStep,userId);
            return super.saveTransaction(userId,testStep,MessageUtils.prettyPrint(edifact),message);
        } catch (Exception e1) {
            throw new TransportClientException("Failed to send the message.");
        }
    }
}
