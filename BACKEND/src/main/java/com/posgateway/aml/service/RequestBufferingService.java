package com.posgateway.aml.service;



import com.posgateway.aml.dto.TransactionRequestDTO;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Request Buffering Service
 * Buffers requests for batch processing to handle 30K+ concurrent requests
 */
// @RequiredArgsConstructor removed
@Service
public class RequestBufferingService {

    private static final Logger logger = LoggerFactory.getLogger(RequestBufferingService.class);

    private BlockingQueue<TransactionRequestDTO> requestBuffer;
    private final AtomicInteger bufferSize = new AtomicInteger(0);

    @Value("${ultra.throughput.request.buffer.size:50000}")
    private int bufferSizeLimit;

    @Value("${ultra.throughput.batch.processing.size:500}")
    private int batchProcessingSize;

    @PostConstruct
    public void init() {
        // Initialize LinkedBlockingQueue after @Value injection
        this.requestBuffer = new LinkedBlockingQueue<>(bufferSizeLimit);
        logger.info("RequestBufferingService initialized with buffer size: {}", bufferSizeLimit);
    }

    /**
     * Add request to buffer
     * 
     * @param request Transaction request
     * @return true if added, false if buffer full
     */
    public boolean addRequest(TransactionRequestDTO request) {
        if (bufferSize.get() >= bufferSizeLimit) {
            logger.warn("Request buffer full, rejecting request");
            return false;
        }

        boolean added = requestBuffer.offer(request);
        if (added) {
            bufferSize.incrementAndGet();
        }
        return added;
    }

    /**
     * Poll requests from buffer for batch processing
     * 
     * @param maxSize Maximum number of requests to poll
     * @return List of requests
     */
    public List<TransactionRequestDTO> pollRequests(int maxSize) {
        List<TransactionRequestDTO> requests = new ArrayList<>(maxSize);
        int polled = 0;

        while (polled < maxSize) {
            TransactionRequestDTO request = requestBuffer.poll();
            if (request == null) {
                break;
            }
            requests.add(request);
            polled++;
            bufferSize.decrementAndGet();
        }

        return requests;
    }

    /**
     * Get current buffer size
     */
    public int getBufferSize() {
        return bufferSize.get();
    }

    /**
     * Check if buffer has capacity
     */
    public boolean hasCapacity() {
        return bufferSize.get() < bufferSizeLimit;
    }

    /**
     * Get buffer capacity
     */
    public int getBufferCapacity() {
        return bufferSizeLimit;
    }
}
