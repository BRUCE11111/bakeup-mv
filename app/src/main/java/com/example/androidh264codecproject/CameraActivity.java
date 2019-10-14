package com.example.androidh264codecproject;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.androidh264codecproject.decoder.FFmpegAVCDecoder;
import com.example.androidh264codecproject.decoder.FFmpegAVCDecoderCallback;
import com.example.androidh264codecproject.encoder.AVCEncoder;
import com.example.androidh264codecproject.encoder.BitrateTool;
import com.example.androidh264codecproject.encoder.MotionVectorList;
import com.example.androidh264codecproject.encoder.MotionVectorListItem;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;


public class CameraActivity extends Fragment {

    static {
        System.loadLibrary("motion_search_jni");
        // System.loadLibrary("motion_search_opencl_jni");
        // System.loadLibrary("encoder_jni");
        System.loadLibrary("ffmpeg_h264_decoder_jni");

        /* FFmpeg Library */
        System.loadLibrary("avcodec");
        System.loadLibrary("avdevice");
        System.loadLibrary("avfilter");
        System.loadLibrary("avformat");
        System.loadLibrary("avutil");
        System.loadLibrary("postproc");
        System.loadLibrary("swresample");
        System.loadLibrary("swscale");
    }

    private MotionVectorList mvList;

    public void putYUVData(byte[] buffer) {
        if (YUVQueue.size() >= 10) {
            YUVQueue.poll();
        }
        YUVQueue.add(buffer);
    }

    public int width=640;
    public int height=480;

    AVCEncoder encoder = new AVCEncoder(width,height,30, BitrateTool.getAdaptiveBitrate(
            width,height),YUVQueue);
    public static ArrayBlockingQueue<MotionVectorList> vectorLists=new ArrayBlockingQueue<MotionVectorList>(10);
    private static int yuvqueuesize = 10;
    public static ArrayBlockingQueue<byte[]> YUVQueue = new ArrayBlockingQueue<>(yuvqueuesize);

    public FFmpegAVCDecoderCallback mDecoderCallback1 = new FFmpegAVCDecoderCallback(width,height);

    int REQUEST_CAMERA_PERMISSION = 1;

    private static Range<Integer>[] fpsRanges;

    /** A shape for extracting frame data.   */
    private int PREVIEW_WIDTH = 640;
    private int PREVIEW_HEIGHT = 480;
    /** ID of the current [CameraDevice].   */
    private String cameraId= null;

    /** A [Semaphore] to prevent the app from exiting before closing the camera.    */
    private Semaphore cameraOpenCloseLock = new Semaphore(1);


    /** A reference to the opened [CameraDevice].    */
    static private CameraDevice cameraDevice=null;

    /** The [android.util.Size] of camera preview.  */
    private Size previewSize= null;

    /** The [android.util.Size.getWidth] of camera preview. */
    private int previewWidth = 0;

    /** The [android.util.Size.getHeight] of camera preview.  */
    private int previewHeight = 0;

    /** A ByteArray to save image data in YUV format  */
    private ArrayList<byte[]> yuvBytes = new ArrayList<>(3);

    /** An IntArray to save image data in ARGB8888 format  */
    private  int[] rgbBytes;


    /** An [ImageReader] that handles preview frame capture.   */
    private ImageReader imageReader  = null;

    /** Orientation of the camera sensor.   */
    private int sensorOrientation= 0;

    /** Whether the current camera device supports Flash or not.    */
    private boolean flashSupported = false;

    /** A [Handler] for running tasks in the background.    */
    private Handler backgroundHandler= null;

    /** An additional thread for running tasks that shouldn't block the UI.   */
    private HandlerThread backgroundThread  = null;

    /** [CaptureRequest.Builder] for the camera preview   */
    private CaptureRequest.Builder previewRequestBuilder = null;

    /** A [SurfaceView] for camera preview.   */
    private SurfaceView surfaceView  = null;

    /** Abstract interface to someone holding a display surface.    */
    private SurfaceHolder surfaceHolder= null;


    /** [CaptureRequest] generated by [.previewRequestBuilder   */
    private CaptureRequest previewRequest= null;

    /** A [CameraCaptureSession] for camera preview.   */
    private CameraCaptureSession captureSession  = null;

    /** Paint class holds the style and color information to draw geometries,text and bitmaps. */
    private Paint paint =new Paint();

    private CameraDevice.StateCallback stateCallback=new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraOpenCloseLock.release();
            CameraActivity.cameraDevice=camera;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {

            cameraOpenCloseLock.release();
            cameraDevice.close();
            CameraActivity.cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            onDisconnected(cameraDevice);
            CameraActivity.this.getActivity().finish();
        }
    };


    @Override
    public void onAttach(Context context) {

        super.onAttach(context);

    }


    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle saveInstanceState){

        View view=inflater.inflate(R.layout.activity_motion,container,false);

        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState){
        surfaceView = view.findViewById(R.id.surfaceView);
        surfaceHolder = surfaceView.getHolder();
    }


    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
        yuvBytes.add(null);
        yuvBytes.add(null);
        yuvBytes.add(null);
        openCamera();
    }


    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();
        encoder.startAsync();
        //encoder.start();
    }

    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }


    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroyView() {

        super.onDestroyView();
    }



    @Override
    public void onDetach() {

        super.onDetach();
    }

    public String TAG="camerainfo";


    /**
     * Stops the background thread and its [Handler].
     */
    private void stopBackgroundThread() {
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            Log.e(TAG, e.toString());
        }
    }

    /**
     * Sets up member variables related to camera.
     */
    private void setUpCameraOutputs() {

        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (int i=0;i<manager.getCameraIdList().length;i++) {
                String cameraId=manager.getCameraIdList()[i];
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                Integer cameraDirection = characteristics.get(CameraCharacteristics.LENS_FACING);
                fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
                if (cameraDirection != null &&
                        cameraDirection == CameraCharacteristics.LENS_FACING_FRONT
                ) {
                    continue;
                }

                previewSize = new Size(PREVIEW_WIDTH, PREVIEW_HEIGHT);

                imageReader = ImageReader.newInstance(
                        PREVIEW_WIDTH, PREVIEW_HEIGHT,
                        ImageFormat.YUV_420_888, /*maxImages*/ 2
                );

                sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

                previewHeight = previewSize.getHeight();
                previewWidth = previewSize.getWidth();

                // Initialize the storage bitmaps once when the resolution is known.
                rgbBytes = new int[previewWidth * previewHeight];

                // Check if the flash is supported.
                flashSupported =
                        characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true;

                this.cameraId = cameraId;

                // We've found a viable camera and finished setting up member variables,
                // so we don't need to iterate through other available cameras.
                return ;
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, e.toString());
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            Log.e(TAG,e.getMessage());
        }
    }


    /**
     * Opens the camera specified by [PosenetActivity.cameraId].
     */
    private void openCamera() {
        int permissionCamera = ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA);
        if (permissionCamera != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
        }
        setUpCameraOutputs();
        CameraManager manager =(CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        try {
            // Wait for camera to open - 2.5 seconds is sufficient
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            manager.openCamera(cameraId, stateCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, e.toString());
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    private void  requestCameraPermission() {

        if (ContextCompat.checkSelfPermission(this.getActivity(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            if (this.shouldShowRequestPermissionRationale(Manifest.permission
                    .CAMERA)) {
                Toast.makeText(this.getActivity(), "请开通相关权限，否则无法正常使用本应用！", Toast.LENGTH_SHORT).show();
            }
            this.requestPermissions(new String[]{Manifest.permission.CAMERA}, 1);

        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {

                Toast.makeText(CameraActivity.this.getContext(),"camera failed", Toast.LENGTH_SHORT).show();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    /** Fill the yuvBytes with data from image planes.   */
    private void fillBytes(Image.Plane[] planes, ArrayList<byte[]> yuvBytes) {
        // Row stride is the total number of bytes occupied in memory by a row of an image.
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (int i=0;i< planes.length;i++) {
            ByteBuffer buffer = planes[i].getBuffer();
            byte[] tmp=new byte[buffer.remaining()];
            buffer.get(tmp);
            yuvBytes.set(i,tmp);
        }
    }

    long startt=0;
    long endt=0;
    Integer frameCounter1=0;
    /** A [OnImageAvailableListener] to receive frames as they are available.  */
    private ImageReader.OnImageAvailableListener imageAvailableListener = new ImageReader.OnImageAvailableListener()
    {
        @Override
        public void onImageAvailable(ImageReader imageReader) {
            // We need wait until we have some size from onPreviewSizeChosen

            frameCounter1++;
            startt=System.currentTimeMillis();
            if (previewWidth == 0 || previewHeight == 0) {
                return ;
            }

            Image image=imageReader.acquireLatestImage();

            if(image==null){
                return ;
            }else{
                fillBytes(image.getPlanes(), yuvBytes);
            }

            ImageUtils.convertYUV420ToARGB8888(
                    yuvBytes.get(0),
                    yuvBytes.get(1),
                    yuvBytes.get(2),
                    previewWidth,
                    previewHeight,
                    /*yRowStride=*/ image.getPlanes()[0].getRowStride(),
                    /*uvRowStride=*/ image.getPlanes()[1].getRowStride(),
                    /*uvPixelStride=*/ image.getPlanes()[1].getPixelStride(),
                    rgbBytes
      );
            // Create bitmap from int array
            Bitmap imageBitmap = Bitmap.createBitmap(
                    rgbBytes, previewWidth, previewHeight,
                    Bitmap.Config.ARGB_8888
            );
            // Create rotated version for portrait display
            Matrix rotateMatrix = new Matrix();
            rotateMatrix.postRotate(90.0f);

            Bitmap rotatedBitmap = Bitmap.createBitmap(
                    imageBitmap, 0, 0, previewWidth, previewHeight,
                    rotateMatrix, true
            );
            // Process an image for analysis in every 3 frames.
            //frameCounter = (frameCounter + 1) % 3
            //if (frameCounter == 0) {
            processImage(image,rotatedBitmap);
            //}
            image.close();
        }
    };

    /**
     * Starts a background thread and its [Handler].
     */
    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("imageAvailableListener");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }


    private static byte[] getDataFromImage(Image image){
        Rect crop=image.getCropRect();
        int format=image.getFormat();
        Log.d("formatimage","msg: "+format+"");
        int width=crop.width();
        int height=crop.height();
        Image.Plane[] planes=image.getPlanes();
        byte[] data=new byte[width*height* ImageFormat.getBitsPerPixel(format)/8];
        byte[] rowdata=new byte[planes[0].getRowStride()];
        int channelOffset=0;
        int outputStride=1;
        for(int i=0;i<planes.length;i++){
            switch (i){
                case 0:
                    channelOffset=0;
                    outputStride=1;
                    break;
                case 1:
                    if(format== ImageFormat.YUV_420_888){
                        channelOffset=width*height;
                        outputStride=1;
                    }
                    break;
                case 2:
                    if(format== ImageFormat.YUV_420_888){
                        channelOffset=(int) (width*height*1.25);
                        outputStride=1;
                    }
                    break;
            }
            ByteBuffer buffer=planes[i].getBuffer();
            int rowStride=planes[i].getRowStride();
            int pixelStride=planes[i].getPixelStride();
            int shift=(i==0)?0:1;
            int w=width>>shift;
            int h=height>>shift;
            buffer.position(rowStride*(crop.top>>shift)+pixelStride*(crop.left>>shift));
            for(int row=0;row<h;row++){
                int length;
                if(pixelStride==1&&outputStride==1){
                    length=w;
                    buffer.get(data,channelOffset,length);
                    channelOffset+=length;
                }else{
                    length=(w-1)*pixelStride+1;
                    buffer.get(rowdata,0,length);
                    for(int col=0;col<w;col++){
                        data[channelOffset]=rowdata[col*pixelStride];
                        channelOffset+=outputStride;
                    }
                }
                if(row<h-1){
                    buffer.position(buffer.position()+rowStride-length);
                }
            }
        }
        return data;
    }

    /** Set the paint color and size.    */
    private void setPaint() {
        paint.setColor(Color.RED) ;
        paint.setTextSize( 80.0f);
        paint.setStrokeWidth(8.0f);
    }

    void processImage(Image image, Bitmap bitmap){
        byte[] yuv=getDataFromImage(image);
        putYUVData(yuv);
        mDecoderCallback1=encoder.mDecoderCallback;
        FFmpegAVCDecoder decoder=mDecoderCallback1.getdecoder();
        mvList = decoder.getMotionVectorList();

        if(mvList!=null) {
            if (vectorLists.size() < 10) {
                vectorLists.add(mvList);
            } else {
                vectorLists.poll();
                vectorLists.add(mvList);
            }

        }



        Canvas canvas = surfaceHolder.lockCanvas();
        draw(canvas,bitmap);
    }

    int fx,fy,dx,dy;
    /** Draw bitmap on Canvas.   */
    private void draw(Canvas canvas, Bitmap bitmap) {

        int screenWidth = canvas.getWidth();
        int screenHeight= canvas.getHeight();
        float width_ratio=screenWidth/width;
        float height_ratio=screenHeight/height;

        setPaint();

        canvas.drawBitmap(
                bitmap,
                new Rect(0, 0, previewHeight, previewWidth),
                new Rect(0, 0, screenWidth, screenHeight),
                paint
        );
        if (this.vectorLists.size()>0) {
            mvList = vectorLists.poll();
            //Log.d(TAG, String.format(Locale.CHINA, "MV List Count: %d", mvList.getCount()));
            for (int i = 0; i < mvList.getCount(); i++) {
                MotionVectorListItem item = mvList.getItem(i);
                int mvX = item.getMvX();
                int mvY = item.getMvY();
                int posX = item.getPosX();
                int posY = item.getPosY();
                int sizeX = item.getSizeX();
                int sizeY = item.getSizeY();
                fx = posX;
                fy = posY;
                dx = mvX + posX;
                dy = mvY + posY;
                canvas.drawLine(fx*width_ratio, fy*height_ratio, dx*width_ratio, dy*height_ratio, paint);
                //Log.d("resultmotion", "" + mvX + " " + mvY + " " + posX + " " + posY + " " + sizeX + " " + sizeY + "");
            }
        }

        endt=System.currentTimeMillis();

        canvas.drawText(
                String.format("Time cost: %d ms", endt-startt),
                (15.0f * width_ratio),
                (453.0f * height_ratio),
                paint
        );

        canvas.drawText(
                "frame_count: "+ String.format("%d", frameCounter1),
                (15.0f * width_ratio),
                (433.0f * height_ratio),
                paint
        );
/*
        float widthRatio = screenWidth.toFloat() / MODEL_WIDTH
        float heightRatio = screenHeight.toFloat() / MODEL_HEIGHT

        // Draw key points over the image.
        for (keyPoint in person.keyPoints) {
            if (keyPoint.score > minConfidence) {
                val position = keyPoint.position
                val adjustedX: Float = position.x.toFloat() * widthRatio
                val adjustedY: Float = position.y.toFloat() * heightRatio
                canvas.drawCircle(adjustedX, adjustedY, circleRadius, paint)
            }
        }

        for (line in bodyJoints) {
            if (
                    (person.keyPoints[line.first.ordinal].score > minConfidence) and
                    (person.keyPoints[line.second.ordinal].score > minConfidence)
      ) {
                canvas.drawLine(
                        person.keyPoints[line.first.ordinal].position.x.toFloat() * widthRatio,
                        person.keyPoints[line.first.ordinal].position.y.toFloat() * heightRatio,
                        person.keyPoints[line.second.ordinal].position.x.toFloat() * widthRatio,
                        person.keyPoints[line.second.ordinal].position.y.toFloat() * heightRatio,
                        paint
                )
            }
        }

        // Draw confidence score of a person.
        val scoreMessage = "SCORE: " + "%.2f".format(person.score)
        canvas.drawText(
                scoreMessage,
                (15.0f * widthRatio),
                (243.0f * heightRatio),
                paint
        )

        canvas.drawText(
                "frame_count: "+"%d".format(frameCounter1),
                (15.0f * widthRatio),
                (233.0f * heightRatio),
                paint
        )

        canvas.drawText(
                "inference time: "+"%d".format(time),
                (15.0f * widthRatio),
                (223.0f * heightRatio),
                paint
        )
*/
        // Draw!
        surfaceHolder.unlockCanvasAndPost(canvas);
    }


    /**
     * Creates a new [CameraCaptureSession] for camera preview.
     */

    private void createCameraPreviewSession() {
        try {

            Log.i("preview","hhh");

            // We capture images from preview in YUV format.
            ImageReader imageReader = ImageReader.newInstance(
                    previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 2
      );
            imageReader.setOnImageAvailableListener(imageAvailableListener, backgroundHandler);

            // This is the surface we need to record images for processing.
            Surface recordingSurface = imageReader.getSurface();
            // We set up a CaptureRequest.Builder with the output Surface.
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW
            );
            previewRequestBuilder.addTarget(recordingSurface);
            //previewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,fpsRanges[7]);

            List<Surface> tmp=new LinkedList<>();
            tmp.add(recordingSurface);
            // Here, we create a CameraCaptureSession for camera preview.
            cameraDevice.createCaptureSession(
                    tmp,
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (cameraDevice == null) return ;

                            // When the session is ready, we start displaying the preview.
                            captureSession = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
                                previewRequestBuilder.set(
                                        CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                                );
                                // Flash is automatically enabled when necessary.
                                setAutoFlash(previewRequestBuilder);
                                // Finally, we start displaying the camera preview.
                                previewRequest = previewRequestBuilder.build();
                                captureSession.setRepeatingRequest(
                                        previewRequest,
                                        captureCallback, backgroundHandler
              );
                            } catch (CameraAccessException e) {
                                Log.e(TAG, e.toString());
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                        }
                    }
            ,
            null
      );
        } catch (CameraAccessException e) {
            Log.e(TAG, e.toString());
        }
    }

    /**
     * Closes the current [CameraDevice].
     */
    private void closeCamera() {
        if (captureSession == null) {
            return ;
        }

        try {
            cameraOpenCloseLock.acquire();
            captureSession.close();
            captureSession = null;
            cameraDevice.close();
            cameraDevice = null;
            imageReader.close();
            imageReader = null;
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            cameraOpenCloseLock.release();
        }
    }


    private CameraCaptureSession.CaptureCallback captureCallback=new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            super.onCaptureProgressed(session, request, partialResult);
        }
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
        }
    };

    private void setAutoFlash(CaptureRequest.Builder requestBuilder ) {
        if (flashSupported) {
            requestBuilder.set(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
            );
        }
    }



}
