package com.budget.tracker;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@Configuration
@Profile("test")
public class TestDatabaseInitializer {

    private final DataSource dataSource;

    public TestDatabaseInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void init() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS transaction_labels CASCADE");
            stmt.execute("DROP TABLE IF EXISTS transfer_labels CASCADE");
            stmt.execute("DROP TABLE IF EXISTS activity_view CASCADE");

            stmt.execute("CREATE TABLE transaction_labels (" +
                    "transaction_id UUID NOT NULL, " +
                    "label_id UUID NOT NULL, " +
                    "PRIMARY KEY (transaction_id, label_id))");

            stmt.execute("CREATE TABLE transfer_labels (" +
                    "transfer_id UUID NOT NULL, " +
                    "label_id UUID NOT NULL, " +
                    "PRIMARY KEY (transfer_id, label_id))");

            stmt.execute("CREATE VIEW activity_view AS " +
                    "SELECT " +
                    "id, " +
                    "'TRANSACTION' AS kind, " +
                    "user_id, " +
                    "account_id, " +
                    "CAST(NULL AS UUID) AS to_account_id, " +
                    "category_id, " +
                    "amount, " +
                    "CAST(type AS VARCHAR) AS type, " +
                    "CAST(NULL AS DECIMAL(19,4)) AS from_amount, " +
                    "CAST(NULL AS DECIMAL(19,4)) AS to_amount, " +
                    "CAST(NULL AS DECIMAL(19,4)) AS adjustment, " +
                    "description, " +
                    "transaction_date, " +
                    "created_at " +
                    "FROM transactions " +
                    "UNION ALL " +
                    "SELECT " +
                    "id, " +
                    "'TRANSFER' AS kind, " +
                    "user_id, " +
                    "from_account_id AS account_id, " +
                    "to_account_id, " +
                    "category_id, " +
                    "CAST(NULL AS DECIMAL(19,4)) AS amount, " +
                    "'TRANSFER' AS type, " +
                    "from_amount, " +
                    "to_amount, " +
                    "adjustment, " +
                    "description, " +
                    "transaction_date, " +
                    "created_at " +
                    "FROM transfers");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize test database view", e);
        }
    }
}
