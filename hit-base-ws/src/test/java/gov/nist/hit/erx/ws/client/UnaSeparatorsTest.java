package gov.nist.hit.erx.ws.client;

import gov.nist.hit.erx.ws.client.exception.SeparatorException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

/**
 * Thi
import java.io.InputStream;the National Institute of Standards and Technology by employees of
 * the Federal Government in the course of their official duties. Pursuant to title 17 Section 105
 * of the United States Code this software is not subject to copyright protection and is in the
 * public domain. This is an experimental system. NIST assumes no responsibility whatsoever for its
 * use by other parties, and makes no guarantees, expressed or implied, about its quality,
 * reliability, or any other characteristic. We would appreciate acknowledgement if the software is
 * used. This software can be redistributed and/or modified freely provided that any derivative
 * works bear some notice that they are derived from it, and any modified versions bear some notice
 * that they have been modified.
 * <p/>
 * Created by Maxence Lefort on 9/19/16.
 */
public class UnaSeparatorsTest {

    @Test
    public void testParseUnaSegment() throws IOException {
        try {
            UnaSeparators unaSeparators = UnaSeparators.parseSeparators("UNA:+./*'");
            Assert.assertTrue(
                    unaSeparators.getDataElementsInACompositeDataElement().equals(":")
                    && unaSeparators.getCompositeDataElements().equals("+")
                    && unaSeparators.getDecimalNotation().equals(".")
                    && unaSeparators.getReleaseIndicator().equals("/")
                    && unaSeparators.getRepetitions().equals("*")
                    && unaSeparators.getSegments().equals("'")
            );

            UnaSeparators newUnaSeparators = new UnaSeparators();
            newUnaSeparators.setDataElementsInACompositeDataElement("\u001C");
            newUnaSeparators.setCompositeDataElements("\u001D");
            newUnaSeparators.setDecimalNotation("\u002E");
            newUnaSeparators.setReleaseIndicator("\u0020");
            newUnaSeparators.setRepetitions("\u003F");
            newUnaSeparators.setSegments("\u001E");
            String message = "UNA:+./*'";
            message = UnaSeparators.replaceSeparatorsInMessage(message,newUnaSeparators);
            unaSeparators = UnaSeparators.parseSeparators(message);
            Assert.assertTrue(
                    unaSeparators.getDataElementsInACompositeDataElement().equals("\u001C")
                    && unaSeparators.getCompositeDataElements().equals("\u001D")
                    && unaSeparators.getDecimalNotation().equals("\u002E")
                    && unaSeparators.getReleaseIndicator().equals("\u0020")
                    && unaSeparators.getRepetitions().equals("\u003F")
                    && unaSeparators.getSegments().equals("\u001E")
            );
        } catch (SeparatorException e) {
            e.printStackTrace();
            Assert.fail();
        }
    }
}
