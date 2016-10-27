package com.kachidoki.imageloader;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.util.LruCache;
import android.util.DisplayMetrics;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Created by mayiwei on 16/10/27.
 */
public class ImageLoader {

    private LruCache<String,Bitmap> mLruCache;

    private ExecutorService mThreadPool;

    private int mThreadCount = 1;

    private Type mType = Type.LIFO;

    private LinkedList<Runnable> mTasks;

    private Thread mPoolThread;
    private Handler mPoolThreadHander;

    private Handler mHandler;

    //引用一个值为1的信号量由于线程池内部也有一个阻塞线程，防止加入任务的速度过快，使LIFO效果不明显
    private volatile Semaphore mSemaphore = new Semaphore(1);

    private volatile Semaphore mPoolSemaphore;

    private static ImageLoader mInstance;


    private class ImgBeanHolder
    {
        Bitmap bitmap;
        ImageView imageView;
        String path;
    }

    private class ImageSize
    {
        int width;
        int height;
    }


    public enum Type{
        FIFO,LIFO
    }

    public static ImageLoader getInstance(){
        if (mInstance == null){
            synchronized (ImageLoader.class){
                if (mInstance ==null){
                    mInstance = new ImageLoader(1,Type.LIFO);
                }
            }
        }
        return mInstance;
    }

    public static ImageLoader getInstance(int threadCount, Type type)
    {

        if (mInstance == null)
        {
            synchronized (ImageLoader.class)
            {
                if (mInstance == null)
                {
                    mInstance = new ImageLoader(threadCount, type);
                }
            }
        }
        return mInstance;
    }


    private ImageLoader(int threadCount,Type type){
        init(threadCount,type);
    }

    private void init(int threadCount ,Type type){
        //loop thread
        mPoolThread = new Thread(){
            @Override
            public void run() {
                try {
                    //请求一个信号量
                    mSemaphore.acquire();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Looper.prepare();

                mPoolThreadHander = new Handler(){
                    @Override
                    public void handleMessage(Message msg) {
                        mThreadPool.execute(getTask());
                        try {
                            mSemaphore.acquire();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                };

                //释放一个信号量
                mSemaphore.release();
                Looper.loop();
            }
        };
        mPoolThread.start();

        int maxMemory = (int) Runtime.getRuntime().maxMemory();
        int cacheSize = maxMemory/8;
        mLruCache = new LruCache<String, Bitmap>(cacheSize){
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getRowBytes()*value.getHeight();
            }
        };
        //只有核心线程的线程池，没有超时且不会回收最快响应请求
        mThreadPool = Executors.newFixedThreadPool(threadCount);
        mPoolSemaphore = new Semaphore(threadCount);
        mTasks = new LinkedList<Runnable>();
        mType = type == null ? Type.LIFO : type;


    }

    public void loadImage(final String path,final ImageView imageView){
        imageView.setTag(path);

        //UI线程
        if (mHandler==null){
            mHandler = new Handler(){
                @Override
                public void handleMessage(Message msg) {
                    ImgBeanHolder holder = (ImgBeanHolder) msg.obj;
                    ImageView imageView = holder.imageView;
                    Bitmap bm = holder.bitmap;
                    String path = holder.path;
                    if (imageView.getTag().toString().equals(path)){
                        imageView.setImageBitmap(bm);
                    }
                }
            };
        }

        Bitmap bm = getBitmapFromLruCache(path);
        if (bm!=null){
            ImgBeanHolder holder = new ImgBeanHolder();
            holder.bitmap = bm;
            holder.imageView = imageView;
            holder.path = path;
            Message message = Message.obtain();
            message.obj = holder;
            mHandler.sendMessage(message);
        }else {
            addTask(new Runnable(){

                @Override
                public void run() {
                    ImageSize imageSize = getImageViewWidth(imageView);

                    int reqWith = imageSize.width;
                    int reqHeight = imageSize.height;

                    Bitmap bm = decodeSampledBitmapFromResource(path,reqWith,reqHeight);
                    addBitmapToLruCache(path,bm);
                    ImgBeanHolder holder = new ImgBeanHolder();
                    holder.bitmap = getBitmapFromLruCache(path);
                    holder.imageView = imageView;
                    holder.path = path;
                    Message message = Message.obtain();
                    message.obj = holder;
                    mHandler.sendMessage(message);
                    mPoolSemaphore.release();
                }
            });
        }
    }

    private synchronized void addTask(Runnable runnable){
        try {
            if (mPoolSemaphore==null){
                mPoolSemaphore.acquire();
            }
        } catch (InterruptedException e) {
        e.printStackTrace();
        }
        mTasks.add(runnable);
        mPoolThreadHander.sendEmptyMessage(0x110);
    }

    private synchronized Runnable getTask(){
        if (mType==Type.FIFO) {
            return mTasks.removeFirst();
        }else if (mType==Type.LIFO){
            return mTasks.removeLast();
        }
        return null;
    }


    /**
     * 根据ImageView获得适当的压缩的宽和高
     *
     * @param imageView
     * @return
     */
    private ImageSize getImageViewWidth(ImageView imageView)
    {
        ImageSize imageSize = new ImageSize();
        final DisplayMetrics displayMetrics = imageView.getContext()
                .getResources().getDisplayMetrics();
        final ViewGroup.LayoutParams params = imageView.getLayoutParams();

        int width = params.width == ViewGroup.LayoutParams.WRAP_CONTENT ? 0 : imageView
                .getWidth(); // Get actual image width
        if (width <= 0)
            width = params.width; // Get layout width parameter
        if (width <= 0)
            width = getImageViewFieldValue(imageView, "mMaxWidth"); // Check
        // maxWidth
        // parameter
        if (width <= 0)
            width = displayMetrics.widthPixels;
        int height = params.height == ViewGroup.LayoutParams.WRAP_CONTENT ? 0 : imageView
                .getHeight(); // Get actual image height
        if (height <= 0)
            height = params.height; // Get layout height parameter
        if (height <= 0)
            height = getImageViewFieldValue(imageView, "mMaxHeight"); // Check
        // maxHeight
        // parameter
        if (height <= 0)
            height = displayMetrics.heightPixels;
        imageSize.width = width;
        imageSize.height = height;
        return imageSize;

    }


    private int calculateInSampleSize(BitmapFactory.Options options,
                                      int reqWidth, int reqHeight)
    {
        // 源图片的宽度
        int width = options.outWidth;
        int height = options.outHeight;
        int inSampleSize = 1;

        if (width > reqWidth && height > reqHeight)
        {
            // 计算出实际宽度和目标宽度的比率
            int widthRatio = Math.round((float) width / (float) reqWidth);
            int heightRatio = Math.round((float) width / (float) reqWidth);
            inSampleSize = Math.max(widthRatio, heightRatio);
        }
        return inSampleSize;
    }


    /**
     * 根据计算的inSampleSize，得到压缩后图片
     *
     * @param pathName
     * @param reqWidth
     * @param reqHeight
     * @return
     */
    private Bitmap decodeSampledBitmapFromResource(String pathName,
                                                   int reqWidth, int reqHeight)
    {
        // 第一次解析将inJustDecodeBounds设置为true，来获取图片大小
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(pathName, options);
        // 调用上面定义的方法计算inSampleSize值
        options.inSampleSize = calculateInSampleSize(options, reqWidth,
                reqHeight);
        // 使用获取到的inSampleSize值再次解析图片
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeFile(pathName, options);

        return bitmap;
    }


    /**
     * 反射获得ImageView设置的最大宽度和高度
     *
     * @param object
     * @param fieldName
     * @return
     */
    private static int getImageViewFieldValue(Object object, String fieldName)
    {
        int value = 0;
        try
        {
            Field field = ImageView.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            int fieldValue = (Integer) field.get(object);
            if (fieldValue > 0 && fieldValue < Integer.MAX_VALUE)
            {
                value = fieldValue;

            }
        } catch (Exception e)
        {
        }
        return value;
    }



    private Bitmap getBitmapFromLruCache(String key)
    {
        return mLruCache.get(key);
    }

    private void addBitmapToLruCache(String key, Bitmap bitmap)
    {
        if (getBitmapFromLruCache(key) == null)
        {
            if (bitmap != null)
                mLruCache.put(key, bitmap);
        }
    }
}
