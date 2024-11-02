package com.example.messenger.picker;

import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.ImageViewState;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.example.messenger.MessageViewModel;
import com.example.messenger.R;
import com.luck.picture.lib.adapter.holder.BasePreviewHolder;
import com.luck.picture.lib.entity.LocalMedia;
import com.luck.picture.lib.utils.MediaUtils;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CustomPreviewImageHolder extends BasePreviewHolder {
    private final MessageViewModel messageViewModel;
    private final SubsamplingScaleImageView subsamplingScaleImageView;
    private final ImageView coverImageView; // Замените на ваш виджет для изображения обложки
    private boolean isOriginalLoaded = false;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public CustomPreviewImageHolder(@NonNull View itemView, MessageViewModel messageViewModel) {
        super(itemView);
        this.messageViewModel = messageViewModel;
        subsamplingScaleImageView = itemView.findViewById(R.id.big_preview_image);
        coverImageView = itemView.findViewById(R.id.preview_image); // Инициализация coverImageView
    }

    @Override
    protected void findViews(View itemView) {
        // subsamplingScaleImageView и coverImageView инициализированы в конструкторе
    }

    @Override
    protected void loadImage(LocalMedia media, int maxWidth, int maxHeight) {
        Glide.with(itemView.getContext())
                .asBitmap()
                .load(media.getAvailablePath())
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                        displayImage(resource, media);
                        loadOriginalImage(media);
                    }

                    @Override
                    public void onLoadFailed(@Nullable Drawable errorDrawable) {}

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {}
                });
    }

    @Override
    protected void onClickBackPressed() {}

    @Override
    protected void onLongPressDownload(LocalMedia media) {}

    private void loadOriginalImage(LocalMedia media) {
        if (isOriginalLoaded) return;

        executorService.execute(() -> {
            String path = messageViewModel.downloadFileJava(itemView.getContext(), "photos", media.getFileName());

            File originalFile = new File(path);

            if (originalFile.exists()) {
                LocalMedia originalMedia = messageViewModel.fileToLocalMedia(originalFile);
                itemView.post(() -> Glide.with(itemView.getContext())
                        .asBitmap()
                        .load(originalMedia.getAvailablePath())
                        .into(new CustomTarget<Bitmap>() {
                            @Override
                            public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                                displayImage(resource, originalMedia);
                                isOriginalLoaded = true;
                            }

                            @Override
                            public void onLoadFailed(@Nullable Drawable errorDrawable) {}

                            @Override
                            public void onLoadCleared(@Nullable Drawable placeholder) {}
                        }));
            }
        });
    }

    private void displayImage(Bitmap resource, LocalMedia media) {
        if (MediaUtils.isLongImage(resource.getWidth(), resource.getHeight())) {
            subsamplingScaleImageView.setVisibility(View.VISIBLE);
            float scale = Math.max(screenWidth / (float) resource.getWidth(), screenHeight / (float) resource.getHeight());
            subsamplingScaleImageView.setImage(ImageSource.cachedBitmap(resource), new ImageViewState(scale, new PointF(0, 0), 0));
        } else {
            subsamplingScaleImageView.setVisibility(View.GONE);
            coverImageView.setImageBitmap(resource);
        }
    }
}
