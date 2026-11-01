package org.openjcontroltower.infrastructure;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/** Minimal real HTTP endpoint so adapters are tested through their actual HTTP client. */
final class StubHttpServer implements AutoCloseable {
    record Request(String method,String path,String authorization,String body) {}
    private record Response(int status,byte[] body,String contentType) {}
    private final HttpServer server;
    private final AtomicReference<Request> last=new AtomicReference<>();
    private final List<Request> requests=new CopyOnWriteArrayList<>();
    /** "METHOD /path" (path without query) to response; unmatched requests get the default response. */
    private final Map<String,Response> routes=new ConcurrentHashMap<>();
    private volatile int status=200;
    private volatile String body="";
    private volatile long delayMillis;

    StubHttpServer() throws IOException {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{
            String query=exchange.getRequestURI().getRawQuery();
            Request request=new Request(exchange.getRequestMethod(),
                exchange.getRequestURI().getRawPath()+(query==null?"":"?"+query),
                exchange.getRequestHeaders().getFirst("Authorization"),
                new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            last.set(request);
            requests.add(request);
            try { Thread.sleep(delayMillis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            Response response=routes.getOrDefault(exchange.getRequestMethod()+" "+exchange.getRequestURI().getRawPath(),
                new Response(status,body.getBytes(StandardCharsets.UTF_8),"application/json"));
            exchange.getResponseHeaders().add("Content-Type",response.contentType());
            exchange.sendResponseHeaders(response.status(),response.body().length==0?-1:response.body().length);
            if (response.body().length>0) exchange.getResponseBody().write(response.body());
            exchange.close();
        });
        server.start();
    }
    StubHttpServer respond(int status,String body) { this.status=status;this.body=body;return this; }
    StubHttpServer route(String methodAndPath,int status,String body) { return route(methodAndPath,status,body.getBytes(StandardCharsets.UTF_8)); }
    StubHttpServer route(String methodAndPath,int status,byte[] body) { return route(methodAndPath,status,body,"application/json"); }
    StubHttpServer route(String methodAndPath,int status,byte[] body,String contentType) {
        routes.put(methodAndPath,new Response(status,body,contentType));return this;
    }
    StubHttpServer delay(long millis) { this.delayMillis=millis;return this; }
    String url() { return "http://127.0.0.1:"+server.getAddress().getPort(); }
    Request last() { return last.get(); }
    List<Request> requests() { return List.copyOf(requests); }
    Request request(String method,String pathPrefix) {
        return requests.stream().filter(r->r.method().equals(method)&&r.path().startsWith(pathPrefix)).findFirst()
            .orElseThrow(()->new AssertionError("No "+method+" "+pathPrefix+" in "+requests));
    }
    @Override public void close() { server.stop(0); }
}
