
package vision;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.view.*;
import android.widget.FrameLayout;
import org.bytedeco.javacpp.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.bytedeco.javacpp.opencv_core.*;

/**
 * Created by hexafraction on 9/14/15.
 */
public class OpenCvLegacyActivity extends Activity {
    private FrameLayout layout;
    protected LegacyFaceView legacyFaceView;
    private LegacyPreview mLegacyPreview;

    static HashSet<MatCallback> callbacks = new HashSet<>();

    static volatile boolean running;

    public static void startActivity(Context cx) {
        if (!running) {
            running = true;
            cx.startActivity(new Intent(cx, OpenCvLegacyActivity.class));
        }
    }

    public static void addCallback(MatCallback cb) {
        callbacks.add(cb);
    }

    public static void removeCallback(MatCallback cb) {
        callbacks.remove(cb);
    }


    public static void setCameraDisplayOrientation(Activity activity,
                                                   int cameraId, Camera camera) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360; // compensate the mirror
        } else { // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
    }

    public void onOrientationChanged(int orientation) {
        setCameraDisplayOrientation(this, mLegacyPreview.mCID, mLegacyPreview.mCamera);
    }


    @Override
    protected void onPause() {
        running = false;
        super.onPause();
        this.finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // Hide the window title.
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // Create our Preview view and set it as the content of our activity.
        try {

            layout = new FrameLayout(this);
            legacyFaceView = new LegacyFaceView(this);
            mLegacyPreview = new LegacyPreview(this, legacyFaceView);

            layout.addView(mLegacyPreview);

            layout.addView(legacyFaceView);
            setContentView(layout);
        } catch (IOException e) {
            e.printStackTrace();
            new AlertDialog.Builder(this).setMessage(e.getMessage()).create().show();
        }
    }


}

// ----------------------------------------------------------------------

class LegacyFaceView extends View implements Camera.PreviewCallback {
    private opencv_core.Mat yuvImage = new opencv_core.Mat();
    private Mat rgbImage = new Mat();
    private opencv_core.CvMemStorage storage;
    private volatile boolean needAnotherFrame = true;
    Camera.Size size;

    public class LegacyRunProcess implements Runnable {

        @Override
        public void run() {
            while (run) {
                if (arrPending != null) {
                    if (arrData == null || arrData.length != arrPending.length) arrData = new byte[arrPending.length];
                    System.arraycopy(arrPending, 0, arrData, 0, arrPending.length);
                    if (size != null) processImage(arrData, size.width, size.height);
                    needAnotherFrame = true;
                }
            }
        }
    }

    public LegacyFaceView(OpenCvLegacyActivity context) throws IOException {
        super(context);


        storage = opencv_core.CvMemStorage.create();
    }

    public void onPreviewFrame(final byte[] data, final Camera camera) {


        try

        {
            if (needAnotherFrame) {
                needAnotherFrame = false;
                if (arrPending == null || arrPending.length != data.length) arrPending = new byte[data.length];
                size = camera.getParameters().getPreviewSize();
                System.arraycopy(data, 0, arrPending, 0, data.length);
            }
            camera.addCallbackBuffer(data);
        } catch (RuntimeException e)

        {
            // The camera has probably just been released, ignore.
            Log.e("KP", e.getClass().getName() + ":" + e.getMessage());
            for (StackTraceElement el : e.getStackTrace()) {
                Log.e("KP:ST", el.toString());
            }
        }

    }

    private byte[] arrData, arrPending;
    volatile boolean run = true;
    Thread imgProcessor;

    protected void processImage(byte[] data, int width, int height) {
        Log.i("KPP", ("data = [" + data + "], width = [" + width + "], height = [" + height + "]"));
        Log.i("KP", "Entering process");
        // First, downsample our image and convert it into a grayscale IplImage
        int bytesPerPixel = data.length / (width * height);
        //1620 for YUV NV21
        if (yuvImage == null || yuvImage.arrayWidth() != width || yuvImage.arrayHeight() != height + (height / 2)) {
            Log.i("PREPROC", "Remaking yuv");
            yuvImage.create(height + (height / 2), width, CV_8UC1);
        }
        if (rgbImage == null || rgbImage.arrayWidth() != width || rgbImage.arrayHeight() != height) {
            Log.i("PREPROC", "Remaking rgbImage: Currently " + rgbImage.arrayWidth() + "*" + rgbImage.arrayHeight());
            rgbImage.create(height, width, CV_8UC1);
        }


        Log.i("OPENCV", "Processing " + Thread.currentThread().getName());

        ByteBuffer imageBuffer = yuvImage.createBuffer();
        for (int i = 0; i < data.length; i++) {
            imageBuffer.put(i, data[i]);

        }
        opencv_imgproc.cvtColor(yuvImage, rgbImage, opencv_imgproc.COLOR_YUV2RGB_NV21);

        for (MatCallback cb : OpenCvLegacyActivity.callbacks) {
            cb.handleMat(rgbImage);
        }

        cvClearMemStorage(storage);
        postInvalidate();
    }

    AtomicReference<opencv_features2d.KeyPoint> kpRef = new AtomicReference<>(null);
    AtomicReference<Point2f> lineRef = new AtomicReference<>(null);
    public String status = "";

    @Override
    protected void onDraw(Canvas canvas) {
        for (MatCallback cb : OpenCvLegacyActivity.callbacks) {
            cb.draw(canvas);
        }
        super.onDraw(canvas);
    }
}

// ----------------------------------------------------------------------

class LegacyPreview extends SurfaceView implements SurfaceHolder.Callback {
    SurfaceHolder mHolder;
    Camera mCamera;
    Camera.PreviewCallback previewCallback;

    LegacyPreview(Context context, final Camera.PreviewCallback previewCallback) {
        super(context);
        this.previewCallback = previewCallback;

        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = getHolder();
        mHolder.addCallback(this);
        this.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                mCamera.autoFocus(new Camera.AutoFocusCallback() {
                    @Override
                    public void onAutoFocus(boolean b, Camera camera) {
                        if (previewCallback instanceof LegacyFaceView) {
                            ((LegacyFaceView) previewCallback).status = ("Autofocus " + (b ? "succeeded" : "failed"));
                        }
                    }
                });
            }
        });
    }

    int mCID = 0;


    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, acquire the camera and tell it where
        // to draw.
        int numberOfCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                Log.d("DBG", "Camera found");
                mCID = i;
                break;
            }
        }

        mCamera = Camera.open(mCID);
        OpenCvLegacyActivity.setCameraDisplayOrientation((Activity) this.getContext(), mCID, mCamera);
        try {
            mCamera.setPreviewDisplay(holder);
        } catch (IOException exception) {
            mCamera.release();
            mCamera = null;
        }
        ((OpenCvLegacyActivity) this.getContext()).legacyFaceView.run = true;
        ((OpenCvLegacyActivity) this.getContext()).legacyFaceView.imgProcessor = new Thread(((OpenCvLegacyActivity) this.getContext()).legacyFaceView.new LegacyRunProcess(), "openCvProcessorThread");
        ((OpenCvLegacyActivity) this.getContext()).legacyFaceView.imgProcessor.start();

    }

    int rt = 0;

    public void surfaceDestroyed(SurfaceHolder holder) {
        // Surface will be destroyed when we return, so stop the preview.
        // Because the CameraDevice object is not a shared resource, it's very
        // important to release it when the activity is paused.
        mCamera.stopPreview();

        mCamera.release();

        mCamera = null;
        ((OpenCvLegacyActivity) this.getContext()).legacyFaceView.run = false;
    }


    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.05;
        double targetRatio = (double) w / h;
        if (sizes == null) return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        // Try to find an size match aspect ratio and size
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }


    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // Now that the size is known, set up the camera parameters and begin
        // the preview.
        Camera.Parameters parameters = mCamera.getParameters();

        List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
        Camera.Size optimalSize = getOptimalPreviewSize(sizes, w, h);
        parameters.setPreviewSize(optimalSize.width, optimalSize.height);
        parameters.setPreviewFormat(ImageFormat.NV21);
        parameters.setRotation(rt);
        Log.w("RT", "setting rt: " + rt);
        mCamera.setParameters(parameters);
        OpenCvLegacyActivity.setCameraDisplayOrientation((Activity) this.getContext(), mCID, mCamera);
        if (previewCallback != null) {
            mCamera.setPreviewCallbackWithBuffer(previewCallback);
            Camera.Size size = parameters.getPreviewSize();
            byte[] data = new byte[size.width * size.height *
                    ImageFormat.getBitsPerPixel(parameters.getPreviewFormat()) / 8];
            mCamera.addCallbackBuffer(data);
        }
        mCamera.startPreview();
    }

}