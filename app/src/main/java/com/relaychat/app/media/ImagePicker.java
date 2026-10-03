package com.relaychat.app.media;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import com.relaychat.app.model.ImageAttachment;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class ImagePicker {
    private static final int MAX_DIMENSION = 1536;
    private static final int JPEG_QUALITY = 85;
    private static final int MAX_BYTES = 3_500_000;

    private ImagePicker() {
    }

    public static ImageAttachment load(ContentResolver resolver, Uri uri) throws IOException {
        String type = resolver.getType(uri);
        Bitmap bitmap = null;
        try {
            bitmap = decode(resolver, uri);
            bitmap = applyRotation(bitmap, readRotation(resolver, uri));
            bitmap = scaleDown(bitmap);

            byte[] bytes;
            String mimeType;
            if ("image/png".equalsIgnoreCase(type)) {
                bytes = compress(bitmap, Bitmap.CompressFormat.PNG, 100);
                mimeType = "image/png";
                if (bytes.length > MAX_BYTES) {
                    bytes = compress(bitmap, Bitmap.CompressFormat.JPEG, JPEG_QUALITY);
                    mimeType = "image/jpeg";
                }
            } else {
                bytes = compress(bitmap, Bitmap.CompressFormat.JPEG, JPEG_QUALITY);
                mimeType = "image/jpeg";
            }
            return new ImageAttachment(mimeType, bytes);
        } finally {
            if (bitmap != null) {
                bitmap.recycle();
            }
        }
    }

    private static Bitmap decode(ContentResolver resolver, Uri uri) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = open(resolver, uri)) {
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("无法读取所选图片");
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight);
        try (InputStream input = open(resolver, uri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(input, null, options);
            if (bitmap == null) {
                throw new IOException("无法解码所选图片");
            }
            return bitmap;
        }
    }

    private static InputStream open(ContentResolver resolver, Uri uri) throws IOException {
        InputStream input = resolver.openInputStream(uri);
        if (input == null) {
            throw new IOException("无法打开所选图片");
        }
        return input;
    }

    private static int sampleSize(int width, int height) {
        int largest = Math.max(width, height);
        int size = 1;
        while (largest / (size * 2) >= MAX_DIMENSION) {
            size *= 2;
        }
        return size;
    }

    private static Bitmap scaleDown(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int largest = Math.max(width, height);
        if (largest <= MAX_DIMENSION) {
            return bitmap;
        }
        float ratio = MAX_DIMENSION / (float) largest;
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap,
                Math.max(1, Math.round(width * ratio)),
                Math.max(1, Math.round(height * ratio)), true);
        if (scaled != bitmap) {
            bitmap.recycle();
        }
        return scaled;
    }

    private static Bitmap applyRotation(Bitmap bitmap, int rotation) {
        if (rotation == 0) {
            return bitmap;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0,
                bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (rotated != bitmap) {
            bitmap.recycle();
        }
        return rotated;
    }

    private static int readRotation(ContentResolver resolver, Uri uri) {
        try (ParcelFileDescriptor descriptor = resolver.openFileDescriptor(uri, "r")) {
            if (descriptor == null) {
                return 0;
            }
            ExifInterface exif = new ExifInterface(descriptor.getFileDescriptor());
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
                return 90;
            }
            if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
                return 180;
            }
            if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
                return 270;
            }
            return 0;
        } catch (Exception error) {
            return 0;
        }
    }

    private static byte[] compress(Bitmap bitmap, Bitmap.CompressFormat format, int quality) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        bitmap.compress(format, quality, output);
        return output.toByteArray();
    }
}
