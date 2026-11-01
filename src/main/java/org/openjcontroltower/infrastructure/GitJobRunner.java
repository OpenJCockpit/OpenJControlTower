package org.openjcontroltower.infrastructure;

import java.util.Map;

/** Runs the Git job script in a disposable, isolated container that is always removed afterwards. */
public interface GitJobRunner {
    record Result(int exitCode, String logs) {}
    /** @param environment the job inputs, including credentials; implementations must keep them out of logs and specs. */
    Result run(String jobId, String image, String script, Map<String,String> environment);
}
