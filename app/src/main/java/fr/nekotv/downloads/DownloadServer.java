package fr.nekotv.downloads;

import fi.iki.elonen.NanoHTTPD;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** HTTP facade exposing download management API. */
public final class DownloadServer extends NanoHTTPD {
    private final DownloadHandler handler;

    public DownloadServer(int port, DownloadHandler handler) {
        super(port);
        this.handler = handler;
    }

    @Override
    public Response serve(IHTTPSession session) {
        String method = session.getMethod().name();
        String uri = session.getUri();
        String path = uri;

        try {
            if ("GET".equalsIgnoreCase(method)) {
                if (path.equals("/downloads")) {
                    return newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", handler.getAllJobs());
                } else if (path.startsWith("/downloads/")) {
                    return newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", handler.getJob(path.substring(11)));
                } else if (path.equals("/library")) {
                    return newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", handler.getLibrary());
                }
            } else if ("POST".equalsIgnoreCase(method)) {
                if (path.equals("/downloads")) {
                    return newFixedLengthResponse(Response.Status.CREATED, "application/json; charset=utf-8", handler.createJob(session.getInputStream()));
                }
            } else if ("PATCH".equalsIgnoreCase(method)) {
                if (path.startsWith("/downloads/")) {
                    return newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", handler.updateJob(path.substring(11), session.getInputStream()));
                }
            } else if ("DELETE".equalsIgnoreCase(method)) {
                if (path.startsWith("/downloads/")) {
                    return newFixedLengthResponse(Response.Status.NO_CONTENT, "application/json; charset=utf-8", handler.deleteJob(path.substring(11)));
                }
            }
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json; charset=utf-8", "{\"error\":\"not_found\"}");
        } catch (Exception e) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json; charset=utf-8", "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
}
