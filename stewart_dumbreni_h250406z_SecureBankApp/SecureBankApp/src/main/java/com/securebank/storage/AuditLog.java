package com.securebank.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public final class AuditLog {

    private final Path file;

    public AuditLog(Path file) { this.file = file; }

    public synchronized void log(String event, String user, String detail) {
        String line = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS) + " | " + clean(event)
                + " | " + clean(user) + " | " + clean(detail) + System.lineSeparator();
        try {
            Files.writeString(file, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Warning: could not write audit log (" + e.getMessage() + ")");
        }
    }

    private static String clean(String s) {
        if (s == null) return "-";
        String t = s.replaceAll("[\\r\\n|]", "_");
        return t.length() > 80 ? t.substring(0, 80) : t;
    }
}
