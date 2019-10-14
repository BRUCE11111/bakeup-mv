package com.example.androidh264codecproject;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.SurfaceTexture;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.widget.TextView;


import androidx.appcompat.widget.AppCompatTextView;


import com.example.androidh264codecproject.decoder.FFmpegAVCDecoder;
import com.example.androidh264codecproject.encoder.MotionVectorList;
import com.example.androidh264codecproject.encoder.MotionVectorListItem;

import java.util.concurrent.CopyOnWriteArrayList;


public class MyDrawView extends View {




    int fx,fy,dx,dy;
    MotionVectorList mvList;
    Bitmap bitmap;
    Canvas canvas1;



    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
        canvas1=new Canvas(bitmap);
    }
    public void setMvList(MotionVectorList mvList){
        this.mvList=mvList;
    }

    public MyDrawView(Context context, AttributeSet attrs){
        super(context,attrs);
    }
    public MyDrawView(Context context){
        super(context);
    }

    public MyDrawView(Context context, MotionVectorList mvList,Bitmap bitmap){
        super(context);
        this.mvList=mvList;
        this.bitmap=bitmap;
        this.canvas1=new Canvas(this.bitmap);
    }

    public void setImgSize(int width, int height) {
        mImgWidth = width;
        mImgHeight = height;
        requestLayout();
    }

    Paint p=new Paint();



    @Override
    protected void onDraw(Canvas canvas){
        super.onDraw(canvas);





        p.setStyle(Paint.Style.FILL);
        p.setAntiAlias(true);
        p.setColor(Color.RED);
        canvas.drawColor(Color.TRANSPARENT);
        p.setStrokeWidth(5);

        //canvas.drawCircle(50,105,10,p);




        if (MainActivity.vectorLists.size()>0) {
            mvList=MainActivity.vectorLists.poll();
            //Log.d(TAG, String.format(Locale.CHINA, "MV List Count: %d", mvList.getCount()));
            for (int i = 0; i < mvList.getCount(); i++) {
                MotionVectorListItem item = mvList.getItem(i);
                int mvX = item.getMvX();
                int mvY = item.getMvY();
                int posX = item.getPosX();
                int posY = item.getPosY();
                int sizeX = item.getSizeX();
                int sizeY = item.getSizeY();
                fx=posX;
                fy=posY;
                dx=mvX+posX;
                dy=mvY+posY;
                canvas.drawLine(fx,fy,dx,dy,p);

                Log.d("resultmotion",""+mvX+" "+ mvY+" "+posX+" "+posY+" "+sizeX+" "+sizeY+"");
            }
        }


        float juli=(float) Math.sqrt((dx-fx)*(dy-fy)+(dy-fy)*(dy-fy));
        float juliX=(float)dx-fx;
        float juliY=(float)dy-fy;
        //float dianX=(float) dx-()

    }

    private int mRatioWidth = 0;
    private int mRatioHeight = 0;

    private final CopyOnWriteArrayList<PointF> mDrawPoint = new CopyOnWriteArrayList<>();
    private int mWidth, mHeight;
    private float mRatioX, mRatioY;
    private int mImgWidth, mImgHeight;



    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (0 == mRatioWidth || 0 == mRatioHeight) {
            setMeasuredDimension(width, height);
        } else {
            if (width < height * mRatioWidth / mRatioHeight) {
                mWidth = width;
                mHeight = width * mRatioHeight / mRatioWidth;
            } else {
                mWidth = height * mRatioWidth / mRatioHeight;
                mHeight = height;
            }
        }

        setMeasuredDimension(mWidth, mHeight);

        mRatioX = ((float) mImgWidth) / mWidth;
        mRatioY = ((float) mImgHeight) / mHeight;
    }

}
