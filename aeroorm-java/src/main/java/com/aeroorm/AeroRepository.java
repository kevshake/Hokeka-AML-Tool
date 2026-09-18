package com.aeroorm;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Bin;
import com.aerospike.client.Key;
import com.aerospike.client.Record;
import com.aerospike.client.policy.WritePolicy;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal key-first repository over Aerospike records. Supports typed entities with
 * {@link AeroKey} and {@link AeroBin} fields, plus {@code Map<String,Object>} payloads.
 */
public class AeroRepository<T> {

    private final AerospikeClient client;
    private final String namespace;
    private final String setName;
    private final Class<T> entityClass;
    private final Field keyField;
    private final Map<Field, String> binFields = new HashMap<>();

    public AeroRepository(AerospikeClient client, String namespace, Class<T> entityClass) {
        this.client = client;
        this.namespace = namespace;
        this.entityClass = entityClass;
        AeroEntity entity = entityClass.getAnnotation(AeroEntity.class);
        if (entity == null) {
            throw new IllegalArgumentException("Missing @AeroEntity on " + entityClass.getName());
        }
        this.setName = entity.set();
        Field foundKey = null;
        for (Field field : entityClass.getDeclaredFields()) {
            field.setAccessible(true);
            if (field.isAnnotationPresent(AeroKey.class)) {
                if (foundKey != null) {
                    throw new IllegalArgumentException("Multiple @AeroKey fields on " + entityClass.getName());
                }
                foundKey = field;
            }
            if (field.isAnnotationPresent(AeroBin.class)) {
                AeroBin bin = field.getAnnotation(AeroBin.class);
                String binName = bin.value().isBlank() ? field.getName() : bin.value();
                binFields.put(field, binName);
            }
        }
        if (foundKey == null) {
            throw new IllegalArgumentException("Missing @AeroKey on " + entityClass.getName());
        }
        this.keyField = foundKey;
    }

    public void save(T entity, int ttlSeconds) {
        if (client == null || !client.isConnected()) {
            throw new IllegalStateException("Aerospike client not connected");
        }
        String userKey = readKey(entity);
        Key key = new Key(namespace, setName, userKey);
        WritePolicy wp = new WritePolicy();
        if (ttlSeconds > 0) {
            wp.expiration = ttlSeconds;
        }
        client.put(wp, key, toBins(entity));
    }

    public T findByKey(String userKey) {
        if (client == null || !client.isConnected() || userKey == null) {
            return null;
        }
        Key key = new Key(namespace, setName, userKey);
        Record record = client.get(null, key);
        if (record == null) {
            return null;
        }
        return fromRecord(userKey, record);
    }

    /** Map-backed save for dynamic profile payloads (risk_profile cache). */
    public void saveMap(String userKey, Map<String, Object> payload, int ttlSeconds) {
        if (client == null || !client.isConnected() || userKey == null) {
            throw new IllegalStateException("Aerospike client not connected");
        }
        Key key = new Key(namespace, setName, userKey);
        WritePolicy wp = new WritePolicy();
        if (ttlSeconds > 0) {
            wp.expiration = ttlSeconds;
        }
        client.put(wp, key, mapToBins(payload));
    }

    public Map<String, Object> findMap(String userKey) {
        if (client == null || !client.isConnected() || userKey == null) {
            return null;
        }
        Key key = new Key(namespace, setName, userKey);
        Record record = client.get(null, key);
        if (record == null) {
            return null;
        }
        Map<String, Object> out = new HashMap<>();
        record.bins.forEach((k, v) -> out.put(k, coerce(v)));
        return out;
    }

    public String getSetName() {
        return setName;
    }

    private String readKey(T entity) {
        try {
            Object raw = keyField.get(entity);
            return raw == null ? null : raw.toString();
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Could not read AeroKey", ex);
        }
    }

    private Bin[] toBins(T entity) {
        return binFields.entrySet().stream()
                .map(e -> toBin(e.getValue(), readField(entity, e.getKey())))
                .toArray(Bin[]::new);
    }

    private Object readField(T entity, Field field) {
        try {
            return field.get(entity);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Could not read bin field", ex);
        }
    }

    private T fromRecord(String userKey, Record record) {
        try {
            T instance = entityClass.getDeclaredConstructor().newInstance();
            keyField.set(instance, userKey);
            for (Map.Entry<Field, String> e : binFields.entrySet()) {
                Object val = record.getValue(e.getValue());
                if (val != null) {
                    e.getKey().set(instance, coerce(val));
                }
            }
            return instance;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Could not materialize " + entityClass.getName(), ex);
        }
    }

    private static Bin[] mapToBins(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return new Bin[0];
        }
        return map.entrySet().stream()
                .filter(e -> e.getKey() != null)
                .map(e -> toBin(e.getKey(), e.getValue()))
                .toArray(Bin[]::new);
    }

    private static Bin toBin(String name, Object value) {
        if (value instanceof String s) {
            return new Bin(name, s);
        }
        if (value instanceof Long l) {
            return new Bin(name, l);
        }
        if (value instanceof Integer i) {
            return new Bin(name, i.longValue());
        }
        if (value instanceof Double d) {
            return new Bin(name, d);
        }
        if (value instanceof Float f) {
            return new Bin(name, f.doubleValue());
        }
        if (value instanceof Boolean b) {
            return new Bin(name, b);
        }
        if (value instanceof java.util.List<?> li) {
            return new Bin(name, li);
        }
        if (value instanceof java.util.Map<?, ?> m) {
            return new Bin(name, m);
        }
        return new Bin(name, value != null ? value.toString() : "");
    }

    private static Object coerce(Object value) {
        if (value instanceof Long || value instanceof Double || value instanceof String || value instanceof Boolean) {
            return value;
        }
        return value == null ? null : String.valueOf(value);
    }
}
