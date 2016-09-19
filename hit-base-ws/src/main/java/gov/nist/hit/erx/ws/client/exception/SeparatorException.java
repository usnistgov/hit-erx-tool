package gov.nist.hit.erx.ws.client.exception;

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
 * Created by Maxence Lefort on 9/19/16.
 */
public class SeparatorException extends Exception {
    public SeparatorException(String message) {
        super("Error while parsing the delimiters: "+message);
    }

    public SeparatorException(int separatorsCount) {
        super("Error while parsing the delimiters, "+separatorsCount+" separators found instead of 6");
    }

    public SeparatorException() {
        super("Unable to parse the delimiters");
    }
}
