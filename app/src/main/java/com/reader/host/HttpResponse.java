package com.reader.host;

import java.util.Map;

/** Value object for an {@code http.execute} response (Host → Core). */
public final class HttpResponse {

    private final int status;
    private final String body;
    private final Map<String, String> headers;
    private final String finalUrl;

    public HttpResponse(int status, String body) {
        this(status, body, null, null);
    }

    public HttpResponse(int status, String body, Map<String, String> headers) {
        this(status, body, headers, null);
    }

    public HttpResponse(int status, String body, Map<String, String> headers, String finalUrl) {
        this.status = status;
        this.body = body == null ? "" : body;
        this.headers = headers == null ? null : headers;
        this.finalUrl = finalUrl;
    }

    public int status() {
        return status;
    }

    public String body() {
        return body;
    }

    public Map<String, String> headers() {
        return headers;
    }

    public boolean hasHeaders() {
        return headers != null;
    }

    public String finalUrl() {
        return finalUrl;
    }

    public boolean hasFinalUrl() {
        return finalUrl != null;
    }
}
