package gov.nist.hit.erx.web.controller;


import com.mifmif.common.regex.Generex;
import gov.nist.hit.core.api.SessionContext;
import gov.nist.hit.core.domain.*;
import gov.nist.hit.core.domain.util.XmlUtil;
import gov.nist.hit.core.edi.domain.EDITestContext;
import gov.nist.hit.core.repo.UserRepository;
import gov.nist.hit.core.service.*;
import gov.nist.hit.core.service.exception.MessageParserException;
import gov.nist.hit.core.service.exception.TestCaseException;
import gov.nist.hit.core.service.exception.UserNotFoundException;
import gov.nist.hit.core.transport.exception.TransportClientException;
import gov.nist.hit.core.xml.domain.XMLTestContext;
import gov.nist.hit.erx.web.utils.Utils;
import gov.nist.hit.erx.ws.client.WebServiceClient;
import gov.nist.hit.impl.EdiMessageEditor;
import gov.nist.hit.impl.EdiMessageParser;
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
import java.text.SimpleDateFormat;
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
    protected TestCaseExecutionService testCaseExecutionService;

    @Autowired
    protected UserConfigService userConfigService;

    @Autowired
    protected TestCaseExecutionDataService testCaseExecutionDataService;

    @Autowired
    @Qualifier("WebServiceClient")
    protected WebServiceClient webServiceClient;

    @Autowired
    protected UserService userService;

    private final static String DOMAIN = "erx";
    private final static String PROTOCOL = "rest";

    @Transactional()
    @RequestMapping(value = "/taInitiator", method = RequestMethod.POST)
    public Map<String, String> taInitiatorConfig(HttpSession session) throws UserNotFoundException {
        logger.info("Fetching user ta initiator information ... ");
        Long userId = SessionContext.getCurrentUserId(session);
        User user = null;
        TransportConfig transportConfig = null;
        if (userId == null || (user = userService.findOne(userId)) == null) {
            throw new UserNotFoundException();
        }
        transportConfig = transportConfigService.findOneByUserAndProtocol(user.getId(), PROTOCOL);
        if (transportConfig == null) {
            transportConfig = transportConfigService.create(PROTOCOL);
            user.addConfig(transportConfig);
            userService.save(user);
            transportConfigService.save(transportConfig);
        }
        Map<String, String> config = transportConfig.getTaInitiator();
        return config;
    }

    private void initTestCaseExecution(Map<String, String> config, TestStep testStep) {
        Long userConfigId = null;
        try {
            userConfigId = userConfigService.findUserIdByProperties(config);
        } catch (NullPointerException e) {
            //do nothing, it means we have to init the user, see finally below
        } finally {
            TestCaseExecution testCaseExecution = null;
            UserConfig userConfig = null;
            if (userConfigId == null) {
                userConfig = new UserConfig();
                userConfig.setProperties(config);
                userConfig = userConfigService.save(userConfig);
            } else {
                userConfig = userConfigService.findOne(userConfigId);
            }
            testCaseExecution = new TestCaseExecution();
            testCaseExecution.setUserConfig(userConfig);
            TestCase testCase = testStep.getTestCase();
            testCaseExecution.setTestCase(testCase);
            testCaseExecution.setCurrentTestStepId(testStep.getId());
            logger.info("Init testCaseExecution : UserId : "+userConfig.getId()+", TestStep "+testStep.getId()+", TestCase "+testStep.getTestCase().getId());
            testCaseExecutionService.save(testCaseExecution);
        }
    }

    @Transactional()
    @RequestMapping(value = "/sutInitiator", method = RequestMethod.POST)
    public Map<String, String> sutInitiatorConfig(HttpSession session, HttpServletRequest request)
            throws UserNotFoundException {
        logger.info("Fetching user information ... ");
        Long userId = SessionContext.getCurrentUserId(session);
        User user = null;
        if (userId == null || (user = userService.findOne(userId)) == null) {
            throw new UserNotFoundException();
        }
        TransportConfig transportConfig = null;
        transportConfig = transportConfigService.findOneByUserAndProtocol(userId, PROTOCOL);
        if (transportConfig == null) {
            transportConfig = transportConfigService.create(PROTOCOL);
            user.addConfig(transportConfig);
            userService.save(user);
        }
        Map<String, String> config = transportConfig.getSutInitiator();
        if (config == null) {
            config = new HashMap<String, String>();
            transportConfig.setSutInitiator(config);
        }

        int token = new Random().nextInt(999);


        if (config.get("password") == null && config.get("username") == null) {
            config.put("username", "vendor_" + user.getId() + "_" + token);
            config.put("password", "vendor_" + user.getId() + "_" + token);
        }

        if (config.get("endpoint") == null) {
            config.put("endpoint", Utils.getUrl(request) + "/api/ws/" + DOMAIN + "/" + PROTOCOL + "/message");
        }
        transportConfigService.save(transportConfig);
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
            initTestCaseExecution(config, testStep);
        }
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
        TransportConfig config = transportConfigService.findOneByUserAndProtocol(userId, PROTOCOL);
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
                    webServiceClient.send(outgoingMessage, request.getConfig().get("username"), request.getConfig().get("password"), request.getConfig().get("endpoint"));
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
            //Read data in the received message

            TestStep nextTestStep = null;
            Iterator<TestStep> testSteps = testStep.getTestCase().getTestSteps().iterator();
            while(testSteps.hasNext()){
                TestStep next = testSteps.next();
                if(next.getId()==testStep.getId()&&testSteps.hasNext()){
                    nextTestStep = testSteps.next();
                    break;
                }
            }
            ArrayList<TestStepFieldPair> fieldsToReadInReceivedMessage = new ArrayList<>();
            Collection<DataMapping> dataMappings = testStep.getTestCase().getDataMappings();
            for (DataMapping dataMapping : dataMappings) {
                if (dataMapping.getSource() instanceof TestStepFieldPair) {
                    TestStepFieldPair source = (TestStepFieldPair) dataMapping.getSource();
                    if (source.getTestStep().getId() == nextTestStep.getId()) {
                        fieldsToReadInReceivedMessage.add(source);
                    }
                }
            }

            if (testStep.getTestContext() instanceof EDITestContext) {
                EDITestContext ediTestContext = (EDITestContext) testStep.getTestContext();
                EdiMessageParser ediMessageParser = new EdiMessageParser();
                ArrayList<String> fieldNames = new ArrayList<>();
                for (TestStepFieldPair source : fieldsToReadInReceivedMessage) {
                    fieldNames.add(source.getField());
                }
                gov.nist.hit.core.domain.Message messageReceived = new gov.nist.hit.core.domain.Message();
                messageReceived.setContent(transaction.getIncoming());
                Map<String, String> data = null;
                try {
                    data = ediMessageParser.readInMessage(messageReceived, fieldNames, ediTestContext);
                    for (TestStepFieldPair source : fieldsToReadInReceivedMessage) {
                        TestCaseExecutionData testCaseExecutionData = new TestCaseExecutionData();
                        testCaseExecutionData.setTestStepFieldPair(source);
                        testCaseExecutionData.setData(data.get(source.getField()));
                        testCaseExecutionDataService.save(testCaseExecutionData);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (testStep.getTestContext() instanceof XMLTestContext) {
                try {
                        /*String receivedMessageType = messageTypeFinder.findXmlMessageType(received.getMessage());
                        XMLMessageEditor xmlMessageEditor = new XMLMessageEditor();
                        HashMap<String, String> replaceTokens = new HashMap<>();
                        SimpleDateFormat simpleDateFormat = new SimpleDateFormat();
                        simpleDateFormat.applyPattern("yyyy-MM-dd'T'HH:mm:ss");
                        replaceTokens.put("SentTime", simpleDateFormat.format(new Date()));
                        responseMessage = xmlMessageEditor.replaceInMessage(message,replaceTokens,testContext);*/
                    //read in xml message
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                throw new MessageParserException("Message received must be either EDI or XML (" + testStep.getTestContext().getFormat() + " found instead)");
            }

            return transaction;
        } catch (Exception e1) {
            throw new TransportClientException("Failed to send the message." + e1.getMessage());
        }
    }

    private boolean userExist(Long userId) {
        User user = userService.findOne(userId);
        return user != null;
    }

}
