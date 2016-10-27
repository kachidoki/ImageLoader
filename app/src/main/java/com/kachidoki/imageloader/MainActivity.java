package com.kachidoki.imageloader;

import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.GridLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.Toast;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ProgressDialog mProgressDialog;
    private ImageView mImageView;

    private int mPicsSize;
    private File mImgDir;
    //所有图片
    private List<String> mImgs;

    private GridView mGirdView;
    private ListAdapter mAdapter;

    private HashSet<String> mDirPaths = new HashSet<String>();

    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            mProgressDialog.dismiss();
            mImgs = Arrays.asList(mImgDir.list(new FilenameFilter() {
                @Override
                public boolean accept(File file, String s) {
                    if (s.endsWith(".jpg")){
                        return true;
                    }
                    return false;
                }
            }));
            mAdapter = new MyAdapter(getApplicationContext(),mImgs,mImgDir.getAbsolutePath());
            mGirdView.setAdapter(mAdapter);
        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mGirdView = (GridView) findViewById(R.id.id_gridView);
        getImages();

    }
    /**
     * 利用ContentProvider扫描手机中的图片，此方法在运行在子线程中 完成图片的扫描，最终获得jpg最多的那个文件夹
     */
    private void getImages()
    {
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
        {
            Toast.makeText(this, "暂无外部存储", Toast.LENGTH_SHORT).show();
            return;
        }
        // 显示进度条
        mProgressDialog = ProgressDialog.show(this, null, "正在加载...");

        new Thread(new Runnable()
        {

            @Override
            public void run()
            {
                Uri mImageUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                ContentResolver mContentResolver = MainActivity.this
                        .getContentResolver();

                // 只查询jpeg和png的图片
                Cursor mCursor = mContentResolver.query(mImageUri, null,
                        MediaStore.Images.Media.MIME_TYPE + "=? or "
                                + MediaStore.Images.Media.MIME_TYPE + "=?",
                        new String[] { "image/jpeg", "image/png" },
                        MediaStore.Images.Media.DATE_MODIFIED);

                while (mCursor.moveToNext())
                {
                    // 获取图片的路径
                    String path = mCursor.getString(mCursor
                            .getColumnIndex(MediaStore.Images.Media.DATA));
                    // 获取该图片的父路径名
                    File parentFile = new File(path).getParentFile();
                    String dirPath = parentFile.getAbsolutePath();

                    //利用一个HashSet防止多次扫描同一个文件夹（不加这个判断，图片多起来还是相当恐怖的~~）
                    if(mDirPaths.contains(dirPath))
                    {
                        continue;
                    }
                    else
                    {
                        mDirPaths.add(dirPath);
                    }

                    int picSize = parentFile.list(new FilenameFilter()
                    {
                        @Override
                        public boolean accept(File dir, String filename)
                        {
                            if (filename.endsWith(".jpg"))
                                return true;
                            return false;
                        }
                    }).length;
                    if (picSize > mPicsSize)
                    {
                        mPicsSize = picSize;
                        mImgDir = parentFile;
                    }
                }
                mCursor.close();
                //扫描完成，辅助的HashSet也就可以释放内存了
                mDirPaths = null ;
                // 通知Handler扫描图片完成
                mHandler.sendEmptyMessage(0x110);

            }
        }).start();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mProgressDialog!=null){
            mProgressDialog.dismiss();
        }
    }
}
