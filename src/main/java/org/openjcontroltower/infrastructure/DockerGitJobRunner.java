package org.openjcontroltower.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriUtils;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Runs Git jobs as containers through the Docker Engine API, reached over TCP through a socket proxy
 * that only allows container and image calls. Each container is locked down and removed afterwards.
 */
@Component
@ConditionalOnProperty(name="controltower.git.runner",havingValue="docker",matchIfMissing=true)
public class DockerGitJobRunner implements GitJobRunner {
    static final String LABEL="openjcontroltower.git-job";
    /** Several stacks may share one Docker host: each only cleans up the containers on its own network. */
    static final String OWNER_LABEL="openjcontroltower.owner";
    private static final Logger log=LoggerFactory.getLogger(DockerGitJobRunner.class);
    private final String host;
    private final String network;
    private final RestClient rest;
    public DockerGitJobRunner(@Value("${controltower.git.docker.host}") String host,
        @Value("${controltower.git.docker.network}") String network,
        @Value("${controltower.http.connect-timeout}") Duration connectTimeout,
        @Value("${controltower.git.timeout}") Duration timeout) {
        this.host=host.replaceAll("/+$","");this.network=network.isBlank()?"bridge":network;
        // The JDK client sends "Content-Length: 0" on body-less POSTs; Docker rejects /start calls with an
        // unknown body length. The wait call blocks for the whole job, so reads may take the full job timeout.
        JdkClientHttpRequestFactory factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(connectTimeout).build());
        factory.setReadTimeout(timeout);
        this.rest=RestClient.builder().requestFactory(factory).build();
    }
    @Override
    public Result run(String jobId,String image,String script,Map<String,String> environment) {
        pullIfMissing(image);
        Map<String,Object> hostConfig=new LinkedHashMap<>();
        hostConfig.put("ReadonlyRootfs",true);
        hostConfig.put("Tmpfs",Map.of("/work","rw,noexec,nosuid,size=512m,mode=1777","/tmp","rw,noexec,nosuid,size=64m"));
        hostConfig.put("CapDrop",List.of("ALL"));
        hostConfig.put("SecurityOpt",List.of("no-new-privileges"));
        hostConfig.put("Memory",512L*1024*1024);
        hostConfig.put("MemorySwap",512L*1024*1024);
        hostConfig.put("PidsLimit",256);
        hostConfig.put("Init",true);
        hostConfig.put("NetworkMode",network);
        Map<String,String> env=new LinkedHashMap<>(environment);
        env.put("HOME","/work");
        env.put("GIT_TERMINAL_PROMPT","0");
        Map<String,Object> spec=new LinkedHashMap<>();
        spec.put("Image",image);
        spec.put("Entrypoint",List.of("sh","-c"));
        spec.put("Cmd",List.of(script));
        spec.put("Env",env.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).toList());
        spec.put("User","10001:10001");
        spec.put("WorkingDir","/work");
        spec.put("Labels",Map.of(LABEL,jobId,OWNER_LABEL,network));
        spec.put("HostConfig",hostConfig);
        String name="ojct-git-"+jobId;
        Map<?,?> created=rest.post().uri(uri("/containers/create?name="+name)).body(spec).retrieve().body(Map.class);
        String id=String.valueOf(Objects.requireNonNull(created,"Docker returned no container").get("Id"));
        try {
            rest.post().uri(uri("/containers/"+id+"/start")).retrieve().toBodilessEntity();
            Map<?,?> waited=rest.post().uri(uri("/containers/"+id+"/wait")).retrieve().body(Map.class);
            int exitCode=((Number)Objects.requireNonNull(waited,"Docker returned no exit status").get("StatusCode")).intValue();
            byte[] logs=rest.get().uri(uri("/containers/"+id+"/logs?stdout=true&stderr=true")).retrieve().body(byte[].class);
            return new Result(exitCode,demultiplex(logs==null?new byte[0]:logs));
        } finally {
            remove(id);
        }
    }
    /** Containers left behind by a crash or restart: this single-replica backend owns every job container on its network. */
    @EventListener(ApplicationReadyEvent.class)
    public void removeOrphans() {
        try {
            String filter=UriUtils.encodeQueryParam("{\"label\":[\""+LABEL+"\",\""+OWNER_LABEL+"="+network+"\"]}",StandardCharsets.UTF_8);
            List<?> containers=rest.get().uri(uri("/containers/json?all=true&filters="+filter)).retrieve().body(List.class);
            for (Object container:containers==null?List.of():containers) remove(String.valueOf(((Map<?,?>)container).get("Id")));
        } catch (RestClientException e) {
            log.warn("Could not check for orphaned Git job containers at {}: {}",host,e.getMessage());
        }
    }
    private void pullIfMissing(String image) {
        try { rest.get().uri(uri("/images/"+image+"/json")).retrieve().toBodilessEntity(); }
        catch (HttpClientErrorException.NotFound missing) {
            int tag=image.lastIndexOf(':');
            String query=tag>image.lastIndexOf('/')
                ? "fromImage="+UriUtils.encodeQueryParam(image.substring(0,tag),StandardCharsets.UTF_8)+"&tag="+UriUtils.encodeQueryParam(image.substring(tag+1),StandardCharsets.UTF_8)
                : "fromImage="+UriUtils.encodeQueryParam(image,StandardCharsets.UTF_8);
            rest.post().uri(uri("/images/create?"+query)).retrieve().body(String.class);
        }
    }
    private void remove(String id) {
        try { rest.delete().uri(uri("/containers/"+id+"?force=true")).retrieve().toBodilessEntity(); }
        catch (RestClientException e) { log.warn("Could not remove Git job container {}: {}",id,e.getMessage()); }
    }
    private URI uri(String path) { return URI.create(host+path); }
    /** Without a TTY, Docker prefixes each output chunk with an 8-byte header: stream, 3 zero bytes, length. */
    static String demultiplex(byte[] raw) {
        ByteBuffer buffer=ByteBuffer.wrap(raw);
        StringBuilder out=new StringBuilder();
        while (buffer.remaining()>=8) {
            buffer.position(buffer.position()+4);
            int length=Math.min(buffer.getInt(),buffer.remaining());
            byte[] chunk=new byte[length];
            buffer.get(chunk);
            out.append(new String(chunk,StandardCharsets.UTF_8));
        }
        return out.toString();
    }
}
