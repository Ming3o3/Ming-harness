package org.mingharness.model;

public record ModelRequest(
        String input,
        String model,
        String promptVersion
) {
}
