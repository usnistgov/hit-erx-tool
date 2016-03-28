package gov.nist.hit.erx.web.controller;

import gov.nist.hit.core.service.exception.MessageParserException;
import gov.nist.hit.core.service.exception.UserNotFoundException;
import gov.nist.hit.core.transport.exception.TransportClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Created by mcl1 on 1/13/16.
 */
@RestController
@Controller
@RequestMapping("/ws/erx/rest")
public class RestWebServiceController extends WebServiceController {

    static final Logger logger = LoggerFactory.getLogger(RestWebServiceController.class);
    private final static String DOMAIN = "erx";
    private final static String PROTOCOL = "rest";


    @Transactional()
    @RequestMapping(value = "/message", method = RequestMethod.POST,produces = "text/xml")
    public String message(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @RequestBody String body, HttpServletRequest request,HttpServletResponse response) throws TransportClientException, MessageParserException, UserNotFoundException {
        logger.info("Message received : " + body);
        response = super.setBasicAuth(authorization, response,PROTOCOL,DOMAIN);
        return super.message(body, authorization);
    }

}
