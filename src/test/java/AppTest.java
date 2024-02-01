import org.javagrader.Allow;
import org.javagrader.Grade;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import be.uclouvain.DicomImage;
import be.uclouvain.HttpToolbox;
import be.uclouvain.MockHttpExchange;
import be.uclouvain.MockMatrixTools;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URISyntaxException;

import com.sun.net.httpserver.HttpHandler;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.json.JSONObject;

import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;

import javax.imageio.ImageIO;

@Grade
@Allow("all")  // Allows the use of "java.lang.Thread" and "java.lang.ClassLoader" for dcm4che/HttpToolbox
public class AppTest {
    @Test
    @Grade(value = 1)
    public void testRescale() throws IOException {
        RealMatrix a = MatrixUtils.createRealMatrix(1, 2);
        a.setEntry(0, 0, 0);
        a.setEntry(0, 1, 2000);

        {
            Attributes d = new Attributes();
            d.setString(Tag.RescaleIntercept, VR.DS, "-1000");
            d.setString(Tag.RescaleSlope, VR.DS, "2");

            DicomImage dicom = DicomImage.createFromBytes(MockMatrixTools.createGrayscaleDicom16bpp(a, d));

            RealMatrix b = dicom.getFloatPixelData();
            assertEquals(0, b.getEntry(0, 0), 0.00001);
            assertEquals(2000, b.getEntry(0, 1), 0.00001);

            RealMatrix c = App.applyRescale(dicom);
            assertEquals(-1000, c.getEntry(0, 0), 0.00001);
            assertEquals(3000, c.getEntry(0, 1), 0.00001);
        }

        {
            Attributes d = new Attributes();
            d.setString(Tag.RescaleSlope, VR.DS, "3");

            DicomImage dicom = DicomImage.createFromBytes(MockMatrixTools.createGrayscaleDicom16bpp(a, d));

            RealMatrix b = dicom.getFloatPixelData();
            assertEquals(0, b.getEntry(0, 0), 0.00001);
            assertEquals(2000, b.getEntry(0, 1), 0.00001);

            RealMatrix c = App.applyRescale(dicom);
            assertEquals(0, c.getEntry(0, 0), 0.00001);
            assertEquals(6000, c.getEntry(0, 1), 0.00001);
        }

        {
            Attributes d = new Attributes();
            d.setString(Tag.RescaleIntercept, VR.DS, "-1500");

            DicomImage dicom = DicomImage.createFromBytes(MockMatrixTools.createGrayscaleDicom16bpp(a, d));

            RealMatrix b = dicom.getFloatPixelData();
            assertEquals(0, b.getEntry(0, 0), 0.00001);
            assertEquals(2000, b.getEntry(0, 1), 0.00001);

            RealMatrix c = App.applyRescale(dicom);
            assertEquals(-1500, c.getEntry(0, 0), 0.00001);
            assertEquals(500, c.getEntry(0, 1), 0.00001);
        }

        {
            DicomImage dicom = DicomImage.createFromBytes(MockMatrixTools.createGrayscaleDicom16bpp(a));

            RealMatrix b = dicom.getFloatPixelData();
            assertEquals(0, b.getEntry(0, 0), 0.00001);
            assertEquals(2000, b.getEntry(0, 1), 0.00001);

            RealMatrix c = App.applyRescale(dicom);
            assertEquals(0, c.getEntry(0, 0), 0.00001);
            assertEquals(2000, c.getEntry(0, 1), 0.00001);
        }
    }

    private static JSONObject runGetRange(Attributes d) throws IOException, URISyntaxException {
        RealMatrix a = MatrixUtils.createRealMatrix(1, 2);
        a.setEntry(0, 0, 0);
        a.setEntry(0, 1, 2000);

        byte[] dicom = MockMatrixTools.createGrayscaleDicom16bpp(a, d);

        HttpHandler handler = new AppLauncher();
        assertEquals(404, MockHttpExchange.executeGetAsStatusCode(handler, "/get-range"));
        assertEquals(200, MockHttpExchange.executeMultipartUploadAsStatusCode(handler, "/upload-source", "data", dicom));
        JSONObject obj = MockHttpExchange.executeGetAsJsonObject(handler, "/get-range");
        assertEquals(2, obj.length());
        return obj;
    }

    @Test
    @Grade(value = 1)
    public void testGetRange() throws IOException, URISyntaxException {
        {
            JSONObject obj = runGetRange(new Attributes());
            assertEquals(obj.getDouble("low"), 0, 0.00001);
            assertEquals(obj.getDouble("high"), 2000, 0.00001);
        }

        {
            Attributes d = new Attributes();
            d.setString(Tag.RescaleSlope, VR.DS, "1.5");

            JSONObject obj = runGetRange(d);
            assertEquals(obj.getDouble("low"), 0, 0.00001);
            assertEquals(obj.getDouble("high"), 3000, 0.00001);
        }

        {
            Attributes d = new Attributes();
            d.setString(Tag.RescaleIntercept, VR.DS, "-1200");

            JSONObject obj = runGetRange(d);
            assertEquals(obj.getDouble("low"), -1200, 0.00001);
            assertEquals(obj.getDouble("high"), 800, 0.00001);
        }

        {
            Attributes d = new Attributes();
            d.setString(Tag.RescaleIntercept, VR.DS, "-1500");
            d.setString(Tag.RescaleSlope, VR.DS, "2");

            JSONObject obj = runGetRange(d);
            assertEquals(obj.getDouble("low"), -1500, 0.00001);
            assertEquals(obj.getDouble("high"), 2500, 0.00001);
        }
    }

    private static int runApplyHounsfield(float value,
                                          float low,
                                          float high,
                                          Attributes d) throws IOException, URISyntaxException {
        RealMatrix a = MatrixUtils.createRealMatrix(1, 1);
        a.setEntry(0, 0, value);

        byte[] dicom = MockMatrixTools.createGrayscaleDicom16bpp(a, d);

        HttpHandler handler = new AppLauncher();
        assertEquals(404, MockHttpExchange.executePostAsStatusCode(handler, "/apply-hounsfield", new byte[0]));
        assertEquals(200, MockHttpExchange.executeMultipartUploadAsStatusCode(handler, "/upload-source", "data", dicom));

        JSONObject request = new JSONObject();
        request.put("low", low);
        request.put("high", high);

        BufferedImage image = MockHttpExchange.decodeRawImage(MockHttpExchange.executePostAsBytes(handler, "/apply-hounsfield", MockHttpExchange.stringToBytes(request.toString())));
        assertEquals(1, image.getHeight());
        assertEquals(1, image.getWidth());
        assertTrue(MockHttpExchange.isGrayscale(image));
        return MockHttpExchange.getBlue(image, 0, 0);
    }

    @Test
    @Grade(value = 1)
    public void testApplyHounsfield() throws IOException, URISyntaxException {
        {
            assertEquals(100, runApplyHounsfield(100, 0, 255, new Attributes()));
            assertEquals(255, runApplyHounsfield(100, 0, 99, new Attributes()));
            assertEquals(255, runApplyHounsfield(100, 0, 100, new Attributes()));
            assertEquals(252, runApplyHounsfield(100, 0, 101, new Attributes()));
            assertEquals(1, runApplyHounsfield(100, 99, 255, new Attributes()));
            assertEquals(0, runApplyHounsfield(100, 100, 255, new Attributes()));
            assertEquals(0, runApplyHounsfield(100, 101, 255, new Attributes()));
        }

        {
            Attributes d = new Attributes();
            d.setString(Tag.RescaleIntercept, VR.DS, "-99");
            assertEquals(1, runApplyHounsfield(100, 0, 255, d));
        }

        {
            Attributes d = new Attributes();
            d.setString(Tag.RescaleIntercept, VR.DS, "50");
            assertEquals(150, runApplyHounsfield(100, 0, 255, d));
        }

        {
            Attributes d = new Attributes();
            d.setString(Tag.RescaleSlope, VR.DS, "2");
            assertEquals(200, runApplyHounsfield(100, 0, 255, d));
        }

        {
            Attributes d = new Attributes();
            d.setString(Tag.RescaleIntercept, VR.DS, "-800");
            d.setString(Tag.RescaleSlope, VR.DS, "10");
            assertEquals(200, runApplyHounsfield(100, 0, 255, d));
        }
    }

    @Test
    @Grade(value = 1)
    public void testInvalidPayload() throws IOException, URISyntaxException {
        RealMatrix a = MatrixUtils.createRealMatrix(1, 1);
        a.setEntry(0, 0, 100);

        byte[] dicom = MockMatrixTools.createGrayscaleDicom16bpp(a);

        HttpHandler handler = new AppLauncher();
        assertEquals(200, MockHttpExchange.executeMultipartUploadAsStatusCode(handler, "/upload-source", "data", dicom));

        assertEquals(400, MockHttpExchange.executePostAsStatusCode(handler, "/apply-hounsfield", MockHttpExchange.stringToBytes("nope")));

        {
            JSONObject request = new JSONObject();
            request.put("low", 0);
            assertEquals(400, MockHttpExchange.executePostAsStatusCode(handler, "/apply-hounsfield", MockHttpExchange.stringToBytes(request.toString())));
        }

        {
            JSONObject request = new JSONObject();
            request.put("high", 255);
            assertEquals(400, MockHttpExchange.executePostAsStatusCode(handler, "/apply-hounsfield", MockHttpExchange.stringToBytes(request.toString())));
        }

        {
            JSONObject request = new JSONObject();
            request.put("low", 0);
            request.put("high", 255);
            assertEquals(200, MockHttpExchange.executePostAsStatusCode(handler, "/apply-hounsfield", MockHttpExchange.stringToBytes(request.toString())));
        }
    }

    private static BufferedImage runApplyHounsfield(String resource,
                                                    double low,
                                                    double high) throws IOException, URISyntaxException {
        HttpHandler handler = new AppLauncher();
        assertEquals(200, MockHttpExchange.executeMultipartUploadAsStatusCode(handler, "/upload-source", "data", HttpToolbox.readResource(resource)));
        JSONObject request = new JSONObject();
        request.put("low", low);
        request.put("high", high);
        BufferedImage image = MockHttpExchange.decodeRawImage(MockHttpExchange.executePostAsBytes(handler, "/apply-hounsfield", MockHttpExchange.stringToBytes(request.toString())));
        assertTrue(MockHttpExchange.isGrayscale(image));
        return image;
    }

    private void assertConstantImage(BufferedImage image,
                                     int value) {
        assertTrue(MockHttpExchange.isGrayscale(image));
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                assertEquals(value, MockHttpExchange.getBlue(image, x, y));
            }
        }
    }


    @Test
    @Grade(value = 1)
    public void testBrain() throws IOException, URISyntaxException {
        BufferedImage image = runApplyHounsfield("/ct-brain.dcm", -2000, -1000);
        assertEquals(512, image.getWidth());
        assertEquals(512, image.getHeight());
        assertConstantImage(image, 255);

        image = runApplyHounsfield("/ct-brain.dcm", 3000, 4000);
        assertConstantImage(image, 0);

        image = runApplyHounsfield("/ct-brain.dcm", 1000, -1000);
        assertEquals(512, image.getWidth());
        assertEquals(512, image.getHeight());
        assertConstantImage(image, 0);

        image = runApplyHounsfield("/ct-brain.dcm", 0, 255);
        //ImageIO.write(image, "png", new File("/tmp/ct-brain-a.png"));
        assertTrue(MockHttpExchange.isSameImage(image, ImageIO.read(HttpToolbox.getResourceStream("/ct-brain-a.png"))));

        image = runApplyHounsfield("/ct-brain.dcm", -700, 1300);
        //ImageIO.write(image, "png", new File("/tmp/ct-brain-b.png"));
        assertTrue(MockHttpExchange.isSameImage(image, ImageIO.read(HttpToolbox.getResourceStream("/ct-brain-b.png"))));

        image = runApplyHounsfield("/ct-brain.dcm", -1000, 200);
        //ImageIO.write(image, "png", new File("/tmp/ct-brain-c.png"));
        assertTrue(MockHttpExchange.isSameImage(image, ImageIO.read(HttpToolbox.getResourceStream("/ct-brain-c.png"))));
    }
}
