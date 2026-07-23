package com.ticketflow1.ticketing;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TicketingApplication {

	public static void main(String[] args) {
		configureDatabaseUrlFallback();
		SpringApplication.run(TicketingApplication.class, args);
	}

	private static void configureDatabaseUrlFallback() {
		String databaseUrl = System.getenv("DATABASE_URL");
		if (databaseUrl == null || databaseUrl.isBlank()
				|| hasEnv("SPRING_DATASOURCE_URL") || hasEnv("POSTGRES_HOST")) {
			return;
		}
		try {
			applyExplicitDatabaseCredentials();
			if (databaseUrl.startsWith("jdbc:postgresql://")) {
				System.setProperty("spring.datasource.url", databaseUrl);
				return;
			}
			URI uri = URI.create(databaseUrl);
			String scheme = uri.getScheme();
			if (!"postgres".equalsIgnoreCase(scheme) && !"postgresql".equalsIgnoreCase(scheme)) {
				return;
			}
			String userInfo = uri.getRawUserInfo();
			if (userInfo != null) {
				String[] parts = userInfo.split(":", 2);
				if (parts.length > 0 && !hasDatasourceUsername()) {
					System.setProperty("spring.datasource.username", decode(parts[0]));
				}
				if (parts.length > 1 && !hasDatasourcePassword()) {
					System.setProperty("spring.datasource.password", decode(parts[1]));
				}
			}
			String path = uri.getRawPath() == null || uri.getRawPath().isBlank() ? "/" : uri.getRawPath();
			String jdbcUrl = "jdbc:postgresql://" + uri.getHost()
					+ (uri.getPort() > 0 ? ":" + uri.getPort() : "")
					+ path;
			String query = uri.getRawQuery();
			if (query == null || query.isBlank()) {
				if (uri.getHost() != null && uri.getHost().endsWith(".render.com")) {
					query = "sslmode=require";
				}
			} else if (!query.toLowerCase(java.util.Locale.ROOT).contains("sslmode=")
					&& uri.getHost() != null && uri.getHost().endsWith(".render.com")) {
				query = query + "&sslmode=require";
			}
			if (query != null && !query.isBlank()) {
				jdbcUrl = jdbcUrl + "?" + query;
			}
			System.setProperty("spring.datasource.url", jdbcUrl);
		} catch (IllegalArgumentException ignored) {
			// Spring will fall back to the normal POSTGRES_* defaults and report
			// the connection error with its standard diagnostics.
		}
	}

	private static boolean hasEnv(String key) {
		String value = System.getenv(key);
		return value != null && !value.isBlank();
	}

	private static boolean hasDatasourceUsername() {
		return hasEnv("SPRING_DATASOURCE_USERNAME") || hasEnv("POSTGRES_USER")
				|| System.getProperty("spring.datasource.username") != null;
	}

	private static boolean hasDatasourcePassword() {
		return hasEnv("SPRING_DATASOURCE_PASSWORD") || hasEnv("POSTGRES_PASSWORD")
				|| System.getProperty("spring.datasource.password") != null;
	}

	private static void applyExplicitDatabaseCredentials() {
		if (hasEnv("DATABASE_USERNAME") && !hasDatasourceUsername()) {
			System.setProperty("spring.datasource.username", System.getenv("DATABASE_USERNAME"));
		}
		if (hasEnv("DATABASE_PASSWORD") && !hasDatasourcePassword()) {
			System.setProperty("spring.datasource.password", System.getenv("DATABASE_PASSWORD"));
		}
	}

	private static String decode(String value) {
		return URLDecoder.decode(value, StandardCharsets.UTF_8);
	}
}
