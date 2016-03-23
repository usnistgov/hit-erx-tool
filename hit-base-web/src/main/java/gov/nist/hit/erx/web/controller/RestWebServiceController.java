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

/**
 * Created by mcl1 on 1/13/16.
 */
@RestController
@Controller
@RequestMapping("/ws/erx/rest")
public class RestWebServiceController extends WebServiceController {

    static final Logger logger = LoggerFactory.getLogger(RestWebServiceController.class);

    @Transactional()
    @RequestMapping(value = "/message", method = RequestMethod.POST,produces = "text/xml")
    public String message(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @RequestBody String body) throws TransportClientException, MessageParserException, UserNotFoundException {
        logger.info("Message received : " + body);
        return super.message(body, authorization);
    }

}
