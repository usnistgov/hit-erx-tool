package gov.nist.hit.erx.web.utils;

import gov.nist.hit.core.domain.Message;
import gov.nist.hit.core.domain.TestContext;
import gov.nist.hit.core.xml.domain.XMLTestContext;
import gov.nist.hit.impl.EdiMessageParser;
import gov.nist.hit.impl.XMLMessageEditor;
import gov.nist.hit.utils.XMLUtils;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.xml.sax.SAXException;

import javax.xml.bind.DatatypeConverter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
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
 * Created by Maxence Lefort on 3/24/16.
 */
public class SurescriptUtils {


    public static String addEnveloppe(String outgoing,TestContext testContext) throws Exception {
        Message toBeParsedMessage = new Message();
        toBeParsedMessage.setContent(outgoing);
        EdiMessageParser ediMessageParser = new EdiMessageParser();
        ArrayList<String> dataToRead = new ArrayList<>();
        String messageIDField = "UIB-030-01";
        String toQualifierField, fromQualifierField,toQualifierAttributeField, fromQualifierAttributeField, relatesToMessageIDField, prescriberOrderNumberField;
        toQualifierField = "UIB-070-01";
        fromQualifierField = "UIB-060-01";
        toQualifierAttributeField = "UIB-070-02";
        fromQualifierAttributeField = "UIB-060-02";
        relatesToMessageIDField = "UIB-030-02";
        prescriberOrderNumberField = "UIH-030-01";
        String sentTimeField1 = "UIB-080-01";
        String sentTimeField2 = "UIB-080-02";
        dataToRead.add(messageIDField);
        dataToRead.add(toQualifierField);
        dataToRead.add(fromQualifierField);
        dataToRead.add(toQualifierAttributeField);
        dataToRead.add(fromQualifierAttributeField);
        dataToRead.add(relatesToMessageIDField);
        dataToRead.add(prescriberOrderNumberField);
        dataToRead.add(sentTimeField1);
        dataToRead.add(sentTimeField2);
        Map<String, String> dataRead = ediMessageParser.readInMessage(toBeParsedMessage, dataToRead, testContext);
        String messageId = null;
        if (dataRead.containsKey(messageIDField)) {
            messageId = dataRead.get(messageIDField);
        }
        String toQualifier = null;
        if (dataRead.containsKey(toQualifierField)) {
            toQualifier = dataRead.get(toQualifierField);
        }
        String toQualifierAttribute = null;
        if (dataRead.containsKey(toQualifierAttributeField)) {
            toQualifierAttribute = dataRead.get(toQualifierAttributeField);
        }
        String fromQualifier = null;
        if (dataRead.containsKey(fromQualifierField)) {
            fromQualifier = dataRead.get(fromQualifierField);
        }
        String fromQualifierAttribute = null;
        if (dataRead.containsKey(fromQualifierAttributeField)) {
            fromQualifierAttribute = dataRead.get(fromQualifierAttributeField);
        }
        String sentTime = null;
        if (dataRead.containsKey(sentTimeField1)&&dataRead.containsKey(sentTimeField2)) {
            sentTime = dataRead.get(sentTimeField1)+"'T'"+dataRead.get(sentTimeField2);
        }
        String relatesToMessageID = "", prescriberOrderNumber = "";
        if (dataRead.containsKey(relatesToMessageIDField) && !dataRead.get(relatesToMessageIDField).isEmpty()){
            relatesToMessageID = "<RelatesToMessageID>"+dataRead.get(relatesToMessageIDField)+"</RelatesToMessageID>";
        }
        if (dataRead.containsKey(prescriberOrderNumberField) && !dataRead.get(prescriberOrderNumberField).isEmpty()){
            prescriberOrderNumber = "<PrescriberOrderNumber>"+dataRead.get(prescriberOrderNumberField)+"</PrescriberOrderNumber>";
        }
        return addEnveloppe(toBeParsedMessage.getContent(), messageId, toQualifier, fromQualifier, toQualifierAttribute, fromQualifierAttribute, relatesToMessageID, prescriberOrderNumber, sentTime);
    }

    public static String addEnveloppe(String message, String messageID,String toQualifier,String fromQualifier, String toQualifierAttribute, String fromQualifierAttribute, String relatesToMessageID, String prescriberOrderNumber, String sentTime) throws Exception {
        //message = message.replace("\n","");
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<Message xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" version=\"010\" release=\"006\" xmlns=\"http://www.ncpdp.org/schema/SCRIPT\">" +
                "    <Header>" +
                "        <To Qualifier=\""+toQualifierAttribute+"\">"+toQualifier+"</To>" +
                "        <From Qualifier=\""+fromQualifierAttribute+"\">"+fromQualifier+"</From>" +
                "        <MessageID>"+messageID+"</MessageID>" +
                "        <SentTime>"+sentTime+"</SentTime>" +
                        relatesToMessageID +
                        prescriberOrderNumber +
                "    </Header>" +
                "    <Body>" +
                "<EDIFACTMessage>" +
                DatatypeConverter.printBase64Binary(message.getBytes(Charsets.UTF_8))+
                "    </EDIFACTMessage>" +
                "</Body>" +
                "</Message>";
    }

    public static String parseEnveloppe(String wrappedMessage) throws IOException, SAXException, ParserConfigurationException {
        String message = "";
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        org.w3c.dom.Document doc = docBuilder.parse(IOUtils.toInputStream(wrappedMessage));
        String encodedEdifactMessage = XMLUtils.getNodesByNameOrXPath("/Message/Body/EDIFACTMessage", doc).item(0).getTextContent();
        message = new String(Base64.decodeBase64(encodedEdifactMessage), Charsets.UTF_8);
        return message;
    }
}
