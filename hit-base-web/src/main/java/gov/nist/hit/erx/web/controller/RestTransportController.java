package gov.nist.hit.erx.web.controller;


import gov.nist.hit.core.api.SessionContext;
import gov.nist.hit.core.domain.*;
import gov.nist.hit.core.service.exception.TestStepException;
import gov.nist.hit.core.service.exception.UserNotFoundException;
import gov.nist.hit.core.transport.exception.TransportClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Created by mcl1 on 12/16/15.
 */

@RestController
@Controller
@RequestMapping("/transport/erx/rest")
public class RestTransportController extends TransportController {

    static final Logger logger = LoggerFactory.getLogger(RestTransportController.class);

    private final static String DOMAIN = "erx";
    private final static String PROTOCOL = "rest";

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
        logger.info("Searching transaction...");
        Map<String, String> criteria = new HashMap<String, String>();
        criteria.put("username", request.getConfig().get("username"));
        criteria.put("password", request.getConfig().get("password"));
        return searchTransaction(criteria);
    }

    @Transactional()
    @RequestMapping(value = "/populateMessage", method = RequestMethod.POST)
    public TransportResponse populateMessage(@RequestBody TransportRequest request, HttpSession session) {
        return super.populateMessage(request, session);
    }

    @Transactional()
    @RequestMapping(value = "/send", method = RequestMethod.POST)
    public Transaction send(@RequestBody TransportRequest request, HttpSession session) throws TransportClientException {
        logger.info("Sending message  with user id=" + SessionContext.getCurrentUserId(session) + " and test step with id="
                + request.getTestStepId());
        Long testStepId = request.getTestStepId();
        TestStep testStep = testStepService.findOne(testStepId);
        if (testStep == null)
            throw new TestStepException("Unknown test step with id=" + testStepId);
        String outgoingMessage = super.removeCarriageReturn(request.getMessage());
        String incoming = send(request,outgoingMessage);
        Long userId = SessionContext.getCurrentUserId(session);
        parseIncomingMessage(incoming,testStep,userId);
        return saveTransaction(userId,testStep,incoming,outgoingMessage);
    }
}
