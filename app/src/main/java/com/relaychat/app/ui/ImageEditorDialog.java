package com.relaychat.app.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.relaychat.app.model.ImageAttachment;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class ImageEditorDialog {
    public interface OnApplyListener {
        void onApply(ImageAttachment attachment);
    }

    private ImageEditorDialog() {
    }

    public static void show(Context context, ImageAttachment source, OnApplyListener listener) {
        Bitmap bitmap = BitmapFactory.decodeByteArray(source.getData(), 0, source.getData().length);
        if (bitmap != null) {
            new EditorDialog(context, source, bitmap, listener).show();
        }
    }

    private static final class EditorDialog extends Dialog {
        private final ImageAttachment source;
        private final OnApplyListener listener;
        private final EditorCanvas canvas;
        private Bitmap bitmap;
        private TextView modeLabel;

        EditorDialog(Context context, ImageAttachment source, Bitmap bitmap,
                     OnApplyListener listener) {
            super(context);
            this.source = source;
            this.bitmap = bitmap;
            this.listener = listener;
            canvas = new EditorCanvas(context, bitmap);
        }

        @Override
        protected void onCreate(android.os.Bundle state) {
            super.onCreate(state);
            LinearLayout root = UiKit.vertical(getContext());
            int padding = UiKit.dp(getContext(), 12);
            root.setPadding(padding, padding, padding, padding);
            root.setBackgroundColor(UiKit.SURFACE);

            LinearLayout header = UiKit.horizontal(getContext());
            TextView title = UiKit.heading(getContext(), "编辑图片", 18);
            header.addView(title, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            Button close = UiKit.iconButton(getContext(), UiKit.Icon.CLOSE, false);
            close.setContentDescription("关闭图片编辑器");
            close.setOnClickListener(view -> dismiss());
            header.addView(close, new LinearLayout.LayoutParams(
                    UiKit.dp(getContext(), 44), UiKit.dp(getContext(), 44)));
            root.addView(header);

            modeLabel = UiKit.text(getContext(), "选择工具编辑图片", 12, UiKit.MUTED);
            modeLabel.setPadding(0, UiKit.dp(getContext(), 2), 0, UiKit.dp(getContext(), 6));
            root.addView(modeLabel);

            FrameLayout preview = new FrameLayout(getContext());
            preview.setBackgroundColor(Color.rgb(242, 244, 246));
            preview.addView(canvas, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(getContext(), 360)));
            root.addView(preview, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(getContext(), 360)));

            LinearLayout tools = UiKit.horizontal(getContext());
            tools.setGravity(Gravity.CENTER);
            Button draw = tool("绘制", UiKit.Icon.PENCIL);
            draw.setOnClickListener(view -> {
                canvas.setMode(canvas.getMode() == EditorCanvas.Mode.DRAW
                        ? EditorCanvas.Mode.NONE : EditorCanvas.Mode.DRAW);
                updateMode();
            });
            Button erase = tool("橡皮擦", UiKit.Icon.ERASER);
            erase.setOnClickListener(view -> {
                canvas.setMode(canvas.getMode() == EditorCanvas.Mode.ERASE
                        ? EditorCanvas.Mode.NONE : EditorCanvas.Mode.ERASE);
                updateMode();
            });
            Button undo = tool("撤销", UiKit.Icon.UNDO);
            undo.setOnClickListener(view -> canvas.undo());
            Button redo = tool("重做", UiKit.Icon.REDO);
            redo.setOnClickListener(view -> canvas.redo());
            Button clear = tool("清除", UiKit.Icon.TRASH);
            clear.setOnClickListener(view -> canvas.clearMarks());
            Button rotate = UiKit.iconButton(getContext(), UiKit.Icon.ROTATE_RIGHT, false);
            rotate.setContentDescription("旋转");
            rotate.setMinWidth(0);
            rotate.setMinHeight(0);
            rotate.setMinimumWidth(0);
            rotate.setMinimumHeight(0);
            rotate.setPadding(UiKit.dp(getContext(), 5), UiKit.dp(getContext(), 3),
                    UiKit.dp(getContext(), 5), UiKit.dp(getContext(), 3));
            rotate.setLayoutParams(new LinearLayout.LayoutParams(
                    UiKit.dp(getContext(), 42), UiKit.dp(getContext(), 36)));
            rotate.setOnClickListener(view -> rotate(90));
            tools.addView(draw);
            tools.addView(erase, spacer());
            tools.addView(undo, spacer());
            tools.addView(redo, spacer());
            tools.addView(clear, spacer());
            tools.addView(rotate, spacer());
            root.addView(tools);

            Button apply = UiKit.button(getContext(), "完成", true);
            apply.setOnClickListener(view -> apply());
            root.addView(apply, UiKit.matchWrap(UiKit.dp(getContext(), 10), 0));
            setContentView(root);
            getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        private Button tool(String label, UiKit.Icon icon) {
            Button button = UiKit.iconButton(getContext(), icon, false);
            button.setContentDescription(label);
            button.setMinWidth(0);
            button.setMinHeight(0);
            button.setMinimumWidth(0);
            button.setMinimumHeight(0);
            button.setPadding(UiKit.dp(getContext(), 5), UiKit.dp(getContext(), 3),
                    UiKit.dp(getContext(), 5), UiKit.dp(getContext(), 3));
            button.setLayoutParams(new LinearLayout.LayoutParams(
                    UiKit.dp(getContext(), 42), UiKit.dp(getContext(), 36)));
            return button;
        }

        private LinearLayout.LayoutParams spacer() {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.leftMargin = UiKit.dp(getContext(), 4);
            return params;
        }

        private void updateMode() {
            switch (canvas.getMode()) {
                case DRAW:
                    modeLabel.setText("绘制模式：红色标记");
                    break;
                case ERASE:
                    modeLabel.setText("橡皮擦模式：擦除标记");
                    break;
                default:
                    modeLabel.setText("选择工具编辑图片");
                    break;
            }
        }

        private void rotate(float degrees) {
            Matrix matrix = new Matrix();
            matrix.postRotate(degrees);
            Bitmap previous = bitmap;
            Bitmap rotated = Bitmap.createBitmap(previous, 0, 0, previous.getWidth(),
                    previous.getHeight(), matrix, true);
            if (rotated != previous) {
                bitmap = rotated;
                canvas.replaceBitmap(bitmap);
                previous.recycle();
            }
        }

        private void apply() {
            Bitmap result = canvas.renderBitmap();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            result.compress(Bitmap.CompressFormat.JPEG, 90, output);
            result.recycle();
            listener.onApply(source.copyWithData("image/jpeg", output.toByteArray()));
            dismiss();
        }

        @Override
        public void dismiss() {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
                bitmap = null;
            }
            super.dismiss();
        }
    }

    private static final class EditorCanvas extends View {
        enum Mode { NONE, DRAW, ERASE }

        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint markPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<Overlay> overlays = new ArrayList<>();
        private final Deque<EditorState> undo = new ArrayDeque<>();
        private final Deque<EditorState> redo = new ArrayDeque<>();
        private Bitmap bitmap;
        private Mode mode = Mode.NONE;
        private RectF imageRect = new RectF();
        private Path activePath;

        EditorCanvas(Context context, Bitmap bitmap) {
            super(context);
            markPaint.setColor(Color.rgb(226, 55, 55));
            markPaint.setStyle(Paint.Style.STROKE);
            markPaint.setStrokeWidth(UiKit.dp(context, 4));
            markPaint.setStrokeCap(Paint.Cap.ROUND);
            markPaint.setStrokeJoin(Paint.Join.ROUND);
            replaceBitmap(bitmap);
        }

        Mode getMode() {
            return mode;
        }

        void setMode(Mode mode) {
            this.mode = mode;
            activePath = null;
            invalidate();
        }

        void replaceBitmap(Bitmap bitmap) {
            this.bitmap = bitmap;
            overlays.clear();
            undo.clear();
            redo.clear();
            requestLayout();
            invalidate();
        }

        void undo() {
            if (!undo.isEmpty()) {
                redo.push(snapshot());
                restore(undo.pop());
            }
        }

        void redo() {
            if (!redo.isEmpty()) {
                undo.push(snapshot());
                restore(redo.pop());
            }
        }

        void clearMarks() {
            if (!overlays.isEmpty()) {
                saveForUndo();
                overlays.clear();
                invalidate();
            }
        }

        private void saveForUndo() {
            undo.push(snapshot());
            redo.clear();
        }

        private EditorState snapshot() {
            List<Overlay> copy = new ArrayList<>();
            for (Overlay overlay : overlays) {
                copy.add(overlay.copy());
            }
            return new EditorState(copy);
        }

        private void restore(EditorState state) {
            overlays.clear();
            overlays.addAll(state.overlays);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (bitmap == null) {
                return;
            }
            float scale = Math.min(getWidth() / (float) bitmap.getWidth(),
                    getHeight() / (float) bitmap.getHeight());
            float width = bitmap.getWidth() * scale;
            float height = bitmap.getHeight() * scale;
            imageRect.set((getWidth() - width) / 2f, (getHeight() - height) / 2f,
                    (getWidth() + width) / 2f, (getHeight() + height) / 2f);
            canvas.drawBitmap(bitmap, null, imageRect, bitmapPaint);
            for (Overlay overlay : overlays) {
                overlay.draw(canvas, markPaint);
            }
            if (activePath != null) {
                canvas.drawPath(activePath, markPaint);
            }
        }

        Bitmap renderBitmap() {
            Bitmap result = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(),
                    Bitmap.Config.ARGB_8888);
            Canvas output = new Canvas(result);
            output.drawBitmap(bitmap, 0, 0, bitmapPaint);
            float scale = imageRect.width() / bitmap.getWidth();
            output.save();
            output.scale(1f / scale, 1f / scale);
            output.translate(-imageRect.left, -imageRect.top);
            for (Overlay overlay : overlays) {
                overlay.draw(output, markPaint);
            }
            output.restore();
            return result;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (!imageRect.contains(x, y)) {
                        return false;
                    }
                    if (mode == Mode.DRAW) {
                        saveForUndo();
                        activePath = new Path();
                        activePath.moveTo(x, y);
                    } else if (mode == Mode.ERASE) {
                        saveForUndo();
                        eraseAt(x, y);
                    }
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (mode == Mode.DRAW && activePath != null) {
                        activePath.lineTo(x, y);
                    } else if (mode == Mode.ERASE) {
                        eraseAt(x, y);
                    }
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (mode == Mode.DRAW && activePath != null) {
                        overlays.add(Overlay.path(activePath));
                        activePath = null;
                    }
                    invalidate();
                    return true;
                default:
                    return true;
            }
        }

        private void eraseAt(float x, float y) {
            float radius = UiKit.dp(getContext(), 24);
            for (int index = overlays.size() - 1; index >= 0; index--) {
                if (overlays.get(index).hit(x, y, radius)) {
                    overlays.remove(index);
                    break;
                }
            }
        }
    }

    private static final class EditorState {
        final List<Overlay> overlays;

        EditorState(List<Overlay> overlays) {
            this.overlays = overlays;
        }
    }

    private static final class Overlay {
        enum Type { PATH }

        final Type type;
        final Path path;

        private Overlay(Type type, Path path) {
            this.type = type;
            this.path = path;
        }

        static Overlay path(Path path) {
            return new Overlay(Type.PATH, new Path(path));
        }

        Overlay copy() {
            return path(path);
        }

        void draw(Canvas canvas, Paint markPaint) {
            canvas.drawPath(path, markPaint);
        }

        boolean hit(float x, float y, float radius) {
            RectF bounds = new RectF();
            path.computeBounds(bounds, true);
            bounds.inset(-radius, -radius);
            return bounds.contains(x, y);
        }
    }
}
