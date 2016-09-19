package gov.nist.hit.erx.ws.client;

import gov.nist.hit.erx.ws.client.exception.SeparatorException;

import java.awt.event.KeyEvent;

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
public class UnaSeparators {
    // :
    private String dataElementsInACompositeDataElement;
    // +
    private String compositeDataElements;
    // .
    private String decimalNotation;
    // /
    private String releaseIndicator;
    // *
    private String repetitions;
    // '
    private String segments;

    //UNA:+./*'

    public static UnaSeparators parseSeparators(String message) throws SeparatorException {
        if(message.toLowerCase().startsWith("una")) {
            char[] chars = message.toCharArray();
            UnaSeparators unaSeparators = new UnaSeparators();
            unaSeparators.setDataElementsInACompositeDataElement(message.substring(3, 4));
            unaSeparators.setCompositeDataElements(message.substring(4, 5));
            unaSeparators.setDecimalNotation(message.substring(5, 6));
            unaSeparators.setReleaseIndicator(message.substring(6, 7));
            unaSeparators.setRepetitions(message.substring(7, 8));
            unaSeparators.setSegments(message.substring(8, 9));
            return unaSeparators;
        } else {
            throw new SeparatorException();
        }
    }

    public static String replaceSeparatorsInMessage(String message,UnaSeparators newSeparators) throws SeparatorException {
        //if(newSeparators.isValid()) {
        UnaSeparators currentSeparators = parseSeparators(message);
        message = message.replace(currentSeparators.getDataElementsInACompositeDataElement(), newSeparators.getDataElementsInACompositeDataElement());
        message = message.replace(currentSeparators.getCompositeDataElements(), newSeparators.getCompositeDataElements());
        message = message.replace(currentSeparators.getDecimalNotation(), newSeparators.getDecimalNotation());
        message = message.replace(currentSeparators.getReleaseIndicator(), newSeparators.getReleaseIndicator());
        message = message.replace(currentSeparators.getRepetitions(), newSeparators.getRepetitions());
        message = message.replace(currentSeparators.getSegments(), newSeparators.getSegments());
        //} else {
        //    throw new SeparatorException("Invalid separators.");
        //}
        return message;
    }

    private boolean containsNonPrintable(String message){
        for(int i=0;i<message.length();i++){
            char c = message.charAt(i);
            Character.UnicodeBlock block = Character.UnicodeBlock.of( c );
            if( (Character.isISOControl(c)) || c == KeyEvent.CHAR_UNDEFINED || block == null || block == Character.UnicodeBlock.SPECIALS){
                return true;
            }
        }
        return false;
    }

    private boolean isValid(){
        return this.getDataElementsInACompositeDataElement()!=null && !this.getDataElementsInACompositeDataElement().equals("")
        && this.getCompositeDataElements()!=null && !this.getCompositeDataElements().equals("")
        && this.getDecimalNotation()!=null && !this.getDecimalNotation().equals("")
        && this.getReleaseIndicator()!=null && !this.getReleaseIndicator().equals("")
        && this.getRepetitions()!=null && !this.getRepetitions().equals("")
        && this.getSegments()!=null && !this.getSegments().equals("");
    }

    public String getDataElementsInACompositeDataElement() {
        return dataElementsInACompositeDataElement;
    }

    public void setDataElementsInACompositeDataElement(String dataElementsInACompositeDataElement) {
        this.dataElementsInACompositeDataElement = dataElementsInACompositeDataElement;
    }

    public String getCompositeDataElements() {
        return compositeDataElements;
    }

    public void setCompositeDataElements(String compositeDataElements) {
        this.compositeDataElements = compositeDataElements;
    }

    public String getDecimalNotation() {
        return decimalNotation;
    }

    public void setDecimalNotation(String decimalNotation) {
        this.decimalNotation = decimalNotation;
    }

    public String getReleaseIndicator() {
        return releaseIndicator;
    }

    public void setReleaseIndicator(String releaseIndicator) {
        this.releaseIndicator = releaseIndicator;
    }

    public String getRepetitions() {
        return repetitions;
    }

    public void setRepetitions(String repetitions) {
        this.repetitions = repetitions;
    }

    public String getSegments() {
        return segments;
    }

    public void setSegments(String segments) {
        this.segments = segments;
    }

}
