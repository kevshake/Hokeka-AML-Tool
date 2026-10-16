package com.posgateway.aml.config;

import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;

/**
 * Writes a {@link StreamingResponseBody} returned inside a wildcard {@code ResponseEntity<?>}.
 *
 * <p>Spring MVC only routes a streaming body to its async {@code StreamingResponseBodyReturnValueHandler}
 * when the handler method <em>declares</em> {@code ResponseEntity<StreamingResponseBody>}. Endpoints that
 * return either a 202 job descriptor or a CSV stream (Console list exports, {@code /exports/*.csv})
 * must declare {@code ResponseEntity<?>}, so the stream fell through to the message converters and
 * failed with "No converter for [...$$Lambda] with preset Content-Type 'text/csv'" (HTTP 500).
 *
 * <p>This converter streams the body synchronously on the request thread straight into the servlet
 * output stream (nothing is buffered), for whatever content type the endpoint preset.
 */
public class StreamingResponseBodyMessageConverter extends AbstractHttpMessageConverter<StreamingResponseBody> {

    /** Accepts every media type: the endpoint chooses the Content-Type (text/csv, …). */
    public StreamingResponseBodyMessageConverter() {
        super(MediaType.ALL);
    }

    /** Any StreamingResponseBody implementation, including lambdas. */
    @Override
    protected boolean supports(Class<?> clazz) {
        return StreamingResponseBody.class.isAssignableFrom(clazz);
    }

    /** Write-only converter: a streaming body is never a request payload. */
    @Override
    public boolean canRead(Class<?> clazz, MediaType mediaType) {
        return false;
    }

    /** Never called because {@link #canRead} is false; reading a stream callback is meaningless. */
    @Override
    protected StreamingResponseBody readInternal(Class<? extends StreamingResponseBody> clazz,
                                                 HttpInputMessage inputMessage) {
        throw new HttpMessageNotReadableException("StreamingResponseBody cannot be read from a request", inputMessage);
    }

    /** Lets the callback write directly to the response body and flushes it. */
    @Override
    protected void writeInternal(StreamingResponseBody body, HttpOutputMessage outputMessage) throws IOException {
        body.writeTo(outputMessage.getBody());
        outputMessage.getBody().flush();
    }

    /** Unknown length: the body is streamed, so no Content-Length is precomputed. */
    @Override
    protected Long getContentLength(StreamingResponseBody body, MediaType contentType) {
        return null;
    }
}
