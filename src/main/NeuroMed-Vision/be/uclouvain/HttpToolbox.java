/**
 * Copyright (c) 2022, Sebastien Jodogne, ICTEAM UCLouvain, Belgium
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 **/


package java.be.uclouvain;

import com.sun.net.httpserver.HttpExchange;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * This class contains several functions to ease the creation of REST API.
 */
public class HttpToolbox {
    static private final int PRETTY_PRINT_INDENT_FACTOR = 2;

    private HttpToolbox() {
        // This class cannot be instantiated
    }

    /**
     * Get a stream for a static resource from the classpath of the project (such as raw data, HTML or JavaScript files).
     *
     * @param resource The name of the resource of interest.
     * @return The content of the resource.
     * @throws IOException If the resource is nonexistent or cannot be read.
     */
    static public InputStream getResourceStream(final String resource) throws IOException {
        if (resource.startsWith("/")) {
            InputStream stream = HttpToolbox.class.getResourceAsStream(resource);
            if (stream == null) {
                throw new IOException("Missing resource: " + resource);
            } else {
                return stream;
            }
        } else {
            throw new IllegalArgumentException("The path to embedded resources should start with as slash (/)");
        }
    }

    /**
     * Read a static resource from the classpath of the project (such as raw data, HTML or JavaScript files).
     *
     * @param resource The name of the resource of interest.
     * @return The content of the resource.
     * @throws IOException If the resource is nonexistent or cannot be read.
     */
    static public byte[] readResource(final String resource) throws IOException {
        return readBytesFromStream(getResourceStream(resource));
    }

    /**
     * Ensure that the REST call corresponds to a GET request.
     *
     * @param exchange The context of the REST call.
     * @return `true` iff. this is indeed a GET call. If `false` is returned, the caller must do no further action on `exchange`.
     * @throws IOException If error during the HTTP exchange.
     */
    static public boolean protectGetRequest(HttpExchange exchange) throws IOException {
        if (exchange.getRequestMethod().equals("GET")) {
            return true;
        } else {
            sendMethodNotAllowed(exchange, "GET");
            return false;
        }
    }

    /**
     * Ensure that the REST call corresponds to a POST request.
     *
     * @param exchange The context of the REST call.
     * @return `true` iff. this is indeed a POST call. If `false` is returned, the caller must do no further action on `exchange`.
     * @throws IOException If error during the HTTP exchange.
     */
    static public boolean protectPostRequest(HttpExchange exchange) throws IOException {
        if (exchange.getRequestMethod().equals("POST")) {
            return true;
        } else {
            sendMethodNotAllowed(exchange, "POST");
            return false;
        }
    }

    /**
     * Serve a static resource as the result of a GET request.
     *
     * @param exchange    The context of the REST call.
     * @param contentType The MIME type of the resource.
     * @param resource    The name of the resource of interest.
     * @throws IOException If error during the HTTP exchange.
     */
    static public void serveStaticResource(HttpExchange exchange,
                                           final String contentType,
                                           final String resource) throws IOException {
        if (protectGetRequest(exchange)) {
            sendResponse(exchange, contentType, readResource(resource));
        }
    }

    /**
     * Send 405 HTTP status, meaning that the HTTP method is not supported for this URI.
     *
     * @param exchange       The context of the REST call.
     * @param allowedMethods List of the HTTP methods that are supported by this URI.
     * @throws IOException If error during the HTTP exchange.
     */
    static public void sendMethodNotAllowed(HttpExchange exchange,
                                            String allowedMethods) throws IOException {
        exchange.getResponseHeaders().set("Allow", allowedMethods);
        exchange.sendResponseHeaders(405, -1);
    }

    /**
     * Send a byte array as the response to the given REST API request.
     *
     * @param exchange    The context of the REST call.
     * @param contentType The MIME type of the response.
     * @param body        The body of the response.
     * @throws IOException If error during the HTTP exchange.
     */
    static public void sendResponse(HttpExchange exchange,
                                    String contentType,
                                    byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    /**
     * Send a string as the response to the given REST API request.
     *
     * @param exchange    The context of the REST call.
     * @param contentType The MIME type of the response.
     * @param body        The body of the response.
     * @throws IOException If error during the HTTP exchange.
     */
    static public void sendResponse(HttpExchange exchange,
                                    String contentType,
                                    String body) throws IOException {
        sendResponse(exchange, contentType, body.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Send a JSON array as the response to the given REST API request.
     *
     * @param exchange The context of the REST call.
     * @param body     The body of the response.
     * @throws IOException If error during the HTTP exchange.
     */
    static public void sendResponse(HttpExchange exchange,
                                    JSONArray body) throws IOException {
        sendResponse(exchange, "application/json", body.toString(PRETTY_PRINT_INDENT_FACTOR) + "\n");
    }

    /**
     * Send a JSON object as the response to the given REST API request.
     *
     * @param exchange The context of the REST call.
     * @param body     The body of the response.
     * @throws IOException If error during the HTTP exchange.
     */
    static public void sendResponse(HttpExchange exchange,
                                    JSONObject body) throws IOException {
        sendResponse(exchange, "application/json", body.toString(PRETTY_PRINT_INDENT_FACTOR) + "\n");
    }

    /**
     * Send 400 HTTP status, meaning that the user request was incorrect.
     *
     * @param exchange The context of the REST call.
     * @throws IOException If error during the HTTP exchange.
     */
    public static void sendBadRequest(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(400, -1);  // 400 - Bad request
    }

    /**
     * Send 404 HTTP status, meaning that the URI is not available in this REST API.
     *
     * @param exchange The context of the REST call.
     * @throws IOException If error during the HTTP exchange.
     */
    public static void sendNotFound(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(404, -1);  // 404 - Not found
    }

    /**
     * Send 500 HTTP status, meaning an internal server error.
     *
     * @param exchange The context of the REST call.
     * @throws IOException If error during the HTTP exchange.
     */
    public static void sendInternalServerError(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(500, -1);  // 500 - Internal server error
    }

    /**
     * Redirect the request to another URL.
     *
     * @param exchange The context of the REST call.
     * @param url      Target URL for the redirection.
     * @throws IOException If error during the HTTP exchange.
     */
    public static void sendRedirection(HttpExchange exchange,
                                       String url) throws IOException {
        if (protectGetRequest(exchange)) {
            exchange.getResponseHeaders().set("Location", url);
            exchange.sendResponseHeaders(302, -1);  // 302 - Found (temporary redirect)
        }
    }

    /**
     * Read the content of an input stream, and return it as an array of bytes.
     *
     * @param stream The stream of interest.
     * @return The content of the stream.
     * @throws IOException If error while reading the stream.
     */
    public static byte[] readBytesFromStream(InputStream stream) throws IOException {
        // The call below is only valid for Java >= 9
        //return stream.readAllBytes();

        // Fallback implementation for Java <= 8
        byte[] buffer = new byte[4 * 1024];  // 4 KB

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            for (; ; ) {
                int readLen = stream.read(buffer, 0, buffer.length);
                if (readLen == -1) {
                    return outputStream.toByteArray();
                } else {
                    outputStream.write(buffer, 0, readLen);
                }
            }
        }
    }

    /**
     * Read the body of some POST or PUT request, and return it as an array of bytes.
     *
     * @param exchange The context of the REST call.
     * @return The content of the body.
     * @throws IOException If error while reading the body.
     */
    public static byte[] getRequestBody(HttpExchange exchange) throws IOException {
        if (exchange.getRequestMethod().equals("POST") ||
                exchange.getRequestMethod().equals("PUT")) {
            return readBytesFromStream(exchange.getRequestBody());
        } else {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Read the body of some POST or PUT request, and return it as a plain string.
     *
     * @param exchange The context of the REST call.
     * @return The content of the body.
     * @throws IOException If error while reading the body.
     */
    public static String getRequestBodyAsString(HttpExchange exchange) throws IOException {
        return new String(getRequestBody(exchange), StandardCharsets.UTF_8);
    }

    /**
     * Read the body of some POST or PUT request, and parse it as a JSON object.
     *
     * @param exchange The context of the REST call.
     * @return The content of the body.
     * @throws IOException If error while reading the body.
     */
    public static JSONObject getRequestBodyAsJsonObject(HttpExchange exchange) throws IOException {
        try {
            return new JSONObject(getRequestBodyAsString(exchange));
        } catch (JSONException e) {
            throw new IOException();
        }
    }

    /**
     * Get one part of a multipart POST request.
     *
     * @param exchange The context of the REST call.
     * @param name     The name of the part of interest.
     * @return The content of the part, as an array of bytes.
     * @throws IOException If error while reading the part.
     */
    public static byte[] getMultipartFile(HttpExchange exchange,
                                          String name) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            throw new IllegalArgumentException("This function is only applicable to POST requests");
        }

        byte[] body = getRequestBody(exchange);

        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        Map<String, String> parsed = java.be.uclouvain.MultipartReader.parseHeader(contentType);

        if (!parsed.containsKey("boundary")) {
            throw new IOException("No boundary provided for a multipart content");
        }

        java.be.uclouvain.MultipartReader reader = new java.be.uclouvain.MultipartReader(body, parsed.get("boundary"));
        while (reader.readNext()) {
            String contentDisposition = reader.getCurrentHeaders().get("Content-Disposition");
            parsed = java.be.uclouvain.MultipartReader.parseHeader(contentDisposition);

            if (parsed.get("name").equals(name)) {
                return reader.getCurrentPart();
            }
        }

        throw new IOException("No multipart file was provided with name: " + name);
    }

    /**
     * Parse the arguments provided to a GET request.
     *
     * @param uri The URI associated with the GET request.
     * @return The dictionary of the arguments.
     */
    public static Map<String, String> parseGetArguments(String uri) {
        Map<String, String> result = new HashMap<>();

        String decoded;

        try {
            decoded = URLDecoder.decode(uri, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new java.lang.UnsupportedOperationException("UTF-8 decoding is not available on this system");
        }

        int pos = decoded.indexOf('?');
        if (pos != -1) {
            String[] tokens = decoded.substring(pos + 1).split("&");

            for (String token : tokens) {
                int separator = token.indexOf('=');
                if (separator == -1) {
                    result.put(token.trim(), "");
                } else {
                    result.put(token.substring(0, separator).trim(),
                            token.substring(separator + 1).trim());
                }
            }
        }

        return result;
    }

    /**
     * Send a Java image as the response to the given REST API request.
     * The image can be decoded using the JavaScript function
     * "BestRendering.LoadImageFromBackendIntoCanvas()".
     *
     * @param exchange The context of the REST call.
     * @param image    The image that will be sent in the body.
     * @throws IOException If error during the HTTP exchange.
     */
    public static void sendImageToJavaScript(HttpExchange exchange,
                                             BufferedImage image) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();

        final int width = image.getWidth();
        body.write((byte) (width >>> 24));
        body.write((byte) (width >>> 16));
        body.write((byte) (width >>> 8));
        body.write((byte) width);

        final int height = image.getHeight();
        body.write((byte) (height >>> 24));
        body.write((byte) (height >>> 16));
        body.write((byte) (height >>> 8));
        body.write((byte) height);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                body.write((byte) (rgb >>> 16));
                body.write((byte) (rgb >>> 8));
                body.write((byte) (rgb));
                body.write(255);  // alpha channel
            }
        }

        HttpToolbox.sendResponse(exchange, "application/octet-stream", body.toByteArray());
    }

    /**
     * Converts a byte array formatted using UTF-8, into a Java string.
     * @param bytes The input byte array.
     * @return The output string.
     */
    public static String bytesToString(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Parse a string containing a JSON dictionary.
     * @param json The input string.
     * @return The JSON dictionary contained in the string.
     * @throws IOException If the input string doesn't contain a JSON dictionary.
     */
    public static JSONObject parseJsonObject(String json) throws IOException {
        try {
            return new JSONObject(json);
        } catch (JSONException e) {
            throw new IOException();
        }
    }

    /**
     * Parse a byte array containing a JSON dictionary.
     * @param json The input byte array.
     * @return The JSON dictionary contained in the string.
     * @throws IOException If the input byte array doesn't contain a JSON dictionary.
     */
    public static JSONObject parseJsonObject(byte[] json) throws IOException {
        return parseJsonObject(bytesToString(json));
    }

    /**
     * Parse a string containing a JSON array.
     * @param json The input string.
     * @return The JSON array contained in the string.
     * @throws IOException If the input string doesn't contain a JSON array.
     */
    public static JSONArray parseJsonArray(String json) throws IOException {
        try {
            return new JSONArray(json);
        } catch (JSONException e) {
            throw new IOException();
        }
    }

    /**
     * Parse a byte array containing a JSON array.
     * @param json The input byte array.
     * @return The JSON array contained in the string.
     * @throws IOException If the input byte array doesn't contain a JSON array.
     */
    public static JSONArray parseJsonArray(byte[] json) throws IOException {
        return parseJsonArray(bytesToString(json));
    }

    /**
     * Issue an HTTP GET request to the given URL using the built-in HTTP client of Java.
     * @param url The URL of interest.
     * @return The response body.
     * @throws IOException If there was an HTTP connection error.
     */
    public static byte[] HttpClientGetBytes(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) (new URL(url).openConnection());
        connection.setRequestProperty("accept", "*/*");
        return HttpToolbox.readBytesFromStream(connection.getInputStream());
    }

    /**
     * Issue an HTTP GET request to the given URL, and parse the resulting multipart response.
     * @param url The URL of interest.
     * @return Each part of the multipart response.
     * @throws IOException If there was an HTTP connection error, or if the server has
     * not answered with a valid multipart response.
     */
    public static List<byte[]> HttpClientGetMultipart(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) (new URL(url).openConnection());
        connection.setRequestProperty("accept", "*/*");

        Map<String, String> parsed = java.be.uclouvain.MultipartReader.parseHeader(connection.getHeaderField("content-type"));

        if (!parsed.containsKey("boundary")) {
            throw new IOException("No boundary provided for a multipart content");
        }

        List<byte[]> parts = new LinkedList<>();

        final byte[] body =  HttpToolbox.readBytesFromStream(connection.getInputStream());
        java.be.uclouvain.MultipartReader reader = new java.be.uclouvain.MultipartReader(body, parsed.get("boundary"));
        while (reader.readNext()) {
            parts.add(reader.getCurrentPart());
        }

        return parts;
    }
}
