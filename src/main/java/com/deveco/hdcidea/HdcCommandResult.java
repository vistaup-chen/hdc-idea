package com.deveco.hdcidea;

/**
 * Result of executing an HDC command.
 */
public class HdcCommandResult {

    private final int exitCode;
    private final String stdout;
    private final String stderr;

    public HdcCommandResult(int exitCode, String stdout, String stderr) {
        this.exitCode = exitCode;
        this.stdout = stdout;
        this.stderr = stderr;
    }

    public int getExitCode() {
        return exitCode;
    }

    public String getStdout() {
        return stdout;
    }

    public String getStderr() {
        return stderr;
    }

    public boolean isSuccess() {
        return exitCode == 0;
    }

    public String getOutput() {
        if (exitCode == 0) {
            return stdout.isEmpty() ? "(no output)" : stdout;
        }
        return stderr.isEmpty() ? stdout : stderr;
    }
}
