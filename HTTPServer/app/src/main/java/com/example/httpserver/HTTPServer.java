package com.example.httpserver;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Binder;


public class HTTPServer extends Service {

    private SocketServer s;
    private HttpServerActivity httpActivity;

    private byte[] imageBuffer;

    private final IBinder localService = new LocalService();

    class LocalService extends Binder {
        HTTPServer getService() {
            return HTTPServer.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return localService;
    }

    public HTTPServer() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startID) {
        httpActivity = new HttpServerActivity();
        s = new SocketServer(httpActivity.mHandler, httpActivity);
        s.start();
        return super.onStartCommand(intent, flags, startID);
    }

}
