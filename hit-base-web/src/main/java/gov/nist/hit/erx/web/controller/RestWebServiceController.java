package gov.nist.hit.erx.web.controller;

import com.google.gson.Gson;
import com.mifmif.common.regex.Generex;
import gov.nist.hit.MessageTypeFinder;
import gov.nist.hit.core.domain.*;
import gov.nist.hit.core.edi.domain.EDITestContext;
import gov.nist.hit.core.repo.MessageRepository;
import gov.nist.hit.core.repo.TestContextRepository;
import gov.nist.hit.core.service.*;
import gov.nist.hit.core.service.exception.MessageParserException;
import gov.nist.hit.core.transport.exception.TransportClientException;
import gov.nist.hit.erx.ws.client.Message;
import gov.nist.hit.impl.EdiMessageEditor;
import gov.nist.hit.impl.EdiMessageParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by mcl1 on 1/13/16.
 */
@RestController
@Controller
@RequestMapping("/ws/erx/rest")
public class RestWebServiceController {

    static final Logger logger = LoggerFactory.getLogger(RestWebServiceController.class);
    @Autowired
    protected TransactionService transactionService;

    @Autowired
    protected TransportMessageService transportMessageService;

    @Autowired
    protected MessageRepository messageRepository;

    @Autowired
    protected TestContextRepository testContextRepository;

    @Autowired
    protected TestCaseExecutionService testCaseExecutionService;

    @Autowired
    protected UserConfigService userConfigService;

    @Autowired
    protected TestCaseExecutionDataService testCaseExecutionDataService;

    @Autowired
    protected TestStepService testStepService;


    @Transactional()
    @RequestMapping(value = "/message", method = RequestMethod.POST)
    public String message(@RequestBody TransportRequest request) throws TransportClientException, MessageParserException {
        //TODO check auth
        Gson gson = new Gson();
        String responseMessage = "";
        String jsonRequest = gson.toJson(request);
        Message received = gson.fromJson(jsonRequest, Message.class);
        logger.info("Message received : " + jsonRequest);
        //TODO modify the response message
        Map<String, String> criteria = new HashMap<>();
        criteria.put("username", received.getConfig().getUsername());
        criteria.put("password", received.getConfig().getPassword());
        Transaction transaction = new Transaction();
        transaction.setProperties(criteria);
        transaction.setIncoming(received.getMessage());

        Long messageId = transportMessageService.findMessageIdByProperties(criteria);
        gov.nist.hit.core.domain.Message message;
        TestContext testContext = null;
        if (messageId != null) {
            message = messageRepository.getOne(messageId);
            if (message != null) {
                testContext = testContextRepository.findOneByMessageId(messageId);
                MessageTypeFinder messageTypeFinder = MessageTypeFinder.getInstance();
            } else {
                throw new TransportClientException("Message with id " + messageId + " not found");
            }
        } else {
            throw new TransportClientException("Message id not found for criteria " + criteria.toString());
        }
        ArrayList<TestStepFieldPair> fieldsToReadInReceivedMessage = new ArrayList<>();
        HashMap<String, String> fieldsToBeReplacedInSentMessage = new HashMap<>();
        Long userConfigId = userConfigService.findUserIdByProperties(criteria);
        TestCaseExecution testCaseExecution = null;
        if (userConfigId != null && testContext != null) {
            TestStep currentTestStep = testStepService.findOneByTestContext(testContext.getId());
            testCaseExecution = testCaseExecutionService.findOneByUserConfigId(userConfigId);
            if (testCaseExecution != null) {
                Collection<DataMapping> dataMappings = testCaseExecution.getTestCase().getDataMappings();
                for (DataMapping dataMapping : dataMappings) {
                    TestStepFieldPair target = dataMapping.getTarget();
                    if (target.getTestStep().getId() == currentTestStep.getId()) {
                        logger.info("####Target found. currentTestStepId : " + currentTestStep.getId() + "\ntestCaseExecution testStepId : " + testCaseExecution.getCurrentTestStepId() + "\nMapping : " + dataMapping.toString());
                        String data = "";
                        if (dataMapping.getSource() instanceof TestStepFieldPair) {
                            TestCaseExecutionData testCaseExecutionData = testCaseExecutionDataService.getTestCaseExecutionData(target.getId());
                            if (testCaseExecutionData != null) {
                                data = testCaseExecutionData.getData();
                            }
                        } else {
                            if (dataMapping.getSource() instanceof MappingSourceConstant) {
                                data = ((MappingSourceConstant) dataMapping.getSource()).getValue();
                            } else if (dataMapping.getSource() instanceof MappingSourceCurrentDate) {
                                SimpleDateFormat simpleDateFormat = new SimpleDateFormat();
                                simpleDateFormat.applyPattern(((MappingSourceCurrentDate) dataMapping.getSource()).getFormat());
                                data = simpleDateFormat.format(new Date());
                            } else if (dataMapping.getSource() instanceof MappingSourceRandom) {
                                Generex generex = new Generex(((MappingSourceRandom) dataMapping.getSource()).getRegex());
                                data = generex.random();
                            }
                        }
                        fieldsToBeReplacedInSentMessage.put(target.getField(), data);
                    }
                    if (dataMapping.getSource() instanceof TestStepFieldPair) {
                        TestStepFieldPair source = (TestStepFieldPair) dataMapping.getSource();
                        if (source.getTestStep().getId() == testCaseExecution.getCurrentTestStepId()) {
                            fieldsToReadInReceivedMessage.add(source);
                        }
                    }
                }
            }
        }

        if (testContext instanceof EDITestContext) {
            EDITestContext ediTestContext = (EDITestContext) testContext;
            EdiMessageParser ediMessageParser = new EdiMessageParser();
            ArrayList<String> fieldNames = new ArrayList<>();
            for (TestStepFieldPair source : fieldsToReadInReceivedMessage) {
                fieldNames.add(source.getField());
            }
            gov.nist.hit.core.domain.Message messageReceived = new gov.nist.hit.core.domain.Message();
            messageReceived.setContent(received.getMessage());
            Map<String, String> data = null;
            try {
                data = ediMessageParser.readInMessage(messageReceived, fieldNames, testContext);
                for (TestStepFieldPair source : fieldsToReadInReceivedMessage) {
                    TestCaseExecutionData testCaseExecutionData = new TestCaseExecutionData();
                    testCaseExecutionData.setTestStepFieldPair(source);
                    testCaseExecutionData.setData(data.get(source.getField()));
                    testCaseExecutionDataService.save(testCaseExecutionData);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            EdiMessageEditor ediMessageEditor = new EdiMessageEditor();
            try {
                responseMessage = ediMessageEditor.replaceInMessage(message, fieldsToBeReplacedInSentMessage, testContext);
                logger.info("Generated response message : " + responseMessage);
            } catch (Exception e) {
                responseMessage = e.getMessage();
                e.printStackTrace();
            }
            transaction.setOutgoing(responseMessage);
        } else if (testContext.getFormat().toLowerCase().equals("xml")) {
            try {
                        /*String receivedMessageType = messageTypeFinder.findXmlMessageType(received.getMessage());
                        XMLMessageEditor xmlMessageEditor = new XMLMessageEditor();
                        HashMap<String, String> replaceTokens = new HashMap<>();
                        SimpleDateFormat simpleDateFormat = new SimpleDateFormat();
                        simpleDateFormat.applyPattern("yyyy-MM-dd'T'HH:mm:ss");
                        replaceTokens.put("SentTime", simpleDateFormat.format(new Date()));
                        responseMessage = xmlMessageEditor.replaceInMessage(message,replaceTokens,testContext);*/
                logger.info("Message sent back : " + message.getContent());
                responseMessage = message.getContent();
                transaction.setIncoming(received.getMessage());
                transaction.setOutgoing(responseMessage);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            throw new MessageParserException("Message with id " + messageId + " must be either EDI or XML (" + testContext.getFormat() + " found instead)");
        }

        transactionService.save(transaction);
        return responseMessage;
    }


    @Transactional()
    @RequestMapping(value = "/test", method = RequestMethod.GET)
    public String test(@RequestParam String username) {
        //TODO check auth
        String password = "pass";
        return "hello " + username;
    }

}
