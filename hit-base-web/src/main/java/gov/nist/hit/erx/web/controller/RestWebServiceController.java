package gov.nist.hit.erx.web.controller;

import com.google.gson.Gson;
import gov.nist.hit.core.domain.*;
import gov.nist.hit.core.repo.MessageRepository;
import gov.nist.hit.core.repo.TestContextRepository;
import gov.nist.hit.core.service.*;
import gov.nist.hit.core.service.exception.MessageParserException;
import gov.nist.hit.core.transport.exception.TransportClientException;
import gov.nist.hit.erx.web.utils.MappingUtils;
import gov.nist.hit.erx.web.utils.TestCaseExecutionUtils;
import gov.nist.hit.erx.web.utils.TestStepUtils;
import gov.nist.hit.erx.ws.client.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

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
    protected UserConfigService userConfigService;

    @Autowired
    protected TestStepService testStepService;

    @Autowired
    protected MappingUtils mappingUtils;

    @Autowired
    protected TestCaseExecutionUtils testCaseExecutionUtils;

    @Autowired
    protected TestStepUtils testStepUtils;

    @Transactional()
    @RequestMapping(value = "/message", method = RequestMethod.POST)
    public String message(@RequestBody TransportRequest request) throws TransportClientException, MessageParserException {
        //TODO check auth
        Gson gson = new Gson();
        String jsonRequest = gson.toJson(request);
        Message received = gson.fromJson(jsonRequest, Message.class);
        logger.info("Message received : " + received.getMessage());
        //TODO modify the response message
        Map<String, String> criteria = new HashMap<>();
        criteria.put("username", received.getConfig().getUsername());
        criteria.put("password", received.getConfig().getPassword());
        Transaction transaction = new Transaction();
        transaction.setProperties(criteria);
        transaction.setIncoming(received.getMessage());

        Long messageId = transportMessageService.findMessageIdByProperties(criteria);
        gov.nist.hit.core.domain.Message outgoingMessage = new gov.nist.hit.core.domain.Message();
        TestContext testContext = null;
        if (messageId != null) {
            outgoingMessage.setContent(messageRepository.getContentById(messageId));
            if (outgoingMessage.getContent() != null) {
                testContext = testContextRepository.findOneByMessageId(messageId);
            } else {
                throw new TransportClientException("Message with id " + messageId + " not found");
            }
        } else {
            throw new TransportClientException("Message id not found for criteria " + criteria.toString());
        }
        Long userId = userConfigService.findUserIdByProperties(criteria);
        if (testContext != null) {
            TestStep currentTestStep = testStepService.findOneByTestContext(testContext.getId());
            TestCaseExecution testCaseExecution = testCaseExecutionUtils.findOne(userId);
            if (testCaseExecution != null) {
                TestCase testCase = currentTestStep.getTestCase();
                testCaseExecution.setTestCase(testCase);
                testCaseExecution.setCurrentTestStepId(currentTestStep.getId());
                gov.nist.hit.core.domain.Message receivedMessage = new gov.nist.hit.core.domain.Message();
                receivedMessage.setContent(received.getMessage());
                TestStep receivedMessageTestStep = testStepUtils.findPrevious(currentTestStep);
                mappingUtils.readDatasFromMessage(receivedMessage, receivedMessageTestStep,testCaseExecution);
                String content = mappingUtils.writeDataInMessage(outgoingMessage, currentTestStep, testCaseExecution);
                outgoingMessage.setContent(content);
                //Note : There shouldn't be any information to be read from the message we send, this is just a security net
                //mappingUtils.readDatasFromMessage(outgoingMessage, responseTestStep, testCaseExecution);
                transaction.setOutgoing(content);
            }
        }
        transactionService.save(transaction);
        return transaction.getOutgoing();
    }

}
