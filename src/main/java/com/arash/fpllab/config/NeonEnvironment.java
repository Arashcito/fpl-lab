package com.arash.fpllab.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lets the app use the variables Neon provides, with no copying by hand.
 *
 * 1. Locally, reads ".env.local" (written by `neon deploy` / `neon env pull`) if it exists.
 *    Its values win over same-named environment variables, like `neon-env run` would inject them:
 *    a machine-wide AWS_ACCESS_KEY_ID (e.g. from the AWS CLI) must not replace Neon's storage key.
 *    The file is never in the Docker image, so on Render the dashboard's variables are used.
 * 2. Turns Neon's DATABASE_URL_UNPOOLED (or DATABASE_URL) - "postgresql://user:pass@host/db?sslmode=require" -
 *    into the JDBC url, username and password Spring needs. The direct (unpooled) URL is preferred:
 *    Hibernate updates the schema at startup, which Neon recommends doing over a direct connection,
 *    and this long-running server already keeps its own small connection pool.
 *    An explicit DB_URL still takes precedence (e.g. docker-compose's local Postgres).
 */
public class NeonEnvironment implements EnvironmentPostProcessor {

    static final String DOTENV_FILE = ".env.local";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        if (!env.getProperty("fpl.neon.enabled", Boolean.class, true)) {
            return;   // tests use an in-memory database
        }
        Map<String, Object> dotenv = readDotenv(Path.of(DOTENV_FILE));
        if (!dotenv.isEmpty()) {
            env.getPropertySources().addFirst(new MapPropertySource("neon-dotenv", dotenv));
        }

        if (env.getProperty("DB_URL") != null) {
            return;
        }
        String neonUrl = env.getProperty("DATABASE_URL_UNPOOLED", env.getProperty("DATABASE_URL"));
        if (neonUrl == null || neonUrl.isBlank()) {
            return;
        }
        Map<String, Object> datasource = toSpringDatasource(neonUrl);
        if (!datasource.isEmpty()) {
            env.getPropertySources().addFirst(new MapPropertySource("neon-database", datasource));
        }
    }

    /** postgresql://user:pass@host:port/db?sslmode=require -> spring.datasource.url / username / password */
    static Map<String, Object> toSpringDatasource(String url) {
        Map<String, Object> props = new LinkedHashMap<>();
        URI uri = URI.create(url.trim());
        if (uri.getScheme() == null || !uri.getScheme().startsWith("postgres")) {
            return props;
        }
        String port = uri.getPort() > 0 ? ":" + uri.getPort() : "";
        String query = jdbcQuery(uri.getRawQuery());
        props.put("spring.datasource.url", "jdbc:postgresql://" + uri.getHost() + port + uri.getRawPath()
                + (query.isEmpty() ? "" : "?" + query));
        String userInfo = uri.getRawUserInfo();
        if (userInfo != null) {
            int colon = userInfo.indexOf(':');
            props.put("spring.datasource.username", decode(colon < 0 ? userInfo : userInfo.substring(0, colon)));
            if (colon >= 0) {
                props.put("spring.datasource.password", decode(userInfo.substring(colon + 1)));
            }
        }
        return props;
    }

    /** Keeps sslmode; drops libpq-only options the JDBC driver doesn't understand (e.g. channel_binding). */
    private static String jdbcQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return "sslmode=require&reWriteBatchedInserts=true";
        }
        StringBuilder sb = new StringBuilder();
        for (String part : rawQuery.split("&")) {
            if (part.startsWith("channel_binding=")) {
                continue;
            }
            if (!sb.isEmpty()) sb.append('&');
            sb.append(part);
        }
        if (!sb.toString().contains("sslmode=")) {
            sb.append(sb.isEmpty() ? "" : "&").append("sslmode=require");
        }
        // lets the driver send batched inserts as multi-row statements (far fewer round trips)
        if (!sb.toString().contains("reWriteBatchedInserts=")) {
            sb.append("&reWriteBatchedInserts=true");
        }
        return sb.toString();
    }

    static Map<String, Object> readDotenv(Path file) {
        Map<String, Object> values = new HashMap<>();
        if (!Files.isRegularFile(file)) {
            return values;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String l = line.trim();
                if (l.isEmpty() || l.startsWith("#") || !l.contains("=")) continue;
                if (l.startsWith("export ")) l = l.substring(7).trim();
                int eq = l.indexOf('=');
                String key = l.substring(0, eq).trim();
                String value = l.substring(eq + 1).trim();
                if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                        || value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                values.put(key, value);
            }
        } catch (IOException ignored) {
            // no local env file: nothing to add
        }
        return values;
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}
