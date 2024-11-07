package com.example.messenger.picker;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.example.messenger.MessageViewModel;
import com.example.messenger.R;
import com.luck.picture.lib.adapter.PicturePreviewAdapter;
import com.luck.picture.lib.adapter.holder.BasePreviewHolder;

public class CustomPreviewAdapter extends PicturePreviewAdapter {
    private final MessageViewModel messageViewModel;
    private final String filename;

    public CustomPreviewAdapter(MessageViewModel messageViewModel, String filename) {
        this.messageViewModel = messageViewModel;
        this.filename = filename;
    }

    @NonNull
    @Override
    public BasePreviewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == BasePreviewHolder.ADAPTER_TYPE_IMAGE) {
            View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.ps_custom_preview_image, parent, false);
            return new CustomPreviewImageHolder(itemView, messageViewModel, filename);
        } else {
            return super.onCreateViewHolder(parent, viewType);
        }
    }
}
