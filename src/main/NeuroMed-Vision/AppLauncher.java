package NeuroMed-Vision;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.be.uclouvain.DicomImage;
import java.be.uclouvain.HttpToolbox;
import java.io.IOException;
import java.net.InetSocketAddress;

public class AppLauncher implements HttpHandler {
    private DicomImage image;
    
    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/", new AppLauncher());
        server.setExecutor(null); // creates a default executor
        server.start();
    }

    public void handle(HttpExchange exchange) throws IOException {
        String uri = exchange.getRequestURI().normalize().toString();

        switch (uri) {
            case "/":
                HttpToolbox.sendRedirection(exchange, "index.html");
                break;
            case "/index.html":
                HttpToolbox.serveStaticResource(exchange, "text/html", "/index.html");
                break;
            case "/app.js":
                HttpToolbox.serveStaticResource(exchange, "application/javascript", "/app.js");
                break;
            case "/BestRendering.js":
                HttpToolbox.serveStaticResource(exchange, "application/javascript", "/BestRendering.js");
                break;
            case "/axios.min.js":
                HttpToolbox.serveStaticResource(exchange, "application/javascript", "/axios.min.js");
                break;
            case "/axios.min.map":
                HttpToolbox.serveStaticResource(exchange, "application/octet-stream", "/axios.min.map");
                break;
            case "/upload-source":
                if (HttpToolbox.protectPostRequest(exchange)) {
                    byte[] body = HttpToolbox.getMultipartFile(exchange, "data");
                    synchronized (this) {
                        image = DicomImage.createFromBytes(body);
                    }
                    HttpToolbox.sendResponse(exchange, "text/plain", "");
                }
                break;
            case "/get-range":
                if (HttpToolbox.protectGetRequest(exchange)) {
                    synchronized (this) {
                        if (image == null) {
                            HttpToolbox.sendNotFound(exchange);
                        } else {
                            App.getHounsfieldRange(exchange, image);
                        }
                    }
                }
                break;
            case "/apply-hounsfield":
                if (HttpToolbox.protectPostRequest(exchange)) {
                    synchronized (this) {
                        if (image == null) {
                            HttpToolbox.sendNotFound(exchange);
                        } else {
                            App.applyHounsfieldWindowing(exchange, image);
                        }
                    }
                }
                break;
            default:
                HttpToolbox.sendNotFound(exchange);
        }
    }
}
