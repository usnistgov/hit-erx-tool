package gov.nist.hit.erx.web.controller;

import com.google.gson.Gson;
import gov.nist.hit.core.domain.Transaction;
import gov.nist.hit.core.domain.TransportMessage;
import gov.nist.hit.core.domain.TransportRequest;
import gov.nist.hit.core.repo.MessageRepository;
import gov.nist.hit.core.service.TransactionService;
import gov.nist.hit.core.service.TransportMessageService;
import gov.nist.hit.core.transport.exception.TransportClientException;
import gov.nist.hit.erx.ws.client.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by mcl1 on 1/13/16.
 */
@RestController
@Controller
@RequestMapping("/ws/erx/rest")
public class RestWebServiceController {

    static final Logger logger = LoggerFactory.getLogger(RestWebServiceController.class);
    @Autowired
    protected TransactionService transactionService;

    @Autowired
    protected TransportMessageService transportMessageService;

    @Autowired
    protected MessageRepository messageRepository;

    @Transactional()
    @RequestMapping(value = "/message", method = RequestMethod.POST)
    public String message(@RequestBody TransportRequest request) throws TransportClientException {
        //TODO check auth
        Gson gson = new Gson();
        String jsonRequest = gson.toJson(request);
        //{"config":{"username":"vendor_1_396","password":"vendor_1_396"},"message":"UNA:+./*"}
        Message received = gson.fromJson(jsonRequest,Message.class);
        logger.info("Message received : " + jsonRequest);
        //TODO modify the response message
        Map<String, String> criteria = new HashMap<>();
        criteria.put("username", received.getConfig().getUsername());
        criteria.put("password", received.getConfig().getPassword());
        Transaction transaction = new Transaction();
        transaction.setProperties(criteria);
        transaction.setIncoming(received.getMessage());
        Long messageId = transportMessageService.findMessageIdByProperties(criteria);
        String message;
        if(messageId!=null) {
             message = messageRepository.getContentById(messageId);
            if (message != null) {
                transaction.setOutgoing(message.toString());
            } else {
                throw new TransportClientException("Message with id "+messageId+" not found");
            }
        } else {
            throw new TransportClientException("Message id not found for criteria "+criteria.toString());
        }
        transactionService.save(transaction);
        return message;

    }

    @Transactional()
    @RequestMapping(value = "/test", method = RequestMethod.GET)
    public String test(@RequestParam String username) {
        //TODO check auth
        String password = "pass";
        return "hello " + username;
    }

}
