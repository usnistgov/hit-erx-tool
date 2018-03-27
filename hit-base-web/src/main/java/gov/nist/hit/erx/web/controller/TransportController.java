package gov.nist.hit.erx.web.controller;

import gov.nist.auth.hit.core.domain.Account;
import gov.nist.auth.hit.core.domain.TransportConfig;
import gov.nist.hit.core.api.SessionContext;
import gov.nist.hit.core.domain.*;
import gov.nist.hit.core.domain.util.XmlUtil;
import gov.nist.hit.core.service.*;
import gov.nist.hit.core.service.exception.UserNotFoundException;
import gov.nist.hit.core.transport.exception.TransportClientException;
import gov.nist.hit.erx.web.utils.MappingUtils;
import gov.nist.hit.erx.web.utils.TestCaseExecutionUtils;
import gov.nist.hit.erx.web.utils.TestStepUtils;
import gov.nist.hit.erx.web.utils.Utils;
import gov.nist.hit.erx.ws.client.WebServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

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


@PropertySource(value = {"classpath:app-config.properties"})
@Controller
public abstract class TransportController {

    static final Logger logger = LoggerFactory.getLogger(TransportController.class);

    @Autowired
    protected TestStepService testStepService;


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
    protected AccountService accountService;

    @Autowired
    protected PasswordService passwordService;

    @Value("${app.transport.baseUrl:#{null}}")
    private String transportBaseUrl;

    public TransportConfig configs(HttpSession session, HttpServletRequest request, String PROTOCOL, String DOMAIN)
            throws UserNotFoundException {
        logger.info("Fetching user configuration information ... ");
        Long userId = SessionContext.getCurrentUserId(session);
        Account user = null;
        if (userId == null || (user = accountService.findOne(userId)) == null) {
            throw new UserNotFoundException();
        }
        TransportConfig transportConfig =
                transportConfigService.findOneByUserAndProtocolAndDomain(userId, PROTOCOL, DOMAIN);
        if (transportConfig == null) {
            transportConfig = transportConfigService.create(PROTOCOL, DOMAIN);
            transportConfig.setUserId(userId);
            Map<String, String> taInitiatorConfig = taInitiatorConfig(user, request);
            transportConfig.setTaInitiator(taInitiatorConfig);
        }
        Map<String, String> sutInitiatorConfig = sutInitiatorConfig(user, request,PROTOCOL,DOMAIN);
        transportConfig.setSutInitiator(sutInitiatorConfig);
        transportConfigService.save(transportConfig);
        return transportConfig;
    }


    private Map<String, String> taInitiatorConfig(Account account, HttpServletRequest request)
            throws UserNotFoundException {
        logger.info("Creating user ta initiator config information ... ");
        Map<String, String> config = new HashMap<String, String>();
        config.put("username", "");
        config.put("password", "");
        return config;
    }

    private Map<String, String> sutInitiatorConfig(Account user, HttpServletRequest request, String PROTOCOL, String DOMAIN)
            throws UserNotFoundException {
        logger.info("Creating user sut initiator config information ... ");
        Map<String, String> config = new HashMap<String, String>();
        int token = new Random().nextInt(999);
        config.put("username",
                user.isGuestAccount() ? "vendor_" + user.getId() + "_" + token : user.getUsername());
        config.put("password",
                user.isGuestAccount() ? "vendor_" + user.getId() + "_" + token : passwordService.getEncryptedPassword(user.getUsername()));
        String endpoint = "";
        if(transportBaseUrl == null || transportBaseUrl == "null") {
            endpoint = Utils.getUrl(request);
        } else {
            endpoint = transportBaseUrl;
        }
        endpoint += "/api/wss/" + DOMAIN + "/" + PROTOCOL + "/message";
        if(endpoint.contains("psapps.nist.gov:7319")){
            endpoint = endpoint.replace("psapps.nist.gov:7319","www-s.nist.gov");
        }
        config.put("endpoint", endpoint);
        return config;
    }

    public boolean startListener(TransportRequest request, HttpSession session, String PROTOCOL, String DOMAIN)
            throws UserNotFoundException {
        Long userId = SessionContext.getCurrentUserId(session);
        if (userId == null || (accountService.findOne(userId)) == null) {
            throw new UserNotFoundException();
        }
        logger.info("Starting listener for userId: "+userId+" and protocol: "+PROTOCOL);
        clearExchanges(userId, PROTOCOL, DOMAIN);
        if (request.getResponseMessageId() == null)
            throw new gov.nist.hit.core.service.exception.TransportException("Response message not found");

        TransportMessage transportMessage = new TransportMessage();
        transportMessage.setMessageId(request.getResponseMessageId());
        Map<String, String> config = new HashMap<String, String>();
        config.putAll(getSutInitiatorConfig(userId,PROTOCOL,DOMAIN));
        transportMessage.setProperties(config);
        transportMessageService.save(transportMessage);
        TestStep testStep = testStepService.findOne(request.getTestStepId());
        if (testStep != null) {
            testCaseExecutionUtils.initTestCaseExecution(userId, testStep);
        }
        userConfigService.save(new UserConfig(config, userId));
        return true;
    }

    private boolean clearExchanges(Long userId, String PROTOCOL, String DOMAIN) {
        Map<String, String> config = getSutInitiatorConfig(userId,PROTOCOL,DOMAIN);
        Map<String, String> criteria = new HashMap<String, String>();
        criteria.put("username", config.get("username"));
        criteria.put("password", config.get("password"));
        logger.info("Clearing all the previous transactions for criterias: "+criteria.toString());
        clearMessages(criteria);
        clearTransactions(criteria);
        return true;
    }

    private void clearMessages(Map<String, String> criteria) {
        List<TransportMessage> transportMessages =
                transportMessageService.findAllByProperties(criteria);
        if (transportMessages != null && !transportMessages.isEmpty()) {
            transportMessageService.delete(transportMessages);
        }
    }

    private void clearTransactions(Map<String, String> criteria) {
        List<Transaction> transactions = transactionService.findAllByProperties(criteria);
        if (transactions != null && !transactions.isEmpty()) {
            transactionService.delete(transactions);
        }
    }

    public boolean stopListener(TransportRequest request, HttpSession session, String PROTOCOL, String DOMAIN)
            throws UserNotFoundException {
        Long accountId = SessionContext.getCurrentUserId(session);
        if (accountId == null || (accountService.findOne(accountId)) == null) {
            throw new UserNotFoundException();
        }
        logger.info("Stopping listener for userId: "+accountId+" and protocol: "+PROTOCOL);
        clearExchanges(accountId,PROTOCOL,DOMAIN);
        return true;
    }

    public Transaction searchTransaction(Map<String, String> criteria) {
        //We don't search criteria here as it's relative to a protocol
        logger.info("Searching transaction with criteria: "+criteria.toString());
        //criteria.remove("password");
        Transaction transaction = transactionService.findOneByProperties(criteria);
        return transaction;
    }

    public TransportResponse populateMessage(TransportRequest request, HttpSession session) {
        Long testStepId = request.getTestStepId();
        Long accountId = SessionContext.getCurrentUserId(session);
        TransportResponse transportResponse = new TransportResponse();
        String messageContent = request.getMessage();
        TestStep testStep = testStepService.findOne(testStepId);
        if (testStep != null) {
            TestCaseExecution testCaseExecution = testCaseExecutionUtils.initTestCaseExecution(accountId, testStep);
            if (testCaseExecution != null) {
                Message message = new Message();
                message.setContent(messageContent);
                messageContent = mappingUtils.writeDataInMessage(message, testStep, testCaseExecution);
            }
        }
        logger.info("Response message successfully populated and ready to send back: "+messageContent);
        transportResponse.setOutgoingMessage(messageContent);
        return transportResponse;
    }

    public String send(TransportRequest request,String message) throws TransportClientException {
        try {
            logger.info("Message formatted to be sent : "+message);
            String contentType = MediaType.TEXT_XML_VALUE;
            if(request.getConfig().get("Content-Type")!=null){
                contentType = request.getConfig().get("Content-Type");
            }
            String incoming = webServiceClient.send(message, request.getConfig().get("username"), request.getConfig().get("password"), request.getConfig().get("endpoint"),contentType);
            logger.info("Response received : "+incoming);
            return incoming;
        } catch (Exception e1) {
            logger.error(e1.toString());
            throw new TransportClientException("Failed to send the message." + e1.getMessage());
        }
    }

    public void parseIncomingMessage(String incomingMessage,TestStep testStep,Long accountId){
        logger.info("Parsing incoming message: "+incomingMessage);
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
            Map<String, String> data = mappingUtils.readDatasFromMessage(message2, nextTestStep,
                testCaseExecutionUtils.initTestCaseExecution(accountId, nextTestStep));
            if(data!=null) {
                logger.info("Data extracted from the received message for the mapping: " + data.toString());
            } else {
                logger.info("No data extracted from the received message for the mapping.");
            }
        }
    }

    protected Transaction saveTransaction(Long accountId,TestStep testStep,String incoming,String outgoing){
        Transaction transaction = transactionService.findOneByUserAndTestStep(accountId, testStep.getId());
        if (transaction == null) {
            transaction = new Transaction();
            //transaction.setTestStep(testStepService.findOne(testStepId));
            transaction.setUserId(accountId);
            //transaction.setOutgoing(outgoingMessage.getContent());
            transaction.setOutgoing(outgoing);
            transaction.setIncoming(incoming);
            transactionService.save(transaction);
            logger.info("Transaction successfully saved: {accountId:"+accountId+",testStepId:"+testStep.getPersistentId()+",incoming:"+incoming+",outgoing:"+outgoing+"}");
        }
        return transaction;
    }

    private Map<String, String> getSutInitiatorConfig(Long accountId, String PROTOCOL, String DOMAIN) {
        TransportConfig config = transportConfigService.findOneByUserAndProtocolAndDomain(accountId, PROTOCOL, DOMAIN);
        Map<String, String> sutInitiator = config != null ? config.getSutInitiator() : null;
        if (sutInitiator == null || sutInitiator.isEmpty()) {
            logger.error("SUT configuration not found for accountId "+accountId+" and protocol "+PROTOCOL);
            throw new gov.nist.hit.core.service.exception.TransportException(
                "No System Under Test configuration info found");
        } else {
            logger.info("SUT configuration found for userId "+accountId+" and protocol "+PROTOCOL+" : "+sutInitiator.toString());
        }
        //Get the replaceSeparators parameters for the response we send back
        Map<String, String> taInitiator = config != null ? config.getTaInitiator() : null;
        if(taInitiator!=null&&taInitiator.containsKey("replaceSeparators")){
            sutInitiator.put("replaceSeparators",taInitiator.get("replaceSeparators"));
            logger.info("Adding the replaceSeparator parameter to the SUT config: "+sutInitiator.get("replaceSeparators"));
        }
        return sutInitiator;
    }

    private boolean accountExist(Long accountId) {
        Account account = accountService.findOne(accountId);
        return account != null;
    }
}
