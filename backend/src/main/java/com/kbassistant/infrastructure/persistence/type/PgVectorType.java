package com.kbassistant.infrastructure.persistence.type;

import com.pgvector.PGvector;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;

/**
 * Hibernate 6 custom type that maps PostgreSQL vector(N) ↔ Java float[].
 *
 * Why a custom UserType instead of @JdbcTypeCode or @JavaType:
 * The `vector` PostgreSQL type is completely unknown to Hibernate's dialect.
 * There's no standard JDBC type code for it. UserType gives us full control
 * over how the value is read from ResultSet and written to PreparedStatement.
 *
 * Usage on entity fields:
 *   @Type(PgVectorType.class)
 *   @Column(name = "embedding", columnDefinition = "vector(1536)")
 *   private float[] embedding;
 *
 * Write path: float[] → PGvector (extends PGobject) → JDBC setObject(Types.OTHER)
 * Read path:  ResultSet.getObject() → PGobject string "[1.0,2.0,...]" → float[]
 */
public class PgVectorType implements UserType<float[]> {

    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    public Class<float[]> returnedClass() {
        return float[].class;
    }

    @Override
    public boolean equals(float[] x, float[] y) {
        return Arrays.equals(x, y);
    }

    @Override
    public int hashCode(float[] x) {
        return Arrays.hashCode(x);
    }

    @Override
    public float[] nullSafeGet(ResultSet rs,
                                int position,
                                SharedSessionContractImplementor session,
                                Object owner) throws SQLException {
        Object obj = rs.getObject(position);
        if (obj == null || rs.wasNull()) return null;

        // PostgreSQL JDBC returns the vector column as a PGobject whose
        // getValue() is "[1.0,2.0,...]". Parse it manually to avoid
        // requiring PGvector to be registered with the JDBC connection.
        String vectorStr = obj.toString().trim();
        if (vectorStr.isEmpty()) return null;

        // Strip surrounding brackets
        vectorStr = vectorStr.substring(1, vectorStr.length() - 1);
        String[] parts = vectorStr.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }

    @Override
    public void nullSafeSet(PreparedStatement st,
                             float[] value,
                             int index,
                             SharedSessionContractImplementor session) throws SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
        } else {
            // PGvector extends PGobject — the PostgreSQL JDBC driver knows how
            // to serialize PGobject instances to the wire protocol.
            st.setObject(index, new PGvector(value));
        }
    }

    @Override
    public float[] deepCopy(float[] value) {
        return value == null ? null : Arrays.copyOf(value, value.length);
    }

    @Override
    public boolean isMutable() {
        return true;
    }

    @Override
    public Serializable disassemble(float[] value) {
        return deepCopy(value);
    }

    @Override
    public float[] assemble(Serializable cached, Object owner) {
        return deepCopy((float[]) cached);
    }
}
