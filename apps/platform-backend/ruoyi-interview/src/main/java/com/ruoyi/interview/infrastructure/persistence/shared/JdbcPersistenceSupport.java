package com.ruoyi.interview.infrastructure.persistence.shared;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class JdbcPersistenceSupport {

    private JdbcPersistenceSupport() {
    }

    public static void addEnvelope(
            MapSqlParameterSource parameters,
            String prefix,
            EncryptedEnvelope envelope
    ) {
        parameters.addValue(prefix + "KeyId", envelope == null ? null : envelope.keyId());
        parameters.addValue(prefix + "Algorithm", envelope == null ? null : envelope.algorithm());
        parameters.addValue(prefix + "Nonce", envelope == null ? null : envelope.nonce());
        parameters.addValue(prefix + "Ciphertext", envelope == null ? null : envelope.ciphertext());
        parameters.addValue(prefix + "AadHash", envelope == null ? null : envelope.aadHash());
    }

    public static EncryptedEnvelope readEnvelope(ResultSet resultSet, String prefix) throws SQLException {
        EncryptedEnvelope envelope = readNullableEnvelope(resultSet, prefix);
        if (envelope == null) {
            throw new DataIntegrityViolationException("required encrypted envelope is missing: " + prefix);
        }
        return envelope;
    }

    public static EncryptedEnvelope readNullableEnvelope(ResultSet resultSet, String prefix) throws SQLException {
        String keyId = resultSet.getString(prefix + "_key_id");
        String algorithm = resultSet.getString(prefix + "_algorithm");
        byte[] nonce = resultSet.getBytes(prefix + "_nonce");
        byte[] ciphertext = resultSet.getBytes(prefix + "_ciphertext");
        String aadHash = resultSet.getString(prefix + "_aad_hash");
        boolean absent = keyId == null && algorithm == null && nonce == null && ciphertext == null && aadHash == null;
        if (absent) {
            return null;
        }
        if (keyId == null || algorithm == null || nonce == null || ciphertext == null || aadHash == null) {
            throw new DataIntegrityViolationException("partial encrypted envelope is forbidden: " + prefix);
        }
        return new EncryptedEnvelope(keyId, algorithm, nonce, ciphertext, aadHash);
    }

    public static Instant readInstant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        if (value == null) {
            throw new DataIntegrityViolationException("required timestamp is missing: " + column);
        }
        return value.toInstant();
    }

    public static Instant readNullableInstant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    public static OffsetDateTime writeInstant(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    public static void versionedUpsert(
            NamedParameterJdbcTemplate jdbc,
            String insertSql,
            String updateSql,
            MapSqlParameterSource parameters,
            long aggregateVersion,
            String resourceDescription
    ) {
        int inserted = jdbc.update(insertSql, parameters);
        if (inserted == 1) {
            return;
        }
        if (aggregateVersion == 0) {
            throw new OptimisticLockingFailureException(
                    resourceDescription + " already exists at an unknown version");
        }
        parameters.addValue("expectedVersion", aggregateVersion - 1);
        int updated = jdbc.update(updateSql, parameters);
        if (updated != 1) {
            throw new OptimisticLockingFailureException(
                    resourceDescription + " aggregate version conflict");
        }
    }
}

