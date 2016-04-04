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

    public void readDatasFromMessage(Message message, TestStep testStep, TestCaseExecution testCaseExecution){
        MessageParser messageParser = null;
        if(testStep.getTestContext() instanceof EDITestContext){
            messageParser = new EdiMessageParser();
        } else if (testStep.getTestContext() instanceof XMLTestContext){
            messageParser = new XMLMessageParser();
        }
        if(messageParser!=null){
            HashMap<TestStepFieldPair,String> keysToFind = new HashMap<>();
            for(DataMapping dataMapping : testStep.getTestCase().getDataMappings()){
                if(dataMapping.getSource() instanceof TestStepFieldPair){
                    TestStepFieldPair source = (TestStepFieldPair) dataMapping.getSource();
                    if(source.getTestStep().getId()==testStep.getId()){
                        keysToFind.put(source,source.getField());
                    }
                }
            }
            ArrayList<String> dataToBeFound = new ArrayList<>(keysToFind.values());
            try {
                Map<String, String> datas = messageParser.readInMessage(message, dataToBeFound, testStep.getTestContext());
                saveData(keysToFind,datas,testCaseExecution);
            } catch (Exception e) {
                logger.error(e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void saveData(HashMap<TestStepFieldPair,String> keysToFind,Map<String, String> datas,TestCaseExecution testCaseExecution){
        for(TestStepFieldPair currentTestStepFieldPair : keysToFind.keySet()){
            String currentField = keysToFind.get(currentTestStepFieldPair);
            String data = datas.get(currentField);
            if(data != null && !"".equals(data)){
                TestCaseExecutionData testCaseExecutionData = new TestCaseExecutionData();
                testCaseExecutionData.setTestStepFieldPair(currentTestStepFieldPair);
                testCaseExecutionData.setData(data);
                testCaseExecutionData.setTestCaseExecution(testCaseExecution);
                testCaseExecutionDataService.save(testCaseExecutionData);
                logger.info("Mapping data saved : data mapping with id "+testCaseExecutionData.getTestStepFieldPair().getId()+" and test case execution id "+testCaseExecution.getId()+" TestStep "+testCaseExecutionData.getTestStepFieldPair().getTestStep().getPosition()+" (id="+testCaseExecutionData.getTestStepFieldPair().getTestStep().getId()+"), field : "+testCaseExecutionData.getTestStepFieldPair().getField()+", data : "+testCaseExecutionData.getData());
            } else {
                logger.error("Failed to save data : pair with id "+currentTestStepFieldPair.getId()+" and test case execution id "+testCaseExecution.getId()+" TestStep "+currentTestStepFieldPair.getTestStep().getPosition()+" (id="+currentTestStepFieldPair.getTestStep().getId()+"), field : "+currentTestStepFieldPair.getField());
            }
        }
    }

    public String writeDataInMessage(Message message, TestStep testStep, TestCaseExecution testCaseExecution){
        HashMap<String,String> dataToReplaceInMessage = new HashMap<>();
        for(DataMapping dataMapping : testStep.getTestCase().getDataMappings()) {
            if (null != dataMapping.getTarget().getTestStep() && dataMapping.getTarget().getTestStep().getId() == testStep.getId()) {
                String data = "";
                if (dataMapping.getSource() instanceof TestStepFieldPair) {
                    TestCaseExecutionData testCaseExecutionData = testCaseExecutionDataService.getTestCaseExecutionData(dataMapping.getSource().getId(),testCaseExecution.getId());
                    if(testCaseExecutionData!=null) {
                        data = testCaseExecutionData.getData();
                        logger.info("Mapping data loaded : TestStep " + testCaseExecutionData.getTestStepFieldPair().getTestStep().getPosition() + ", field : " + testCaseExecutionData.getTestStepFieldPair().getField() + ", data : " + testCaseExecutionData.getData() +" into TestStep : "+dataMapping.getTarget().getTestStep().getPosition()+"field " + dataMapping.getTarget().getField());
                    } else {
                        logger.info("Failed to load data mapping with id "+dataMapping.getSource().getId()+" and test case execution id "+testCaseExecution.getId()+" from testStep "+((TestStepFieldPair) dataMapping.getSource()).getTestStep().getPosition()+" (id="+((TestStepFieldPair) dataMapping.getSource()).getTestStep().getId()+"), field "+((TestStepFieldPair) dataMapping.getSource()).getField());
                    }
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
                    logger.error("Invalid mapping for test step " + testStep.getId());
                }
                dataToReplaceInMessage.put(dataMapping.getTarget().getField(),data);
            }
        }
        if(dataToReplaceInMessage.size()>0) {
            MessageEditor messageEditor = null;
            if (testStep.getTestContext() instanceof EDITestContext) {
                messageEditor = new EdiMessageEditor();
            } else if (testStep.getTestContext() instanceof XMLTestContext) {
                messageEditor = new XMLMessageEditor();
            } else {
                logger.error("Message must be either EDI or XML. " + testStep.getTestContext().getFormat() + " found instead.");
            }
            if (messageEditor != null) {
                try {
                    String editedMessage = messageEditor.replaceInMessage(message,dataToReplaceInMessage,testStep.getTestContext());
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
