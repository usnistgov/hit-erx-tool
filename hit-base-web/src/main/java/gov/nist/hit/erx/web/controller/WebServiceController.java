package gov.nist.hit.erx.web.controller;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.DatatypeConverter;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import gov.nist.auth.hit.core.domain.TransportConfig;
import gov.nist.hit.core.domain.TestCase;
import gov.nist.hit.core.domain.TestCaseExecution;
import gov.nist.hit.core.domain.TestContext;
import gov.nist.hit.core.domain.TestStep;
import gov.nist.hit.core.domain.Transaction;
import gov.nist.hit.core.repo.MessageRepository;
import gov.nist.hit.core.repo.TestContextRepository;
import gov.nist.hit.core.service.TestStepService;
import gov.nist.hit.core.service.TransactionService;
import gov.nist.hit.core.service.TransportConfigService;
import gov.nist.hit.core.service.TransportMessageService;
import gov.nist.hit.core.service.UserConfigService;
import gov.nist.hit.core.service.exception.MessageParserException;
import gov.nist.hit.core.service.exception.UserNotFoundException;
import gov.nist.hit.core.transport.exception.TransportClientException;
import gov.nist.hit.erx.web.utils.MappingUtils;
import gov.nist.hit.erx.web.utils.TestCaseExecutionUtils;
import gov.nist.hit.erx.web.utils.TestStepUtils;
import gov.nist.hit.erx.ws.client.utils.MessageUtils;

/**
 * This software was developed at the National Institute of Standards and
 * Technology by employees of the Federal Government in the course of their
 * official duties. Pursuant to title 17 Section 105 of the United States Code
 * this software is not subject to copyright protection and is in the public
 * domain. This is an experimental system. NIST assumes no responsibility
 * whatsoever for its use by other parties, and makes no guarantees, expressed
 * or implied, about its quality, reliability, or any other characteristic. We
 * would appreciate acknowledgement if the software is used. This software can
 * be redistributed and/or modified freely provided that any derivative works
 * bear some notice that they are derived from it, and any modified versions
 * bear some notice that they have been modified.
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

	public Map<String, String> getCriteriaFromBasicAuth(String authorization) {
		if (authorization != null && authorization.startsWith("Basic")) {
			authorization = authorization.replace("Basic ", "").trim();
			String[] credentials = (new String(Base64.decodeBase64(authorization), Charset.forName("UTF-8"))
					.split(":"));
			if (credentials.length == 2) {
				String username = credentials[0];
				String password = credentials[1];
				Map<String, String> criteria = new HashMap<>();
				criteria.put("username", username);
				criteria.put("password", password);
				logger.info("Criteria successfully found: " + criteria.toString());
				return criteria;
			} else {
				logger.error("Malformed basic auth: " + authorization);
			}
			return null;
		}
		logger.error("Failed to read the basic auth header: " + authorization);
		return null;
	}

	public String message(String message, Map<String, String> criteria, String protocol, String domain)
			throws TransportClientException, MessageParserException, UserNotFoundException {
		if (criteria != null) {
			logger.info("Message received for criteria " + criteria + " and protocol " + protocol);
			// criteria.remove("password");
			Long userId = userConfigService.findUserIdByProperties(criteria);
			Long messageId = transportMessageService.findMessageIdByProperties(criteria);
			if (messageId == null) {
				logger.error("Error : Listener not started for user " + criteria.get("username") + " , domain ="
						+ domain + " ,protocol " + protocol + ".");
				return "Error : Listener not started for user " + criteria.get("username") + " , domain =" + domain
						+ " ,protocol " + protocol + ".";
			}
			if (userId == null) {
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
					mappingUtils.readDatasFromMessage(receivedMessage, receivedMessageTestStep, testCaseExecution);
					String content = mappingUtils.writeDataInMessage(outgoingMessage, currentTestStep,
							testCaseExecution);
					// Note : There shouldn't be any information to be read from
					// the message we send, this is just a security net
					// mappingUtils.readDatasFromMessage(outgoingMessage,
					// responseTestStep, testCaseExecution);
					transaction.setOutgoing(MessageUtils.prettyPrint(content));
				}
			}
			Map<String, String> sutConfig = getSutInitiatorConfig(userId, protocol, domain);
			Boolean replaceSeparators = Boolean.parseBoolean(sutConfig.get("replaceSeparators"));
			logger.info("Reading sut configuration to check if a separator replacement is needed. Result: "
					+ replaceSeparators + "(Extracted from sut config: " + sutConfig + ")");
			transactionService.save(transaction);
			String cleanMessage = MessageUtils.cleanToSend(transaction.getOutgoing(), replaceSeparators);
			logger.info("Message cleaned to send back: " + cleanMessage);
			return cleanMessage;
		}
		return null;
	}

	private Map<String, String> getSutInitiatorConfig(Long userId, String PROTOCOL, String DOMAIN) {
		TransportConfig config = transportConfigService.findOneByUserAndProtocolAndDomain(userId, PROTOCOL, DOMAIN);
		Map<String, String> sutInitiator = config != null ? config.getSutInitiator() : null;
		if (sutInitiator == null || sutInitiator.isEmpty()) {
			logger.error("No System Under Test configuration info found for userId " + userId + " and the protocol "
					+ PROTOCOL);
			throw new gov.nist.hit.core.service.exception.TransportException(
					"No System Under Test configuration info found");
		}
		// Get the replaceSeparators parameters for the response we send back
		Map<String, String> taInitiator = config != null ? config.getTaInitiator() : null;
		if (taInitiator != null && taInitiator.containsKey("replaceSeparators")) {
			sutInitiator.put("replaceSeparators", taInitiator.get("replaceSeparators"));
		}
		logger.info("SUT configuration found for userId " + userId + " and the protocol " + PROTOCOL + ": "
				+ sutInitiator.toString());
		return sutInitiator;
	}

	public HttpServletResponse setBasicAuth(Map<String, String> criterias, HttpServletResponse response,
			String protocol, String domain) throws WebServiceException {
		Long userId = userConfigService.findUserIdByProperties(criterias);
		if (userId != null) {
			Map<String, String> credentials = getSutInitiatorConfig(userId, protocol, domain);
			String plainCreds = credentials.get("username") + ":" + credentials.get("password");
			byte[] plainCredsBytes = plainCreds.getBytes();
			String base64Creds = DatatypeConverter.printBase64Binary(plainCredsBytes);
			response.setHeader("Authorization", "Basic " + base64Creds);
			logger.info("Basic auth header for the response configured with credentials: " + plainCreds);
			return response;
		}
		throw new WebServiceException("No user found by the criteria specificed");
	}
}
