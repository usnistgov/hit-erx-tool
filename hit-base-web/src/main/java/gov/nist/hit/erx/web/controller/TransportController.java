package gov.nist.hit.erx.web.controller;

import gov.nist.hit.core.api.SessionContext;
import gov.nist.hit.core.domain.*;
import gov.nist.hit.core.domain.util.XmlUtil;
import gov.nist.hit.core.repo.UserRepository;
import gov.nist.hit.core.service.*;
import gov.nist.hit.core.service.exception.TestStepException;
import gov.nist.hit.core.service.exception.UserNotFoundException;
import gov.nist.hit.core.transport.exception.TransportClientException;
import gov.nist.hit.erx.web.utils.MappingUtils;
import gov.nist.hit.erx.web.utils.TestCaseExecutionUtils;
import gov.nist.hit.erx.web.utils.TestStepUtils;
import gov.nist.hit.erx.web.utils.Utils;
import gov.nist.hit.erx.ws.client.WebServiceClient;
import gov.nist.hit.impl.EdiMessageParser;
import org.apache.xalan.xsltc.DOM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.*;

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



@Controller
public abstract class TransportController {

    static final Logger logger = LoggerFactory.getLogger(TransportController.class);

    @Autowired
    protected TestStepService testStepService;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected TransactionService transactionService;

    @Autowired
    protected TransportConfigService transportConfigService;

    @Autowired
    protected TransportMessageService transportMessageService;

    @Autowired
    protected TestCaseExecutionUtils testCaseExecutionUtils;

    @Autowired
    protected MappingUtils mappingUtils;

    @Autowired
    protected TestStepUtils testStepUtils;

    @Autowired
    protected UserConfigService userConfigService;

    @Autowired
    @Qualifier("WebServiceClient")
    protected WebServiceClient webServiceClient;

    @Autowired
    protected UserService userService;

    public TransportConfig configs(HttpSession session, HttpServletRequest request, String PROTOCOL, String DOMAIN)
            throws UserNotFoundException {
        logger.info("Fetching user configuration information ... ");
        Long userId = SessionContext.getCurrentUserId(session);
        if (userId == null) {
            throw new UserNotFoundException();
        }
        User user = userService.findOne(userId);
        TransportConfig transportConfig =
                transportConfigService.findOneByUserAndProtocolAndDomain(userId, PROTOCOL, DOMAIN);
        if (transportConfig == null) {
            transportConfig = transportConfigService.create(PROTOCOL, DOMAIN);
            user.addConfig(transportConfig);
            userService.save(user);
            Map<String, String> sutInitiatorConfig = sutInitiatorConfig(user, request, PROTOCOL, DOMAIN);
            Map<String, String> taInitiatorConfig = taInitiatorConfig(user, request);
            transportConfig.setSutInitiator(sutInitiatorConfig);
            transportConfig.setTaInitiator(taInitiatorConfig);
            transportConfigService.save(transportConfig);
        }
        return transportConfig;
    }

    private Map<String, String> taInitiatorConfig(User user, HttpServletRequest request)
            throws UserNotFoundException {
        logger.info("Creating user ta initiator config information ... ");
        Map<String, String> config = new HashMap<String, String>();
        config.put("username", "");
        config.put("password", "");
        return config;
    }

    private Map<String, String> sutInitiatorConfig(User user, HttpServletRequest request, String PROTOCOL, String DOMAIN)
            throws UserNotFoundException {
        logger.info("Creating user sut initiator config information ... ");
        Map<String, String> config = new HashMap<String, String>();
        int token = new Random().nextInt(999);
        config.put("username", "vendor_" + user.getId() + "_" + token);
        config.put("password", "vendor_" + user.getId() + "_" + token);
        config.put("endpoint", Utils.getUrl(request) + "/api/ws/" + DOMAIN + "/" + PROTOCOL + "/message");
        return config;
    }

    public boolean startListener(TransportRequest request, HttpSession session, String PROTOCOL, String DOMAIN)
            throws UserNotFoundException {
        logger.info("Starting listener");
        Long userId = SessionContext.getCurrentUserId(session);
        if (userId == null || (userService.findOne(userId)) == null) {
            throw new UserNotFoundException();
        }
        if (request.getResponseMessageId() == null)
            throw new gov.nist.hit.core.service.exception.TransportException("Response message not found");
        removeUserTransaction(userId, PROTOCOL, DOMAIN);

        Map<String, String> config = new HashMap<String, String>();
        config.putAll(getSutInitiatorConfig(userId, PROTOCOL, DOMAIN));
        TransportMessage transportMessage = new TransportMessage();
        transportMessage.setMessageId(request.getResponseMessageId());
        transportMessage.setProperties(config);
        transportMessageService.save(transportMessage);
        TestStep testStep = testStepService.findOne(request.getTestStepId());
        if (testStep != null) {
            testCaseExecutionUtils.initTestCaseExecution(userId, testStep);
        }
        userConfigService.save(new UserConfig(config, userId));
        return true;
    }

    public boolean stopListener(TransportRequest request, HttpSession session, String PROTOCOL, String DOMAIN)
            throws UserNotFoundException {
        logger.info("Stopping listener ");
        Long userId = SessionContext.getCurrentUserId(session);
        if (userId == null || (userService.findOne(userId)) == null) {
            throw new UserNotFoundException();
        }
        removeUserTransaction(userId,PROTOCOL,DOMAIN);
        return true;
    }

    public Transaction searchTransaction(Map<String, String> criteria) {
        //We don't search criteria here as it's relative to a protocol
        logger.info("Searching transaction...");
        Transaction transaction = transactionService.findOneByProperties(criteria);
        return transaction;
    }

    public TransportResponse populateMessage(TransportRequest request, HttpSession session) {
        Long testStepId = request.getTestStepId();
        Long userId = SessionContext.getCurrentUserId(session);
        TransportResponse transportResponse = new TransportResponse();
        String messageContent = request.getMessage();
        TestStep testStep = testStepService.findOne(testStepId);
        if (testStep != null) {
            TestCaseExecution testCaseExecution = testCaseExecutionUtils.initTestCaseExecution(userId, testStep);
            if (testCaseExecution != null) {
                Message message = new Message();
                message.setContent(messageContent);
                messageContent = mappingUtils.writeDataInMessage(message, testStep, testCaseExecution);
            }
        }
        transportResponse.setOutgoingMessage(messageContent);
        return transportResponse;
    }

    public String send(TransportRequest request,String message) throws TransportClientException {
        try {
            String incoming = webServiceClient.send(message, request.getConfig().get("username"), request.getConfig().get("password"), request.getConfig().get("endpoint"));
            logger.info("Response received : "+incoming);
            return incoming;
        } catch (Exception e1) {
            logger.error(e1.toString());
            throw new TransportClientException("Failed to send the message." + e1.getMessage());
        }
    }

    public void parseIncomingMessage(String incomingMessage,TestStep testStep,Long userId){
        String tmp = incomingMessage;
        try {
            incomingMessage = XmlUtil.prettyPrint(incomingMessage);
        } catch (Exception e) {
            incomingMessage = tmp;
        }
        //Read data in the received message
        TestStep nextTestStep = testStepUtils.findNext(testStep);
        if (nextTestStep != null) {
            Message message2 = new Message();
            message2.setContent(incomingMessage);
            mappingUtils.readDatasFromMessage(message2, nextTestStep, testCaseExecutionUtils.initTestCaseExecution(userId, nextTestStep));
        }
    }

    protected Transaction saveTransaction(Long userId,TestStep testStep,String incoming,String outgoing){
        Transaction transaction = transactionService.findOneByUserAndTestStep(userId, testStep.getId());
        if (transaction == null) {
            transaction = new Transaction();
            //transaction.setTestStep(testStepService.findOne(testStepId));
            transaction.setUser(userRepository.findOne(userId));
            //transaction.setOutgoing(outgoingMessage.getContent());
            transaction.setOutgoing(outgoing);
            transaction.setIncoming(incoming);
            transactionService.save(transaction);
        }
        return transaction;
    }


    @Transactional
    private boolean removeUserTransaction(Long userId, String PROTOCOL, String DOMAIN) {
        Map<String, String> config = getSutInitiatorConfig(userId, PROTOCOL, DOMAIN);
        List<TransportMessage> transportMessages = transportMessageService.findAllByProperties(config);
        if (transportMessages != null) {
            transportMessageService.delete(transportMessages);
        }
        List<Transaction> transactions = transactionService.findAllByProperties(config);
        if (transactions != null) {
            transactionService.delete(transactions);
        }
        return true;
    }

    private Map<String, String> getSutInitiatorConfig(Long userId, String PROTOCOL, String DOMAIN) {
        TransportConfig config = transportConfigService.findOneByUserAndProtocolAndDomain(userId, PROTOCOL, DOMAIN);
        Map<String, String> sutInitiator = config != null ? config.getSutInitiator() : null;
        if (sutInitiator == null || sutInitiator.isEmpty())
            throw new gov.nist.hit.core.service.exception.TransportException(
                    "No System Under Test configuration info found");
        return sutInitiator;
    }

    private boolean userExist(Long userId) {
        User user = userService.findOne(userId);
        return user != null;
    }
}
