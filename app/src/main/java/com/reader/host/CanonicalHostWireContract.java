package com.reader.host;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Android's fail-closed boundary for the Reader UI HostRequest 1.2.0 and
 * HostResult 1.0.0 wire contracts.
 *
 * <p>Platform handlers may keep private DTOs behind this boundary, but the
 * request object accepted from Reader UI and every successful result returned
 * to it are closed objects. Unknown historical aliases are rejected on input;
 * handler-only correlation/debug fields are removed from successful output.
 */
public final class CanonicalHostWireContract {

    private static final String INVALID_PARAMS = "INVALID_PARAMS";
    private static final String INVALID_HOST_RESULT = "INVALID_HOST_RESULT";

    private static final class Shape {
        final Set<String> required;
        final Set<String> allowed;

        Shape(Set<String> required, Set<String> allowed) {
            this.required = required;
            this.allowed = allowed;
        }
    }

    private static final Map<String, Shape> REQUESTS = new LinkedHashMap<>();
    private static final Map<String, Shape> RESULTS = new LinkedHashMap<>();

    static {
        request("http.execute", "url,method,headers", "url,method,headers,body,charset,followRedirects,maxRedirects");
        request("http.cancel", "requestId", "requestId");
        request("webview.open", "url", "url,profileId");
        request("webview.close", "", "profileId");
        request("webview.evaluate", "url,script", "url,script,profileId,timeoutMs");
        request("cookie.get", "url", "url,name,sessionId");
        request("cookie.set", "url,cookie", "url,cookie,sessionId");
        request("cookie.clear", "", "url,domain,sessionId");
        request("file.read", "path", "path,encoding,byteOffset,maxBytes");
        request("file.write", "path", "path,content,contentBase64,encoding,createDirectories,append");
        request("file.delete", "path", "path");
        request("storage.path", "scope", "scope");
        request("persistence.get", "namespace,key", "namespace,key");
        request("persistence.put", "namespace,key,value,expectedRevision", "namespace,key,value,expectedRevision");
        request("credential.get", "key", "key");
        request("credential.set", "key,value", "key,value");
        request("credential.delete", "key", "key");
        request("tts.system.start", "text", "text,voice,language,rate,pitch,articleId,correlationId,generation,sliceIndex");
        request("tts.system.stop", "", "");
        request("tts.system.pause", "", "");
        request("tts.system.resume", "", "");
        request("permission.request", "scope", "scope");
        request("permission.check", "scope", "scope");
        request("background.schedule", "taskId", "taskId,delayMs");
        request("background.cancel", "taskId", "taskId");
        request("timer.foreground.arm", "timerId,correlationId,delayMs,generation,oneShot,foregroundOnly", "timerId,correlationId,delayMs,generation,oneShot,foregroundOnly");
        request("timer.foreground.cancel", "timerId,correlationId,delayMs,generation,oneShot,foregroundOnly", "timerId,correlationId,delayMs,generation,oneShot,foregroundOnly");
        request("notification.show", "id,title,body", "id,title,body");
        request("notification.cancel", "id", "id");
        request("share.invoke", "text", "text,title,url,files");
        request("clipboard.copy", "text", "text");
        request("clipboard.paste", "", "");
        request("device.vibrate", "", "durationMs");
        request("device.screen.keep-on", "enabled", "enabled");
        request("device.screen.release", "", "");
        request("file.select", "", "mimeTypes,allowsMultiple");
        request("font.registerFile", "path,familyName", "path,familyName");
        request("font.unregisterFile", "path,familyName", "path,familyName");
        request("clipboard.read", "", "");
        request("clipboard.write", "text", "text");
        request("tts.start", "text", "text,voice,language,rate,pitch,articleId,correlationId,generation,sliceIndex");
        request("tts.stop", "", "");
        request("tts.pause", "", "");
        request("brightness.set", "value", "value");
        request("brightness.get", "", "");
        request("screen.keepAwake", "", "");
        request("screen.allowSleep", "", "");
        request("haptics.light", "", "");
        request("haptics.medium", "", "");
        request("haptics.heavy", "", "");
        request("network.status", "", "");
        request("webdav.connect", "", "url,serverURL,username,password");
        request("webdav.backup", "", "serverURL,username,password");
        request("webdav.restore", "remoteURL", "remoteURL,serverURL,username,password");
        request("share.text", "text", "text");
        request("share.file", "path", "path");
        request("background.task.start", "name", "name");
        request("background.task.end", "taskId", "taskId");

        result("http.execute", "status,body", "status,body,bodyBase64,headers,finalUrl,charsetHint,cookies");
        result("http.cancel", "cancelled", "cancelled");
        result("webview.open", "opened", "opened,profileId");
        result("webview.close", "closed", "closed");
        result("webview.evaluate", "result", "result,finalUrl,title");
        result("cookie.get", "cookies", "cookies");
        result("cookie.set", "stored", "stored");
        result("cookie.clear", "cleared", "cleared");
        result("file.read", "", "content,contentBase64,encoding,byteLength");
        result("file.write", "written", "written,byteLength");
        result("file.delete", "deleted", "deleted");
        result("storage.path", "path", "path");
        result("persistence.get", "found", "found,value,revision");
        result("persistence.put", "stored,revision", "stored,revision");
        result("credential.get", "exists", "exists,value");
        result("credential.set", "stored", "stored");
        result("credential.delete", "deleted", "deleted");
        result("tts.system.start", "started", "started");
        result("tts.system.stop", "acknowledged", "acknowledged");
        result("tts.system.pause", "acknowledged", "acknowledged");
        result("tts.system.resume", "acknowledged", "acknowledged");
        result("permission.request", "granted", "granted");
        result("permission.check", "granted", "granted");
        result("background.schedule", "scheduled", "scheduled");
        result("background.cancel", "cancelled", "cancelled");
        result("timer.foreground.arm", "armed", "armed,replaced,timerId,generation");
        result("timer.foreground.cancel", "cancelled", "cancelled,timerId,generation");
        result("notification.show", "shown", "shown,id");
        result("notification.cancel", "cancelled", "cancelled");
        result("share.invoke", "shared", "shared");
        result("clipboard.copy", "copied", "copied");
        result("clipboard.paste", "text", "text");
        result("device.vibrate", "vibrated", "vibrated");
        result("device.screen.keep-on", "enabled", "enabled");
        result("device.screen.release", "released", "released");
        result("file.select", "selected,files", "selected,files");
        result("font.registerFile", "registered,path,familyName,fontNames", "registered,path,familyName,fontNames");
        result("font.unregisterFile", "logicalUnregistered,physicallyUnregistered,restartRequired", "logicalUnregistered,physicallyUnregistered,restartRequired");
        result("clipboard.read", "text", "text");
        result("clipboard.write", "written", "written");
        result("tts.start", "started", "started,rate,pitch,language");
        result("tts.stop", "stopped", "stopped");
        result("tts.pause", "paused", "paused");
        result("brightness.set", "brightness", "brightness");
        result("brightness.get", "brightness", "brightness");
        result("screen.keepAwake", "applied,keepAwake", "applied,keepAwake");
        result("screen.allowSleep", "applied,keepAwake", "applied,keepAwake");
        result("haptics.light", "performed,style", "performed,style");
        result("haptics.medium", "performed,style", "performed,style");
        result("haptics.heavy", "performed,style", "performed,style");
        result("network.status", "connected,status,interface,isExpensive,isConstrained", "connected,status,interface,isExpensive,isConstrained");
        result("webdav.connect", "connected,statusCode,message", "connected,statusCode,message");
        result("webdav.backup", "backedUp,remoteURL,statusCode,resourceCount", "backedUp,remoteURL,statusCode,resourceCount");
        result("webdav.restore", "restored,remoteURL,statusCode,applied", "restored,remoteURL,statusCode,applied");
        result("share.text", "shared", "shared");
        result("share.file", "shared,path", "shared,path");
        result("background.task.start", "started,taskId,name", "started,taskId,name");
        result("background.task.end", "ended,taskId", "ended,taskId");

        if (REQUESTS.size() != 58 || RESULTS.size() != 58 ||
                !REQUESTS.keySet().equals(RESULTS.keySet())) {
            throw new IllegalStateException("canonical Host wire manifest must be an exact 58/58 set");
        }
    }

    private CanonicalHostWireContract() {}

    /** Returns null when valid or a Host failure when the canonical DTO is invalid. */
    public static HostReply validateRequest(HostRequest request) {
        Shape shape = REQUESTS.get(request.capability());
        if (shape == null) return null; // Host-private Core capabilities are outside this contract.
        final Map<String, Object> payload;
        try {
            payload = object(request.paramsJson(), "request payload");
        } catch (IllegalArgumentException error) {
            return HostReply.error(INVALID_PARAMS, error.getMessage(), false);
        }
        String violation = shapeViolation(shape, payload);
        if (violation == null) violation = requestInvariantViolation(request.capability(), payload);
        return violation == null
                ? null
                : HostReply.error(INVALID_PARAMS, request.capability() + ": " + violation, false);
    }

    /**
     * Projects a successful handler result onto the closed canonical DTO.
     * Unknown platform/debug fields are never returned to Reader UI.
     */
    public static HostReply projectSuccess(String capability, String resultJson) {
        Shape shape = RESULTS.get(capability);
        if (shape == null) return HostReply.complete(resultJson);
        final Map<String, Object> raw;
        try {
            raw = object(resultJson, "host result");
        } catch (IllegalArgumentException error) {
            return HostReply.error(INVALID_HOST_RESULT, error.getMessage(), false);
        }
        Map<String, Object> normalized = normalizeResult(capability, raw);
        Map<String, Object> projected = new LinkedHashMap<>();
        for (String field : shape.allowed) {
            if (normalized.containsKey(field)) projected.put(field, normalized.get(field));
        }
        String violation = shapeViolation(shape, projected);
        if (violation == null) violation = sanitizeNestedResult(capability, projected);
        if (violation == null) violation = resultInvariantViolation(capability, projected);
        if (violation != null) {
            return HostReply.error(
                    INVALID_HOST_RESULT,
                    capability + " handler returned a non-canonical success: " + violation,
                    false);
        }
        return HostReply.complete(Json.stringify(projected));
    }

    public static Set<String> capabilities() {
        return Collections.unmodifiableSet(REQUESTS.keySet());
    }

    private static Map<String, Object> normalizeResult(String capability, Map<String, Object> raw) {
        Map<String, Object> normalized = new LinkedHashMap<>(raw);
        if ("webview.evaluate".equals(capability) && !normalized.containsKey("result") &&
                normalized.containsKey("value")) {
            Object value = normalized.get("value");
            if (value instanceof String) {
                try {
                    value = Json.parse((String) value);
                } catch (RuntimeException ignored) {
                    // A JavaScript string that is not JSON remains a string value.
                }
            }
            if (value instanceof Map) {
                normalized.put("result", value);
            } else {
                Map<String, Object> boxed = new LinkedHashMap<>();
                boxed.put("value", value);
                normalized.put("result", boxed);
            }
        }
        if ("tts.system.stop".equals(capability) && !normalized.containsKey("acknowledged")) {
            normalized.put("acknowledged", normalized.get("stopped"));
        }
        if ("tts.system.pause".equals(capability) && !normalized.containsKey("acknowledged")) {
            normalized.put("acknowledged", normalized.get("paused"));
        }
        if ("tts.system.resume".equals(capability) && !normalized.containsKey("acknowledged")) {
            normalized.put("acknowledged", normalized.get("resumed"));
        }
        if (("permission.request".equals(capability) || "permission.check".equals(capability)) &&
                !normalized.containsKey("granted") && normalized.get("status") instanceof String) {
            normalized.put("granted", "GRANTED".equals(normalized.get("status")));
        }
        if ("device.screen.keep-on".equals(capability) && !normalized.containsKey("enabled")) {
            Object value = normalized.containsKey("keepOn")
                    ? normalized.get("keepOn") : normalized.get("applied");
            normalized.put("enabled", value);
        }
        if ("webdav.restore".equals(capability) && !normalized.containsKey("applied")) {
            normalized.put("applied", normalized.get("restored"));
        }
        return normalized;
    }

    private static String requestInvariantViolation(String capability, Map<String, Object> payload) {
        if ("cookie.set".equals(capability) && !(payload.get("cookie") instanceof Map)) {
            return "cookie must be an object";
        }
        if ("file.write".equals(capability)) {
            boolean text = payload.get("content") instanceof String;
            boolean base64 = payload.get("contentBase64") instanceof String;
            if (text == base64) return "exactly one of content or contentBase64 is required";
        }
        if ("persistence.put".equals(capability) && !(payload.get("expectedRevision") instanceof String)) {
            return "expectedRevision must be a string";
        }
        if ("webdav.connect".equals(capability) || "webdav.backup".equals(capability) ||
                "webdav.restore".equals(capability)) {
            int credentials = 0;
            for (String key : new String[]{"serverURL", "username", "password"}) {
                if (payload.containsKey(key)) credentials++;
            }
            if (credentials != 0 && credentials != 3) {
                return "serverURL, username, and password must be supplied together";
            }
        }
        return null;
    }

    private static String resultInvariantViolation(String capability, Map<String, Object> result) {
        if ("file.read".equals(capability)) {
            boolean text = result.get("content") instanceof String;
            boolean base64 = result.get("contentBase64") instanceof String;
            if (text == base64) return "exactly one of content or contentBase64 is required";
        }
        if ("persistence.get".equals(capability)) {
            Object found = result.get("found");
            if (!(found instanceof Boolean)) return "found must be boolean";
            if (Boolean.TRUE.equals(found) &&
                    (!(result.get("value") instanceof String) ||
                            !(result.get("revision") instanceof String))) {
                return "found=true requires string value and revision";
            }
            if (Boolean.FALSE.equals(found) && result.size() != 1) {
                return "found=false must not carry value or revision";
            }
        }
        if ("credential.get".equals(capability)) {
            Object exists = result.get("exists");
            if (!(exists instanceof Boolean)) return "exists must be boolean";
            if (Boolean.TRUE.equals(exists) && !(result.get("value") instanceof String)) {
                return "exists=true requires string value";
            }
            if (Boolean.FALSE.equals(exists) && result.size() != 1) {
                return "exists=false must not carry value";
            }
        }
        if ("webview.evaluate".equals(capability) && !(result.get("result") instanceof Map)) {
            return "result must be an object";
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static String sanitizeNestedResult(
            String capability,
            Map<String, Object> result) {
        if (("cookie.get".equals(capability) || "http.execute".equals(capability)) &&
                result.containsKey("cookies")) {
            Object rawCookies = result.get("cookies");
            if (!(rawCookies instanceof java.util.List)) return "cookies must be an array";
            java.util.List<Object> cookies = new java.util.ArrayList<>();
            for (Object item : (java.util.List<Object>) rawCookies) {
                if (!(item instanceof Map)) return "each cookie must be an object";
                Map<String, Object> cookie = (Map<String, Object>) item;
                if (!(cookie.get("name") instanceof String) ||
                        !(cookie.get("value") instanceof String)) {
                    return "each cookie requires string name and value";
                }
                Map<String, Object> projected = new LinkedHashMap<>();
                for (String field : csv("name,value,domain,path,secure,httpOnly")) {
                    if (cookie.containsKey(field)) projected.put(field, cookie.get(field));
                }
                cookies.add(projected);
            }
            result.put("cookies", cookies);
        }
        if ("file.select".equals(capability)) {
            Object rawFiles = result.get("files");
            if (!(rawFiles instanceof java.util.List)) return "files must be an array";
            java.util.List<Object> files = new java.util.ArrayList<>();
            for (Object item : (java.util.List<Object>) rawFiles) {
                if (!(item instanceof Map)) return "each selected file must be an object";
                Map<String, Object> file = (Map<String, Object>) item;
                if (!(file.get("path") instanceof String) || !(file.get("name") instanceof String)) {
                    return "each selected file requires string path and name";
                }
                Map<String, Object> projected = new LinkedHashMap<>();
                for (String field : csv("path,name,mimeType,size")) {
                    if (file.containsKey(field)) projected.put(field, file.get(field));
                }
                files.add(projected);
            }
            result.put("files", files);
        }
        if ("font.registerFile".equals(capability)) {
            Object fontNames = result.get("fontNames");
            if (!(fontNames instanceof java.util.List) ||
                    ((java.util.List<Object>) fontNames).stream().anyMatch(value -> !(value instanceof String))) {
                return "fontNames must be an array of strings";
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(String json, String label) {
        final Object parsed;
        try {
            parsed = Json.parse(json == null ? "{}" : json);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException(label + " must be valid JSON: " + error.getMessage());
        }
        if (!(parsed instanceof Map)) {
            throw new IllegalArgumentException(label + " must be a JSON object");
        }
        return (Map<String, Object>) parsed;
    }

    private static String shapeViolation(Shape shape, Map<String, Object> value) {
        Set<String> missing = new LinkedHashSet<>(shape.required);
        missing.removeAll(value.keySet());
        if (!missing.isEmpty()) return "missing required fields " + missing;
        Set<String> unknown = new LinkedHashSet<>(value.keySet());
        unknown.removeAll(shape.allowed);
        if (!unknown.isEmpty()) return "unknown fields " + unknown;
        return null;
    }

    private static void request(String capability, String required, String allowed) {
        put(REQUESTS, capability, required, allowed);
    }

    private static void result(String capability, String required, String allowed) {
        put(RESULTS, capability, required, allowed);
    }

    private static void put(
            Map<String, Shape> target,
            String capability,
            String required,
            String allowed) {
        Set<String> requiredSet = csv(required);
        Set<String> allowedSet = csv(allowed);
        if (!allowedSet.containsAll(requiredSet)) {
            throw new IllegalArgumentException(capability + " required fields must be allowed");
        }
        if (target.put(capability, new Shape(requiredSet, allowedSet)) != null) {
            throw new IllegalArgumentException("duplicate canonical capability " + capability);
        }
    }

    private static Set<String> csv(String value) {
        if (value == null || value.isEmpty()) return Collections.emptySet();
        return Collections.unmodifiableSet(
                new LinkedHashSet<>(Arrays.asList(value.split(","))));
    }
}
