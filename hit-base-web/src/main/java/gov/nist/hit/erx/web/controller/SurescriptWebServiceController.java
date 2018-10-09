package gov.nist.hit.erx.web.controller;

import java.io.IOException;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.xml.sax.SAXException;

import gov.nist.hit.core.domain.TestContext;
import gov.nist.hit.core.service.exception.MessageParserException;
import gov.nist.hit.core.service.exception.UserNotFoundException;
import gov.nist.hit.core.transport.exception.TransportClientException;
import gov.nist.hit.erx.web.utils.SurescriptUtils;
import gov.nist.hit.erx.web.utils.TestCaseExecutionUtils;

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
 * <p/>
 * Created by Maxence Lefort on 3/18/16.
 */
@RestController
@Controller
public class SurescriptWebServiceController extends WebServiceController {

	private final static String PROTOCOL = "surescript";

	@Autowired
	protected TestCaseExecutionUtils testCaseExecutionUtils;

	@RequestMapping(value = "/wss/{domain}/surescript/message", method = RequestMethod.POST, produces = "text/xml")
	public String message(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @RequestBody String body,
			HttpServletRequest request, HttpServletResponse response, @PathVariable("domain") String domain)
			throws TransportClientException, MessageParserException {
		try {
			String message = SurescriptUtils.parseEnveloppe(body);
			Map<String, String> criteria = getCriteriaFromBasicAuth(authorization);
			criteria.put("domain", domain);
			criteria.put("protocol", PROTOCOL);
			String outgoing = super.message(message, criteria, PROTOCOL, domain);
			TestContext testContext = testStepService.findOne(testCaseExecutionUtils
					.findOne(userConfigService.findUserIdByProperties(getCriteriaFromBasicAuth(authorization)))
					.getCurrentTestStepId()).getTestContext();
			response = super.setBasicAuth(criteria, response, PROTOCOL, domain);
			return SurescriptUtils.addEnveloppe(outgoing, testContext);
		} catch (UserNotFoundException e) {
			logger.error("UserNotFoundException error.\n" + e.getMessage());
			e.printStackTrace();
		} catch (SAXException e) {
			logger.error("SAXException error.\n" + e.getMessage());
			e.printStackTrace();
		} catch (ParserConfigurationException e) {
			logger.error("ParserConfigurationException error.\n" + e.getMessage());
			e.printStackTrace();
		} catch (IOException e) {
			logger.error("IOException error.\n" + e.getMessage());
			e.printStackTrace();
		} catch (WebServiceException e) {
			logger.error("WebServiceException error.\n" + e.getMessage());
			e.printStackTrace();
		} catch (Exception e) {
			logger.error("UserNotFoundException error.\n" + e.getMessage());
			e.printStackTrace();
		}
		return "";
	}

}