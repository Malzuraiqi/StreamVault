package com.streamvault.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class QueryLogger {

    private static final DateTimeFormatter fmt =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void log(String servlet, String operation, String sql, int rowsAffected) {
        System.out.println(
                "[" + LocalDateTime.now().format(fmt) + "] " +
                        "[" + servlet + "] " +
                        "[" + operation + "] " +
                        "SQL: " + sql.replaceAll("\\s+", " ").trim() +
                        (rowsAffected >= 0 ? " | Rows: " + rowsAffected : "")
        );
    }

    public static void log(String servlet, String operation, String sql) {
        log(servlet, operation, sql, -1);
    }
}