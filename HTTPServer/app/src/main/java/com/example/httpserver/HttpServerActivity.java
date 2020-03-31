package com.example.httpserver;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.os.Bundle;
import android.app.Activity;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.util.Date;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.StrictMode;

public class HttpServerActivity extends Activity implements OnClickListener{

	private SocketServer s;
	public long transferredDataSize = 0;
	public String logText = "";
	private Camera mCamera;
	private CameraPreview mPreview;
	private Handler cameraHandler = new Handler();

	private byte[] imageBuffer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_http_server);
        
        Button btn1 = (Button)findViewById(R.id.button1);
        Button btn2 = (Button)findViewById(R.id.button2);
         
        btn1.setOnClickListener(this);
        btn2.setOnClickListener(this);

		if (checkCameraHardware(getApplicationContext())) { // camera is in device
			Log.d("DEBUG", "camera found");
			mCamera = getCameraInstance();
			mCamera.setPreviewCallback(mPrev);
			mPreview = new CameraPreview(this, mCamera, mPicture);
			FrameLayout preview = findViewById(R.id.cameraView);
			preview.addView(mPreview);
			mCamera.startPreview();
		}

		//this is for boundary
		StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
		StrictMode.setThreadPolicy(policy);
    }

	public String formattedSize(long bytes, boolean si) {
		int unit = si ? 1000 : 1024;
		if (bytes < unit) return bytes + " B";
		int exp = (int) (Math.log(bytes) / Math.log(unit));
		String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp-1) + (si ? "" : "i");
		return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
	}

	private final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
		Bundle bundle = msg.getData();
		TextView text = (TextView)findViewById(R.id.logsView);

		Date date = new Date();
		String dateString = date.getHours() + ":" + date.getMinutes() + ":" + date.getSeconds();
		String typeOfRequest = bundle.getString("REQUEST");
		String name = bundle.getString("NAME");
		Long size = bundle.getLong("SIZE");

		transferredDataSize += size;
		logText += dateString + "\t" + typeOfRequest + "\t" + name + "\t" + formattedSize(size, true) + "\n";

		text.setText(logText + "\nTotal transfer data: " + formattedSize(transferredDataSize, true));
		}
	};

	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		if (v.getId() == R.id.button1) {
			s = new SocketServer(mHandler, this);
			s.start();
			takePicture();
		}
		if (v.getId() == R.id.button2) {
			try {
				s.close();
				s.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	public byte[] getPicture()
	{
		Bitmap rotateImageData = BitmapFactory.decodeByteArray(imageBuffer, 0, imageBuffer.length);
		rotateImageData = rotate(rotateImageData, 90);
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		rotateImageData.compress(Bitmap.CompressFormat.JPEG,100,stream);
		return stream.toByteArray();
	}

	/** Check if this device has a camera */
	private boolean checkCameraHardware(Context context) {
		if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
			// this device has a camera
			return true;
		} else {
			// no camera on this device
			return false;
		}
	}

	/** A safe way to get an instance of the Camera object. */
	public static Camera getCameraInstance(){
		Camera c = null;
		try {
			Camera.CameraInfo info = new Camera.CameraInfo();
			c = Camera.open(); // attempt to get a Camera instance
			c.setDisplayOrientation(90);

			Camera.Parameters parameters = c.getParameters();
			parameters.set("jpeg-quality", 100);
			parameters.setPictureFormat(PixelFormat.JPEG);
			parameters.setPictureSize(640, 480);
			c.setParameters(parameters);

		}
		catch (Exception e){
			// Camera is not available (in use or does not exist)
			Log.d("ERROR", "Camera instance not be detected");
		}
		return c; // returns null if camera is unavailable
	}

	private Camera.PreviewCallback mPrev = new Camera.PreviewCallback()
	{

		@Override
		public void onPreviewFrame(byte[] bytes, Camera camera)
		{
			try {
				imageBuffer = convertoToJpeg(bytes, camera);
			} catch (Exception e) {
				Log.d("ERROR", "convert image error");
			}
		}
	};

	public byte[] convertoToJpeg(byte[] data, Camera camera)
	{
		YuvImage image = new YuvImage(data, ImageFormat.NV21, camera.getParameters().getPreviewSize().width, camera.getParameters().getPreviewSize().height, null);

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		image.compressToJpeg(new Rect(0, 0, camera.getParameters().getPreviewSize().width, camera.getParameters().getPreviewSize().height), 100, baos);//this line decreases the image quality

		return baos.toByteArray();
	}


	private Camera.PictureCallback mPicture = new Camera.PictureCallback() {

		@Override
		public void onPictureTaken(byte[] data, Camera camera) {

			mCamera.startPreview();

			/* This is point 1 (save image to file) */
			/*
			File pictureFile = new File(Environment.getExternalStorageDirectory().getPath() + "/OSMZ/camera" + File.separator + "camera.jpg");
			try {
				Bitmap rotateImageData = BitmapFactory.decodeByteArray(data, 0, data.length);
				rotateImageData = rotate(rotateImageData, 90);
				ByteArrayOutputStream stream = new ByteArrayOutputStream();
				rotateImageData.compress(Bitmap.CompressFormat.JPEG,100,stream);
				FileOutputStream fos = new FileOutputStream(pictureFile);
				fos.write(stream.toByteArray());
				fos.close();
			} catch (FileNotFoundException e) {
				Log.d("MyCameraApp", "File not found: " + e.getMessage());
			} catch (IOException e) {
				Log.d("MyCameraApp", "Error accessing file: " + e.getMessage());
			}
			*/

			/* This is point 2 */
			Bitmap rotateImageData = BitmapFactory.decodeByteArray(data, 0, data.length);
			rotateImageData = rotate(rotateImageData, 90);
			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			rotateImageData.compress(Bitmap.CompressFormat.JPEG,100,stream);
			imageBuffer = data;
		}
	};

	public static Bitmap rotate(Bitmap bitmap, int degree)
	{
		int w = bitmap.getWidth();
		int h = bitmap.getHeight();

		Matrix mtx = new Matrix();
		mtx.setRotate(degree);

		return Bitmap.createBitmap(bitmap, 0, 0, w, h, mtx, true);
	}

	public void takePicture(){
		if (mCamera == null) {
			mCamera = getCameraInstance();
			Log.d("DEBUG", "reinit camera");
		}
		try {
            mCamera.takePicture(null, null, mPicture);
        } catch (Exception e) {
		    Log.d("ERROR", "Take picture error: " + e.getLocalizedMessage());
        }

		cameraHandler.postDelayed(new Runnable() {
			@Override
			public void run() {
				takePicture();
			}
		}, 5000);
	}
    
}
