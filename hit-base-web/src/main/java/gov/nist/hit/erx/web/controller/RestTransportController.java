package gov.nist.hit.erx.web.controller;


import gov.nist.hit.core.domain.KeyValuePair;
import gov.nist.hit.core.domain.SendRequest;
import gov.nist.hit.core.domain.TestStep;
import gov.nist.hit.core.domain.TestStepTestingType;
import gov.nist.hit.core.domain.Transaction;
import gov.nist.hit.core.domain.TransportConfig;
import gov.nist.hit.core.domain.User;
import gov.nist.hit.core.domain.util.XmlUtil;
import gov.nist.hit.core.repo.TransactionRepository;
import gov.nist.hit.core.repo.UserRepository;
import gov.nist.hit.core.service.TestStepService;
import gov.nist.hit.core.service.TransportConfigService;
import gov.nist.hit.core.service.exception.DuplicateTokenIdException;
import gov.nist.hit.core.service.exception.TestCaseException;
import gov.nist.hit.core.service.exception.UserNotFoundException;
import gov.nist.hit.core.service.exception.UserTokenIdNotFoundException;
import gov.nist.hit.core.transport.exception.TransportClientException;
import gov.nist.hit.core.transport.service.TransportClient;
import gov.nist.hit.erx.service.utils.ConnectivityUtil;
import gov.nist.hit.erx.web.utils.Utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;


/**
 * Created by mcl1 on 12/16/15.
 */

@RestController
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
    private TransportClient transportClient;


    private final static String DOMAIN = "erx";
    private final static String PROTOCOL = "rest";



    /*@Autowired
    protected SOAPSecurityFaultCredentialsRepository securityFaultCredentialsRepository;*/

    String SUMBIT_SINGLE_MESSAGE_TEMPLATE = null;

    public RestTransportController() throws IOException {
        SUMBIT_SINGLE_MESSAGE_TEMPLATE =
                IOUtils.toString(IsolatedTestingController.class
                        .getResourceAsStream("/templates/SubmitSingleMessage.xml"));
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

        if (config.getSutInitiator().get("faultPassword") == null
                && config.getSutInitiator().get("faultUsername") == null) {
            List<KeyValuePair> pairs = new ArrayList<KeyValuePair>();
            pairs.add(new KeyValuePair("faultUsername", "faultUser_" + config.getId()));
            pairs.add(new KeyValuePair("faultPassword", "faultUser_" + config.getId()));
            transportConfigService.set(pairs, TestStepTestingType.SUT_INITIATOR, config);
        }

        if (config.getSutInitiator().get("endpoint") == null) {
            List<KeyValuePair> pairs = new ArrayList<KeyValuePair>();
            pairs.add(new KeyValuePair("endpoint", Utils.getUrl(request) + "/ws/iisService"));
            transportConfigService.set(pairs, TestStepTestingType.SUT_INITIATOR, config);
        }
        userRepository.save(user);
        return config;
    }

    @Transactional()
    @RequestMapping(value = "/startListener", method = RequestMethod.POST)
    public boolean open(@RequestBody SendRequest request) {
        logger.info("Open transaction for user with id=" + request.getUserId()
                + " and of test step with id=" + request.getTestStepId());
        Transaction transaction = transaction(request);
        if (transaction != null) {
            transaction.init();;
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
    public boolean close(@RequestBody SendRequest request) {
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
    public Transaction transaction(@RequestBody SendRequest request) {
        logger.info("Get transaction of user with id=" + request.getUserId()
                + " and of testStep with id=" + request.getTestStepId());
        Transaction transaction =
                transactionRepository
                        .findOneByUserAndTestStep(request.getUserId(), request.getTestStepId());
        if (transaction == null) {
            transaction = new Transaction();
            transaction.setTestStep(testStepService.findOne(request.getTestStepId()));
            transaction.setUser(userRepository.findOne(request.getUserId()));
            transactionRepository.save(transaction);
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
            /*outgoingMessage =
                    ConnectivityUtil.updateSubmitSingleMessageRequest(SUMBIT_SINGLE_MESSAGE_TEMPLATE,
                            request.getMessage(), request.getConfig().get("username"),
                            request.getConfig().get("password"));*/
            String incomingMessage =
                    transportClient.send(outgoingMessage, request.getConfig().get("username"),request.getConfig().get("password"),request.getConfig().get("endpoint"));
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
    public void message(@RequestBody SendRequest request){
        //TODO check auth
        String username = "test";
        String password = "pass";

        //return null;
    }

    public TransactionRepository getTransactionRepository() {
        return transactionRepository;
    }

    public void setTransactionRepository(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }


}
