package gov.nist.hit.erx.web.controller;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import gov.nist.hit.core.service.exception.MessageParserException;
import gov.nist.hit.core.service.exception.UserNotFoundException;
import gov.nist.hit.core.transport.exception.TransportClientException;

/**
 * Created by mcl1 on 1/13/16.
 */
@RestController
@Controller
public class RestWebServiceController extends WebServiceController {

	static final Logger logger = LoggerFactory.getLogger(RestWebServiceController.class);
	private final static String PROTOCOL = "rest";

	@RequestMapping(value = "/wss/{domain}/rest/message", method = RequestMethod.POST, produces = "text/xml")
	public String message(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @RequestBody String body,
			HttpServletRequest request, HttpServletResponse response, @PathVariable("domain") String domain)
			throws TransportClientException, MessageParserException, UserNotFoundException, WebServiceException {
		Map<String, String> criteria = getCriteriaFromBasicAuth(authorization);
		criteria.put("domain", domain);
		criteria.put("protocol", PROTOCOL);
		String username = "";
		if (criteria.containsKey("username")) {
			username = criteria.get("username");
		}
		logger.info("Message received for user \"" + username + "\" : " + body);
		response = super.setBasicAuth(criteria, response, PROTOCOL, domain);
		return super.message(body, criteria, PROTOCOL, domain);
	}

}
