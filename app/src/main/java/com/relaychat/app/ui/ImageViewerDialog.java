package com.relaychat.app.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.relaychat.app.model.ImageAttachment;

/** Displays a sent image at its natural aspect ratio. */
public final class ImageViewerDialog extends Dialog {
    private ImageViewerDialog(Context context, ImageAttachment attachment) {
        super(context);

        ZoomPane zoomPane = new ZoomPane(context);
        zoomPane.setBackgroundColor(Color.BLACK);
        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setContentDescription("已发送图片");
        image.setImageBitmap(decode(attachment.getData()));
        zoomPane.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(zoomPane);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            window.setDimAmount(0.7f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        setCanceledOnTouchOutside(true);
    }

    public static void show(Context context, ImageAttachment attachment) {
        if (attachment == null || attachment.getData().length == 0) {
            return;
        }
        ImageViewerDialog dialog = new ImageViewerDialog(context, attachment);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            window.setGravity(Gravity.CENTER);
        }
    }

    private static Bitmap decode(byte[] data) {
        try {
            return BitmapFactory.decodeByteArray(data, 0, data.length);
        } catch (Exception | OutOfMemoryError error) {
            return null;
        }
    }
}
