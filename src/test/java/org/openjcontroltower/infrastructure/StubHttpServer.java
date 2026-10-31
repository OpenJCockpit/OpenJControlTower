package org.openjcontroltower.infrastructure;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/** Minimal real HTTP endpoint so adapters are tested through their actual HTTP client. */
final class StubHttpServer implements AutoCloseable {
    record Request(String method,String path,String authorization,String body) {}
    private final HttpServer server;
    private final AtomicReference<Request> last=new AtomicReference<>();
    private volatile int status=200;
    private volatile String body="";
    private volatile long delayMillis;

    StubHttpServer() throws IOException {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{
            last.set(new Request(exchange.getRequestMethod(),exchange.getRequestURI().getRawPath(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)));
            try { Thread.sleep(delayMillis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","application/json");
            exchange.sendResponseHeaders(status,bytes.length==0?-1:bytes.length);
            if (bytes.length>0) exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }
    StubHttpServer respond(int status,String body) { this.status=status;this.body=body;return this; }
    StubHttpServer delay(long millis) { this.delayMillis=millis;return this; }
    String url() { return "http://127.0.0.1:"+server.getAddress().getPort(); }
    Request last() { return last.get(); }
    @Override public void close() { server.stop(0); }
}
