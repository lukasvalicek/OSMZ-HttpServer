package com.example.httpserver;

import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.util.Log;
import android.webkit.MimeTypeMap;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import android.os.Handler;

public class ClientThread extends Thread {
    private Socket socket;
    private Handler handler;
    private static DataOutputStream stream;
    private HttpServerActivity activity;
    private ByteArrayOutputStream imageBuffer;
    private boolean closeSocketEnabled = true;


    public ClientThread(Socket s, Handler h, HttpServerActivity activity) {
        this.socket = s;
        this.handler = h;
        this.activity = activity;
        this.imageBuffer = new ByteArrayOutputStream();
    }

    private String getMimeType(String fileName) {
        String type = null;
        String extension = MimeTypeMap.getFileExtensionFromUrl(fileName);
        if (extension != null) {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }
        return type;
    }

    private String formattedSize(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp-1) + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    private void callbackMsg(String type, String fileName, Long size) {
        Message msg = handler.obtainMessage();
        Bundle bundle = new Bundle();
        bundle.putString("REQUEST", type);
        bundle.putString("NAME", fileName);
        bundle.putLong("SIZE", size);
        msg.setData(bundle);
        handler.sendMessage(msg);
    }

    public void run() {
        try {
            OutputStream o = socket.getOutputStream();
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(o));
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            List<String> request = new ArrayList<String>();
            String oneLine = "";
            try {
                while (!(oneLine = in.readLine()).isEmpty()) {
                    request.add(oneLine);
                    Log.d("SERVER", oneLine);
                }
            } catch (Exception e) {
                Log.d("SERVER", "ERROR");
            }

            String path = Environment.getExternalStorageDirectory().getPath() + "/OSMZ";
            String fileName = "";
            try {
                fileName = request.get(0).split(" ")[1];
            } catch (Exception e) {
                Log.d("SERVER", "ERROR File name");
            }

            if (fileName.equals("/")) {
                fileName = "/index.html";
            }

            File file = new File(path + fileName);
            String resultHTML = "";

            if (fileName.equals("/camera/snapshot")) {
                Log.d("DEBUG", "snapshot");
                byte[] image = activity.getPicture();
                resultHTML += "HTTP/1.0 200 OK\r\n" +
                        "Content-Length: " + image.length + "\r\n" +
                        "Content-Type: image/jpeg\r\n" +
                        "\r\n";
                out.write(resultHTML);
                out.flush();

                o.write(activity.getPicture());
                o.flush();

                callbackMsg("Camera", "type: \"snapshot\" ", Long.valueOf(image.length));
            } else if (fileName.equals("/camera/stream")) {
                Log.d("DEBUG", "stream");
                stream = new DataOutputStream(socket.getOutputStream());
                try
                {
                    Log.d("onPreviewFrame", "stream");
                    stream.write(("HTTP/1.0 200 OK\r\n" +
                                  "Content-Type: multipart/x-mixed-replace; boundary=\"OSMZ_boundary\"\r\n" ).getBytes());
                    stream.flush();

                    closeSocketEnabled = false;

                    callbackMsg("Camera", "type: \"stream - inicialize\"", Long.valueOf(0));

                    sendBoundary();
                }
                catch (IOException e)
                {
                    Log.d("ERROR:", e.getLocalizedMessage());
                }

            } else if (fileName.equals("/camera")) {
                String pageHTML = "<html><head><title>Camera image</title><meta http-equiv=\"refresh\" content=\"5\"></head><body><img src=\"camera/camera.jpg\" alt=\"camera\"></body></html>";
                resultHTML += "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/html\r\n" +
                        "Content-Length: " + pageHTML.length() + "\r\n" +
                        "\r\n";
                out.write(resultHTML + pageHTML);
                out.flush();
            } else if (fileName.contains("/cgi-bin")) {
                Log.d("DEBUG", "cgi-bin");

                String commands[] = fileName.split("/");

                if (commands.length < 3) {
                    callbackMsg("cgi-bin - ERROR command must be enter", fileName , Long.valueOf(0)); // example 127.0.0.1:12345/cgi-bin/ls (3 part is "ls")
                    return;
                }

                try
                {
                    String urlArray[] = fileName.split("/cgi-bin/");
                    String commandWithArgs = urlArray[1];
                    commandWithArgs = commandWithArgs.replace("%20", " ");

                    String commandsArray[] = commandWithArgs.split(" ");

                    String pageHTML = "<html><head><title>cgi-bin: " + commandsArray[0] + "</title></head><body><h1>cgi-bin: " + commandsArray[0] + "</h1>";

                    ProcessBuilder p = new ProcessBuilder(commandsArray);
                    Process process = p.start();

                    String line;

                    BufferedReader stdOut = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    while ((line = stdOut.readLine()) != null) {
                        pageHTML += line + "<br>";
                    }

                    BufferedReader stdErr = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                    while ((line = stdErr.readLine()) != null) {
                        pageHTML += line + "<br>";
                    }

                    pageHTML +="</body></html>";

                    resultHTML += "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/html\r\n" +
                            "Content-Length: " + pageHTML.length() + "\r\n" +
                            "\r\n" +
                            pageHTML;

                    out.write(resultHTML);
                    out.flush();

                    callbackMsg("cgi-bin", commandWithArgs, Long.valueOf(resultHTML.length()));

                }
                catch (Exception e)
                {
                    Log.d("ProcessOutput", "just failed: " + e.getMessage());

                }

            } else if (file.exists() && file.isFile()) {
                Log.d("DEBUG", "File found");
                resultHTML += "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: " + getMimeType(fileName) + "\r\n" +
                        "Content-Length: " + file.length() + "\r\n" +
                        "\r\n";

                out.write(resultHTML);
                out.flush();

                FileInputStream fs = new FileInputStream(path + fileName);
                int len = 0;
                byte[] buffer = new byte[2048];
                while ((len = fs.read(buffer)) > 0) {
                    o.write(buffer, 0, len);
                }

                callbackMsg("File", fileName, file.length());

            } else if (file.isDirectory()) {
                Log.d("SERVER", "Directory found");
                resultHTML += "<html><head><title>Index of " + fileName + "</title></head><body><h1>Index of " + fileName + "</h1>";
                resultHTML += "<table><tr><th style=\"width:200px;\">Name</th><th style=\"width:100px;\">Type</th><th style=\"width:200px;\">Last modified</th><th style=\"width:100px;\">Size</th></tr><tr><td colspan=\"4\"><hr></td></tr><tr><td><a href=\"../\">Parent Directory</a></td><td></td><td></td><td></td></tr>";

                File[] listOfFiles = file.listFiles();
                String foldersHTML = "";
                String filesHTML = "";
                for (File actualFile : listOfFiles) {
                    if (actualFile.isDirectory()) {
                        foldersHTML += "<tr><td><a href=\"" + actualFile.getName() + "/\">" + actualFile.getName() + "/</a></td><td>Directory</td><td style=\"text-align:center;\"><td></td><td></td></tr>";
                    } else if (actualFile.isFile()) {
                        Log.d("SERVER", "" + actualFile.lastModified());
                        SimpleDateFormat sdf = new SimpleDateFormat("YYYY-MM-dd HH:mm:ss");
                        filesHTML += "<tr><td><a href=\"" + actualFile.getName() + "\">" + actualFile.getName() + "</a></td><td>" + getMimeType(actualFile.getName()) + "</td><td>" + sdf.format(actualFile.lastModified()) + "</td><td>" + formattedSize(actualFile.length(), true) + "</td></tr>";
                    }
                }

                resultHTML += foldersHTML + filesHTML + "</table></body></html>";
                resultHTML = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/html\r\n" +
                        "Content-Length: " + resultHTML.length() + "\r\n" +
                        "\r\n" + resultHTML;

                out.write(resultHTML);
                out.flush();

                callbackMsg("Directory", fileName, file.length());

            } else {
                Log.d("SERVER", "File not found");
                resultHTML += "HTTP/1.1 404 Not Found\r\n" +
                        "\r\n" +
                        "<html><head><title>404 Not Found</title></head><body><h1>File not found - 404</h1></body></html>\r\n";
                out.write(resultHTML);
                out.flush();

                callbackMsg("404", fileName, (long) resultHTML.length());
            }

            out.flush();

            if (closeSocketEnabled) {
                socket.close();
            }
        } catch (IOException e) {
            Log.d("SERVER", "ERROR - ClientSocketThread");
        }
    }

    public void sendBoundary() {
        if (stream != null) {
            try {
                Log.d("onPreviewFrame", "stream boundary");
                byte[] baos = activity.getPicture();

                imageBuffer.reset();
                imageBuffer.write(baos);
                imageBuffer.flush();

                stream.write(("\n--OSMZ_boundary\n" +
                        "Content-type: image/jpeg\n" +
                        "Content-Length: " + imageBuffer.size() + "\n\n").getBytes());

                stream.write(imageBuffer.toByteArray());
                stream.write(("\n").getBytes());

                stream.flush();

                callbackMsg("Camera", "type: \"stream\"", Long.valueOf(imageBuffer.size()));

                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Log.d("DEBUG", "RUN sendBoundary delay");
                        sendBoundary();
                    }
                }, 1000);
            } catch (IOException e) {
                Log.d("ERROR:", "Boundary error: " + e.getLocalizedMessage());
            }
        }
    }

}
