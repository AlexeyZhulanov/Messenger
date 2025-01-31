package com.example.messenger.picker;

import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;
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
import com.example.messenger.BaseInfoViewModel;
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
    private final BaseInfoViewModel viewModel;
    public SubsamplingScaleImageView subsamplingScaleImageView;
    private final ImageView coverImageView;
    private final StyledPlayerView videoPlayerView;
    private ExoPlayer exoPlayer;
    private boolean isOriginalLoaded = false;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public CustomPreviewImageHolder(@NonNull View itemView, BaseInfoViewModel viewModel) {
        super(itemView);
        this.viewModel = viewModel;
        subsamplingScaleImageView = itemView.findViewById(R.id.big_preview_image);
        coverImageView = itemView.findViewById(R.id.preview_image);
        videoPlayerView = itemView.findViewById(R.id.video_player_view);
    }

    @Override
    protected void findViews(View itemView) {}

    @Override
    protected void loadImage(LocalMedia media, int maxWidth, int maxHeight) {
        resetViewHolder();
        String currentFilename = viewModel.parseOriginalFilename(media.getAvailablePath());
        Log.d("testInfoAdapter", "path: " + media.getAvailablePath() + " current: " + currentFilename);
        if (isVideoFile(currentFilename)) {
            loadVideoFromFile(media, currentFilename);
        } else {
            loadImageFromFile(media, currentFilename);
        }
    }

    private void resetViewHolder() {
        // Сбрасываем состояние оригинальной загрузки для каждого нового элемента
        isOriginalLoaded = false;
        subsamplingScaleImageView.recycle(); // Сбрасываем состояние ImageView
        if (exoPlayer != null) {
            exoPlayer.stop();
            exoPlayer.clearMediaItems();
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

    private void loadImageFromFile(LocalMedia media, String filename) {
        Glide.with(itemView.getContext())
                .asBitmap()
                .load(media.getAvailablePath())
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                        displayImage(resource);
                        loadOriginalImage(filename);
                    }

                    @Override
                    public void onLoadFailed(@Nullable Drawable errorDrawable) {}

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {}
                });
    }

    private void loadVideoFromFile(LocalMedia media, String filename) {
        Glide.with(itemView.getContext())
                .asBitmap()
                .load(media.getAvailablePath())
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                        displayImage(resource);
                        loadVideo(filename);
                    }

                    @Override
                    public void onLoadFailed(@Nullable Drawable errorDrawable) {}

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {}
                });
    }

    private void loadVideo(String filename) {
        if (isOriginalLoaded) return;
        AtomicBoolean flagExist = new AtomicBoolean(true);

        executorService.execute(() -> {
            String path;
            if(viewModel.fManagerIsExist(filename)) {
                path = viewModel.fManagerGetFilePath(filename);
            } else {
                try {
                    path = viewModel.downloadFileJava(itemView.getContext(), "photos", filename);
                    flagExist.set(false);
                } catch (Exception e) {
                    return;
                }
            }

            File originalFile = new File(path);

            if (originalFile.exists()) {
                if (!flagExist.get()) {
                    try {
                        viewModel.fManagerSaveFileJava(filename, readFileToBytes(originalFile));
                        viewModel.addTempFile(filename);
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

    private void loadOriginalImage(String filename) {
        if (isOriginalLoaded) return;
        AtomicBoolean flagExist = new AtomicBoolean(true);

        executorService.execute(() -> {
            String path;
            if(viewModel.fManagerIsExist(filename)) {
                path = viewModel.fManagerGetFilePath(filename);
            } else {
                try {
                    path = viewModel.downloadFileJava(itemView.getContext(), "photos", filename);
                    flagExist.set(false);
                } catch (Exception e) {
                    return;
                }
            }

            File originalFile = new File(path);

            if (originalFile.exists()) {
                if (!flagExist.get()) {
                    try {
                        viewModel.fManagerSaveFileJava(filename, readFileToBytes(originalFile));
                        viewModel.addTempFile(path);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                LocalMedia originalMedia = viewModel.fileToLocalMedia(originalFile);
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
