package com.streamvault.db;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

public class TestConnection {
    public static void main(String[] args) {
        System.out.println("Testing JDBC connection to StreamVault...");
        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn != null) {
                DatabaseMetaData meta = conn.getMetaData();
                System.out.println("Connection successful!");
                System.out.println("Database: " + meta.getDatabaseProductName());
                System.out.println("Version : " + meta.getDatabaseProductVersion());
                System.out.println("URL     : " + meta.getURL());
            }
        } catch (SQLException e) {
            System.err.println("Connection failed: " + e.getMessage());
        }
    }
}
