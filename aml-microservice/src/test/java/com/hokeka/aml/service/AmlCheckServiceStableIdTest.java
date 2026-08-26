package com.hokeka.aml.service;

import com.hokeka.aml.model.TransactionRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the fix for W22-4: when a caller omits transactionId, the fallback cache key used to
 * be "TXN-" + System.currentTimeMillis(), a different value on every single call -- meaning the
 * Aerospike score cache could never be hit for such requests, defeating caching entirely. The
 * fallback must now be a deterministic hash of the request's own content: identical requests
 * (repeated, e.g. a client retry) must produce the same id, and different requests must not.
 */
class AmlCheckServiceStableIdTest {

    private final AmlCheckService service = new AmlCheckService("IR,KP,SY,CU,SD", "NG,RU,CN,VE");

    private String invoke(TransactionRequest request) throws Exception {
        Method m = AmlCheckService.class.getDeclaredMethod("deriveStableRequestId", TransactionRequest.class);
        m.setAccessible(true);
        return (String) m.invoke(service, request);
    }

    private TransactionRequest sampleRequest() {
        TransactionRequest r = new TransactionRequest();
        r.setPspId(7L);
        r.setMerchantId("mrc_001");
        r.setAmount(new BigDecimal("100.00"));
        r.setCurrency("KES");
        r.setSenderName("Jane Doe");
        return r;
    }

    @Test
    void identicalRequestsProduceTheSameStableId() throws Exception {
        TransactionRequest a = sampleRequest();
        TransactionRequest b = sampleRequest(); // separate instance, same content

        String idA = invoke(a);
        String idB = invoke(b);

        // The whole point of the fix: two calls describing the same transaction must land on the
        // same cache key, unlike the old System.currentTimeMillis()-based one.
        assertEquals(idA, idB);
        assertTrue(idA.startsWith("TXN-"));
    }

    @Test
    void differentRequestsProduceDifferentStableIds() throws Exception {
        TransactionRequest a = sampleRequest();
        TransactionRequest b = sampleRequest();
        b.setAmount(new BigDecimal("999.00"));

        assertNotEquals(invoke(a), invoke(b));
    }
}
