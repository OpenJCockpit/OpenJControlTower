package org.openjcontroltower.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.*;

/**
 * Runs Git jobs as Kubernetes Jobs in the backend's own namespace. The job inputs, including credentials,
 * live in a Secret owned by the Job, so Kubernetes deletes both together. Pods follow the restricted
 * Pod Security Standard and are removed when the job ends.
 */
@Component
@ConditionalOnProperty(name="controltower.git.runner",havingValue="kubernetes")
public class KubernetesGitJobRunner implements GitJobRunner {
    /** Seam so tests do not wait for real time between status polls. */
    @FunctionalInterface interface Sleeper { void sleep(Duration duration) throws InterruptedException; }
    private static final Logger log=LoggerFactory.getLogger(KubernetesGitJobRunner.class);
    private static final Duration POLL=Duration.ofSeconds(2);
    private final String api;
    private final Path tokenFile;
    private final String namespace;
    private final Duration timeout;
    private final RestClient rest;
    private final Sleeper sleeper;

    @Autowired
    public KubernetesGitJobRunner(@Value("${controltower.git.kubernetes.api-url}") String api,
        @Value("${controltower.git.kubernetes.service-account-dir}") String serviceAccountDir,
        @Value("${controltower.git.kubernetes.namespace}") String namespace,
        @Value("${controltower.http.connect-timeout}") Duration connectTimeout,
        @Value("${controltower.http.read-timeout}") Duration readTimeout,
        @Value("${controltower.git.timeout}") Duration timeout) {
        this(api,Path.of(serviceAccountDir),namespace,connectTimeout,readTimeout,timeout,Thread::sleep);
    }
    KubernetesGitJobRunner(String api,Path serviceAccountDir,String namespace,Duration connectTimeout,
                           Duration readTimeout,Duration timeout,Sleeper sleeper) {
        this.api=api.replaceAll("/+$","");
        this.tokenFile=serviceAccountDir.resolve("token");
        this.namespace=namespace.isBlank()?read(serviceAccountDir.resolve("namespace")).strip():namespace;
        this.timeout=timeout;
        this.sleeper=sleeper;
        HttpClient.Builder client=HttpClient.newBuilder().connectTimeout(connectTimeout);
        if (this.api.startsWith("https:")) client.sslContext(sslContext(serviceAccountDir.resolve("ca.crt")));
        JdkClientHttpRequestFactory factory=new JdkClientHttpRequestFactory(client.build());
        factory.setReadTimeout(readTimeout);
        // Service account tokens are rotated by the kubelet: read the current one for every request.
        this.rest=RestClient.builder().requestFactory(factory)
            .requestInitializer(request->request.getHeaders().setBearerAuth(read(tokenFile).strip())).build();
    }

    @Override
    public Result run(String jobId,String image,String script,Map<String,String> environment) {
        String name="ojct-git-"+jobId;
        Map<?,?> job=rest.post().uri(uri("/apis/batch/v1/namespaces/"+namespace+"/jobs")).body(jobSpec(name,image,script))
            .retrieve().body(Map.class);
        try {
            String uid=String.valueOf(((Map<?,?>)Objects.requireNonNull(job,"Kubernetes returned no job").get("metadata")).get("uid"));
            rest.post().uri(uri("/api/v1/namespaces/"+namespace+"/secrets")).body(Map.of(
                "metadata",Map.of("name",name,"labels",labels(),
                    "ownerReferences",List.of(Map.of("apiVersion","batch/v1","kind","Job","name",name,"uid",uid,"controller",true))),
                "type","Opaque","stringData",environment)).retrieve().toBodilessEntity();
            Boolean succeeded=awaitCompletion(name);
            if (succeeded==null) return new Result(124,"OJCT_ERROR=Git job did not finish within "+timeout.toSeconds()+" seconds");
            return new Result(succeeded?0:1,podLogs(name));
        } finally {
            try {
                rest.delete().uri(uri("/apis/batch/v1/namespaces/"+namespace+"/jobs/"+name+"?propagationPolicy=Background"))
                    .retrieve().toBodilessEntity();
            } catch (RestClientException e) { log.warn("Could not delete Git job {}; its TTL removes it later: {}",name,e.getMessage()); }
        }
    }
    /** @return true when succeeded, false when failed, null on timeout. */
    private Boolean awaitCompletion(String name) {
        for (long waited=0;waited<timeout.toMillis();waited+=POLL.toMillis()) {
            Map<?,?> job=rest.get().uri(uri("/apis/batch/v1/namespaces/"+namespace+"/jobs/"+name)).retrieve().body(Map.class);
            Map<?,?> status=job==null||job.get("status")==null?Map.of():(Map<?,?>)job.get("status");
            if (count(status.get("succeeded"))>0) return true;
            if (count(status.get("failed"))>0) return false;
            try { sleeper.sleep(POLL); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Interrupted while waiting for the Git job",e); }
        }
        return null;
    }
    private String podLogs(String name) {
        Map<?,?> pods=rest.get().uri(uri("/api/v1/namespaces/"+namespace+"/pods?labelSelector=job-name%3D"+name)).retrieve().body(Map.class);
        List<?> items=pods==null||pods.get("items")==null?List.of():(List<?>)pods.get("items");
        if (items.isEmpty()) return "";
        String pod=String.valueOf(((Map<?,?>)((Map<?,?>)items.getFirst()).get("metadata")).get("name"));
        String logs=rest.get().uri(uri("/api/v1/namespaces/"+namespace+"/pods/"+pod+"/log")).retrieve().body(String.class);
        return logs==null?"":logs;
    }
    private Map<String,Object> jobSpec(String name,String image,String script) {
        Map<String,Object> container=new LinkedHashMap<>();
        container.put("name","git");
        container.put("image",image);
        container.put("imagePullPolicy","IfNotPresent");
        container.put("command",List.of("sh","-c",script));
        container.put("workingDir","/work");
        container.put("env",List.of(Map.of("name","HOME","value","/work"),Map.of("name","GIT_TERMINAL_PROMPT","value","0")));
        container.put("envFrom",List.of(Map.of("secretRef",Map.of("name",name))));
        container.put("resources",Map.of("requests",Map.of("cpu","100m","memory","128Mi"),"limits",Map.of("memory","512Mi")));
        container.put("securityContext",Map.of("allowPrivilegeEscalation",false,"readOnlyRootFilesystem",true,
            "capabilities",Map.of("drop",List.of("ALL"))));
        container.put("volumeMounts",List.of(Map.of("name","work","mountPath","/work"),Map.of("name","tmp","mountPath","/tmp")));
        Map<String,Object> pod=new LinkedHashMap<>();
        pod.put("restartPolicy","Never");
        pod.put("automountServiceAccountToken",false);
        pod.put("enableServiceLinks",false);
        pod.put("securityContext",Map.of("runAsNonRoot",true,"runAsUser",10001,"runAsGroup",10001,
            "seccompProfile",Map.of("type","RuntimeDefault")));
        pod.put("containers",List.of(container));
        pod.put("volumes",List.of(Map.of("name","work","emptyDir",Map.of("sizeLimit","512Mi")),
            Map.of("name","tmp","emptyDir",Map.of("sizeLimit","64Mi"))));
        return Map.of("apiVersion","batch/v1","kind","Job",
            "metadata",Map.of("name",name,"labels",labels()),
            "spec",Map.of("backoffLimit",0,"activeDeadlineSeconds",timeout.toSeconds(),"ttlSecondsAfterFinished",600,
                "template",Map.of("metadata",Map.of("labels",labels()),"spec",pod)));
    }
    private static Map<String,String> labels() {
        return Map.of("app.kubernetes.io/name","openjcontroltower","app.kubernetes.io/component","git-job");
    }
    private URI uri(String path) { return URI.create(api+path); }
    private static long count(Object value) { return value instanceof Number n?n.longValue():0; }
    private static String read(Path file) {
        try { return Files.readString(file,StandardCharsets.UTF_8); }
        catch (IOException e) { throw new IllegalStateException("Cannot read Kubernetes service account file "+file,e); }
    }
    static SSLContext sslContext(Path caFile) {
        try (InputStream in=Files.newInputStream(caFile)) {
            KeyStore trust=KeyStore.getInstance(KeyStore.getDefaultType());
            trust.load(null,null);
            int i=0;
            for (Certificate certificate:CertificateFactory.getInstance("X.509").generateCertificates(in))
                trust.setCertificateEntry("ca-"+i++,certificate);
            TrustManagerFactory managers=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            managers.init(trust);
            SSLContext context=SSLContext.getInstance("TLS");
            context.init(null,managers.getTrustManagers(),null);
            return context;
        } catch (IOException|GeneralSecurityException e) {
            throw new IllegalStateException("Cannot load the Kubernetes API CA certificate "+caFile,e);
        }
    }
}
