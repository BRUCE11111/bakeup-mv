package com.example.androidh264codecproject.encoder;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;

import android.graphics.YuvImage;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import androidx.annotation.NonNull;
import androidx.camera.core.ImageProxy;
import androidx.constraintlayout.solver.GoalRow;
import androidx.lifecycle.LifecycleOwner;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.example.androidh264codecproject.MainActivity;
import com.example.androidh264codecproject.decoder.DecoderCallback;
import com.example.androidh264codecproject.decoder.FFmpegAVCDecoderCallback;
import com.example.androidh264codecproject.yuvtools.YUVFileReader;
import com.example.androidh264codecproject.yuvtools.YUVFormatTransformTool;
import com.example.androidh264codecproject.yuvtools.YUVI420FileReader;

import static android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
import static android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM;
import static android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME;

public class AVCEncoder {

    private static final String TAG = "MediaCodec";

    private static final int TIMEOUT_USEC = 12000;
    private static final int DEFAULT_OUT_DATA_SIZE = 4096;

    private int mWidth;
    private int mHeight;
    private int mFrameRate;

    private MediaCodec mMediaCodec;

    private YUVI420FileReader mYUVFileReader;
    //private String mOutputPath;
    //private String mOutputFilename;
    private BufferedOutputStream mOutputStream = null;
    private BufferedOutputStream outputStream;

    private boolean isRunning = false;

    public FFmpegAVCDecoderCallback mDecoderCallback = new FFmpegAVCDecoderCallback(mWidth,mHeight);

    private static int yuvqueuesize = 10;

    public static ArrayBlockingQueue<byte[]> YUVQueue = new ArrayBlockingQueue<>(yuvqueuesize);




    public AVCEncoder(int width, int height, int frameRate, int bitrate,ArrayBlockingQueue<byte[]> yuvbyte) {
        mWidth     = width;
        mHeight    = height;
        mFrameRate = frameRate;
        this.YUVQueue=yuvbyte;


        final String MIME     = "video/avc";
        final float  GOP_SIZE = 12.0f;

        //mediaFormat is an android's type.It's encapsulate the information describing the format of media
        //data,be it audio or video,as well as optional feature metadata.
        //create a mimimal video format
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME, width, height);
        //set the video format
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                               MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        //set the video bitrate
        //mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, width*height*5);

        //set the video framerate
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        //mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10);
        mediaFormat.setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, GOP_SIZE/frameRate);

        // If not set KEY_I_FRAME_INTERVAL, NullPointerException will occur
        //int keyIFrameInterval = mediaFormat.getInteger(MediaFormat.KEY_I_FRAME_INTERVAL);
        //Log.i(TAG, String.format(Locale.CHINA, "keyIFrameInterval: %d", keyIFrameInterval));
        try {
            //mediacodec class is an android's class.It can be used to access low-level media codecs,
            //encoder/decoder components.It is part of the Android low-level multimedia support
            //infrastructure.
            mMediaCodec = MediaCodec.createEncoderByType(MIME);
            //configure a component to be used with a descrambler
            mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*
    public void setInputYUVFile(String filepath) {
        try {
            File file = new File(filepath);
            String filename = filepath.substring(filepath.lastIndexOf('/')+1, filepath.lastIndexOf('.'));
            mOutputFilename = filename + ".h264";
            Log.d(TAG, String.format("OutPutFileName: %s", mOutputFilename));

            mYUVFileReader = new YUVI420FileReader(file, mWidth, mHeight);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }*/


    public void setInputYUV(byte[] yuv) {
        this.yuv=yuv;

    }



    private byte[] yuv;

    public void setYUVbyte(byte[] yuv){
        this.yuv=yuv;
    }

    /*
    public void setOutputH264Path(String outputPath) {
        mOutputPath = outputPath;

        createOutputH264File();
    }

    public void setDecoderCallback(DecoderCallback callback) {
        mDecoderCallback = callback;
    }

    private String buildOutputFilePath() {
        return mOutputPath + File.separator + mOutputFilename;
    }

    private void createOutputH264File() {
        File file = new File(buildOutputFilePath());
        file.deleteOnExit();
        try {
            mOutputStream = new BufferedOutputStream(new FileOutputStream(file));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
*/
    private void stop() {
        try {
            mMediaCodec.stop();
            mMediaCodec.release();

            if (mYUVFileReader != null)
                mYUVFileReader.close();
            if (mOutputStream != null)
                mOutputStream.close();
            if (mDecoderCallback != null)
                mDecoderCallback.close();

            Log.d(TAG, "Stop codec success");
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    /*public void stopThread() {
        isRunning = false;
        try {
            stop();
            mOutputStream.flush();
            mOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }*/

    public void start() {
        mMediaCodec.start();
        Thread encoderThread = new Thread(new EncodeRunnable());
        encoderThread.start();
    }

    public void startAsync() {
        //set an asynchronous callback for actionable MediaCodec events on the default looper
        HandlerThread handlerThread=new HandlerThread("asyrun");
        handlerThread.start();
        Handler handler=new Handler(handlerThread.getLooper());
        mMediaCodec.setCallback(new AVCCallback(),handler);
        mMediaCodec.start();
    }

    /*private void NV21ToNV12(byte[] nv21,byte[] nv12,int width,int height) {
        if(nv21 == null || nv12 == null)
            return;
        int frameSize = width*height;
        int i, j;
        System.arraycopy(nv21, 0, nv12, 0, frameSize);
        for (i = 0; i < frameSize; i++) {
            nv12[i] = nv21[i];
        }
        for (j = 0; j < frameSize/2; j+=2) {
            nv12[frameSize + j-1] = nv21[j+frameSize];
        }
        for (j = 0; j < frameSize/2; j+=2) {
            nv12[frameSize + j] = nv21[j+frameSize-1];
        }
    }*/



    /**
     * @Description: Generates the presentation time for frame N,
     *               in microseconds.
     */
    public byte[] configbyte;
    private long computePresentationTime(long frameIndex) {
        return 132+frameIndex * 100000 / mFrameRate;
    }

    private class EncodeRunnable implements Runnable {
        @Override
        public void run() {
            isRunning = true;
            byte[] input = null;
            long pts = 0;
            long generateIndex = 0;
            int frameIdx = 0;
            long startMs, encTimeSum = 0;
            long readFrameTime = 0;
            byte[] configByte = null;
            byte[] outData = new byte[DEFAULT_OUT_DATA_SIZE];
            byte[] keyFrameData = new byte[DEFAULT_OUT_DATA_SIZE];

            while (isRunning) {
      /*
                if (MainActivity.YUVQueue.size() >0){
                    input = MainActivity.YUVQueue.poll();
                    //byte[] yuv420sp = new byte[m_width*m_height*3/2];
                    //NV21ToNV12(input,yuv420sp,m_width,m_height);
                    //input = yuv420sp;
                }
                //Log.i("hello","where1");

                if(input!=null){
                    try {
                        //long startMs = System.currentTimeMillis();

                        //ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
                        //ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();

                        int inputBufferIndex = mMediaCodec.dequeueInputBuffer(-1);
                        //Log.i("hello","what happened");

                        if (inputBufferIndex >= 0) {
                            pts = computePresentationTime(generateIndex);
                            ByteBuffer inputBuffer = mMediaCodec.getInputBuffer(inputBufferIndex);
                            //ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                            inputBuffer.clear();
                            inputBuffer.put(input);
                            mMediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, pts, 0);
                            generateIndex += 1;
                            Log.i("hello","where2");
                        }


                        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                        int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
                        if(outputBufferIndex<0){
                            Log.i("why","OH MY GOD  ");
                        }
                        while (outputBufferIndex >= 0) {
                            Log.i("hello","where4");
                            //Log.i("AvcEncoder", "Get H264 Buffer Success! flag = "+bufferInfo.flags+",pts = "+bufferInfo.presentationTimeUs+"");
                            ByteBuffer outputBuffer = mMediaCodec.getOutputBuffer(outputBufferIndex);
                            byte[] outData = new byte[bufferInfo.size];
                            outputBuffer.get(outData);
                            if(bufferInfo.flags == 2){
                                configbyte = new byte[bufferInfo.size];
                                configbyte = outData;
                                Log.i("key","compression");
                            }else if(bufferInfo.flags == 1){
                                byte[] keyframe = new byte[bufferInfo.size + configbyte.length];
                                System.arraycopy(configbyte, 0, keyframe, 0, configbyte.length);
                                System.arraycopy(outData, 0, keyframe, configbyte.length, outData.length);
                                Log.i("key","keykeykey");

                                outputStream.write(keyframe, 0, keyframe.length);
                            }else{
                                outputStream.write(outData, 0, outData.length);

                                if (mDecoderCallback != null) {
                                    mDecoderCallback.call(outData, outData.length);
                                }
                            }

                            mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                            outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
                        }

                    } catch (Throwable t) {
                        t.printStackTrace();
                    }

                    //Log.i("hello","process data");

                }else {
                    //Log.i("hello","where3");
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }




            }
*/
                /*
                // Read frame from MainActivity
                if (MainActivity.YUVQueue.size() >0){
                    input = MainActivity.YUVQueue.poll();
                    byte[] yuv420sp = new byte[mWidth * mHeight *3/2];
                    NV21ToNV12(input,yuv420sp, mWidth, mHeight);
                    input = yuv420sp;
                }*/

                // Read YUV frame from file
                /*
                try {
                    startMs = System.currentTimeMillis();
                    //input = mYUVFileReader.readFrameData();
                    input=yuv;

                    // input = YUVFormatTransformTool.I420ToYV12(input, mWidth, mHeight);
                    readFrameTime = System.currentTimeMillis() - startMs;
                }
                catch (IOException e) {
                    e.printStackTrace();
                    input = null;
                }*/

                try {
                    Log.i("hello","Handle the yuv420 data");
                    startMs = System.currentTimeMillis();
                    //input = mYUVFileReader.readFrameData();
                    if (YUVQueue.size() >0){
                        input = YUVQueue.poll();
                        //byte[] yuv420sp = new byte[m_width*m_height*3/2];
                        //NV21ToNV12(input,yuv420sp,m_width,m_height);
                        //input = yuv420sp;
                        //Log.i("image",input.length+" lenth");

                    // input = YUVFormatTransformTool.I420ToYV12(input, mWidth, mHeight);
                    readFrameTime = System.currentTimeMillis() - startMs;


                    startMs = System.currentTimeMillis();
                    //ByteBuffer[] inputBuffers  = mMediaCodec.getInputBuffers();
                    //ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();
                    int inputBufferIndex = mMediaCodec.dequeueInputBuffer(-1);
                    if (inputBufferIndex >= 0) {
                        if (input != null) {
                            pts = computePresentationTime(generateIndex);
                            //ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                            ByteBuffer inputBuffer = mMediaCodec.getInputBuffer(inputBufferIndex);
                            if (inputBuffer != null) {
                                inputBuffer.clear();
                                inputBuffer.put(input);
                            }
                            mMediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, pts, 0);
                            generateIndex += 1;

                        } else {
                            // Set the flag of end of stream
                            Log.i("hello1","input the queueINputBuffer");
                            mMediaCodec.queueInputBuffer(inputBufferIndex, 0, 0, 0,
                                                         BUFFER_FLAG_END_OF_STREAM);
                        }
                    }

                    long queueInMs = System.currentTimeMillis() - startMs;

                    startMs = System.currentTimeMillis();

                    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                    int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(
                                                            bufferInfo, TIMEOUT_USEC);

                    while (outputBufferIndex >= 0) {

                       //
                        // Log.i("why","your are right");
                        if ((bufferInfo.flags & BUFFER_FLAG_END_OF_STREAM) != 0) {
                            Log.d(TAG, "End of output buffer");
                            break;
                        }

                        //Log.i("AVCEncoder",
                        //      "Get H264 Buffer Success! flag = "+
                        //      bufferInfo.flags+
                        //      ",pts = "+bufferInfo.presentationTimeUs+"");

                        //ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                        ByteBuffer outputBuffer = mMediaCodec.getOutputBuffer(outputBufferIndex);
                        if (outputBuffer != null) {
                            // NOTE: bufferInfo.size may change here
                            if (bufferInfo.size > outData.length)
                                outData = new byte[bufferInfo.size];
                            outputBuffer.get(outData, 0, bufferInfo.size);
                        }

                        if (bufferInfo.flags == 0) {
                            if (mOutputStream != null){
                               // mOutputStream.write(outData, 0, outData.length);
                            }
                            if (mDecoderCallback != null) {
                                mDecoderCallback.call(outData, outData.length);
                               // Log.d("callback1","decoder");

                            }else{

                            }
                        } else if (bufferInfo.flags == BUFFER_FLAG_KEY_FRAME) {
                            assert(configByte != null);
                            int outSize = bufferInfo.size + configByte.length;
                            if (outSize > keyFrameData.length)
                                keyFrameData = new byte[outSize];
                            // Config data
                            System.arraycopy(configByte, 0,
                                    keyFrameData, 0, configByte.length);
                            // Frame data
                            System.arraycopy(outData,    0,
                                    keyFrameData, configByte.length, bufferInfo.size);

                            if (mOutputStream != null){
                               // mOutputStream.write(keyFrameData, 0, keyFrameData.length);
                            }

                            if (mDecoderCallback != null){
                                mDecoderCallback.call(keyFrameData, outSize);
                                //Log.d("callback1","decoder1");
                            }
                        } else if (bufferInfo.flags == BUFFER_FLAG_CODEC_CONFIG) {
                            configByte = new byte[bufferInfo.size];
                            //configByte = outData; // BUG: Must create a buffer for configByte
                            System.arraycopy(outData, 0, configByte, 0, bufferInfo.size);
                        }

                        mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                        outputBufferIndex = mMediaCodec.dequeueOutputBuffer(
                                                            bufferInfo, TIMEOUT_USEC);
                    }

                    long queueOutMs = System.currentTimeMillis() - startMs;
                    Log.i("hello", String.format(
                            "Frame %d encoding finished, read %d ms, qIn %d ms, qOut %d ms",
                            frameIdx, readFrameTime, queueInMs, queueOutMs));

                    encTimeSum += (queueInMs + queueOutMs);

                    // No more frame to be encoded, exit
                    if (input == null) {
                        //Log.d(TAG, "End of input frames");
                        //break;
                    }

                    frameIdx ++;}
                } catch (Throwable t) {
                    t.printStackTrace();
                }

            }

            //Log.i("endhello", String.format(Locale.CHINA,
                                //"Encoding finished, avg %.2f FPS", 1000.0f * frameIdx / encTimeSum));
            stop();
            Log.i("end","end process");
        }
    }

    private class AVCCallback extends MediaCodec.Callback {

        private long lastEncTime = -1;
        private int frameIndex = 0;

        private boolean endInputBuffer  = false;
        private boolean endOutputBuffer = false;

        private int mGenerateIndex;
        private byte[] mConfigByte;
        private byte[] mOutData;
        private byte[] mKeyFrameData;

        private AVCCallback() {
            mGenerateIndex = 0;
            mConfigByte    = new byte[DEFAULT_OUT_DATA_SIZE];
            mOutData       = new byte[DEFAULT_OUT_DATA_SIZE];
            mKeyFrameData  = new byte[DEFAULT_OUT_DATA_SIZE];
        }

        long encTime=0;
        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
            encTime=System.currentTimeMillis();

            ByteBuffer inputBuffer = mMediaCodec.getInputBuffer(index);
            inputBuffer.clear();
            byte [] dataSources = YUVQueue.poll();
            int length = 0;
            if(dataSources != null) {
                inputBuffer.put(dataSources);
                length = dataSources.length;
            }
            mMediaCodec.queueInputBuffer(index,0, length,0,0);
           // Log.i("ruandecode111",""+(System.currentTimeMillis()-encTime));
            /*
            byte[] input = null;
            //try {
                //input = mYUVFileReader.readFrameData();
                //input=yuv;
            //}
            /*catch (IOException e) {
                e.printStackTrace();
            }*/
/*
            if (YUVQueue.size() >0){
                input = YUVQueue.poll();
                 Log.i("hello","data input already");
            }

            if (index >= 0) {
                if (input != null) {
                    long pts = computePresentationTime(mGenerateIndex);
                    ByteBuffer inputBuffer = codec.getInputBuffer(index);
                    if (inputBuffer != null) {
                        inputBuffer.clear();
                        inputBuffer.put(input);
                        Log.i("hello","input data mGenerateINdex");
                    }
                    codec.queueInputBuffer(index, 0, input.length, 0, 0);
                    mGenerateIndex ++;
                }

                /*
                else {
                    if (!endInputBuffer) {
                        codec.queueInputBuffer(index, 0, 0, 0, BUFFER_FLAG_END_OF_STREAM);
                        Log.d(TAG, "End of input buffer");
                        endInputBuffer = true;
                    }
                }*/

            }


        @Override
        public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index,
                                            @NonNull MediaCodec.BufferInfo info) {

            encTime=System.currentTimeMillis();
            ByteBuffer outputBuffer = mMediaCodec.getOutputBuffer(index);
            MediaFormat outputFormat = mMediaCodec.getOutputFormat(index);

            if(outputBuffer != null && info.size > 0){

                mOutData = new byte[outputBuffer.remaining()];
                outputBuffer.get(mOutData,0,info.size);
                int outSize = 0;
                if (info.flags == 0) {
                    outSize = mOutData.length;
                } else if (info.flags == BUFFER_FLAG_KEY_FRAME) {
                    outSize = info.size + mConfigByte.length;
                    if (outSize > mKeyFrameData.length)
                        mKeyFrameData = new byte[outSize];
                    System.arraycopy(mConfigByte, 0, mKeyFrameData, 0, mConfigByte.length);
                    System.arraycopy(mOutData, 0, mKeyFrameData, mConfigByte.length, info.size);
                } else if (info.flags == BUFFER_FLAG_CODEC_CONFIG) {
                    outSize     = info.size;
                    mConfigByte = new byte[outSize];
                    System.arraycopy(mOutData, 0, mConfigByte, 0, info.size);
                }

                if (lastEncTime > 0) {
                    long encTime = System.currentTimeMillis() - lastEncTime;
                    Log.i(TAG, String.format(Locale.CHINA, "frame %d, async enc time %d ms",
                            frameIndex, encTime));
                }




                if (info.flags == 0) {
                    //if (mOutputStream != null)
                    //mOutputStream.write(mOutData, 0, mOutData.length);
                    if (mDecoderCallback != null)
                        mDecoderCallback.call(mOutData, mOutData.length);
                    Log.i("hello","processing");
                } else if (info.flags == BUFFER_FLAG_KEY_FRAME) {
                    //if (mOutputStream != null)
                    //mOutputStream.write(mKeyFrameData, 0, mKeyFrameData.length);
                    if (mDecoderCallback != null)
                        mDecoderCallback.call(mKeyFrameData, outSize);
                    Log.i("hello","processing keyframe");
                }

               // Log.i("ruandecode222",""+(System.currentTimeMillis()-encTime));

            }
            mMediaCodec.releaseOutputBuffer(index, true);


/*

            Log.i("info1","run decoder");
            if (index >= 0){

                if ((info.flags & BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "End of output buffer");
                    stop();
                } else {
                    ByteBuffer outputBuffer = codec.getOutputBuffer(index);
                    if (outputBuffer != null) {
                        if (mOutData.length < info.size)
                            mOutData = new byte[info.size];
                        outputBuffer.get(mOutData, 0, info.size);
                    }

                    int outSize = 0;
                    if (info.flags == 0) {
                        outSize = mOutData.length;
                    } else if (info.flags == BUFFER_FLAG_KEY_FRAME) {
                        outSize = info.size + mConfigByte.length;
                        if (outSize > mKeyFrameData.length)
                            mKeyFrameData = new byte[outSize];
                        System.arraycopy(mConfigByte, 0, mKeyFrameData, 0, mConfigByte.length);
                        System.arraycopy(mOutData, 0, mKeyFrameData, mConfigByte.length, info.size);
                    } else if (info.flags == BUFFER_FLAG_CODEC_CONFIG) {
                        outSize     = info.size;
                        mConfigByte = new byte[outSize];
                        System.arraycopy(mOutData, 0, mConfigByte, 0, info.size);
                    }

                    if (lastEncTime > 0) {
                        long encTime = System.currentTimeMillis() - lastEncTime;
                        Log.i(TAG, String.format(Locale.CHINA, "frame %d, async enc time %d ms",
                                frameIndex, encTime));
                    }


                    long encTime=System.currentTimeMillis();

                        if (info.flags == 0) {
                            //if (mOutputStream != null)
                                //mOutputStream.write(mOutData, 0, mOutData.length);
                            if (mDecoderCallback != null)
                                mDecoderCallback.call(mOutData, mOutData.length);
                            Log.i("hello","processing");
                        } else if (info.flags == BUFFER_FLAG_KEY_FRAME) {
                            //if (mOutputStream != null)
                                //mOutputStream.write(mKeyFrameData, 0, mKeyFrameData.length);
                            if (mDecoderCallback != null)
                                mDecoderCallback.call(mKeyFrameData, outSize);
                            Log.i("hello","processing keyframe");
                        }

                    Log.i("ruandecode",""+(System.currentTimeMillis()-encTime));
                    codec.releaseOutputBuffer(index, false);

                    frameIndex ++;
                    lastEncTime = System.currentTimeMillis();
                    */

                }



        @Override
        public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {}

        @Override
        public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {}
    }

}
