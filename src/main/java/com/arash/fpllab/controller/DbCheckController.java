package com.arash.fpllab.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * This class is only to make sure the DB is ok
 * */
@RestController
public class DbCheckController {

    private final DataSource dataSource;

    public DbCheckController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/api/db/ping")
    public String ping() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            return "DB OK ✅ url=" + c.getMetaData().getURL();
        }
    }
}
