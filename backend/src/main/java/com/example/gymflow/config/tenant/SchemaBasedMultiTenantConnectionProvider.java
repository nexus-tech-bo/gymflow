package com.example.gymflow.config.tenant;

import lombok.RequiredArgsConstructor;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;

/**
 * Routes each Hibernate session to its tenant schema by setting the PostgreSQL {@code search_path}.
 */
@Component
@RequiredArgsConstructor
public class SchemaBasedMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {
    private static final Pattern SCHEMA = Pattern.compile("^(public|gym_\\d+)$");

    private final DataSource dataSource;

    @Override
    public Connection getAnyConnection() throws SQLException {
        var connection = dataSource.getConnection();
        try {
            setSearchPath(connection, "public");
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        return connection;
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        // search_path cannot be bound as a parameter: validate before concatenating
        if (!SCHEMA.matcher(tenantIdentifier).matches())
            throw new SQLException("Invalid tenant identifier: " + tenantIdentifier);
        var connection = dataSource.getConnection();
        try {
            setSearchPath(connection, tenantIdentifier + ", public");
        } catch (SQLException e) {
            connection.close(); // do not leak a pooled connection
            throw e;
        }
        return connection;
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        try {
            setSearchPath(connection, "public");
        } finally {
            connection.close();
        }
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        throw new UnsupportedOperationException("Unwrap not supported");
    }

    private void setSearchPath(Connection connection, String path) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET search_path TO " + path);
        }
    }
}
