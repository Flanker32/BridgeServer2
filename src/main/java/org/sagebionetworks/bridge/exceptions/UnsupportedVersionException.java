package org.sagebionetworks.bridge.exceptions;

import org.apache.hc.core5.http.HttpStatus;
import org.sagebionetworks.bridge.models.ClientInfo;

@SuppressWarnings("serial")
@NoStackTraceException
public class UnsupportedVersionException extends BridgeServiceException {
    
    private final ClientInfo clientInfo;
    
    public UnsupportedVersionException(ClientInfo clientInfo) {
        super("This app version is not supported. Please update.", HttpStatus.SC_GONE);	// 410
        this.clientInfo = clientInfo;
    }

    public ClientInfo getClientInfo() {
        return clientInfo;
    }
    
}