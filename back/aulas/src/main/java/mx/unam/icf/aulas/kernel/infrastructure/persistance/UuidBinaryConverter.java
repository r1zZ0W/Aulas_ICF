package mx.unam.icf.aulas.kernel.infrastructure.persistance;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * JPA converter for storing UUID values as BINARY(16) in the database.
 *
 * This converter is auto-applied to all UUID fields, providing compact storage
 * and consistent mapping across entities.
 */
@Converter(autoApply = true)
public class UuidBinaryConverter implements AttributeConverter<UUID, byte[]> {

    /**
     * Converts a UUID into a 16-byte binary array for database storage.
     * Prevents data mismatch errors in BINARY(16) columns. Returns null
     * immediately if the input UUID is null.
     *
     * @param attribute the UUID entity attribute to be converted
     * @return a 16-byte array, or null if the input is null

     */
    @Override
    public byte[] convertToDatabaseColumn(UUID attribute) {
        if (attribute == null)
            return null;

        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(attribute.getMostSignificantBits());
        buffer.putLong(attribute.getLeastSignificantBits());

        return buffer.array();
    }

    /**
     * Converts a 16-byte binary array from the database back into a UUID.
     * Returns null immediately if the input array is null or does not
     * contain exactly 16 bytes.
     *
     * @param dbData the 16-byte binary data from the database column
     * @return the reconstructed UUID, or null if the input is invalid
     */
    @Override
    public UUID convertToEntityAttribute(byte[] dbData) {
        if (dbData == null || dbData.length != 16)
            return null;

        ByteBuffer buffer = ByteBuffer.wrap(dbData);
        long mostSignificant = buffer.getLong();
        long leastSignificant = buffer.getLong();
        return new UUID(mostSignificant, leastSignificant);
    }
}

