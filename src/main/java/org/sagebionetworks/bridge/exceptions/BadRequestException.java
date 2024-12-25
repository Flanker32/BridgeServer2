package org.sagebionetworks.bridge.exceptions;

import org.apache.hc.core5.http.HttpStatus;

@SuppressWarnings("serial")
@NoStackTraceException
public class BadRequestException extends BridgeServiceException {

    public BadRequestException(Throwable throwable) {
        super(throwable, HttpStatus.SC_BAD_REQUEST);
    }
    
    public BadRequestException(String message) {
        super(message, HttpStatus.SC_BAD_REQUEST);
    }
    
}
