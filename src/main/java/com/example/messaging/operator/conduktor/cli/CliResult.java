package com.example.messaging.operator.conduktor.cli;

public record CliResult(int exitCode, String stdout, String stderr) {

    public boolean isSuccess() {
        return exitCode == 0;
    }

    public String getErrorMessage() {
        if (isSuccess()) {
            return null;
        }
        return stderr.isBlank() ? "CLI exited with code %d".formatted(exitCode) : stderr;
    }
}
