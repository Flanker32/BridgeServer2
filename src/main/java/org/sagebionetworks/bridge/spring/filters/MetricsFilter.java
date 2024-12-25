package org.sagebionetworks.bridge.spring.filters;

import static org.apache.hc.core5.http.HttpHeaders.USER_AGENT;
import static org.sagebionetworks.bridge.BridgeConstants.X_FORWARDED_FOR_HEADER;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.hc.core5.net.URLEncodedUtils;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import org.apache.hc.core5.http.NameValuePair;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.models.Metrics;

@Component
public class MetricsFilter implements Filter {

    private static final Logger LOG = LoggerFactory.getLogger(MetricsFilter.class);
    
    public static final String X_PASSTHROUGH = "X-Passthrough";

    // Allow-list for query parameters metrics logging.
    private static final List<String> ALLOW_LIST =
            BridgeConfigFactory.getConfig().getList("query.param.allowlist");
    
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        final Metrics metrics = RequestContext.get().getMetrics();
        
        HttpServletRequest request = (HttpServletRequest)req;
        HttpServletResponse response = (HttpServletResponse)res;
        metrics.setMethod(request.getMethod());
        metrics.setUri(request.getServletPath());
        metrics.setProtocol(request.getProtocol());
        metrics.setRemoteAddress(header(request, X_FORWARDED_FOR_HEADER, request.getRemoteAddr()));
        metrics.setUserAgent(header(request, USER_AGENT, null));

        // Process the query parameters, and append them to the metrics.
        List<NameValuePair> params = URLEncodedUtils.parse(request.getQueryString(), StandardCharsets.UTF_8);

        Multimap<String, String> paramsMap = MultimapBuilder.linkedHashKeys().linkedListValues().build();
        params.stream().filter(i -> ALLOW_LIST.contains(i.getName()))
                .forEach(i -> paramsMap.put(i.getName(), i.getValue()));

        metrics.setQueryParams(paramsMap);

        try {
            chain.doFilter(req, res);
            metrics.setStatus(response.getStatus());
        } finally {
            // Log session info when a session is present
            UserSession session = (UserSession) request.getAttribute("CreatedUserSession");
            if (session != null) {
                // Record UserSession to Metrics.
                writeSessionInfoToMetrics(metrics, session);
            }
            if (response.getHeader(X_PASSTHROUGH) == null) {
                metrics.end();
                LOG.info(metrics.toJsonString());
            }
        }
    }

    private String header(HttpServletRequest request, String name, String defaultVal) {
        final String value = request.getHeader(name);
        return (value != null) ? value : defaultVal;
    }

    /**
     * Writes the user's account ID, internal session ID, and app ID to the metrics.
     * If either metrics or session is null, then this method does nothing.
     */
    private static void writeSessionInfoToMetrics(Metrics metrics, UserSession session) {
        if (metrics != null && session != null) {
            metrics.setSessionId(session.getInternalSessionToken());
            metrics.setUserId(session.getId());
            metrics.setAppId(session.getAppId());
        }
    }

    @Override
    public void init(FilterConfig filterConfig) {
        // no-op
    }

    @Override
    public void destroy() {
        // no-op
    }
}
