package com.example.httpserver;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Semaphore;
import android.os.Handler;
import android.util.Log;

public class SocketServer extends Thread {
	
	private ServerSocket serverSocket;
	private final int port = 12345;
	private boolean bRunning;
	private Handler handler;
	private static final int MAX_AVAILABLE = 100;
	private Semaphore semaphore;
	private byte[] imageBuffer;
	private HttpServerActivity activity;

	public SocketServer(HttpServerActivity activity) {
		this.handler = activity.mHandler;
		this.semaphore = new Semaphore(MAX_AVAILABLE, true);
		this.activity = activity;
	}

	public SocketServer(Handler h, HttpServerActivity activity) {
		this.handler = h;
		this.semaphore = new Semaphore(MAX_AVAILABLE, true);
		this.activity = activity;
	}

	public void close() {
		try {
			serverSocket.close();
		} catch (IOException e) {
			Log.d("SERVER", "Error, probably interrupted in accept(), see log");
			e.printStackTrace();
		}
		bRunning = false;
	}

	public void run() {
        try {
        	Log.d("SERVER", "Creating Socket");
            serverSocket = new ServerSocket(port);
            bRunning = true;
            while (bRunning) {
                Socket s = serverSocket.accept();
                try {
					this.semaphore.acquire();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				new ClientThread(s, this.handler, this.activity).start();
            }
        } 
        catch (IOException e) {
            if (serverSocket != null && serverSocket.isClosed())
            	Log.d("SERVER", "Normal exit");
            else {
            	Log.d("SERVER", "Error");
            	e.printStackTrace();
            }
        }
        finally {
        	serverSocket = null;
        	bRunning = false;
        }
		this.semaphore.release();
    }

}
