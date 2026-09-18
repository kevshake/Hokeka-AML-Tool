package com.posgateway.aml.service.enrichment;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicLong;

/**
 * NOT an assertion test — a throughput probe for the IP-reputation validation path on the current
 * machine. Prints single-thread and all-core ops/sec. Compute-only (no DB / network / ML), so this
 * is an upper bound for the IP-check component, NOT an end-to-end transaction TPS.
 */
class IpReputationBenchmarkTest {

    private IpReputationService newService() {
        IpReputationService s = new IpReputationService();
        ReflectionTestUtils.setField(s, "enabled", true);
        ReflectionTestUtils.setField(s, "anonymizingCidrsRaw",
                "45.83.0.0/16,185.220.100.0/22,104.16.0.0/12,34.192.0.0/10");
        s.refreshRanges();
        return s;
    }

    // Representative mix: clean public, VPN range, private, malformed, geo-mismatch inputs.
    private static final String[] IPS = {
            "8.8.8.8", "1.1.1.1", "44.0.0.1", "45.83.10.20", "185.220.101.9",
            "10.0.0.5", "192.168.1.9", "127.0.0.1", "100.64.2.3", "999.1.1.1",
            "203.0.113.7", "104.18.5.6", "34.200.10.10", "172.16.9.9", "8.8.4.4"
    };
    private static final String[] DECLARED = {"US", "KE", "GB", "US", "DE"};

    @Test
    void benchmarkIpReputationThroughput() throws Exception {
        IpReputationService service = newService();
        int cores = Runtime.getRuntime().availableProcessors();

        // Warm up the JIT.
        long warm = 0;
        for (int i = 0; i < 2_000_000; i++) {
            warm += service.assess(IPS[i % IPS.length], DECLARED[i % DECLARED.length], "US").manipulated() ? 1 : 0;
        }

        // --- single thread ---
        int iters = 5_000_000;
        long t0 = System.nanoTime();
        long sink = 0;
        for (int i = 0; i < iters; i++) {
            var a = service.assess(IPS[i % IPS.length], DECLARED[i % DECLARED.length], "US");
            sink += (a.manipulated() ? 1 : 0) + (a.geoMismatch() ? 1 : 0);
        }
        long t1 = System.nanoTime();
        double singlePerSec = iters / ((t1 - t0) / 1_000_000_000.0);

        // --- all cores ---
        int threads = cores;
        int perThread = 3_000_000;
        AtomicLong sink2 = new AtomicLong();
        Thread[] pool = new Thread[threads];
        long t2 = System.nanoTime();
        for (int tIdx = 0; tIdx < threads; tIdx++) {
            final int seed = tIdx;
            pool[tIdx] = new Thread(() -> {
                long local = 0;
                for (int i = 0; i < perThread; i++) {
                    int idx = (i + seed) % IPS.length;
                    var a = service.assess(IPS[idx], DECLARED[idx % DECLARED.length], "US");
                    local += a.manipulated() ? 1 : 0;
                }
                sink2.addAndGet(local);
            });
            pool[tIdx].start();
        }
        for (Thread th : pool) {
            th.join();
        }
        long t3 = System.nanoTime();
        double multiPerSec = (long) threads * perThread / ((t3 - t2) / 1_000_000_000.0);

        System.out.println("\n================ IP-REPUTATION VALIDATION BENCHMARK ================");
        System.out.println("CPU logical cores      : " + cores);
        System.out.printf("Single-thread throughput: %,.0f IP checks/sec%n", singlePerSec);
        System.out.printf("All-core throughput     : %,.0f IP checks/sec (%d threads)%n", multiPerSec, threads);
        System.out.println("Scope: compute-only (no DB / network / ML). Upper bound for the IP-check");
        System.out.println("component, NOT an end-to-end transaction TPS.");
        System.out.println("(sink=" + (sink + sink2.get() + warm) + ")");
        System.out.println("===================================================================\n");
    }
}
