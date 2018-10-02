package gov.nist.hit.erx.web.controller;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import gov.nist.auth.hit.core.domain.TransportConfig;
import gov.nist.hit.core.api.SessionContext;
import gov.nist.hit.core.domain.Message;
import gov.nist.hit.core.domain.TestStep;
import gov.nist.hit.core.domain.Transaction;
import gov.nist.hit.core.domain.TransportRequest;
import gov.nist.hit.core.domain.TransportResponse;
import gov.nist.hit.core.service.exception.TestStepException;
import gov.nist.hit.core.service.exception.UserNotFoundException;
import gov.nist.hit.core.transport.exception.TransportClientException;
import gov.nist.hit.erx.ws.client.utils.MessageUtils;

/**
 * Created by mcl1 on 12/16/15.
 */

@RestController
@Controller
public class UrlEncodedTransportController extends TransportController {

	static final Logger logger = LoggerFactory.getLogger(UrlEncodedTransportController.class);

	private final static String PROTOCOL = "urlencoded";

	@RequestMapping(value = "/transport/{domain}/urlencoded/configs", method = RequestMethod.POST)
	public TransportConfig configs(HttpSession session, HttpServletRequest request,
			@PathVariable("domain") String domain) throws UserNotFoundException {
		return configs(session, request, PROTOCOL, domain);
	}

	@RequestMapping(value = "/transport/{domain}/urlencoded/startListener", method = RequestMethod.POST)
	public boolean startListener(@RequestBody TransportRequest request, HttpSession session,
			@PathVariable("domain") String domain) throws UserNotFoundException {
		return startListener(request, session, PROTOCOL, domain);
	}

	@RequestMapping(value = "/transport/{domain}/urlencoded/stopListener", method = RequestMethod.POST)
	public boolean stopListener(@RequestBody TransportRequest request, HttpSession session,
			@PathVariable("domain") String domain) throws UserNotFoundException {
		return stopListener(request, session, PROTOCOL, domain);
	}

	@RequestMapping(value = "/transport/{domain}/urlencoded/searchTransaction", method = RequestMethod.POST)
	public Transaction searchTransaction(@RequestBody TransportRequest request, @PathVariable("domain") String domain) {
		logger.info("Searching transaction...");
		Map<String, String> criteria = new HashMap<String, String>();
		criteria.put("username", request.getConfig().get("username"));
		criteria.put("password", request.getConfig().get("password"));
		criteria.put("domain", domain);
		criteria.put("protocol", PROTOCOL);
		return searchTransaction(criteria);
	}

	@Override
	@RequestMapping(value = "/transport/{domain}/urlencoded/populateMessage", method = RequestMethod.POST)
	public TransportResponse populateMessage(@RequestBody TransportRequest request, HttpSession session,
			@PathVariable("domain") String domain) {
		return super.populateMessage(request, session, domain);
	}

	@RequestMapping(value = "/transport/{domain}/urlencoded/send", method = RequestMethod.POST)
	public Transaction send(@RequestBody TransportRequest request, HttpSession session,
			@PathVariable("domain") String domain) throws TransportClientException {
		logger.info("Sending message  with user id=" + SessionContext.getCurrentUserId(session)
				+ " and test step with id=" + request.getTestStepId());
		Long testStepId = request.getTestStepId();
		TestStep testStep = testStepService.findOne(testStepId);
		if (testStep == null)
			throw new TestStepException("Unknown test step with id=" + testStepId);
		String outgoingMessage = null;
		try {
			boolean replaceSeparators = Boolean.parseBoolean(request.getConfig().get("replaceSeparators"));
			outgoingMessage = MessageUtils.encodeMedHistory(request.getMessage(), replaceSeparators);
			StringBuilder body = new StringBuilder();
			body.append("request=");
			body.append(outgoingMessage);
			request.getConfig().put("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE);
			String incoming = send(request, body.toString());
			Long userId = SessionContext.getCurrentUserId(session);
			String decodedMessage = decodeIncomingMessage(incoming, testStep, userId);
			return saveTransaction(userId, testStep, MessageUtils.prettyPrint(decodedMessage), outgoingMessage);
		} catch (UnsupportedEncodingException e) {
			logger.error("Unable to encode outgoing message: " + request.getMessage());
			e.printStackTrace();
		}
		return null;
	}

	// @Override
	// public String send(TransportRequest request,String message) throws
	// TransportClientException {
	// try {
	// logger.info("Message formatted to be sent : "+message);
	// String incoming = webServiceClient.send(message,
	// request.getConfig().get("username"), request.getConfig().get("password"),
	// request.getConfig().get("endpoint"));
	// logger.info("Response received : "+incoming);
	// return incoming;
	// } catch (Exception e1) {
	// logger.error(e1.toString());
	// throw new TransportClientException("Failed to send the message." +
	// e1.getMessage());
	// }
	// }

	public String decodeIncomingMessage(String incomingMessage, TestStep testStep, Long accountId) {
		String decodedMessage = null;
		try {
			if (incomingMessage.startsWith("request=")) {
				incomingMessage = incomingMessage.substring("request=".length());
			}
			decodedMessage = MessageUtils.decodeMedHistory(incomingMessage);
			// Read data in the received message
			TestStep nextTestStep = testStepUtils.findNext(testStep);
			if (nextTestStep != null) {
				Message message = new Message();
				message.setContent(decodedMessage);
				mappingUtils.readDatasFromMessage(message, nextTestStep,
						testCaseExecutionUtils.initTestCaseExecution(accountId, nextTestStep));
			}
			return decodedMessage;
		} catch (UnsupportedEncodingException e) {
			logger.error("Unable to decode the incoming message (UnsupportedEncodingException): " + incomingMessage);
			e.printStackTrace();
		}
		return incomingMessage;
	}
}
