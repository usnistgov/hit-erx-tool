package gov.nist.hit.erx.web.controller;


import com.google.gson.Gson;
import gov.nist.hit.core.domain.*;
import gov.nist.hit.core.domain.util.XmlUtil;
import gov.nist.hit.core.repo.UserRepository;
import gov.nist.hit.core.service.*;
import gov.nist.hit.core.service.exception.TestCaseException;
import gov.nist.hit.core.service.exception.UserNotFoundException;
import gov.nist.hit.core.transport.exception.TransportClientException;
import gov.nist.hit.erx.web.utils.Utils;
import gov.nist.hit.erx.ws.client.WebServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


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
    protected UserService userService;

    @Autowired
    @Qualifier("WebServiceClient")
    protected WebServiceClient webServiceClient;

    private final static String DOMAIN = "erx";
    private final static String PROTOCOL = "rest";

    @Transactional()
    @RequestMapping(value = "/user/{userId}/taInitiator", method = RequestMethod.POST)
    public Map<String, String> taInitiatorConfig(@PathVariable("userId") final Long userId)
            throws UserNotFoundException {
        logger.info("Fetching user ta initiator information ... ");
        User user = null;
        TransportConfig transportConfig = null;
        if (userId == null || (user = userRepository.findOne(userId)) == null) {
            throw new UserNotFoundException();
        }
        transportConfig = transportConfigService.findOneByUserAndProtocol(user.getId(), PROTOCOL);
        if (transportConfig == null) {
            transportConfig = transportConfigService.create(PROTOCOL);
            user.addConfig(transportConfig);
            userRepository.save(user);
            transportConfigService.save(transportConfig);
        }
        Map<String, String> config = transportConfig.getTaInitiator();
        return config;
    }

    @Transactional()
    @RequestMapping(value = "/user/{userId}/sutInitiator", method = RequestMethod.POST)
    public Map<String, String> sutInitiatorConfig(@PathVariable("userId") final Long userId,
                                                  HttpServletRequest request) throws UserNotFoundException {
        logger.info("Fetching user information ... ");
        User user = null;
        TransportConfig transportConfig = null;
        if (userId == null || (user = userRepository.findOne(userId)) == null) {
            throw new UserNotFoundException();
        }
        transportConfig = transportConfigService.findOneByUserAndProtocol(user.getId(), PROTOCOL);
        if (transportConfig == null) {
            transportConfig = transportConfigService.create(PROTOCOL);
            user.addConfig(transportConfig);
            userRepository.save(user);
        }
        Map<String, String> config = transportConfig.getSutInitiator();
        if (config == null) {
            config = new HashMap<String, String>();
            transportConfig.setSutInitiator(config);
        }

        if (config.get("password") == null && config.get("username") == null) {
            config.put("username", "vendor_" + user.getId());
            config.put("password", "vendor_" + user.getId());
        }

        if (config.get("endpoint") == null) {
            config.put("endpoint", Utils.getUrl(request) + "/api/transport/" + DOMAIN + "/" + PROTOCOL + "/message");
        }
        transportConfigService.save(transportConfig);
        return config;
    }

    @Transactional()
    @RequestMapping(value = "/startListener", method = RequestMethod.POST)
    public boolean startListener(@RequestBody TransportRequest request)  throws UserNotFoundException {
        stopListener(request);
        logger.info("Starting listener for user with id=" + request.getUserId());
        if (request.getResponseMessageId() == null)
            throw new gov.nist.hit.core.service.exception.TransportException("Response message not found");
        TransportMessage transportMessage = new TransportMessage();
        transportMessage.setMessageId(request.getResponseMessageId());
        transportMessage.setProperties(request.getConfig());
        transportMessageService.save(transportMessage);
        return true;
    }

    private Map<String, String> getSutInitiatorConfig(Long userId) {
        TransportConfig config = transportConfigService.findOneByUserAndProtocol(userId, PROTOCOL);
        Map<String, String> sutInitiator = config != null ? config.getSutInitiator() : null;
        if (sutInitiator == null || sutInitiator.isEmpty())
            throw new gov.nist.hit.core.service.exception.TransportException(
                    "No System Under Test configuration info found");
        return sutInitiator;
    }

    @Transactional()
    @RequestMapping(value = "/stopListener", method = RequestMethod.POST)
    public boolean stopListener(@RequestBody TransportRequest request)  throws UserNotFoundException {
        logger.info("Stopping listener for user with id=" + request.getUserId());

        if (request.getUserId() == null)
            throw new gov.nist.hit.core.service.exception.TransportException("User info not found");

        if (!userExist(request.getUserId()))
            throw new gov.nist.hit.core.service.exception.TransportException(
                    "We couldn't recognize the user");

        Map<String, String> config = getSutInitiatorConfig(request.getUserId());
        TransportMessage transportMessage = transportMessageService.findOneByProperties(config);
        if (transportMessage != null) {
            transportMessageService.delete(transportMessage);
        }
        Transaction transaction = transactionService.findOneByProperties(config);
        if (transaction != null) {
            transactionService.delete(transaction);
        }
        return true;
    }

    @RequestMapping(value = "/searchTransaction", method = RequestMethod.POST)
    public Transaction searchTransaction(@RequestBody TransportRequest request) {
        logger.info("Get transaction of user with id=" + request.getUserId()
                + " and of testStep with id=" + request.getTestStepId());
        Map<String,String> criteria = new HashMap<>();
        criteria.put("username", request.getConfig().get("username"));
        criteria.put("password", request.getConfig().get("password"));
        Transaction transaction = transactionService.findOneByProperties(criteria);
        return transaction;
    }

    @Transactional()
    @RequestMapping(value = "/send", method = RequestMethod.POST)
    public Transaction send(@RequestBody TransportRequest request) throws TransportClientException {
        logger.info("Sending message  with user id=" + request.getUserId() + " and test step with id="
                + request.getTestStepId());
        try {
            Long testStepId = request.getTestStepId();
            Long userId = request.getUserId();
            TransportConfig config =
                    transportConfigService.findOneByUserAndProtocol(userId, PROTOCOL);
            config.setTaInitiator(request.getConfig());
            transportConfigService.save(config);
            TestStep testStep = testStepService.findOne(testStepId);
            if (testStep == null)
                throw new TestCaseException("Unknown test step with id=" + testStepId);
            String outgoingMessage = request.getMessage();
            String incomingMessage =
                    webServiceClient.send(outgoingMessage, request.getConfig().get("username"),request.getConfig().get("password"),request.getConfig().get("endpoint"));
            String tmp = incomingMessage;
            try {
                incomingMessage = XmlUtil.prettyPrint(incomingMessage);
            } catch (Exception e) {
                incomingMessage = tmp;
            }
            Transaction transaction = transactionService.findOneByUserAndTestStep(userId, testStepId);
            if (transaction == null) {
                transaction = new Transaction();
                //transaction.setTestStep(testStepService.findOne(testStepId));
                transaction.setUser(userRepository.findOne(userId));
                transaction.setOutgoing(outgoingMessage);
                transaction.setIncoming(incomingMessage);
                transactionService.save(transaction);
            }
            return transaction;
        } catch (Exception e1) {
            throw new TransportClientException("Failed to send the message." + e1.getMessage());
        }
    }

    @Transactional()
    @RequestMapping(value = "/message", method = RequestMethod.POST)
    public TransportMessage message(@RequestBody TransportRequest request) throws TransportClientException {
        //TODO check auth

        Gson gson = new Gson();
        String requestMessage = request.getMessage();
        logger.info("Message received : " + requestMessage);
        //TODO modify the response message
        Map<String,String> criteria = new HashMap<>();
        criteria.put("username", request.getConfig().get("username"));
        criteria.put("password", request.getConfig().get("password"));
        Transaction transaction = transactionService.findOneByProperties(criteria);
        transaction.setIncoming(requestMessage);
        Long messageId = transportMessageService.findMessageIdByProperties(criteria);
        TransportMessage message = transportMessageService.findOne(messageId);
        transaction.setOutgoing(message.toString());
        return message;
    }

    @Transactional()
    @RequestMapping(value = "/test", method = RequestMethod.GET)
    public String test(@RequestParam String username){
        //TODO check auth
        String password = "pass";
        return "hello "+username;
    }

    private boolean userExist(Long userId) {
        User user = userService.findOne(userId);
        return user != null;
    }

    public TransactionService getTransactionService() {
        return transactionService;
    }

    public void setTransactionService(TransactionService transactionService) {
        this.transactionService = transactionService;
    }
}
