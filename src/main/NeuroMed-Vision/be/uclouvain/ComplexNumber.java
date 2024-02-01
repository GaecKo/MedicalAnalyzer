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
 * Simple class to encode a complex number.
 */
public class ComplexNumber {
    private final double real;
    private final double imag;

    /**
     * Construct the zero complex number, i.e. "0 + i*0".
     */
    public ComplexNumber() {
        this.real = 0;
        this.imag = 0;
    }

    /**
     * Construct a real complex number, i.e. "real + i*0".
     * @param real The real component.
     */
    public ComplexNumber(double real) {
        this.real = real;
        this.imag = 0;
    }

    /**
     * Construct the complex number "real + i*imag".
     * @param real The real component.
     * @param imag The imaginary component.
     */
    public ComplexNumber(double real,
                         double imag) {
        this.real = real;
        this.imag = imag;
    }

    /**
     * Get the real component of the complex number.
     * @return The real component.
     */
    public double getReal() {
        return real;
    }

    /**
     * Get the imaginary component of the complex number.
     * @return The imaginary component.
     */
    public double getImag() {
        return imag;
    }

    /**
     * Get the modulus of the complex number (aka. absolute value).
     * @return The modulus.
     */
    public double getModulus() {
        return Math.sqrt(real * real + imag * imag);
    }
}
