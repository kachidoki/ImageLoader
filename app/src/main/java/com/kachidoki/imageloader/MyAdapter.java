package com.kachidoki.imageloader;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;

import java.util.List;

/**
 * Created by mayiwei on 16/10/27.
 */
public class MyAdapter extends BaseAdapter {

    private Context mContext;
    private List<String> mData;
    private String mDirPath;
    private LayoutInflater mInflater;
    private ImageLoader mImageLoader;

    public MyAdapter(Context context,List<String> mData,String dirPath){
        this.mContext = context;
        this.mData = mData;
        this.mDirPath = dirPath;
        mInflater = LayoutInflater.from(mContext);
        mImageLoader = ImageLoader.getInstance(3, ImageLoader.Type.LIFO);
    }

    @Override
    public int getCount() {
        return mData.size();
    }

    @Override
    public Object getItem(int i) {
        return mData.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        ViewHolder holder = null;
        if (view == null){
            holder = new ViewHolder();
            view = mInflater.inflate(R.layout.grid_item,viewGroup,false);
            holder.mImageView = (ImageView) view.findViewById(R.id.id_item_image);
            view.setTag(holder);
        }else {
            holder = (ViewHolder) view.getTag();
        }
        mImageLoader.loadImage(mDirPath + "/" + mData.get(i),holder.mImageView);
        return view;
    }

    private final class ViewHolder
    {
        ImageView mImageView;
    }
}
