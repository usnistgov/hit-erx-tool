package gov.nist.hit.erx.web.controller;

import gov.nist.hit.core.api.SessionContext;
import gov.nist.hit.core.domain.TestContext;
import gov.nist.hit.core.service.exception.MessageParserException;
import gov.nist.hit.core.service.exception.UserNotFoundException;
import gov.nist.hit.core.transport.exception.TransportClientException;
import gov.nist.hit.erx.web.utils.SurescriptUtils;
import gov.nist.hit.erx.web.utils.TestCaseExecutionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.xml.sax.SAXException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
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
 * <p/>
 * Created by Maxence Lefort on 3/18/16.
 */
@RestController
@Controller
@RequestMapping("/wss/erx/surescript")
public class SurescriptWebServiceController extends WebServiceController {

    private final static String DOMAIN = "erx";
    private final static String PROTOCOL = "surescript";

    @Autowired
    protected TestCaseExecutionUtils testCaseExecutionUtils;

    @RequestMapping(value = "/message", method = RequestMethod.POST, produces = "text/xml")
    public String message(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @RequestBody String body, HttpServletRequest request,HttpServletResponse response) throws TransportClientException, MessageParserException {
        try {
            String message = SurescriptUtils.parseEnveloppe(body);
            String outgoing = super.message(message, authorization);
            Map<String,String> criterias = getCriteriaFromBasicAuth(authorization);
            criterias.remove("password");
            Long userId = userConfigService.findUserIdByProperties(criterias);
            if(userId == null){
                throw new UserNotFoundException();
            }
            TestContext testContext = testStepService.findOne(testCaseExecutionUtils.findOne(userId).getCurrentTestStepId()).getTestContext();
            response = super.setBasicAuth(authorization, response,PROTOCOL,DOMAIN);
            return SurescriptUtils.addEnveloppe(outgoing, testContext);
        } catch (UserNotFoundException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }


}