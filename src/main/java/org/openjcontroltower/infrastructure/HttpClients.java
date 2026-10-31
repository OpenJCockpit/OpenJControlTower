package org.openjcontroltower.infrastructure;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.time.Duration;

/** Outbound HTTP must always be bounded: an unanswered call may not hold a dispatch lock forever. */
final class HttpClients {
    private HttpClients() {}
    static RestClient withTimeouts(Duration connect, Duration read) {
        SimpleClientHttpRequestFactory factory=new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connect);
        factory.setReadTimeout(read);
        return RestClient.builder().requestFactory(factory).build();
    }
}
