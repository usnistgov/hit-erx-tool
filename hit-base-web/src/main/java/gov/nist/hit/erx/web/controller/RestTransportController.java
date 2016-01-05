package gov.nist.hit.erx.web.controller;


import gov.nist.hit.core.domain.*;
import gov.nist.hit.core.domain.util.XmlUtil;
import gov.nist.hit.core.repo.TransactionRepository;
import gov.nist.hit.core.repo.UserRepository;
import gov.nist.hit.core.service.TestStepService;
import gov.nist.hit.core.service.TransportConfigService;
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


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
    protected TransactionRepository transactionRepository;

    @Autowired
    protected TransportConfigService transportConfigService;

    @Autowired
    @Qualifier("WebServiceClient")
    protected WebServiceClient webServiceClient;

    private final static String DOMAIN = "erx";
    private final static String PROTOCOL = "rest";



    /*@Autowired
    protected SOAPSecurityFaultCredentialsRepository securityFaultCredentialsRepository;*/

    public RestTransportController() throws IOException {

    }

    @Transactional()
    @RequestMapping(value = "/configListener", method = RequestMethod.POST)
    public TransportConfig config(@RequestParam("userId") final Long userId,
                                  HttpServletRequest request) throws UserNotFoundException {
        logger.info("Fetching user information ... ");
        User user = null;
        TransportConfig config = null;
        if (userId == null || (user = userRepository.findOne(userId)) == null) {
            throw new UserNotFoundException();
        }
        config =
                transportConfigService.findOneByUserAndProtocolAndDomain(user.getId(), PROTOCOL, DOMAIN);
        if (config == null) {
            config = transportConfigService.create("rest");
            user.addConfig(config);
        }
        if (config.getSutInitiator().get("password") == null
                && config.getSutInitiator().get("username") == null) {
            List<KeyValuePair> pairs = new ArrayList<KeyValuePair>();
            pairs.add(new KeyValuePair("username", "vendor_" + config.getId()));
            pairs.add(new KeyValuePair("password", "vendor_" + config.getId()));
            transportConfigService.set(pairs, TestStepTestingType.SUT_INITIATOR, config);
        }

        if (config.getSutInitiator().get("endpoint") == null) {
            List<KeyValuePair> pairs = new ArrayList<KeyValuePair>();
            pairs.add(new KeyValuePair("endpoint", Utils.getUrl(request) + "/message"));
            transportConfigService.set(pairs, TestStepTestingType.SUT_INITIATOR, config);
        }
        userRepository.save(user);
        return config;
    }

    @Transactional()
    @RequestMapping(value = "/startListener", method = RequestMethod.POST)
    public boolean open(@RequestBody SendRequest request)  throws UserNotFoundException {
        logger.info("Open transaction for user with id=" + request.getUserId()
                + " and of test step with id=" + request.getTestStepId());
        Transaction transaction = transaction(request);
        if (transaction != null && transaction.getUser() != null) {
            transaction.init();
            transactionRepository.saveAndFlush(transaction);
            return true;
        }
        return false;
    }

    // private void setResponseMessageId(TransportAccount transportAccount, String messageId) {
    // transportAccount.getInfo().put("responseMessageId", messageId);
    // transportAccountRepository.save(transportAccount);
    // }

    @Transactional()
    @RequestMapping(value = "/stopListener", method = RequestMethod.POST)
    public boolean close(@RequestBody SendRequest request)  throws UserNotFoundException {
        logger.info("Closing transaction for user with id=" + request.getUserId()
                + " and of test step with id=" + request.getTestStepId());
        Transaction transaction = transaction(request);
        if (transaction != null) {
            // setResponseMessageId(transaction.getTransportAccount(), null);
            transaction.close();
            transactionRepository.saveAndFlush(transaction);
        }
        return true;
    }

    @RequestMapping(value = "/transaction", method = RequestMethod.POST)
    public Transaction transaction(@RequestBody SendRequest request) throws UserNotFoundException {
        logger.info("Get transaction of user with id=" + request.getUserId()
                + " and of testStep with id=" + request.getTestStepId());
        Transaction transaction =
                transactionRepository
                        .findOneByUserAndTestStep(request.getUserId(), request.getTestStepId());
        if (transaction == null) {
            transaction = new Transaction();
            transaction.setTestStep(testStepService.findOne(request.getTestStepId()));
            transaction.setUser(userRepository.findOne(request.getUserId()));
            if(transaction.getUser()!=null)
                transactionRepository.save(transaction);
            else {
                throw new UserNotFoundException();
            }
        }
        return transaction;
    }

    @Transactional()
    @RequestMapping(value = "/send", method = RequestMethod.POST)
    public Transaction send(@RequestBody SendRequest request) throws TransportClientException {
        logger.info("Sending message  with user id=" + request.getUserId() + " and test step with id="
                + request.getTestStepId());
        try {
            Long testStepId = request.getTestStepId();
            Long userId = request.getUserId();
            TransportConfig config =
                    transportConfigService.findOneByUserAndProtocolAndDomain(userId, PROTOCOL, DOMAIN);
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
            Transaction transaction = transactionRepository.findOneByUserAndTestStep(userId, testStepId);
            if (transaction == null) {
                transaction = new Transaction();
                transaction.setTestStep(testStepService.findOne(testStepId));
                transaction.setUser(userRepository.findOne(userId));
                transaction.setOutgoing(outgoingMessage);
                transaction.setIncoming(incomingMessage);
                transactionRepository.save(transaction);
            }
            return transaction;
        } catch (Exception e1) {
            throw new TransportClientException("Failed to send the message." + e1.getMessage());
        }
    }

    @Transactional()
    @RequestMapping(value = "/message", method = RequestMethod.POST)
    public String message(@RequestBody SendRequest request) throws TransportClientException {
        //TODO check auth
        logger.debug("Send message request received : "+request.toString());
        return this.webServiceClient.send(request.getMessage(),"username","password",request.getConfig().get("endpoint"));
    }

    @Transactional()
    @RequestMapping(value = "/test", method = RequestMethod.GET)
    public String test(@RequestParam String username){
        //TODO check auth
        String password = "pass";
        return "hello "+username;
    }

    public TransactionRepository getTransactionRepository() {
        return transactionRepository;
    }

    public void setTransactionRepository(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }


}
