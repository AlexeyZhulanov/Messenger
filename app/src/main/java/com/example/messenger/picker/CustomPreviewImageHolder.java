package com.example.messenger.picker;

import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
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
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.luck.picture.lib.adapter.holder.BasePreviewHolder;
import com.luck.picture.lib.entity.LocalMedia;
import com.luck.picture.lib.utils.MediaUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class CustomPreviewImageHolder extends BasePreviewHolder {
    private final MessageViewModel messageViewModel;
    private final String filename;
    private final SubsamplingScaleImageView subsamplingScaleImageView;
    private final ImageView coverImageView;
    private final StyledPlayerView videoPlayerView;
    private ExoPlayer exoPlayer;
    private boolean isOriginalLoaded = false;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public CustomPreviewImageHolder(@NonNull View itemView, MessageViewModel messageViewModel, String filename) {
        super(itemView);
        this.messageViewModel = messageViewModel;
        this.filename = filename;
        subsamplingScaleImageView = itemView.findViewById(R.id.big_preview_image);
        coverImageView = itemView.findViewById(R.id.preview_image);
        videoPlayerView = itemView.findViewById(R.id.video_player_view);
    }

    @Override
    protected void findViews(View itemView) {}

    @Override
    protected void loadImage(LocalMedia media, int maxWidth, int maxHeight) {
        if (isVideoFile(filename)) {
            loadVideoFromFile(media);
        } else {
            loadImageFromFile(media);
        }
    }

    private boolean isVideoFile(String filename) {
        // Список поддерживаемых расширений видео
        String[] videoExtensions = {".mp4", ".avi", ".mkv", ".mov", ".mpeg"};

        // Приводим имя файла к нижнему регистру и проверяем, что оно заканчивается на одно из расширений
        String lowerCaseFilename = filename.toLowerCase();
        for (String extension : videoExtensions) {
            if (lowerCaseFilename.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private void loadImageFromFile(LocalMedia media) {
        Glide.with(itemView.getContext())
                .asBitmap()
                .load(media.getAvailablePath())
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                        displayImage(resource);
                        loadOriginalImage();
                    }

                    @Override
                    public void onLoadFailed(@Nullable Drawable errorDrawable) {}

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {}
                });
    }

    private void loadVideoFromFile(LocalMedia media) {
        Glide.with(itemView.getContext())
                .asBitmap()
                .load(media.getAvailablePath())
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                        displayImage(resource);
                        loadVideo();
                    }

                    @Override
                    public void onLoadFailed(@Nullable Drawable errorDrawable) {}

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {}
                });
    }

    private void loadVideo() {
        if (isOriginalLoaded) return;
        AtomicBoolean flagExist = new AtomicBoolean(true);

        executorService.execute(() -> {
            String path;
            if(messageViewModel.fManagerIsExistJava(filename)) {
                path = messageViewModel.fManagerGetFilePathJava(filename);
            } else {
                path = messageViewModel.downloadFileJava(itemView.getContext(), "photos", filename);
                flagExist.set(false);
            }

            File originalFile = new File(path);

            if (originalFile.exists()) {
                if (!flagExist.get()) {
                    try {
                        messageViewModel.fManagerSaveFileJava(filename, readFileToBytes(originalFile));
                        messageViewModel.addTempFile(filename);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                Uri videoUri = Uri.fromFile(originalFile);
                itemView.post(() -> {
                    exoPlayer = new ExoPlayer.Builder(itemView.getContext()).build();
                    videoPlayerView.setPlayer(exoPlayer);

                    MediaItem mediaItem = MediaItem.fromUri(videoUri);
                    exoPlayer.setMediaItem(mediaItem);
                    exoPlayer.prepare();
                    exoPlayer.setPlayWhenReady(true);

                    subsamplingScaleImageView.setVisibility(View.GONE);
                    coverImageView.setVisibility(View.GONE);
                    videoPlayerView.setVisibility(View.VISIBLE);

                    isOriginalLoaded = true;
                });
            }
        });
    }

    @Override
    protected void onClickBackPressed() {}

    @Override
    protected void onLongPressDownload(LocalMedia media) {}

    public byte[] readFileToBytes(File file) throws IOException {
        return Files.readAllBytes(file.toPath());
    }

    private void loadOriginalImage() {
        if (isOriginalLoaded) return;
        AtomicBoolean flagExist = new AtomicBoolean(true);

        executorService.execute(() -> {
            String path;
            if(messageViewModel.fManagerIsExistJava(filename)) {
                path = messageViewModel.fManagerGetFilePathJava(filename);
            } else {
                path = messageViewModel.downloadFileJava(itemView.getContext(), "photos", filename);
                flagExist.set(false);
            }

            File originalFile = new File(path);

            if (originalFile.exists()) {
                if (!flagExist.get()) {
                    try {
                        messageViewModel.fManagerSaveFileJava(filename, readFileToBytes(originalFile));
                        messageViewModel.addTempFile(filename);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                LocalMedia originalMedia = messageViewModel.fileToLocalMedia(originalFile);
                itemView.post(() -> Glide.with(itemView.getContext())
                        .asBitmap()
                        .load(originalMedia.getAvailablePath())
                        .into(new CustomTarget<Bitmap>() {
                            @Override
                            public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                                displayImage(resource);
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

    private void displayImage(Bitmap resource) {
        videoPlayerView.setVisibility(View.GONE);

        if (MediaUtils.isLongImage(resource.getWidth(), resource.getHeight())) {
            subsamplingScaleImageView.setVisibility(View.VISIBLE);
            coverImageView.setVisibility(View.GONE);
            float scale = Math.max(screenWidth / (float) resource.getWidth(), screenHeight / (float) resource.getHeight());
            subsamplingScaleImageView.setImage(ImageSource.cachedBitmap(resource), new ImageViewState(scale, new PointF(0, 0), 0));
        } else {
            subsamplingScaleImageView.setVisibility(View.GONE);
            coverImageView.setVisibility(View.VISIBLE);
            coverImageView.setImageBitmap(resource);
        }
    }

    @Override
    public void onViewDetachedFromWindow() {
        super.onViewDetachedFromWindow();
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
    }

}
