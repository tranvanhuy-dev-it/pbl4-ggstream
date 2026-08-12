package com.project.meet.meeting.infrastructure;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Hibernate ddl-auto=update adds enum-backed columns but PostgreSQL does not
 * automatically widen an existing enum CHECK constraint when Java gains new
 * constants. Keep that generated constraint in sync without touching data.
 */
@Component
public class MeetingSchemaUpdater implements ApplicationRunner {
	private final JdbcTemplate jdbcTemplate;

	public MeetingSchemaUpdater(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void run(ApplicationArguments args) {
		String product;
		try (var connection = jdbcTemplate.getDataSource().getConnection()) {
			product = connection.getMetaData().getDatabaseProductName();
		} catch (Exception ignored) {
			return;
		}
		if (!"PostgreSQL".equalsIgnoreCase(product)) {
			return;
		}
		jdbcTemplate.execute("ALTER TABLE meetings DROP CONSTRAINT IF EXISTS meetings_status_check");
		jdbcTemplate.execute("""
				ALTER TABLE meetings ADD CONSTRAINT meetings_status_check
				CHECK (status IN ('CREATED','SCHEDULED','WAITING','ACTIVE','ENDED','CANCELLED'))
				""");
	}
}
