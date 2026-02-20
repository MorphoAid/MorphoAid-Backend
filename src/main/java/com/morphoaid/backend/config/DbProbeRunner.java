package com.morphoaid.backend.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

@Component
public class DbProbeRunner implements CommandLineRunner {

    @Autowired
    private DataSource dataSource;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("================= DB PROBE RUNNER =================");
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            System.out.println("Driver Name: " + metaData.getDriverName());
            System.out.println("Database Product Name: " + metaData.getDatabaseProductName());
            System.out.println("URL: " + metaData.getURL());
            System.out.println("==================================================");
        } catch (Exception e) {
            System.err.println("DB Probe Failed!");
            e.printStackTrace();
            throw e;
        }
    }
}
