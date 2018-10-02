package gov.nist.hit.erx.web.controller;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import gov.nist.auth.hit.core.domain.TransportConfig;
import gov.nist.hit.core.api.SessionContext;
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
public class RestTransportController extends TransportController {

	static final Logger logger = LoggerFactory.getLogger(RestTransportController.class);

	private final static String PROTOCOL = "rest";

	@RequestMapping(value = "/transport/{domain}/rest/configs", method = RequestMethod.POST)
	public TransportConfig configs(HttpSession session, HttpServletRequest request,
			@PathVariable("domain") String domain) throws UserNotFoundException {
		return configs(session, request, PROTOCOL, domain);
	}

	@RequestMapping(value = "/transport/{domain}/rest/startListener", method = RequestMethod.POST)
	public boolean startListener(@RequestBody TransportRequest request, HttpSession session,
			@PathVariable("domain") String domain) throws UserNotFoundException {
		return startListener(request, session, PROTOCOL, domain);
	}

	@RequestMapping(value = "/transport/{domain}/rest/stopListener", method = RequestMethod.POST)
	public boolean stopListener(@RequestBody TransportRequest request, HttpSession session,
			@PathVariable("domain") String domain) throws UserNotFoundException {
		return stopListener(request, session, PROTOCOL, domain);
	}

	@RequestMapping(value = "/transport/{domain}/rest/searchTransaction", method = RequestMethod.POST)
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
	@RequestMapping(value = "/transport/{domain}/rest/populateMessage", method = RequestMethod.POST)
	public TransportResponse populateMessage(@RequestBody TransportRequest request, HttpSession session,
			@PathVariable("domain") String domain) {
		return super.populateMessage(request, session, domain);
	}

	@RequestMapping(value = "/transport/{domain}/rest/send", method = RequestMethod.POST)
	public Transaction send(@RequestBody TransportRequest request, HttpSession session,
			@PathVariable("domain") String domain) throws TransportClientException {
		Long userId = SessionContext.getCurrentUserId(session);
		logger.info("Sending message  with user id=" + userId + " and test step with id=" + request.getTestStepId());
		logger.info("Config: " + request.getConfig());
		Long testStepId = request.getTestStepId();
		TestStep testStep = testStepService.findOne(testStepId);
		if (testStep == null)
			throw new TestStepException("Unknown test step with id=" + testStepId);
		boolean replaceSeparators = Boolean.parseBoolean(request.getConfig().get("replaceSeparators"));
		logger.info("Cleaning message to send (replace separators: " + String.valueOf(replaceSeparators));
		String outgoingMessage = MessageUtils.cleanToSend(request.getMessage(), replaceSeparators);
		String incoming = send(request, outgoingMessage);
		parseIncomingMessage(incoming, testStep, userId);
		return saveTransaction(userId, testStep, incoming, outgoingMessage);
	}
}
