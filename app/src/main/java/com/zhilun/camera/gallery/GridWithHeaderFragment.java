package com.zhilun.camera.gallery;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.bumptech.glide.Glide;
import com.zhilun.camera.fisheyecamera.R;

import java.io.File;
import java.util.List;
import java.util.Locale;

public class GridWithHeaderFragment extends Fragment {
    private static final String TAG = "GridWithHeaderFragment";
    private static final int COLUMN_COUNT_DEFAULT = 4;
    private SectionedRecyclerViewAdapter sectionAdapter;
    private ThumbnailLoader thumbnailLoader;
    FileUtils fileUtils;
    RecyclerView recyclerView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_grid_with_header, container, false);
        sectionAdapter = new SectionedRecyclerViewAdapter();
        thumbnailLoader = new ThumbnailLoader();
        String imageDir = Environment.getExternalStorageDirectory().toString() + "/DCIM/Camera";
        fileUtils = new FileUtils(this.getContext(), imageDir, new String[]{"image", "video"});
        for (FileGroup list : fileUtils.getFileGroupList()) {
            sectionAdapter.addSection(new MovieSection(this.getContext(), list.getName(), list.getFileList()));
        }

        recyclerView = (RecyclerView) view.findViewById(R.id.recyclerview);
        recyclerView.setHasFixedSize(true);
        GridLayoutManager glm = new GridLayoutManager(getContext(), COLUMN_COUNT_DEFAULT);
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                switch(sectionAdapter.getSectionItemViewType(position)) {
                    case SectionedRecyclerViewAdapter.VIEW_TYPE_HEADER:
                        return COLUMN_COUNT_DEFAULT;
                    default:
                        return 1;
                }
            }
        });
        recyclerView.setLayoutManager(glm);
        //recyclerView.setLayoutManager(new StaggeredGridLayoutManager(4, StaggeredGridLayoutManager.VERTICAL));
        recyclerView.setAdapter(sectionAdapter);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        if (getActivity() instanceof AppCompatActivity) {
            AppCompatActivity activity = ((AppCompatActivity) getActivity());
            if (activity.getSupportActionBar() != null)
                activity.getSupportActionBar().setTitle(R.string.title_activity_gallery);
        }
    }

    class MovieSection extends StatelessSection {

        Context context;
        String title;
        List<FileInfo> list;

        public MovieSection(Context context, String title, List<FileInfo> list) {
            super(R.layout.section_grid_header, R.layout.section_grid_item);
            this.context = context;
            this.title = title;
            this.list = list;
        }

        @Override
        public int getContentItemsTotal() {
            return list.size();
        }

        @Override
        public RecyclerView.ViewHolder getItemViewHolder(View view) {
            return new ItemViewHolder(view);
        }

        @Override
        public void onBindItemViewHolder(RecyclerView.ViewHolder holder, int position) {
            final ItemViewHolder itemHolder = (ItemViewHolder) holder;
            final String name = list.get(position).getDisplay_name();
            final String category = list.get(position).getTitle();
            final String url = list.get(position).getUrl();

            itemHolder.rootView.setTag(list.get(position).getUrl());
            itemHolder.tvItem.setText(name);
            itemHolder.tvSubItem.setText(category);
            itemHolder.imgSubItem.setVisibility(View.INVISIBLE);
            Log.d(TAG, String.format(Locale.US, "onBindItemViewHolder:%s", list.get(position).getUrl()));

            if (list.get(position).getMime_type().startsWith("video")) {
                thumbnailLoader.showThumbnailByAsyncTask(list.get(position), itemHolder);
            } else if (list.get(position).getMime_type().startsWith("image")) {
                Glide.with(context).load(list.get(position).getUrl())
                        .placeholder(R.drawable.default_image) //加载中显示的图片
                        .error(R.drawable.ic_missing_thumbnail_picture) //加载失败时显示的图片
                        .thumbnail(0.5f) //50%的原图大小
                        //.override(200,200) //设置最终显示的图片像素为80*80,注意:这个是像素,而不是控件的宽高
                        .crossFade() //淡入显示的时间,注意:如果设置了这个,则必须要去掉asBitmap
                        .centerCrop() //中心裁剪,缩放填充至整个ImageView
                        //.skipMemoryCache(true) //跳过内存缓存
                        //.diskCacheStrategy(DiskCacheStrategy.ALL)//DiskCacheStrategy.NONE:什么都不缓存
                        //DiskCacheStrategy.SOURCE:仅缓存原图(全分辨率的图片)//DiskCacheStrategy.RESULT:仅缓存最终的图片,即修改了尺寸或者转换后的图片
                        //DiskCacheStrategy.ALL:缓存所有版本的图片,默认模式
                        .into(itemHolder.imgItem);
            } else {
            }

            itemHolder.rootView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    //Toast.makeText(getContext(), String.format("Clicked on position #%s[%s] of Section %s",
                    //        sectionAdapter.getSectionPosition(itemHolder.getAdapterPosition()), name, category),
                    //        Toast.LENGTH_SHORT).show();

                }
            });
        }

        @Override
        public RecyclerView.ViewHolder getHeaderViewHolder(View view) {
            return new HeaderViewHolder(view);
        }

        @Override
        public void onBindHeaderViewHolder(RecyclerView.ViewHolder holder) {
            HeaderViewHolder headerHolder = (HeaderViewHolder) holder;
            headerHolder.tvTitle.setText(title);
            headerHolder.btnMore.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Toast.makeText(getContext(),
                            String.format("Clicked on more button from the header of Section %s", title),
                            Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    class HeaderViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvTitle;
        private final Button btnMore;

        public HeaderViewHolder(View view) {
            super(view);

            tvTitle = (TextView) view.findViewById(R.id.tvTitle);
            btnMore = (Button) view.findViewById(R.id.btnMore);
        }
    }

    class ItemViewHolder extends RecyclerView.ViewHolder {

        final View rootView;
        final ImageView imgItem;
        final ImageView imgSubItem;
        final TextView tvItem;
        final TextView tvSubItem;

        public ItemViewHolder(View view) {
            super(view);
            rootView = view;
            imgItem = (ImageView) view.findViewById(R.id.imgItem);
            imgSubItem = (ImageView) view.findViewById(R.id.imgSubItem);
            tvItem = (TextView) view.findViewById(R.id.tvItem);
            tvSubItem = (TextView) view.findViewById(R.id.tvSubItem);
        }
    }
}
