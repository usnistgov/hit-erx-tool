package gov.nist.hit.erx.web.utils;


import com.mifmif.common.regex.Generex;
import gov.nist.hit.MessageEditor;
import gov.nist.hit.MessageParser;
import gov.nist.hit.core.domain.*;
import gov.nist.hit.core.edi.domain.EDITestContext;
import gov.nist.hit.core.service.TestCaseExecutionDataService;
import gov.nist.hit.core.xml.domain.XMLTestContext;
import gov.nist.hit.impl.EdiMessageEditor;
import gov.nist.hit.impl.EdiMessageParser;
import gov.nist.hit.impl.XMLMessageEditor;
import gov.nist.hit.impl.XMLMessageParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;

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
 * <p>
 * Created by Maxence Lefort on 2/14/16.
 */

@Service
public class MappingUtils {

    static final Logger logger = LoggerFactory.getLogger(MappingUtils.class);


    @Autowired
    protected TestCaseExecutionDataService testCaseExecutionDataService;

    public void readDatasFromMessage(Message message, List<DataMapping> dataMappings,TestContext testContext, TestStep testStep){
        MessageParser messageParser = null;
        if(testContext instanceof EDITestContext){
            messageParser = new EdiMessageParser();
        } else if (testContext instanceof XMLTestContext){
            messageParser = new XMLMessageParser();
        }
        if(messageParser!=null){
            HashMap<String,TestStepFieldPair> keysToFind = new HashMap<>();
            for(DataMapping dataMapping : dataMappings){
                if(dataMapping.getSource() instanceof TestStepFieldPair){
                    TestStepFieldPair source = (TestStepFieldPair) dataMapping.getSource();
                    if(source.getTestStep().getId()==testStep.getId()){
                        keysToFind.put(source.getField(),source);
                    }
                }
            }
            ArrayList<String> dataToBeFound = (ArrayList<String>) setToArrayList(keysToFind.keySet());
            try {
                Map<String, String> data = messageParser.readInMessage(message, dataToBeFound, testContext);
                for(String key : data.keySet()){
                    TestCaseExecutionData testCaseExecutionData = new TestCaseExecutionData();
                    testCaseExecutionData.setTestStepFieldPair(keysToFind.get(key));
                    testCaseExecutionData.setData(data.get(key));
                    testCaseExecutionDataService.save(testCaseExecutionData);
                }
            } catch (Exception e) {
                logger.error(e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public String writeDataInMessage(Message message, List<DataMapping> dataMappings, TestContext testContext, TestStep testStep){
        HashMap<String,String> dataToReplaceInMessage = new HashMap<>();
        for(DataMapping dataMapping : dataMappings) {
            if (dataMapping.getTarget().getTestStep().getId() == testStep.getId()) {
                String data = "";
                if (dataMapping.getSource() instanceof TestStepFieldPair) {
                    TestCaseExecutionData testCaseExecutionData = testCaseExecutionDataService.getTestCaseExecutionData(dataMapping.getSource().getId());
                    data = testCaseExecutionData.getData();
                } else if (dataMapping.getSource() instanceof MappingSourceConstant) {
                    MappingSourceConstant mappingSourceConstant = (MappingSourceConstant) dataMapping.getSource();
                    data = mappingSourceConstant.getValue();
                } else if (dataMapping.getSource() instanceof MappingSourceCurrentDate) {
                    MappingSourceCurrentDate mappingSourceCurrentDate = (MappingSourceCurrentDate) dataMapping.getSource();
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat();
                    simpleDateFormat.applyPattern(mappingSourceCurrentDate.getFormat());
                    data = simpleDateFormat.format(new Date());
                } else if (dataMapping.getSource() instanceof MappingSourceRandom) {
                    MappingSourceRandom mappingSourceRandom = (MappingSourceRandom) dataMapping.getSource();
                    Generex generex = new Generex(mappingSourceRandom.getRegex());
                    data = generex.random();
                } else {
                    logger.error("Invalid mapping for test step " + testStep.getId() + "(" + testStep.getDescription() + ")");
                }
                dataToReplaceInMessage.put(dataMapping.getTarget().getField(),data);
            }
        }
        if(dataToReplaceInMessage.size()>0) {
            MessageEditor messageEditor = null;
            if (testContext instanceof EDITestContext) {
                messageEditor = new EdiMessageEditor();
            } else if (testContext instanceof XMLTestContext) {
                messageEditor = new XMLMessageEditor();
            } else {
                logger.error("Message must be either EDI or XML. " + testContext.getFormat() + " found instead.");
            }
            if (messageEditor != null) {
                try {
                    String editedMessage = messageEditor.replaceInMessage(message,dataToReplaceInMessage,testContext);
                    return editedMessage;
                } catch (Exception e) {
                    logger.error(e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        return message.getContent();
    }

    private ArrayList<?> setToArrayList(Set<?> set){
        return new ArrayList<>(set);
    }


}
