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


package be.uclouvain;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpPrincipal;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import org.json.JSONObject;

public class MockHttpExchange extends HttpExchange {
    public enum Method {
        GET,
        POST,
        DELETE,
        PUT
    }

    private final URI uri;
    private final Method method;
    private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
    private final Headers requestHeaders = new Headers();
    private final Headers responseHeaders = new Headers();
    private byte[] requestBody = null;
    private boolean hasResponse = false;
    private boolean closed = false;
    private int responseCode = -1;
    private long responseLength = -1;

    public MockHttpExchange(String uri,
                            Method method) throws URISyntaxException {
        this.uri = new URI(uri);
        this.method = method;
    }

    @Override
    public Headers getRequestHeaders() {
        return requestHeaders;
    }

    @Override
    public Headers getResponseHeaders() {
        return responseHeaders;
    }

    @Override
    public URI getRequestURI() {
        return uri;
    }

    @Override
    public String getRequestMethod() {
        switch (method) {
            case GET:
                return "GET";
            case POST:
                return "POST";
            case DELETE:
                return "DELETE";
            case PUT:
                return "PUT";
            default:
                throw new UnsupportedOperationException();
        }
    }

    @Override
    public HttpContext getHttpContext() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
        if (closed) {
            throw new IllegalStateException("Already closed");
        } else if (hasResponse) {
            byte[] body = responseBody.toByteArray();
            if (responseCode == 200 &&
                    body.length != responseLength) {
                throw new IllegalStateException("Size of the response body doesn't match sendResponseHeaders()");
            }

            if (responseCode != 200 &&
                    responseLength != -1) {
                throw new IllegalStateException("Invalid length of body in a failed response");
            }
            closed = true;
        } else {
            throw new IllegalStateException("No invocation of sendResponseHeaders()");
        }
    }

    public void setRequestBody(byte[] body) {
        if (method == Method.GET ||
                method == Method.DELETE) {
            throw new UnsupportedOperationException();
        } else {
            requestBody = body;
        }
    }

    public static byte[] stringToBytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public InputStream getRequestBody() {
        if (method == Method.GET ||
                method == Method.DELETE) {
            throw new UnsupportedOperationException();
        } else {
            return new ByteArrayInputStream(requestBody);
        }
    }

    @Override
    public OutputStream getResponseBody() {
        return responseBody;
    }

    @Override
    public void sendResponseHeaders(int responseCode, long responseLength) {
        if (hasResponse) {
            throw new IllegalStateException("Cannot invoke twice sendResponseHeaders()");
        } else {
            hasResponse = true;
            this.responseCode = responseCode;
            this.responseLength = responseLength;
        }
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getResponseCode() {
        if (closed && hasResponse) {
            return responseCode;
        } else {
            throw new IllegalStateException("No previous call to close() or sendResponseHeaders()");
        }
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getProtocol() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getAttribute(String s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setAttribute(String s, Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setStreams(InputStream inputStream, OutputStream outputStream) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpPrincipal getPrincipal() {
        throw new UnsupportedOperationException();
    }

    public byte[] getResponseBodyAsBytes() throws IOException {
        close();
        if (getResponseCode() == 200) {
            return responseBody.toByteArray();
        } else {
            throw new IOException();
        }
    }

    public static byte[] executeGetAsBytes(HttpHandler handler,
                                           String uri) throws IOException, URISyntaxException {
        MockHttpExchange e = new MockHttpExchange(uri, MockHttpExchange.Method.GET);
        handler.handle(e);
        return e.getResponseBodyAsBytes();
    }

    public static byte[] executePostAsBytes(HttpHandler handler,
                                            String uri,
                                            byte[] body) throws IOException, URISyntaxException {
        MockHttpExchange e = new MockHttpExchange(uri, MockHttpExchange.Method.POST);
        e.setRequestBody(body);
        handler.handle(e);
        return e.getResponseBodyAsBytes();
    }

    private static MockHttpExchange createMultipartUpload(HttpHandler handler,
                                                          String uri,
                                                          String name,
                                                          byte[] part) throws IOException, URISyntaxException {
        MockHttpExchange e = new MockHttpExchange(uri, MockHttpExchange.Method.POST);

        String boundary = "123456789123456789123456789";  // Arbitrary value
        e.getRequestHeaders().set("Content-Type", "multipart/form-data; boundary=" + boundary);

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(stringToBytes("--" + boundary + "\r\n"));
        body.write(stringToBytes("Content-Type: application/octet-stream\r\n"));
        body.write(stringToBytes("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"nope\"\r\n"));
        body.write(stringToBytes("\r\n"));
        body.write(part);
        body.write(stringToBytes("\r\n--" + boundary + "--"));

        e.setRequestBody(body.toByteArray());
        handler.handle(e);
        return e;
    }

    public static byte[] executeMultipartUploadAsBytes(HttpHandler handler,
                                                       String uri,
                                                       String name,
                                                       byte[] part) throws IOException, URISyntaxException {
        return createMultipartUpload(handler, uri, name, part).getResponseBodyAsBytes();
    }

    public static int executeMultipartUploadAsStatusCode(HttpHandler handler,
                                                         String uri,
                                                         String name,
                                                         byte[] part) throws IOException, URISyntaxException {
        HttpExchange e = createMultipartUpload(handler, uri, name, part);
        e.close();
        return e.getResponseCode();
    }

    public static int executeGetAsStatusCode(HttpHandler handler,
                                             String uri) throws IOException, URISyntaxException {
        MockHttpExchange e = new MockHttpExchange(uri, MockHttpExchange.Method.GET);
        handler.handle(e);
        e.close();
        return e.getResponseCode();
    }

    public static String executeGetAsString(HttpHandler handler,
                                            String uri) throws IOException, URISyntaxException {
        return HttpToolbox.bytesToString(executeGetAsBytes(handler, uri));
    }

    public static JSONObject executeGetAsJsonObject(HttpHandler handler,
                                                    String uri) throws IOException, URISyntaxException {
        return HttpToolbox.parseJsonObject(executeGetAsString(handler, uri));
    }

    public static JSONObject executePostAsJsonObject(HttpHandler handler,
                                                     String uri,
                                                     JSONObject json) throws IOException, URISyntaxException {
        byte[] body = stringToBytes(json.toString());
        return HttpToolbox.parseJsonObject(executePostAsBytes(handler, uri, body));
    }

    public static int executePostAsStatusCode(HttpHandler handler,
                                              String uri,
                                              byte[] body) throws IOException, URISyntaxException {
        MockHttpExchange e = new MockHttpExchange(uri, MockHttpExchange.Method.POST);
        e.setRequestBody(body);
        handler.handle(e);
        e.close();
        return e.getResponseCode();
    }

    public static BufferedImage decodeRawImage(byte[] body) throws IOException {
        if (body.length < 8) {
            throw new IOException("Bad image");
        }

        int width = (((body[0] & 0xff) << 24) |
                ((body[1] & 0xff) << 16) |
                ((body[2] & 0xff) << 8) |
                (body[3] & 0xff));
        int height = (((body[4] & 0xff) << 24) |
                ((body[5] & 0xff) << 16) |
                ((body[6] & 0xff) << 8) |
                (body[7] & 0xff));

        if (width * height * 4 + 8 != body.length) {
            throw new IOException("Bad image");
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        int pos = 8;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++, pos += 4) {
                int red = body[pos] & 0xff;
                int green = body[pos + 1] & 0xff;
                int blue = body[pos + 2] & 0xff;
                image.setRGB(x, y, (red << 16) + (green << 8) + blue);
                if ((body[pos + 3] & 0xff) != 255) {
                    throw new IOException("Image with an alpha component");
                }
            }
        }

        return image;
    }

    public static boolean testPixel(BufferedImage image,
                                 int x,
                                 int y,
                                 int red,
                                 int green,
                                 int blue) {
        return ((red << 16) + (green << 8) + blue) == (image.getRGB(x, y) & 0x00ffffff);
    }

    public static int getRed(BufferedImage image,
                             int x,
                             int y) {
        return (image.getRGB(x, y) >> 16) & 0xff;
    }

    public static int getGreen(BufferedImage image,
                               int x,
                               int y) {
        return (image.getRGB(x, y) >> 8) & 0xff;
    }

    public static int getBlue(BufferedImage image,
                              int x,
                              int y) {
        return image.getRGB(x, y) & 0xff;
    }

    public static boolean isGrayscale(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int red = getRed(image, x, y);
                int green = getGreen(image, x, y);
                int blue = getBlue(image, x, y);
                if (!(red == green && red == blue)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean isSameImage(BufferedImage image1,
                                      BufferedImage image2) {
        if (image1.getWidth() != image2.getWidth() ||
            image1.getHeight() != image2.getHeight()) {
            for (int y = 0; y < image1.getHeight(); y++) {
                for (int x = 0; x < image1.getWidth(); x++) {
                    if (getRed(image1, x, y) != getRed(image2, x, y) ||
                            getBlue(image1, x, y) != getBlue(image2, x, y) ||
                            getGreen(image1, x, y) != getGreen(image2, x, y))
                        return false;
                }
            }
        }
        return true;
    }
}
