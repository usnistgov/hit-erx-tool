package gov.nist.hit.erx.web.utils;

import gov.nist.hit.core.domain.TestCase;
import gov.nist.hit.core.domain.TestCaseExecution;
import gov.nist.hit.core.domain.TestStep;
import gov.nist.hit.core.service.TestCaseExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

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
 * <p>
 * Created by Maxence Lefort on 2/17/16.
 */
@Service
public class TestCaseExecutionUtils {

    @Autowired
    protected TestCaseExecutionService testCaseExecutionService;

    static final Logger logger = LoggerFactory.getLogger(TestCaseExecutionUtils.class);

    @Transactional
    public TestCaseExecution initTestCaseExecution(Long userId,TestStep testStep){
        TestCaseExecution testCaseExecution = this.findOne(userId);
        if(testCaseExecution==null) {
            testCaseExecution = new TestCaseExecution();
            testCaseExecution.setUserId(userId);
        }
        TestCase testCase = testStep.getTestCase();
        testCaseExecution.setTestCase(testCase);
        testCaseExecution.setCurrentTestStepId(testStep.getId());
        logger.info("Init testCaseExecution : UserId : "+userId+", TestStep "+testStep.getId()+", TestCase "+testStep.getTestCase().getId());
        testCaseExecution = testCaseExecutionService.save(testCaseExecution);
        return testCaseExecution;
    }

    @Transactional
    public  TestCaseExecution findOne(Long userId) {
        return testCaseExecutionService.findOneByUserId(userId);
    }
    
}