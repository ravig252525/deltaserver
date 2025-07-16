package com.delta.server.delta;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DbCleaner {
    private static final String URL = "jdbc:postgresql://localhost:5432/watchlist";
    private static final String USER = "postgres";
    private static final String PASS = "Ravi1234";

    public static void clearDatabase() throws SQLException {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             Statement st = conn.createStatement()) {

            // Option A: Drop and recreate the public schema
            st.execute("DROP SCHEMA public CASCADE");
            st.execute("CREATE SCHEMA public");

            // Option B: Truncate all tables (if you prefer keeping the schema)
            // WARNING: You must list tables or dynamically query information_schema
            // st.execute("TRUNCATE table1, table2, table3 RESTART IDENTITY CASCADE");
        }
    }

    public static void main(String[] args) throws SQLException {
        clearDatabase();
        System.out.println("Database cleared!");
    }
}

