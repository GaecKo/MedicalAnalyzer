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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * This internal class implements a reader for multipart requests (for use in HttpToolbox).
 */
class MultipartReader {
    private final byte[] body;
    private final java.be.uclouvain.PatternMatcher boundaryMatcher;
    private final java.be.uclouvain.PatternMatcher headerMatcher = new java.be.uclouvain.PatternMatcher("\r\n\r\n");
    private Map<String, String> currentHeaders;
    private byte[] currentPart;
    private int pos;

    /**
     * Construct the reader.
     * @param body The body of the POST request.
     * @param boundary The boundary of the parts, as extracted from the "Content-Type" HTTP header.
     * @throws IOException If unable to parse the multipart request.
     */
    public MultipartReader(byte[] body,
                           String boundary) throws IOException {
        this.body = body;
        this.boundaryMatcher = new java.be.uclouvain.PatternMatcher("--" + boundary);

        Integer first = boundaryMatcher.findEnding(body, 0);
        if (first == null) {
            throw new IOException();
        } else {
            pos = first;
        }
    }

    /**
     * Read the next part in the multipart request.
     * @return `true` iff. another part was found.
     * @throws IOException If unable to parse the multipart request.
     */
    public boolean readNext() throws IOException {
        if (pos + 2 > body.length) {
            throw new IOException();
        } else if (body[pos] == '-' &&
                body[pos + 1] == '-') {
            if ((pos + 2 == body.length) ||
                    // This second flavor is used in Orthanc DICOMweb WADO-RS
                    (pos + 4 == body.length && body[pos + 2] == '\r' && body[pos + 3] == '\n')) {
                return false;
            } else {
                throw new IOException();
            }
        } else if (body[pos] != '\r' ||
                body[pos + 1] != '\n') {
            throw new IOException();
        } else {
            pos += 2;
            Integer endHeaders = headerMatcher.findBeginning(body, pos);
            if (endHeaders == null) {
                throw new IOException();
            }

            currentHeaders = new HashMap<>();

            String headers = new String(Arrays.copyOfRange(body, pos, endHeaders), StandardCharsets.US_ASCII);
            String[] lines = headers.split("\r\n");
            for (String line : lines) {
                int separator = line.indexOf(':');
                if (separator == -1) {
                    currentHeaders.put(line.trim(), "");
                } else {
                    currentHeaders.put(line.substring(0, separator).trim(),
                            line.substring(separator + 1).trim());
                }
            }

            pos = endHeaders + headerMatcher.getPatternLength();
            Integer nextBoundary = boundaryMatcher.findBeginning(body, pos);
            if (nextBoundary == null) {
                throw new IOException();
            } else {
                if (pos > nextBoundary - 2) {
                    throw new IOException();
                } else {
                    currentPart = Arrays.copyOfRange(body, pos, nextBoundary - 2);
                    pos = nextBoundary + boundaryMatcher.getPatternLength();
                    return true;
                }
            }
        }
    }

    /**
     * Get the content of the current part.
     * @return The content as an array of bytes.
     */
    public byte[] getCurrentPart() {
        if (currentPart == null) {
            throw new IllegalStateException();
        } else {
            return currentPart;
        }
    }

    /**
     * Get the HTTP headers of the current part.
     * @return The dictionary of the HTTP headers.
     */
    public Map<String, String> getCurrentHeaders() {
        if (currentHeaders == null) {
            throw new IllegalStateException();
        } else {
            return currentHeaders;
        }
    }

    /**
     * Parse the arguments of one HTTP header.
     * @param header The HTTP header of interest.
     * @return The dictionary of the arguments of the HTTP header.
     */
    static public Map<String, String> parseHeader(String header) {
        Map<String, String> result = new HashMap<>();

        String[] tokens = header.split(";");

        for (String token : tokens) {
            int separator = token.indexOf('=');
            if (separator == -1) {
                result.put(token.trim(), "");
            } else {
                String key = token.substring(0, separator).trim();
                String value = token.substring(separator + 1).trim();
                if (value.length() >= 2 &&
                        value.charAt(0) == '"' &&
                        value.charAt(value.length() - 1) == '"') {
                    result.put(key, value.substring(1, value.length() - 1));
                } else {
                    result.put(key, value);
                }
            }
        }

        return result;
    }
}
