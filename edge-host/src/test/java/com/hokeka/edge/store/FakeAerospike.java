package com.hokeka.edge.store;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.AerospikeException;
import com.aerospike.client.Bin;
import com.aerospike.client.Key;
import com.aerospike.client.Operation;
import com.aerospike.client.Record;
import com.aerospike.client.ResultCode;
import com.aerospike.client.policy.BatchPolicy;
import com.aerospike.client.policy.GenerationPolicy;
import com.aerospike.client.policy.Policy;
import com.aerospike.client.policy.RecordExistsAction;
import com.aerospike.client.policy.WritePolicy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Deliberate test double (intentional-stub: stands in for an Aerospike cluster): a mocked
 * {@link AerospikeClient} backed by an in-memory map, implementing exactly the calls
 * {@link AerospikeFeatureStore} makes — batch and single get, {@code operate} with ADD and READ
 * operations (long and double bins), and generation-checked / create-only {@code put}. Several
 * stores can share one instance like nodes share one site cluster.
 */
public final class FakeAerospike {

    /** One stored record. */
    private record Row(Map<String, Object> bins, int generation) {
    }

    private final Map<String, Row> rows = new ConcurrentHashMap<>();
    /** The client the store under test uses. */
    public final AerospikeClient client = mock(AerospikeClient.class);
    /** Number of batch gets issued. */
    public final AtomicInteger batchGets = new AtomicInteger();
    /** Keys of the most recent batch get. */
    public final AtomicReference<Key[]> lastBatch = new AtomicReference<>();
    private volatile boolean down;

    /** Wires the mock onto the in-memory rows. */
    public FakeAerospike() {
        when(client.isConnected()).thenAnswer(inv -> !down);
        when(client.get(any(BatchPolicy.class), any(Key[].class))).thenAnswer(inv -> {
            failIfDown();
            Key[] keys = inv.getArgument(1);
            batchGets.incrementAndGet();
            lastBatch.set(keys);
            Record[] out = new Record[keys.length];
            for (int i = 0; i < keys.length; i++) {
                out[i] = record(id(keys[i]));
            }
            return out;
        });
        when(client.get(isNull(), any(Key.class))).thenAnswer(inv -> {
            failIfDown();
            return record(id(inv.getArgument(1)));
        });
        when(client.get(any(Policy.class), any(Key.class))).thenAnswer(inv -> {
            failIfDown();
            return record(id(inv.getArgument(1)));
        });
        when(client.operate(any(WritePolicy.class), any(Key.class), any(Operation[].class))).thenAnswer(inv -> {
            failIfDown();
            String id = id(inv.getArgument(1));
            Map<String, Object> result = new HashMap<>();
            synchronized (rows) {
                Row row = rows.get(id);
                Map<String, Object> bins = row == null ? new HashMap<>() : new HashMap<>(row.bins());
                for (int i = 2; i < inv.getArguments().length; i++) {
                    Object arg = inv.getArguments()[i];
                    Operation[] ops = arg instanceof Operation[] arr ? arr : new Operation[]{(Operation) arg};
                    for (Operation op : ops) {
                        if (op.type == Operation.Type.ADD) {
                            Object v = op.value.getObject();
                            bins.merge(op.binName, v, FakeAerospike::add);
                        } else if (op.type == Operation.Type.READ) {
                            result.put(op.binName, bins.get(op.binName));
                        }
                    }
                }
                rows.put(id, new Row(bins, row == null ? 1 : row.generation() + 1));
            }
            return new Record(result, 1, 0);
        });
        org.mockito.Mockito.doAnswer(inv -> {
            failIfDown();
            WritePolicy p = inv.getArgument(0);
            String id = id(inv.getArgument(1));
            synchronized (rows) {
                Row row = rows.get(id);
                if (p != null && p.recordExistsAction == RecordExistsAction.CREATE_ONLY && row != null) {
                    throw new AerospikeException(ResultCode.KEY_EXISTS_ERROR);
                }
                if (p != null && p.generationPolicy == GenerationPolicy.EXPECT_GEN_EQUAL
                        && (row == null || row.generation() != p.generation)) {
                    throw new AerospikeException(ResultCode.GENERATION_ERROR);
                }
                Map<String, Object> bins = row == null ? new HashMap<>() : new HashMap<>(row.bins());
                for (int i = 2; i < inv.getArguments().length; i++) {
                    Object arg = inv.getArguments()[i];
                    Bin[] arr = arg instanceof Bin[] a ? a : new Bin[]{(Bin) arg};
                    for (Bin b : arr) {
                        bins.put(b.name, b.value.getObject());
                    }
                }
                rows.put(id, new Row(bins, row == null ? 1 : row.generation() + 1));
            }
            return null;
        }).when(client).put(any(WritePolicy.class), any(Key.class), any(Bin[].class));
    }

    /** Simulates a cluster outage (every call fails, {@code isConnected} is false). */
    public void setDown(boolean down) {
        this.down = down;
    }

    /** User keys of every stored record of one set. */
    public List<String> keys(String set) {
        return rows.keySet().stream().filter(k -> k.startsWith(set + "/"))
                .map(k -> k.substring(set.length() + 1)).toList();
    }

    /** A stored record's bins (empty when absent). */
    public Map<String, Object> bins(String set, String userKey) {
        Row row = rows.get(set + "/" + userKey);
        return row == null ? Map.of() : row.bins();
    }

    /** Throws as a real client does on a dead cluster. */
    private void failIfDown() {
        if (down) {
            throw new AerospikeException(ResultCode.SERVER_NOT_AVAILABLE);
        }
    }

    /** Adds two numeric bin values with Aerospike's type rule (long + long, double + double). */
    private static Object add(Object a, Object b) {
        if (a instanceof Double || b instanceof Double) {
            return ((Number) a).doubleValue() + ((Number) b).doubleValue();
        }
        return ((Number) a).longValue() + ((Number) b).longValue();
    }

    /** The row as a client {@link Record}, or null. */
    private Record record(String id) {
        Row row = rows.get(id);
        return row == null ? null : new Record(new HashMap<>(row.bins()), row.generation(), 0);
    }

    /** Map key of a client key. */
    private static String id(Key key) {
        return key.setName + "/" + key.userKey;
    }
}
