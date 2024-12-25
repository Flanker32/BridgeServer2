package org.sagebionetworks.bridge.spring.controllers;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sample controller to demonstrate how to implement controllers. Can be deleted after we have enough real controllers
 * that we no longer need a sample. (Or we can delete it now and move it into a wiki or something.)
 */
@CrossOrigin
@RestController
public class EchoController {
    /** Echo handler returns this, to demonstrate how Spring auto-coverts to JSON. */
    public static class EchoResult {
        private final String urlToken;
        private final String queryParam;

        public EchoResult(String urlToken, String queryParam) {
            this.urlToken = urlToken;
            this.queryParam = queryParam;
        }

        public String getUrlToken() {
            return urlToken;
        }

        public String getQueryParam() {
            return queryParam;
        }
    }

    @GetMapping({"/echo", "/echo/{urlToken}"})
    public EchoResult handleEcho(@PathVariable(required = false) String urlToken,
            @RequestParam(required = false) String queryParam) {
        return new EchoResult(urlToken, queryParam);
    }
}
