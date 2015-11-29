package communication;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.Set;

import java.util.Timer;
import java.util.TimerTask;

import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.highgui.Highgui;
import org.opencv.highgui.VideoCapture;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Iterator;

import loader.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.imgproc.Imgproc;
import processing.ObjectFinder;
import processing.ObjectsSetFinder;

@ServerEndpoint(value = "/camera")
public class CameraEndPoint {

    /* The OpenCVLoader class is included in a .jar file that resides in the Apache server.
     This helps to avoid restarting the server everytime a change is done in this program.    
     */
    private static OpenCVLoader loader = new OpenCVLoader();

    private static VideoCapture capture = null;
    private static final AtomicInteger connectionIds = new AtomicInteger(0);
    private static final Set<CameraEndPoint> connections = new CopyOnWriteArraySet<>();

    private Session session;
    private Timer timer = null;
    private TimerTask task = null;

    private static String getBase64String(Mat image) {

        String stringData = null;
        MatOfByte matOfByte = new MatOfByte();
        Highgui.imencode(".png", image, matOfByte);
        byte[] byteArray = matOfByte.toArray();
        InputStream in = new ByteArrayInputStream(byteArray);
        BufferedImage bufImage = null;

        try {

            bufImage = ImageIO.read(in);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            Base64.Encoder encoder = Base64.getEncoder();
            OutputStream b64 = encoder.wrap(os);
            ImageIO.write(bufImage, "png", b64);
            String base64 = os.toString("UTF-8");
            stringData = "data:image/png;base64," + base64;

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return stringData;

    }

    private static TimerTask createReadingFrameTask() {
        return new TimerTask() {
            @Override
            public void run() {

                Calendar cal = Calendar.getInstance();
                SimpleDateFormat sdf = new SimpleDateFormat("EEEE dd MMMM yyyy HH:mm:ss.SSS");
                
                Mat greenObject = Highgui.imread("C:/Users/Gonzalo/Documents/NetBeansProjects/WebsocketHome/web/images/green.png");                
                Mat orangeObject = Highgui.imread("C:/Users/Gonzalo/Documents/NetBeansProjects/WebsocketHome/web/images/orange.jpg");                                

                ArrayList<Integer> greenThresholds = new ArrayList<>();
                greenThresholds.add(0);
                greenThresholds.add(180);
                greenThresholds.add(65);
                greenThresholds.add(256);
                greenThresholds.add(100);
                greenThresholds.add(256);
                
                ArrayList<Integer> orangeThresholds = new ArrayList<>();
                orangeThresholds.add(0);
                orangeThresholds.add(180);
                orangeThresholds.add(80);
                orangeThresholds.add(256);
                orangeThresholds.add(100);
                orangeThresholds.add(256);

                ObjectFinder greenFinder = new ObjectFinder(greenObject, greenThresholds, "Green");
                ObjectFinder orangeFinder = new ObjectFinder(orangeObject, orangeThresholds, "Orange");
                
                ArrayList<ObjectFinder> findersList = new ArrayList<>();
                findersList.add(greenFinder);
                findersList.add(orangeFinder);
                ObjectsSetFinder setFinder = new ObjectsSetFinder(findersList);

//                System.out.println("******** Trying to read new frame ********");
                if (capture != null && capture.isOpened()) {

                    Mat image = new Mat();
                    
                    Mat RGBFrame = new Mat();
                    capture.read(image);
                    
                    Imgproc.cvtColor(image, RGBFrame, Imgproc.COLOR_BGR2RGB);                    

                    if (!image.empty()) {

                        Mat output = image.clone();

//                        greenFinder.process(image, output);                        
                        setFinder.process(image, output);
                        
                        HashMap<String, String> results = new HashMap<>();
                        results.put("input", getBase64String(image));
//                        results.put("backprojection", getBase64String(greenFinder.getBackprojectionImage()));
//                        results.put("thresholdedBackprojection", getBase64String(greenFinder.getThresholdedBackprojection()));
//                        results.put("contours", getBase64String(greenFinder.getContoursImage()));
//                        results.put("morphological", getBase64String(greenFinder.getMorphologicalImage()));
                        results.put("output", getBase64String(output));

                        String timeStamp = sdf.format(cal.getTime());
                        HashMap<String, Boolean> searchResults = setFinder.getResults();
                        Set<String> keySet = searchResults.keySet();
                        Iterator<String> iterator = keySet.iterator();
                        while (iterator.hasNext()) {
                            String objectName = iterator.next();
                            boolean found = searchResults.get(objectName);
                            if (found) {
                                results.put("timeStamp", "<b>"  + objectName + "</b> found at " + timeStamp);
                            }                            
                        }

                        
                        

                        Gson gson = new Gson();
                        String jsonResponse = gson.toJson(results);

                        broadcast(jsonResponse);

                    } else {
                        System.out.println(" --(!) No captured frame -- Break!");
                    }

                } else {
                    System.out.println("The camera is CLOSED!");
                }

            }
        };
    }

    public CameraEndPoint() {

    }

    @OnOpen
    public void start(Session session) {

        if (capture == null) {
            capture = new VideoCapture(0);
        }

        long framerate = 50;
        long delay = (long) 1000.0 / framerate;

        if (timer == null) {
            timer = new Timer();
        }

        // a new task is created for each client
        task = createReadingFrameTask();
        timer.schedule(task, 0, delay);

        this.session = session;
        connections.add(this);

    }

    @OnClose
    public void end() {

        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
        }

        connections.remove(this);

        if (connections.isEmpty()) {
            if (capture != null) {
                capture.release();
                capture = null;
            }
        }

    }

    @OnMessage
    public void incoming(String message) {

        System.out.println("message:");
        System.out.println(message);

        // Never trust the client
//        String filteredMessage = String.format("%s: %s", nickname, HTMLFilter.filter(message.toString()));
//        String filteredMessage = String.format("%s: %s", nickname, message.toString() + " XXXXXXXXXXX YYYYYYYYYYYY");
//        broadcast(filteredMessage);
    }

    @OnError
    public void onError(Throwable t) throws Throwable {
        System.out.println("Chat Error: " + t.toString());
    }

    private static void broadcast(String msg) {
        for (CameraEndPoint client : connections) {
            try {
                synchronized (client) {

                    client.session.getBasicRemote().sendText(msg);
                }
            } catch (IOException e) {
                System.out.println("Chat Error: Failed to send message to client");
                e.printStackTrace();
                connections.remove(client);
                try {
                    client.session.close();
                } catch (IOException e1) {
                    // Ignore
                }
//                String message = String.format("* %s %s", client.nickname, "has been disconnected.");
//                broadcast(message);

                if (client.timer != null) {
                    client.timer.cancel();
                    client.timer.purge();
                    client.timer = null;
                }

                if (connections.isEmpty()) {
                    if (capture != null) {
                        capture.release();
                        capture = null;
                    }
                }

            }
        }
    }
}
