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
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;

import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;

/**
 * Class to ease the access to the content of a DICOM instance. This
 * is a wrapper around the dcm4che library.
 */
public class DicomImage {
    final private Attributes header;
    final private Attributes dataset;

    private DicomImage(DicomInputStream din) throws IOException {
        this.header = din.readFileMetaInformation();
        this.dataset = din.readDataset();
    }

    /**
     * Parse a DICOM instance that is provided as a Java stream.
     * @param stream The input stream.
     * @return The parsed DICOM instance.
     * @throws IOException If the input stream doesn't follow the DICOM standard.
     **/
    public static DicomImage createFromStream(InputStream stream) throws IOException {
        try (DicomInputStream din = new DicomInputStream(stream)) {
            return new DicomImage(din);
        }
    }

    /**
     * Parse a DICOM instance from a static resource from the class of the project.
     * @param resource The name of the resource of interest.
     * @return The parsed DICOM instance.
     * @throws IOException If the resource is missing or doesn't follow the DICOM standard.
     **/
    public static DicomImage createFromResource(String resource) throws IOException {
        return createFromStream(java.be.uclouvain.HttpToolbox.getResourceStream(resource));
    }

    /**
     * Parse a DICOM instance from a byte array.
     * @param content The byte array.
     * @return The parsed DICOM instance.
     * @throws IOException If the content of the byte array doesn't follow the DICOM standard.
     **/
    public static DicomImage createFromBytes(byte[] content) throws IOException {
        return createFromStream(new ByteArrayInputStream(content));
    }

    /**
     * Parse a DICOM instance from a file on the filesystem.
     * @param path Path to the file.
     * @return The parsed DICOM instance.
     * @throws IOException If the file is missing or doesn't follow the DICOM standard.
     **/
    public static DicomImage createFromFile(String path) throws IOException {
        return createFromStream(new FileInputStream(path));
    }

    /**
     * Get the meta-header of the DICOM instance.
     * @return The tags of the meta-header.
     */
    public Attributes getHeader() {
        return header;
    }

    /**
     * Get the dataset of the DICOM instance.
     * @return The tags of the dataset.
     */
    public Attributes getDataset() {
        return dataset;
    }

    /**
     * Check whether the DICOM instance is a color image or a grayscale image.
     * @return `true` iff. the image is grayscale.
     */
    public boolean isGrayscale() {
        return (dataset.getInt(Tag.SamplesPerPixel, 0) == 1 &&
                (dataset.getString(Tag.PhotometricInterpretation, "").equals("MONOCHROME1") ||
                 dataset.getString(Tag.PhotometricInterpretation, "").equals("MONOCHROME2")));
    }

    /**
     * Get the pixel data of a grayscale, uncompressed DICOM instance
     * as a matrix of real numbers.
     *
     * Warning: This method does *not* take rescale slope/intercept
     * into consideration.
     *
     * @return The matrix of the pixel values.
     */
    public RealMatrix getFloatPixelData() {
        if (!isGrayscale()) {
            throw new IllegalArgumentException("Not a grayscale image");
        }

        if (dataset.getInt(Tag.PixelRepresentation, -1) != 0) {
            throw new IllegalArgumentException("Signed pixels are not supported");
        }

        int height = dataset.getInt(Tag.Rows, 0);
        int width = dataset.getInt(Tag.Columns, 0);
        byte[] pixelData = dataset.getSafeBytes(Tag.PixelData);

        RealMatrix matrix = MatrixUtils.createRealMatrix(height, width);

        if (dataset.getInt(Tag.BitsAllocated, 0) == 8 &&
            dataset.getInt(Tag.BitsStored, 0) == 8 &&
            dataset.getInt(Tag.HighBit, 0) == 7) {
            if (pixelData.length != width * height) {
                throw new IllegalArgumentException();
            }

            int pos = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++, pos++) {
                    matrix.setEntry(y, x, pixelData[pos] & 0xff /* To unsigned byte */);
                }
            }
        } else if (dataset.getInt(Tag.BitsAllocated, 0) == 16) {
            if (pixelData.length != width * height * 2) {
                throw new IllegalArgumentException();
            }

            int pos = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++, pos += 2) {
                    int high = pixelData[pos + 1] & 0xff; /* To unsigned byte */
                    int low = pixelData[pos] & 0xff; /* To unsigned byte */
                    matrix.setEntry(y, x, (high << 8) + low);
                }
            }
        } else {
            throw new IllegalArgumentException("Unsupported grayscale image");
        }

        return matrix;
    }

    /**
     * Get the pixel data of a color, uncompressed DICOM instance as a
     * Java AWT image. This method will automatically handle the decoding
     * of palette images.
     * @return The color image.
     */
    public BufferedImage getColorPixelData() {
        if (isGrayscale() ||
            dataset.getInt(Tag.BitsStored, 0) != 8 ||
            dataset.getInt(Tag.BitsAllocated, 0) != 8 ||
            dataset.getInt(Tag.HighBit, 0) != 7 ||
            dataset.getInt(Tag.PixelRepresentation, -1) != 0) {
            throw new IllegalArgumentException("Not a color image");
        }

        int height = dataset.getInt(Tag.Rows, 0);
        int width = dataset.getInt(Tag.Columns, 0);
        byte[] pixelData = dataset.getSafeBytes(Tag.PixelData);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);

        if (dataset.getInt(Tag.SamplesPerPixel, 0) == 3 &&
            dataset.getString(Tag.PhotometricInterpretation, "").equals("RGB")) {
            if (pixelData.length != width * height * 3) {
                throw new IllegalArgumentException();
            }

            int pos = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++, pos += 3) {
                    int red = pixelData[pos] & 0xff;   // To unsigned byte
                    int green = pixelData[pos + 1] & 0xff; // To unsigned byte
                    int blue = pixelData[pos + 2] & 0xff;  // To unsigned byte
                    image.setRGB(x, y, (red << 16) + (green << 8) + blue);
                }
            }

            return image;
        } else if (dataset.getInt(Tag.SamplesPerPixel, 0) == 1 &&
                   dataset.getString(Tag.PhotometricInterpretation, "").equals("PALETTE COLOR")) {
            if (pixelData.length != width * height) {
                throw new IllegalArgumentException();
            }

            byte[] paletteRed, paletteGreen, paletteBlue;

            try {
                paletteRed = dataset.getBytes(Tag.RedPaletteColorLookupTableData);
                paletteGreen = dataset.getBytes(Tag.GreenPaletteColorLookupTableData);
                paletteBlue = dataset.getBytes(Tag.BluePaletteColorLookupTableData);
            } catch (IOException e) {
                throw new IllegalArgumentException("Bad palette");
            }

            if (paletteRed == null ||
                paletteGreen == null ||
                paletteBlue == null ||
                paletteRed.length != 512 ||
                paletteGreen.length != 512 ||
                paletteBlue.length != 512) {
                throw new IllegalArgumentException("Bad palette");
            }

            int pos = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++, pos++) {
                    int value = pixelData[pos] & 0xff;  // To unsigned byte
                    byte red = paletteRed[2 * value + 1];
                    byte green = paletteGreen[2 * value + 1];
                    byte blue = paletteBlue[2 * value + 1];
                    image.setRGB(x, y, (red << 16) + (green << 8) + blue);
                }
            }

            return image;
        } else {
            throw new IllegalArgumentException("Not a color image");
        }
    }

    /**
     * Send a grayscale image, encoded as a matrix of floating-point
     * numbers, as the response to the given REST API request.  The
     * image can be decoded using the JavaScript function
     * "BestRendering.LoadImageFromBackendIntoCanvas()".
     *
     * This method could have been added to `HttpToolbox`, but is kept
     * inside this class to avoid the exercises related to EEG to be
     * dependent upon the `org.apache.commons.math3` library.
     *
     * @param exchange The context of the REST call.
     * @param image The grayscale image that will be sent in the body.
     * @throws IOException If error during the HTTP exchange.
     */
    public static void sendImageToJavaScript(HttpExchange exchange,
                                             RealMatrix image) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();

        final int width = image.getColumnDimension();
        body.write((byte) (width >>> 24));
        body.write((byte) (width >>> 16));
        body.write((byte) (width >>> 8));
        body.write((byte) width);

        final int height = image.getRowDimension();
        body.write((byte) (height >>> 24));
        body.write((byte) (height >>> 16));
        body.write((byte) (height >>> 8));
        body.write((byte) height);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int value;

                double v = image.getEntry(y, x);
                if (v < 0) {
                    value = 0;
                } else if (v >= 255) {
                    value = 255;
                } else {
                    value = (int) Math.floor(v);
                }

                body.write(value & 0xff);
                body.write(value & 0xff);
                body.write(value & 0xff);
                body.write(255);  // alpha channel
            }
        }

        java.be.uclouvain.HttpToolbox.sendResponse(exchange, "application/octet-stream", body.toByteArray());
    }
}
