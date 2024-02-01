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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.util.UIDUtils;

public class MockMatrixTools {
    private static int readNextInteger(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null) {
            throw new IOException();
        } else {
            return Integer.parseInt(line);
        }
    }

    private static double readNextDouble(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null) {
            throw new IOException();
        } else {
            return Double.parseDouble(line);
        }
    }

    public static RealMatrix readMatrixFromPython(InputStream stream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));

        int width = readNextInteger(reader);
        int height = readNextInteger(reader);
        RealMatrix matrix = MatrixUtils.createRealMatrix(height, width);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                matrix.setEntry(y, x, readNextDouble(reader));
            }
        }

        return matrix;
    }

    public static boolean compareMatrices(RealMatrix a,
                                          RealMatrix b) {
        if (a.getRowDimension() != b.getRowDimension() ||
                a.getColumnDimension() != b.getColumnDimension()) {
            return false;
        } else {
            for (int y = 0; y < a.getRowDimension(); y++) {
                for (int x = 0; x < a.getColumnDimension(); x++) {
                    if (Math.abs(a.getEntry(y, x) - b.getEntry(y, x)) >= 0.00001) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    public static byte[] createGrayscaleDicom16bpp(RealMatrix pixelData,
                                             Attributes dataset) throws IOException {
        dataset.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");
        dataset.setInt(Tag.SamplesPerPixel, VR.US, 1);
        dataset.setInt(Tag.Rows, VR.US, pixelData.getRowDimension());
        dataset.setInt(Tag.Columns, VR.US, pixelData.getColumnDimension());
        dataset.setInt(Tag.BitsAllocated, VR.US, 16);
        dataset.setInt(Tag.BitsStored, VR.US, 16);
        dataset.setInt(Tag.HighBit, VR.US, 15);
        dataset.setInt(Tag.PixelRepresentation, VR.US, 0);
        dataset.setString(Tag.SOPClassUID, VR.UI, UID.ComputedRadiographyImageStorage);
        dataset.setString(Tag.StudyInstanceUID, VR.UI, UIDUtils.createUID());
        dataset.setString(Tag.SeriesInstanceUID, VR.UI, UIDUtils.createUID());
        dataset.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());

        byte[] pixelDataBytes = new byte[pixelData.getColumnDimension() * pixelData.getRowDimension() * 2];
        int pos = 0;
        for (int y = 0; y < pixelData.getRowDimension(); y++) {
            for (int x = 0; x < pixelData.getColumnDimension(); x++, pos += 2) {
                int v = (int) Math.round(pixelData.getEntry(y, x));
                if (v < 0) {
                    v = 0;
                } else if (v >= 65535) {
                    v = 65535;
                }
                pixelDataBytes[pos] = (byte) (v & 0xff);
                pixelDataBytes[pos + 1] = (byte) ((v >> 8) & 0xff);
            }
        }
        dataset.setBytes(Tag.PixelData, VR.OB, pixelDataBytes);

        Attributes metaheader = new Attributes();
        metaheader.setString(Tag.TransferSyntaxUID, VR.UI, UID.ExplicitVRLittleEndian);

        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            DicomOutputStream dos = new DicomOutputStream(stream, metaheader.getString(Tag.TransferSyntaxUID));
            dos.writeDataset(metaheader, dataset);
            return stream.toByteArray();
        }
    }

    public static byte[] createGrayscaleDicom16bpp(RealMatrix pixelData) throws IOException {
        return createGrayscaleDicom16bpp(pixelData, new Attributes());
    }
}
