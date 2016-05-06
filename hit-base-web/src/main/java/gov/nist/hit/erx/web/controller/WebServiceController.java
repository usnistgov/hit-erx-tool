package gov.nist.hit.erx.web.controller;

import com.google.gson.Gson;
import gov.nist.hit.core.domain.*;
import gov.nist.hit.core.repo.MessageRepository;
import gov.nist.hit.core.repo.TestContextRepository;
import gov.nist.hit.core.service.*;
import gov.nist.hit.core.service.exception.MessageParserException;
import gov.nist.hit.core.service.exception.UserNotFoundException;
import gov.nist.hit.core.transport.exception.TransportClientException;
import gov.nist.hit.erx.web.utils.MappingUtils;
import gov.nist.hit.erx.web.utils.TestCaseExecutionUtils;
import gov.nist.hit.erx.web.utils.TestStepUtils;
import gov.nist.hit.erx.ws.client.Message;
import gov.nist.hit.erx.ws.client.utils.MessageUtils;
import hl7.v2.validation.vs.CodeUsage;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.xml.sax.SAXException;

import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.DatatypeConverter;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

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
public abstract class WebServiceController {

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
    protected TransportConfigService transportConfigService;

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

    public Map<String,String> getCriteriaFromBasicAuth(String authorization){
        if (authorization != null && authorization.startsWith("Basic")) {
            authorization = authorization.replace("Basic ", "").trim();
            String[] credentials = (new String(Base64.decodeBase64(authorization), Charset.forName("UTF-8")).split(":"));
            if (credentials.length == 2) {
                String username = credentials[0];
                String password = credentials[1];
                Map<String, String> criteria = new HashMap<>();
                criteria.put("username", username);
                criteria.put("password", password);
                return criteria;
            }
        }
        return null;
    }

    public String message(String message,String authorization) throws TransportClientException, MessageParserException, UserNotFoundException {
        //todo put basic auth in response ? (get in ta info)
        Map<String, String> criteria = getCriteriaFromBasicAuth(authorization);
        if(criteria!=null){
            criteria.remove("password");
            Long userId = userConfigService.findUserIdByProperties(criteria);
            Long messageId = transportMessageService.findMessageIdByProperties(criteria);
            if(messageId==null){
                return "Error : Listener not started for user "+criteria.get("username")+".";
            }
            if(userId==null){
                throw new UserNotFoundException();
            }
            Transaction transaction = new Transaction();
            transaction.setProperties(criteria);
            String formattedMessage = MessageUtils.prettyPrint(message);
            transaction.setIncoming(formattedMessage);
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
            if (testContext != null) {
                TestStep currentTestStep = testStepService.findOneByTestContext(testContext.getId());
                TestCaseExecution testCaseExecution = testCaseExecutionUtils.findOne(userId);
                if (testCaseExecution != null) {
                    TestCase testCase = currentTestStep.getTestCase();
                    testCaseExecution.setTestCase(testCase);
                    testCaseExecution.setCurrentTestStepId(currentTestStep.getId());
                    gov.nist.hit.core.domain.Message receivedMessage = new gov.nist.hit.core.domain.Message();
                    receivedMessage.setContent(message);
                    TestStep receivedMessageTestStep = testStepUtils.findPrevious(currentTestStep);
                    mappingUtils.readDatasFromMessage(receivedMessage, receivedMessageTestStep,testCaseExecution);
                    String content = mappingUtils.writeDataInMessage(outgoingMessage, currentTestStep, testCaseExecution);
                    //Note : There shouldn't be any information to be read from the message we send, this is just a security net
                    //mappingUtils.readDatasFromMessage(outgoingMessage, responseTestStep, testCaseExecution);
                    transaction.setOutgoing(MessageUtils.prettyPrint(content));
                }
            }
            transactionService.save(transaction);
            return MessageUtils.cleanToSend(transaction.getOutgoing());
        }
        return null;
    }

    private Map<String, String> getSutInitiatorConfig(Long userId, String PROTOCOL, String DOMAIN) {
        TransportConfig config = transportConfigService.findOneByUserAndProtocolAndDomain(userId, PROTOCOL, DOMAIN);
        Map<String, String> sutInitiator = config != null ? config.getSutInitiator() : null;
        if (sutInitiator == null || sutInitiator.isEmpty())
            throw new gov.nist.hit.core.service.exception.TransportException(
                    "No System Under Test configuration info found");
        return sutInitiator;
    }

    public HttpServletResponse setBasicAuth(String authorization,HttpServletResponse response, String PROTOCOL, String DOMAIN) {
        Map<String,String> criterias = getCriteriaFromBasicAuth(authorization);
        criterias.remove("password");
        Map<String, String> credentials = getSutInitiatorConfig(userConfigService.findUserIdByProperties(criterias), PROTOCOL,DOMAIN);
        String plainCreds = credentials.get("username") + ":" + credentials.get("password");
        byte[] plainCredsBytes = plainCreds.getBytes();
        String base64Creds = DatatypeConverter.printBase64Binary(plainCredsBytes);
        response.setHeader("Authorization", "Basic " + base64Creds);
        return response;
    }
}
