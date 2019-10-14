package com.example.androidh264codecproject;



import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageAnalysisConfig;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureConfig;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.PreviewConfig;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import android.app.Activity;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Rational;
import android.util.Size;
import android.view.TextureView;
import android.view.ViewGroup;
import android.widget.Toast;
import java.util.concurrent.ArrayBlockingQueue;
import com.example.androidh264codecproject.decoder.FFmpegAVCDecoder;
import com.example.androidh264codecproject.decoder.FFmpegAVCDecoderCallback;
import com.example.androidh264codecproject.encoder.AVCEncoder;
import com.example.androidh264codecproject.encoder.BitrateTool;
import com.example.androidh264codecproject.encoder.MotionVectorList;
import java.nio.ByteBuffer;
import android.graphics.Rect;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;


public class MainActivity extends AppCompatActivity implements LifecycleOwner{




    public int width=800;
    public int height=600;
    TextureView textureView;
    private AutoFitTextureView fitTextureView;
    private AutoFitFrameLayout layout_frame;

    private static int yuvqueuesize = 10;

    MyDrawView myDrawView;
    private MotionVectorList mvList;

    public static ArrayBlockingQueue<MotionVectorList> vectorLists=new ArrayBlockingQueue<MotionVectorList>(10);
    public static ArrayBlockingQueue<byte[]> YUVQueue = new ArrayBlockingQueue<byte[]>(yuvqueuesize);
    AVCEncoder encoder = new AVCEncoder(width,height,30, BitrateTool.getAdaptiveBitrate(
            width,height),YUVQueue);
    public FFmpegAVCDecoderCallback mDecoderCallback1 = new FFmpegAVCDecoderCallback(width,height);


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



    public void putYUVData(byte[] buffer, int length) {
        if (YUVQueue.size() >= 10) {
            YUVQueue.poll();
        }
        YUVQueue.add(buffer);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,int[] grantResults){
    //    if(requestCode==REQUEST_)
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            //Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this, mLoaderCallback);
        } else {
            //Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }

        encoder.startAsync();  // Async Mode
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    //Log.i(TAG, "OpenCV loaded successfully");
//                    mOpenCvCameraView.enableView();
//                    mOpenCvCameraView.setOnTouchListener(ColorBlobDetectionActivity.this);
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    private static byte[] getDataFromImage(ImageProxy image){
        Rect crop=image.getCropRect();
        int format=image.getFormat();
        Log.d("formatimage","msg: "+format+"");
        int width=crop.width();
        int height=crop.height();
        ImageProxy.PlaneProxy[] planes=image.getPlanes();
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
                    if(format==ImageFormat.YUV_420_888){
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



    private class LuminosityAnalyzer implements ImageAnalysis.Analyzer {

        @Override
        public void analyze(ImageProxy image,int rotationDegrees){

            //ImageProxy.PlaneProxy[] baos=image.getPlanes();


            byte[] yuv;



            yuv=getDataFromImage(image);
            putYUVData(yuv,yuv.length);

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
                showui();
            }

        }
    }


    void showui(){

        if(vectorLists.size()>0){
            Activity activity=this;
            if(activity!=null){
                activity.runOnUiThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                myDrawView.invalidate();
                            }
                        });
            }
        }else{
            Log.i("youxu","draw edit0000000000000000000000000000000000000000000000000000000");
        }



    }


    private Runnable startCamera(){

        PreviewConfig previewConfig=new PreviewConfig.Builder().setTargetResolution(new Size(width,height))
                .build();
        final Preview preview=new Preview(previewConfig);
        preview.setOnPreviewOutputUpdateListener(
                new Preview.OnPreviewOutputUpdateListener() {
                    @Override
                    public void onUpdated(Preview.PreviewOutput output) {

                        ViewGroup viewGroup=(ViewGroup) fitTextureView.getParent();
                        viewGroup.removeView(fitTextureView);

                        viewGroup.addView(fitTextureView,0);
                        fitTextureView.setSurfaceTexture(output.getSurfaceTexture());

                    }
                }
        );



        HandlerThread handlerThread=new HandlerThread("LuminosityAnalyzer");
        handlerThread.start();
        ImageAnalysisConfig imageAnalysisConfig=new ImageAnalysisConfig.Builder().setTargetResolution(new Size(width,height))
                .setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
                .setCallbackHandler(new Handler(handlerThread.getLooper())).build();
        ImageAnalysis imageAnalysis=new ImageAnalysis(imageAnalysisConfig);
        imageAnalysis.setAnalyzer(new LuminosityAnalyzer());



        ImageCaptureConfig config=new ImageCaptureConfig.Builder().setTargetResolution(new Size(width,height))
                .setCaptureMode(ImageCapture.CaptureMode.MIN_LATENCY)
                .setTargetAspectRatio(new Rational(1,1)).build();
        final ImageCapture imageCapture=new ImageCapture(config);

        /*
        findViewById(R.id.capture_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            imageCapture.takePicture(new ImageCapture.OnImageCapturedListener() {
                @Override
                public void onError(ImageCapture.UseCaseError useCaseError, String message, @Nullable Throwable cause) {
                    super.onError(useCaseError, message, cause);
                }
            });
            }
        });
*/
        CameraX.bindToLifecycle((LifecycleOwner)this,imageAnalysis,preview);
        return null;
    }

    private final TextureView.SurfaceTextureListener surfaceTextureListener =
            new TextureView.SurfaceTextureListener() {

                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {

                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {

                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture texture) {
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkStoragePermission();

        fitTextureView=findViewById(R.id.texture);

        layout_frame=findViewById(R.id.layout_frame);
        layout_frame.setAspectRatio(800, 600);
        fitTextureView.setAspectRatio(800,600);


        textureView= findViewById(R.id.view_finder);
        //textureView.setSurfaceTexture();
        //fitTextureView.post(startCamera());
        myDrawView=findViewById(R.id.mydrawview);
        myDrawView.setImgSize(800,600);
        fitTextureView.post(startCamera());

        //encoder.start();  // Sync Mode



    }



    private void checkStoragePermission() {
        if (this.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            if (this.shouldShowRequestPermissionRationale(Manifest.permission
                    .WRITE_EXTERNAL_STORAGE)) {
                // Toast.makeText(this, "请开通相关权限，否则无法正常使用本应用！", Toast.LENGTH_SHORT).show();
            }

            this.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);

        }

        if (ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            if (this.shouldShowRequestPermissionRationale(Manifest.permission
                    .CAMERA)) {
                 Toast.makeText(this, "请开通相关权限，否则无法正常使用本应用！", Toast.LENGTH_SHORT).show();
            }

            this.requestPermissions(new String[]{Manifest.permission.CAMERA}, 1);

        }

    }
}
