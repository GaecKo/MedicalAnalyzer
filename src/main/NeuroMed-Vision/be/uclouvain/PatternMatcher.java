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

/**
 * This internal class implements the Knuth–Morris–Pratt algorithm for pattern matching in byte streams.
 * https://en.wikipedia.org/wiki/Knuth%E2%80%93Morris%E2%80%93Pratt_algorithm
 */
class PatternMatcher {
    private final String pattern;
    private final int[] t;

    /**
     * Create a pattern matcher for the given pattern.
     * @param pattern The pattern of interest.
     */
    public PatternMatcher(String pattern) {
        this.pattern = pattern;

        // Table-building algorithm
        t = new int[pattern.length()];
        t[0] = -1;

        int pos = 1;
        int cnd = 0;
        while (pos < pattern.length()) {
            if (pattern.charAt(pos) == pattern.charAt(cnd)) {
                t[pos] = t[cnd];
            } else {
                t[pos] = cnd;
                while (cnd >= 0 && pattern.charAt(pos) != pattern.charAt(cnd)) {
                    cnd = t[cnd];
                }
            }
            pos++;
            cnd++;
        }
    }

    /**
     * Find the ending position of the next occurrence of the pattern.
     * @param data Data to be searched in.
     * @param startPos Offset in the data where to start the matching.
     * @return The position after the last character of the pattern.
     */
    public Integer findEnding(byte[] data,
                              int startPos) {
        Integer beginning = findBeginning(data, startPos);
        if (beginning == null) {
            return null;
        } else {
            return beginning + pattern.length();
        }
    }

    /**
     * Find the beginning position of the next occurrence of the pattern.
     * @param data Data to be searched in.
     * @param startPos Offset in the data where to start the matching.
     * @return The position of the first character of the pattern.
     */
    public Integer findBeginning(byte[] data,
                                 int startPos) {
        int j = startPos;
        int k = 0;

        while (j < data.length) {
            if (pattern.charAt(k) == data[j]) {
                j++;
                k++;
                if (k == pattern.length()) {
                    return j - k;
                }
            } else {
                k = t[k];
                if (k < 0) {
                    j++;
                    k++;
                }
            }
        }

        return null;  // Pattern not found
    }

    /**
     * Get the length of the pattern.
     * @return The length of the pattern.
     */
    public int getPatternLength() {
        return pattern.length();
    }
}
