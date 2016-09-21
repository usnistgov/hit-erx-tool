package gov.nist.hit.erx.web.controller;

import gov.nist.hit.core.service.exception.MessageParserException;
import gov.nist.hit.core.service.exception.UserNotFoundException;
import gov.nist.hit.core.transport.exception.TransportClientException;
import gov.nist.hit.erx.ws.client.utils.MessageUtils;
import org.apache.commons.io.Charsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.UnsupportedEncodingException;
import java.util.Map;

/**
 * Created by mcl1 on 1/13/16.
 */
@RestController
@Controller
@RequestMapping("/wss/erx/urlencoded")
public class UrlEncodedWebServiceController extends WebServiceController {

    static final Logger logger = LoggerFactory.getLogger(UrlEncodedWebServiceController.class);
    private final static String DOMAIN = "erx";
    private final static String PROTOCOL = "urlencoded";

    //request=UNA%1c%1d.+%1f%1eUIB%1dUNOA%1c0%1d%1d2903846691140567839541%1c%1cFIL%1d%1d%1dT00000000020141%1cZZZ%1cBHP8OQQDMF%1dS00000000000001%1cZZZ%1cRXHUB%1d20160825%1c231421%1d%1d1%1eUIH%1dSCRIPT%1c010%1c006%1cRXHREQ%1d530261b5e5814b38927cad7e1f66256e%1d%1d%1d20160825%1c231421%1ePVD%1dPC%1dHM00000QA1_0000010016%1c94%1fFJ1234563%1cDH%1f6150042888%1cHPI%1d%1d%1dJennings%1cFaith%1cM%1d%1dFaith+Jennings%1d5501+Dillard+Drive%1cCary%1cNC%1c27511%1ePTT%1d%1d19550108%1dADIRONDACK%1cSUSANNE%1cM%1dF%1d66498%1c94%1d2645+MULBERRY+LANE%1cTOLEDO%1cOH%1c43605%1eCOO%1d%1d%1d%1d%1d%1d%1d%1d%1d07%1c20160227%1c102%1f36%1c20160825%1c102%1d%1d%1d%1dY%1eUIT%1d530261b5e5814b38927cad7e1f66256e%1d5%1eUIZ%1d%1d1%1e

    @RequestMapping(value = "/message", method = RequestMethod.POST,consumes = "application/x-www-form-urlencoded",produces = "application/x-www-form-urlencoded")
    public String message(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @RequestParam("request") String message, HttpServletRequest request,HttpServletResponse response) {
        try {
            String decodedMessage= UriUtils.decode(message, Charsets.UTF_8.displayName());
            logger.info("Message received : " + decodedMessage);
            Map<String, String> criteria =  getCriteriaFromBasicAuth(authorization);
            //String encodedResponseMessage = MessageUtils.cleanToSend(super.message(decodedMessage, criteria),Boolean.parseBoolean(criteria.get("replaceSeparators")));
            String encodedResponseMessage = super.message(decodedMessage, criteria,PROTOCOL,DOMAIN);
            logger.info("Sending back encoded response: "+encodedResponseMessage);
            response = super.setBasicAuth(criteria, response,PROTOCOL,DOMAIN);
            return encodedResponseMessage;
        } catch (UnsupportedEncodingException e) {
            logger.error("Unable to decode the incoming message: "+message);
            e.printStackTrace();
        } catch (UserNotFoundException e) {
            logger.error("User not found");
            e.printStackTrace();
        } catch (TransportClientException e) {
            logger.error("Transport error");
            e.printStackTrace();
        } catch (MessageParserException e) {
            logger.error("Unable to parse the message");
            e.printStackTrace();
        }
        return null;
    }

}