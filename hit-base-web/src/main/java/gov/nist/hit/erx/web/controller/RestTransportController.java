package gov.nist.hit.erx.web.controller;


import gov.nist.hit.core.api.SessionContext;
import gov.nist.hit.core.domain.*;
import gov.nist.hit.core.domain.util.XmlUtil;
import gov.nist.hit.core.repo.UserRepository;
import gov.nist.hit.core.service.*;
import gov.nist.hit.core.service.exception.TestCaseException;
import gov.nist.hit.core.service.exception.TestStepException;
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
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.*;


/**
 * Created by mcl1 on 12/16/15.
 */

@RestController
@Controller
@RequestMapping("/transport/erx/rest")
public class RestTransportController {

    static final Logger logger = LoggerFactory.getLogger(RestTransportController.class);

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

    private final static String DOMAIN = "erx";
    private final static String PROTOCOL = "rest";

    @Transactional
    @RequestMapping(value = "/configs", method = RequestMethod.POST)
    public TransportConfig configs(HttpSession session, HttpServletRequest request)
            throws UserNotFoundException {
        logger.info("Fetching user configuration information ... ");
        Long userId = SessionContext.getCurrentUserId(session);
        User user = null;
        if (userId == null || (user = userService.findOne(userId)) == null) {
            throw new UserNotFoundException();
        }
        TransportConfig transportConfig =
                transportConfigService.findOneByUserAndProtocolAndDomain(userId, PROTOCOL, DOMAIN);
        if (transportConfig == null) {
            transportConfig = transportConfigService.create(PROTOCOL, DOMAIN);
            user.addConfig(transportConfig);
            userService.save(user);
            Map<String, String> sutInitiatorConfig = sutInitiatorConfig(user, request);
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

    private Map<String, String> sutInitiatorConfig(User user, HttpServletRequest request)
            throws UserNotFoundException {
        logger.info("Creating user sut initiator config information ... ");
        Map<String, String> config = new HashMap<String, String>();
        int token = new Random().nextInt(999);
        config.put("username", "vendor_" + user.getId() + "_" + token);
        config.put("password", "vendor_" + user.getId() + "_" + token);
        config.put("endpoint", Utils.getUrl(request) + "/api/ws/" + DOMAIN + "/" + PROTOCOL + "/message");
        return config;
    }

    @Transactional
    @RequestMapping(value = "/startListener", method = RequestMethod.POST)
    public boolean startListener(@RequestBody TransportRequest request, HttpSession session)
            throws UserNotFoundException {
        logger.info("Starting listener");
        Long userId = SessionContext.getCurrentUserId(session);
        if (userId == null || (userService.findOne(userId)) == null) {
            throw new UserNotFoundException();
        }
        if (request.getResponseMessageId() == null)
            throw new gov.nist.hit.core.service.exception.TransportException("Response message not found");
        removeUserTransaction(userId);

        Map<String, String> config = new HashMap<String, String>();
        config.putAll(getSutInitiatorConfig(userId));
        TransportMessage transportMessage = new TransportMessage();
        transportMessage.setMessageId(request.getResponseMessageId());
        transportMessage.setProperties(config);
        transportMessageService.save(transportMessage);
        TestStep testStep = testStepService.findOne(request.getTestStepId());
        if (testStep != null) {
            testCaseExecutionUtils.initTestCaseExecution(userId, testStep);
        }
        userConfigService.save(new UserConfig(config,userId));
        return true;
    }

    @Transactional
    @RequestMapping(value = "/stopListener", method = RequestMethod.POST)
    public boolean stopListener(@RequestBody TransportRequest request, HttpSession session)
            throws UserNotFoundException {
        logger.info("Stopping listener ");
        Long userId = SessionContext.getCurrentUserId(session);
        if (userId == null || (userService.findOne(userId)) == null) {
            throw new UserNotFoundException();
        }
        removeUserTransaction(userId);
        return true;
    }

    @Transactional
    private boolean removeUserTransaction(Long userId) {
        Map<String, String> config = getSutInitiatorConfig(userId);
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

    private Map<String, String> getSutInitiatorConfig(Long userId) {
        TransportConfig config = transportConfigService.findOneByUserAndProtocolAndDomain(userId, PROTOCOL, DOMAIN);
        Map<String, String> sutInitiator = config != null ? config.getSutInitiator() : null;
        if (sutInitiator == null || sutInitiator.isEmpty())
            throw new gov.nist.hit.core.service.exception.TransportException(
                    "No System Under Test configuration info found");
        return sutInitiator;
    }

    @RequestMapping(value = "/searchTransaction", method = RequestMethod.POST)
    public Transaction searchTransaction(@RequestBody TransportRequest request) {
        logger.info("Searching transaction...");
        Map<String, String> criteria = new HashMap<String, String>();
        criteria.put("username", request.getConfig().get("username"));
        criteria.put("password", request.getConfig().get("password"));
        Transaction transaction = transactionService.findOneByProperties(criteria);
        return transaction;
    }

    @Transactional()
    @RequestMapping(value = "/send", method = RequestMethod.POST)
    public Transaction send(@RequestBody TransportRequest request, HttpSession session) throws TransportClientException {
        logger.info("Sending message  with user id=" + request.getUserId() + " and test step with id="
                + request.getTestStepId());
        try {
            Long testStepId = request.getTestStepId();
            Long userId = SessionContext.getCurrentUserId(session);
            TestStep testStep = testStepService.findOne(testStepId);
            if (testStep == null)
                throw new TestCaseException("Unknown test step with id=" + testStepId);
            Message outgoingMessage = testStep.getTestContext().getMessage();
            TestCaseExecution testCaseExecution = testCaseExecutionUtils.initTestCaseExecution(userId,testStep);
            TransportConfig config =
                    transportConfigService.findOneByUserAndProtocolAndDomain(userId, PROTOCOL, DOMAIN);
            config.setTaInitiator(request.getConfig());
            transportConfigService.save(config);
            String responseMessage = mappingUtils.writeDataInMessage(outgoingMessage,testStep,testCaseExecution);
            outgoingMessage.setContent(responseMessage);
            String incomingMessage =
                    webServiceClient.send(outgoingMessage.getContent(), request.getConfig().get("username"), request.getConfig().get("password"), request.getConfig().get("endpoint"));
            String tmp = incomingMessage;
            try {
                incomingMessage = XmlUtil.prettyPrint(incomingMessage);
            } catch (Exception e) {
                incomingMessage = tmp;
            }

            //Read data in the received message
            TestStep nextTestStep = testStepUtils.findNext(testStep);
            if(nextTestStep!=null){
                Message message2 = new Message();
                message2.setContent(incomingMessage);
                mappingUtils.readDatasFromMessage(message2,nextTestStep,testCaseExecutionUtils.initTestCaseExecution(userId,nextTestStep));
            }

            Transaction transaction = transactionService.findOneByUserAndTestStep(userId, testStepId);
            if (transaction == null) {
                transaction = new Transaction();
                //transaction.setTestStep(testStepService.findOne(testStepId));
                transaction.setUser(userRepository.findOne(userId));
                transaction.setOutgoing(outgoingMessage.getContent());
                transaction.setIncoming(incomingMessage);
                transactionService.save(transaction);
            }

            return transaction;
        } catch (Exception e1) {
            logger.error(e1.toString());
            throw new TransportClientException("Failed to send the message." + e1.getMessage());
        }
    }

    private boolean userExist(Long userId) {
        User user = userService.findOne(userId);
        return user != null;
    }

}
