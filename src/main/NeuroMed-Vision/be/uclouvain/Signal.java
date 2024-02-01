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
 * This class encodes a vector of complex numbers, that can be used as
 * inputs or outputs of signal processing algorithms.
 *
 * It also counts the number of read operations applied to all the
 * signals in the application, which is used for unit testing the
 * complexity of algorithms (in production, this mechanism should be
 * removed because of the presence of a mutex).
 */
public class Signal {
    private final java.be.uclouvain.ComplexNumber[] values;
    private static int countReads = 0;

    /**
     * Create a signal of given length.
     * @param length The number of complex numbers in the signal.
     */
    public Signal(int length) {
        values = new java.be.uclouvain.ComplexNumber[length];
        for (int i = 0; i < length; i++) {
            values[i] = new java.be.uclouvain.ComplexNumber();
        }
    }

    /**
     * Get the length of the signal.
     * @return The number of complex numbers in the signal.
     */
    public int getLength() {
        return values.length;
    }

    /**
     * Set one of the items in the signal.
     * @param index The index of the item.
     * @param value The complex number to be stored in the item.
     */
    public void setValue(int index,
                         java.be.uclouvain.ComplexNumber value) {
        values[index] = value;
    }

    /**
     * Set one of the items in the signal as a real number.
     * @param index The index of the item.
     * @param r The real number to be stored in the item.
     */
    public void setValue(int index,
                         double r) {
        setValue(index, new java.be.uclouvain.ComplexNumber(r));
    }

    /**
     * Set one of the items in the signal as a complex number.
     * @param index The index of the item.
     * @param real The real component of the complex number to be
     * stored in the item.
     * @param imag The imaginary component of the complex number to be
     * stored in the item.
     */
    public void setValue(int index,
                         double real,
                         double imag) {
        setValue(index, new java.be.uclouvain.ComplexNumber(real, imag));
    }

    /**
     * Get one complex number stored in the signal.
     * @param index The index of the item of interest.
     * @return The complex number.
     */
    public synchronized java.be.uclouvain.ComplexNumber getValue(int index) {
        countReads++;
        return values[index];
    }

    /**
     * For unit testing, reset the counter of read operations.
     */
    public static synchronized void resetCounters() {
        countReads = 0;
    }

    /**
     * For unit testing, get the number of read accesses that have
     * been done on all the "Signal" objects in the application.
     * @return The number of read accesses.
     */
    public static synchronized int getCountReads() {
        return countReads;
    }
}
