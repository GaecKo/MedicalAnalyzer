package java;

import java.be.uclouvain.DicomImage;
import java.be.uclouvain.HttpToolbox;

import com.sun.net.httpserver.HttpExchange;
import org.apache.commons.math3.linear.MatrixUtils;
import org.dcm4che3.data.Tag;
import org.json.JSONObject;

import java.io.IOException;

import org.apache.commons.math3.linear.RealMatrix;

/**
 * Your task is to implement this class, by developing methods that
 * will be deployed as routes in the REST API of the Web application.
 *
 * Sample DICOM file to be used in the context of this project:
 * "ct-brain.dcm".
 **/
public class App {
    
    /**
     * This method must extract the matrix of pixel data from a
     * grayscale DICOM image, then use the rescale slope/intercept
     * DICOM tags to recover the actual Hounsfield floating-point
     * value of each pixel (cf. theoretical slides).
     *
     * @param image The DICOM image of interest.
     * @return The matrix of the actual Hounsfield values.
     */
    public static RealMatrix applyRescale(DicomImage image) {
        // basic matrix containing the pixels
        RealMatrix matrix = image.getFloatPixelData();

        // slope & intercept
        double slope = image.getDataset().getDouble(Tag.RescaleSlope, 1.0);
        double intercept = image.getDataset().getDouble(Tag.RescaleIntercept, 0.0);

        // rescale formula
        for (int y = 0; y < matrix.getRowDimension(); y++) {
            for (int x = 0; x < matrix.getColumnDimension(); x++) {
                double originalValue = matrix.getEntry(y, x);
                double rescaledValue = originalValue * slope + intercept;
                matrix.setEntry(y, x, rescaledValue);
            }
        }
        return matrix;
    }


    /**
     * This GET route in the REST API must compute the minimal and
     * maximal values in the pixel data, expressed in Hounsfield
     * units, of the DICOM image provided as an argument, then it must
     * send its response with a body that contains a JSON dictionary
     * formatted as follows:
     *
     * {
     *   "low" : -1000,
     *   "high" : 3000
     * }
     *
     * @param exchange The context of this REST API call.
     * @param image    The DICOM image of interest.
     * @throws IOException If some error occurred during the HTTP transfer.
     */
    public static void getHounsfieldRange(HttpExchange exchange, DicomImage image) throws IOException {
        // convert pixel values to Hounsfield units
        RealMatrix hounsfieldMatrix = applyRescale(image);

        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;

        // min / max Hounsfield units in the image
        for (int y = 0; y < hounsfieldMatrix.getRowDimension(); y++) {
            for (int x = 0; x < hounsfieldMatrix.getColumnDimension(); x++) {
                double value = hounsfieldMatrix.getEntry(y, x);
                if (value < min) min = value;
                if (value > max) max = value;
            }
        }

        JSONObject response = new JSONObject();
        response.put("low", min);
        response.put("high", max);
        HttpToolbox.sendResponse(exchange, "application/json", response.toString());
    }


    /**
     * This POST route in the REST API must apply Hounsfield windowing
     * to the provided DICOM image. The resulting image must be sent
     * to the JavaScript front-end using
     * "DicomImage.sendImageToJavaScript()". The body of the POST
     * request shall contain a JSON dictionary formatted as follows:
     *
     * {
     *   "low" : 200,
     *   "high" : 1000
     * }
     *
     * HTTP status 400 must be sent if the body of the request doesn't
     * contain a valid JSON dictionary. If "high <= low", the returned
     * image must be entirely black.
     *
     * @param exchange The context of this REST API call.
     * @param image    The DICOM image of interest.
     * @throws IOException If some error occurred during the HTTP transfer.
     */
    public static void applyHounsfieldWindowing(HttpExchange exchange, DicomImage image) throws IOException {
        try {
            // request analyse
            JSONObject requestBody = HttpToolbox.getRequestBodyAsJsonObject(exchange);
            double low = requestBody.getDouble("low");
            double high = requestBody.getDouble("high");

            int width = image.getDataset().getInt(Tag.Columns, 0);
            int height = image.getDataset().getInt(Tag.Rows, 0);

            // black image in case of bad request
            if (high <= low) {
                RealMatrix blackMatrix = MatrixUtils.createRealMatrix(height, width);
                DicomImage.sendImageToJavaScript(exchange, blackMatrix);
                return;
            }

            // apply windowing based on Hounsfield unit range
            RealMatrix hounsfieldMatrix = applyRescale(image);
            RealMatrix windowedMatrix = MatrixUtils.createRealMatrix(height, width);

            for (int y = 0; y < hounsfieldMatrix.getRowDimension(); y++) {
                for (int x = 0; x < hounsfieldMatrix.getColumnDimension(); x++) {
                    double value = hounsfieldMatrix.getEntry(y, x);
                    // we normalize
                    double normalizedValue = ((value - low) / (high - low)) * 255;
                    normalizedValue = Math.min(Math.max(normalizedValue, 0), 255);

                    windowedMatrix.setEntry(y, x, normalizedValue);
                }
            }

            DicomImage.sendImageToJavaScript(exchange, windowedMatrix);
        } catch (Exception e) {
            // handler
            HttpToolbox.sendBadRequest(exchange);
        }
    }

}
