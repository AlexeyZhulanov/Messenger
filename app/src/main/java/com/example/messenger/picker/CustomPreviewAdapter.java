package com.example.messenger.picker;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.example.messenger.BaseInfoViewModel;
import com.example.messenger.R;
import com.luck.picture.lib.adapter.PicturePreviewAdapter;
import com.luck.picture.lib.adapter.holder.BasePreviewHolder;

public class CustomPreviewAdapter extends PicturePreviewAdapter {
    private final BaseInfoViewModel viewModel;

    public CustomPreviewAdapter(BaseInfoViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @NonNull
    @Override
    public BasePreviewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == BasePreviewHolder.ADAPTER_TYPE_IMAGE) {
            View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.ps_custom_preview_image, parent, false);
            return new CustomPreviewImageHolder(itemView, viewModel);
        } else {
            return super.onCreateViewHolder(parent, viewType);
        }
    }
}
