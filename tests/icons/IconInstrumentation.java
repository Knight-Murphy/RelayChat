package com.relaychat.app.icons;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.relaychat.app.model.ImageAttachment;
import com.relaychat.app.ui.ImageEditorDialog;
import com.relaychat.app.ui.UiKit;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;

public final class IconInstrumentation extends Instrumentation {
    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            verifyIcons();
            verifyEditor();
            result.putString("stream", "PASS: rotation icon rendering and clockwise action\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void verifyIcons() throws Exception {
        Bitmap sheet = Bitmap.createBitmap(420, 360, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(sheet);
        canvas.drawColor(Color.WHITE);
        Paint paint = new Paint();
        paint.setColor(Color.DKGRAY);
        paint.setTextSize(14);
        for (int row = 0; row < 3; row++) {
            Configuration config = new Configuration(getTargetContext().getResources()
                    .getConfiguration());
            config.densityDpi = 160 * (row + 1);
            Context context = getTargetContext().createConfigurationContext(config);
            Button button = UiKit.iconButton(context, UiKit.Icon.ROTATE_RIGHT, false);
            Drawable icon = button.getCompoundDrawablesRelative()[0];
            int size = icon.getIntrinsicWidth();
            Bitmap rendered = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            icon.draw(new Canvas(rendered));
            requireConnected(rendered);
            requireClearEdges(rendered);
            int y = row * 120;
            canvas.drawText("Density " + (row + 1) + "x: native / enlarged", 12, y + 20, paint);
            canvas.drawBitmap(rendered, 20, y + 34, null);
            canvas.drawBitmap(rendered, null, new Rect(150, y + 28, 238, y + 116), null);
            save(rendered, "rotate-" + (row + 1) + "x.png");
            rendered.recycle();
        }
        save(sheet, "rotate-contact-sheet.png");
        sheet.recycle();
    }

    private void verifyEditor() throws Exception {
        Intent launch = getTargetContext().getPackageManager()
                .getLaunchIntentForPackage(getTargetContext().getPackageName());
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity = startActivitySync(launch);
        Bitmap source = Bitmap.createBitmap(160, 100, Bitmap.Config.ARGB_8888);
        Canvas sourceCanvas = new Canvas(source);
        sourceCanvas.drawColor(Color.WHITE);
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        sourceCanvas.drawRect(0, 0, 80, 50, paint);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        source.compress(Bitmap.CompressFormat.PNG, 100, bytes);
        ImageAttachment attachment = new ImageAttachment("image/png", bytes.toByteArray());
        Constructor<?> constructor = Class.forName(
                "com.relaychat.app.ui.ImageEditorDialog$EditorDialog").getDeclaredConstructor(
                Context.class, ImageAttachment.class, Bitmap.class,
                ImageEditorDialog.OnApplyListener.class);
        constructor.setAccessible(true);
        ImageAttachment[] applied = new ImageAttachment[1];
        Dialog[] dialog = new Dialog[1];
        runOnMainSync(() -> {
            try {
                dialog[0] = (Dialog) constructor.newInstance(activity, attachment, source,
                        (ImageEditorDialog.OnApplyListener) value -> applied[0] = value);
                dialog[0].show();
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        });
        waitForIdleSync();
        final Bitmap[] screenshot = new Bitmap[1];
        final Bitmap[] buttonScreenshot = new Bitmap[1];
        runOnMainSync(() -> {
            View decor = dialog[0].getWindow().getDecorView();
            screenshot[0] = Bitmap.createBitmap(decor.getWidth(), decor.getHeight(),
                    Bitmap.Config.ARGB_8888);
            decor.draw(new Canvas(screenshot[0]));
            Button rotate = findButton(decor, true);
            if (rotate == null) {
                throw new AssertionError("Rotation button is missing");
            }
            buttonScreenshot[0] = Bitmap.createBitmap(rotate.getWidth(), rotate.getHeight(),
                    Bitmap.Config.ARGB_8888);
            rotate.draw(new Canvas(buttonScreenshot[0]));
            rotate.performClick();
        });
        save(screenshot[0], "editor.png");
        screenshot[0].recycle();
        save(buttonScreenshot[0], "rotate-button.png");
        buttonScreenshot[0].recycle();
        waitForIdleSync();
        runOnMainSync(() -> {
            Button apply = findButton(dialog[0].getWindow().getDecorView(), false);
            if (apply == null) {
                throw new AssertionError("Apply button is missing");
            }
            apply.performClick();
        });
        if (applied[0] == null) {
            throw new AssertionError("No edited image was returned");
        }
        Bitmap output = android.graphics.BitmapFactory.decodeByteArray(
                applied[0].getData(), 0, applied[0].getData().length);
        if (output.getWidth() != 100 || output.getHeight() != 160
                || Color.red(output.getPixel(75, 25)) < 200
                || Color.green(output.getPixel(75, 25)) > 70) {
            throw new AssertionError("Rotation is not clockwise 90 degrees");
        }
        output.recycle();
    }

    private Button findButton(View view, boolean rotation) {
        if (view instanceof Button) {
            Button button = (Button) view;
            CharSequence description = button.getContentDescription();
            boolean match = rotation
                    ? description != null && description.toString().equals("\u65cb\u8f6c")
                    : button.getText().toString().equals("\u5b8c\u6210");
            if (match) {
                return button;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                Button found = findButton(group.getChildAt(index), rotation);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void requireConnected(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        boolean[] ink = new boolean[width * height];
        int count = 0;
        int start = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (Color.alpha(bitmap.getPixel(x, y)) >= 128) {
                    ink[y * width + x] = true;
                    start = y * width + x;
                    count++;
                }
            }
        }
        if (start < 0) {
            throw new AssertionError("Blank rotation icon");
        }
        int[] queue = new int[count];
        int head = 0;
        int tail = 1;
        queue[0] = start;
        ink[start] = false;
        while (head < tail) {
            int pixel = queue[head++];
            int x = pixel % width;
            int y = pixel / width;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx;
                    int ny = y + dy;
                    if (nx >= 0 && nx < width && ny >= 0 && ny < height
                            && ink[ny * width + nx]) {
                        ink[ny * width + nx] = false;
                        queue[tail++] = ny * width + nx;
                    }
                }
            }
        }
        if (tail != count) {
            throw new AssertionError("Arrowhead is disconnected from the arc");
        }
    }

    private void requireClearEdges(Bitmap bitmap) {
        int last = bitmap.getWidth() - 1;
        for (int i = 0; i <= last; i++) {
            if (Color.alpha(bitmap.getPixel(0, i)) != 0
                    || Color.alpha(bitmap.getPixel(last, i)) != 0
                    || Color.alpha(bitmap.getPixel(i, 0)) != 0
                    || Color.alpha(bitmap.getPixel(i, last)) != 0) {
                throw new AssertionError("Rotation icon touches the clipping boundary");
            }
        }
    }

    private void save(Bitmap bitmap, String name) throws Exception {
        File directory = new File(getTargetContext().getExternalFilesDir(null), "icon-tests");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Cannot create screenshot directory");
        }
        try (FileOutputStream output = new FileOutputStream(new File(directory, name))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        }
    }
}
