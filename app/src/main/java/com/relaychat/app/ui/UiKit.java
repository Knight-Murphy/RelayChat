package com.relaychat.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.List;

public final class UiKit {
    public static final int INK = Color.rgb(24, 35, 48);
    public static final int MUTED = Color.rgb(112, 127, 143);
    public static final int ACCENT = Color.rgb(79, 137, 226);
    public static final int ACCENT_PRESSED = Color.rgb(63, 113, 194);
    public static final int WARM = Color.rgb(194, 65, 12);
    public static final int WARM_SOFT = Color.rgb(255, 242, 234);
    public static final int CANVAS = Color.rgb(247, 249, 251);
    public static final int SURFACE = Color.WHITE;
    public static final int FIELD = Color.rgb(250, 252, 254);
    public static final int BORDER = Color.rgb(220, 227, 234);
    public static final int DANGER = Color.rgb(185, 28, 28);

    public enum Icon {
        MENU, USERS, GEAR, REFRESH, IMAGE, SEND, CHAT,
        CHEVRON_DOWN, CHEVRON_UP, TRIANGLE_DOWN, TRIANGLE_UP,
        CLOSE, PENCIL, COMPOSE, ERASER, UNDO, REDO, TRASH, ROTATE_LEFT, ROTATE_RIGHT
    }

    private UiKit() {
    }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static TextView text(Context context, String value, float sizeSp, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.12f);
        return view;
    }

    public static TextView heading(Context context, String value, float sizeSp) {
        TextView view = text(context, value, sizeSp, INK);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    public static EditText input(Context context, String hint, boolean multiline) {
        EditText input = new EditText(context);
        input.setHint(hint);
        input.setHintTextColor(Color.rgb(143, 153, 149));
        input.setTextColor(INK);
        input.setTextSize(15);
        input.setPadding(dp(context, 16), dp(context, 9), dp(context, 16), dp(context, 9));
        input.setBackground(rounded(FIELD, dp(context, 10), BORDER, dp(context, 1)));
        input.setInputType(multiline
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                : InputType.TYPE_CLASS_TEXT);
        if (multiline) {
            input.setMinLines(3);
            input.setGravity(Gravity.TOP | Gravity.START);
        } else {
            input.setSingleLine(true);
        }
        return input;
    }

    public static Button button(Context context, String label, boolean primary) {
        Button button = new Button(context);
        button.setText(label);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(context, 44));
        button.setPadding(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 8));
        int normal = primary ? ACCENT : Color.WHITE;
        int pressed = primary ? ACCENT_PRESSED : Color.rgb(235, 239, 236);
        button.setTextColor(primary ? Color.WHITE : INK);
        button.setBackground(buttonBackground(normal, pressed, dp(context, 10),
                primary ? normal : BORDER, dp(context, 1)));
        button.setStateListAnimator(null);
        return button;
    }

    public static Button compactButton(Context context, String label, boolean primary) {
        Button button = button(context, label, primary);
        button.setTextSize(13);
        button.setMinHeight(dp(context, 36));
        button.setPadding(dp(context, 11), dp(context, 5), dp(context, 11), dp(context, 5));
        return button;
    }

    public static Button iconButton(Context context, Icon icon, boolean primary) {
        Button button = button(context, "", primary);
        button.setContentDescription(icon.name());
        button.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8));
        setIcon(button, icon, context);
        return button;
    }

    public static void setIcon(Button button, Icon icon, Context context) {
        int color = button.getTextColors().getDefaultColor();
        button.setCompoundDrawablePadding(dp(context, 8));
        button.setCompoundDrawablesRelative(
                new IconDrawable(context, icon, color), null, null, null);
    }

    public static TextView iconView(Context context, Icon icon, int color) {
        TextView view = new TextView(context);
        view.setGravity(Gravity.CENTER);
        view.setCompoundDrawablesRelative(
                new IconDrawable(context, icon, color), null, null, null);
        return view;
    }

    public static LinearLayout vertical(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    public static LinearLayout horizontal(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    public static LinearLayout card(Context context) {
        LinearLayout card = vertical(context);
        card.setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16));
        card.setBackground(rounded(SURFACE, dp(context, 8), BORDER, dp(context, 1)));
        return card;
    }

    public static View divider(Context context) {
        View divider = new View(context);
        divider.setBackgroundColor(Color.rgb(231, 235, 232));
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1)));
        return divider;
    }

    public static Spinner spinner(Context context, List<String> values) {
        Spinner spinner = new Spinner(context, Spinner.MODE_DROPDOWN);
        spinner.setAdapter(new SpinnerTextAdapter(context, values));
        spinner.setBackground(new SpinnerArrowDrawable(context));
        spinner.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        spinner.setMinimumHeight(dp(context, 38));
        spinner.setPadding(0, dp(context, 2), dp(context, 30), 0);
        return spinner;
    }

    public static void replaceSpinnerValues(Spinner spinner, List<String> values, String selected) {
        spinner.setAdapter(new SpinnerTextAdapter(spinner.getContext(), values));
        int index = values.indexOf(selected);
        spinner.setSelection(index < 0 ? 0 : index, false);
    }

    public static GradientDrawable rounded(int fillColor, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) {
            drawable.setStroke(strokeWidth, strokeColor);
        }
        return drawable;
    }

    private static StateListDrawable buttonBackground(int normal, int pressed, int radius,
                                                       int strokeColor, int strokeWidth) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed},
                rounded(pressed, radius, strokeColor, strokeWidth));
        states.addState(new int[]{}, rounded(normal, radius, strokeColor, strokeWidth));
        return states;
    }

    public static void margins(View view, int left, int top, int right, int bottom) {
        ViewGroup.LayoutParams raw = view.getLayoutParams();
        if (raw instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) raw;
            params.setMargins(left, top, right, bottom);
            view.setLayoutParams(params);
        }
    }

    public static LinearLayout.LayoutParams matchWrap(int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = top;
        params.bottomMargin = bottom;
        return params;
    }

    private static final class SpinnerTextAdapter extends ArrayAdapter<String> {
        SpinnerTextAdapter(Context context, List<String> values) {
            super(context, android.R.layout.simple_spinner_item, values);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return textView(position, convertView, false);
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return textView(position, convertView, true);
        }

        private TextView textView(int position, View convertView, boolean dropdown) {
            TextView view = convertView instanceof TextView
                    ? (TextView) convertView
                    : new TextView(getContext());
            view.setText(getItem(position));
            view.setTextSize(16);
            view.setTextColor(INK);
            view.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            view.setSingleLine(true);
            view.setEllipsize(null);
            view.setHorizontallyScrolling(true);
            view.setIncludeFontPadding(true);
            view.setPadding(0, dp(getContext(), 2), 0, 0);
            if (dropdown) {
                view.setBackgroundColor(SURFACE);
                view.setPadding(dp(getContext(), 12), dp(getContext(), 10),
                        dp(getContext(), 12), dp(getContext(), 10));
                view.setLayoutParams(new AbsListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            return view;
        }
    }

    private static final class IconDrawable extends android.graphics.drawable.Drawable {
        private final Icon icon;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final int color;
        private final int size;

        IconDrawable(Context context, Icon icon, int color) {
            this.icon = icon;
            this.color = color;
            this.size = dp(context, 22);
            setBounds(0, 0, size, size);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        public void draw(Canvas canvas) {
            float unit = getBounds().width() / 24f;
            paint.setColor(color);
            paint.setStrokeWidth(2f * unit);
            canvas.save();
            canvas.translate(getBounds().left, getBounds().top);
            switch (icon) {
                case MENU:
                    canvas.drawLine(4 * unit, 7 * unit, 20 * unit, 7 * unit, paint);
                    canvas.drawLine(4 * unit, 12 * unit, 20 * unit, 12 * unit, paint);
                    canvas.drawLine(4 * unit, 17 * unit, 20 * unit, 17 * unit, paint);
                    break;
                case USERS:
                    canvas.drawCircle(9 * unit, 9 * unit, 3 * unit, paint);
                    canvas.drawCircle(17 * unit, 10 * unit, 2.5f * unit, paint);
                    path.reset();
                    path.moveTo(3.5f * unit, 19 * unit);
                    path.cubicTo(4 * unit, 15.5f * unit, 7 * unit, 14 * unit,
                            9 * unit, 14 * unit);
                    path.cubicTo(11 * unit, 14 * unit, 14 * unit, 15.5f * unit,
                            14.5f * unit, 19 * unit);
                    canvas.drawPath(path, paint);
                    path.reset();
                    path.moveTo(14.5f * unit, 15.5f * unit);
                    path.cubicTo(18 * unit, 14 * unit, 21 * unit, 16 * unit,
                            21 * unit, 19 * unit);
                    canvas.drawPath(path, paint);
                    break;
                case GEAR:
                    canvas.drawCircle(12 * unit, 12 * unit, 7 * unit, paint);
                    canvas.drawCircle(12 * unit, 12 * unit, 2.5f * unit, paint);
                    for (int index = 0; index < 8; index++) {
                        double angle = index * Math.PI / 4;
                        float x1 = 12 * unit + (float) Math.cos(angle) * 7 * unit;
                        float y1 = 12 * unit + (float) Math.sin(angle) * 7 * unit;
                        float x2 = 12 * unit + (float) Math.cos(angle) * 10 * unit;
                        float y2 = 12 * unit + (float) Math.sin(angle) * 10 * unit;
                        canvas.drawLine(x1, y1, x2, y2, paint);
                    }
                    break;
                case REFRESH:
                    path.reset();
                    path.moveTo(19 * unit, 8 * unit);
                    path.cubicTo(17 * unit, 4 * unit, 11 * unit, 3 * unit,
                            7 * unit, 6 * unit);
                    path.cubicTo(3 * unit, 9 * unit, 4 * unit, 16 * unit,
                            8 * unit, 19 * unit);
                    path.cubicTo(12 * unit, 22 * unit, 18 * unit, 20 * unit,
                            20 * unit, 16 * unit);
                    canvas.drawPath(path, paint);
                    canvas.drawLine(18 * unit, 4 * unit, 19 * unit, 8 * unit, paint);
                    canvas.drawLine(15 * unit, 8 * unit, 19 * unit, 8 * unit, paint);
                    break;
                case IMAGE:
                    canvas.drawRoundRect(4 * unit, 4 * unit, 20 * unit, 20 * unit,
                            2 * unit, 2 * unit, paint);
                    path.reset();
                    path.moveTo(6 * unit, 17 * unit);
                    path.lineTo(10.5f * unit, 12.5f * unit);
                    path.lineTo(13.5f * unit, 15.5f * unit);
                    path.lineTo(16.5f * unit, 11.5f * unit);
                    path.lineTo(20 * unit, 16.5f * unit);
                    canvas.drawPath(path, paint);
                    canvas.drawCircle(16 * unit, 8.5f * unit, 1.5f * unit, paint);
                    break;
                case SEND:
                    path.reset();
                    path.moveTo(3 * unit, 5 * unit);
                    path.lineTo(21 * unit, 12 * unit);
                    path.lineTo(3 * unit, 19 * unit);
                    path.lineTo(7 * unit, 12 * unit);
                    path.close();
                    canvas.drawPath(path, paint);
                    canvas.drawLine(7 * unit, 12 * unit, 21 * unit, 12 * unit, paint);
                    break;
                case CHAT:
                    path.reset();
                    path.moveTo(12 * unit, 4 * unit);
                    path.cubicTo(6 * unit, 4 * unit, 3 * unit, 7.5f * unit,
                            3 * unit, 12 * unit);
                    path.cubicTo(3 * unit, 15 * unit, 5 * unit, 17 * unit,
                            7 * unit, 18 * unit);
                    path.lineTo(6 * unit, 21 * unit);
                    path.lineTo(10 * unit, 19 * unit);
                    path.cubicTo(17 * unit, 20 * unit, 21 * unit, 17 * unit,
                            21 * unit, 12 * unit);
                    path.cubicTo(21 * unit, 7.5f * unit, 18 * unit, 4 * unit,
                            12 * unit, 4 * unit);
                    canvas.drawPath(path, paint);
                    canvas.drawCircle(8 * unit, 12 * unit, 1 * unit, paint);
                    canvas.drawCircle(12 * unit, 12 * unit, 1 * unit, paint);
                    canvas.drawCircle(16 * unit, 12 * unit, 1 * unit, paint);
                    break;
                case CHEVRON_DOWN:
                    canvas.drawLine(5 * unit, 9 * unit, 12 * unit, 16 * unit, paint);
                    canvas.drawLine(12 * unit, 16 * unit, 19 * unit, 9 * unit, paint);
                    break;
                case CHEVRON_UP:
                    canvas.drawLine(5 * unit, 15 * unit, 12 * unit, 8 * unit, paint);
                    canvas.drawLine(12 * unit, 8 * unit, 19 * unit, 15 * unit, paint);
                    break;
                case TRIANGLE_DOWN:
                    paint.setStyle(Paint.Style.FILL);
                    path.reset();
                    path.moveTo(5 * unit, 9 * unit);
                    path.lineTo(19 * unit, 9 * unit);
                    path.lineTo(12 * unit, 16 * unit);
                    path.close();
                    canvas.drawPath(path, paint);
                    break;
                case TRIANGLE_UP:
                    paint.setStyle(Paint.Style.FILL);
                    path.reset();
                    path.moveTo(5 * unit, 15 * unit);
                    path.lineTo(19 * unit, 15 * unit);
                    path.lineTo(12 * unit, 8 * unit);
                    path.close();
                    canvas.drawPath(path, paint);
                    break;
                case CLOSE:
                    canvas.drawLine(6 * unit, 6 * unit, 18 * unit, 18 * unit, paint);
                    canvas.drawLine(18 * unit, 6 * unit, 6 * unit, 18 * unit, paint);
                    break;
                case PENCIL:
                    paint.setStrokeWidth(2.2f * unit);
                    path.reset();
                    path.moveTo(5 * unit, 18 * unit);
                    path.lineTo(7 * unit, 17.5f * unit);
                    path.lineTo(18 * unit, 6.5f * unit);
                    path.lineTo(16 * unit, 4.5f * unit);
                    path.lineTo(5 * unit, 15.5f * unit);
                    path.close();
                    canvas.drawPath(path, paint);
                    canvas.drawLine(5 * unit, 18 * unit, 5 * unit, 15.5f * unit, paint);
                    break;
                case COMPOSE:
                    path.reset();
                    path.moveTo(10 * unit, 4 * unit);
                    path.lineTo(7 * unit, 4 * unit);
                    path.cubicTo(4.8f * unit, 4 * unit, 3 * unit, 5.8f * unit,
                            3 * unit, 8 * unit);
                    path.lineTo(3 * unit, 17 * unit);
                    path.cubicTo(3 * unit, 19.2f * unit, 4.8f * unit, 21 * unit,
                            7 * unit, 21 * unit);
                    path.lineTo(16 * unit, 21 * unit);
                    path.cubicTo(18.2f * unit, 21 * unit, 20 * unit, 19.2f * unit,
                            20 * unit, 17 * unit);
                    path.lineTo(20 * unit, 14 * unit);
                    canvas.drawPath(path, paint);
                    path.reset();
                    path.moveTo(9 * unit, 15 * unit);
                    path.lineTo(8 * unit, 18 * unit);
                    path.lineTo(11 * unit, 17 * unit);
                    path.lineTo(21 * unit, 7 * unit);
                    path.cubicTo(22.5f * unit, 5.5f * unit, 19.5f * unit, 2.5f * unit,
                            18 * unit, 4 * unit);
                    path.close();
                    canvas.drawPath(path, paint);
                    break;
                case ERASER:
                    path.reset();
                    path.moveTo(5 * unit, 15 * unit);
                    path.lineTo(13 * unit, 7 * unit);
                    path.lineTo(19 * unit, 13 * unit);
                    path.lineTo(11 * unit, 21 * unit);
                    path.close();
                    canvas.drawPath(path, paint);
                    canvas.drawLine(9 * unit, 11 * unit, 15 * unit, 17 * unit, paint);
                    break;
                case UNDO:
                    paint.setStrokeWidth(2.2f * unit);
                    paint.setStyle(Paint.Style.STROKE);
                    path.reset();
                    path.moveTo(19 * unit, 12 * unit);
                    path.lineTo(5 * unit, 12 * unit);
                    path.moveTo(5 * unit, 12 * unit);
                    path.lineTo(10 * unit, 7 * unit);
                    path.moveTo(5 * unit, 12 * unit);
                    path.lineTo(10 * unit, 17 * unit);
                    canvas.drawPath(path, paint);
                    break;
                case REDO:
                    paint.setStrokeWidth(2.2f * unit);
                    paint.setStyle(Paint.Style.STROKE);
                    path.reset();
                    path.moveTo(5 * unit, 12 * unit);
                    path.lineTo(19 * unit, 12 * unit);
                    path.moveTo(19 * unit, 12 * unit);
                    path.lineTo(14 * unit, 7 * unit);
                    path.moveTo(19 * unit, 12 * unit);
                    path.lineTo(14 * unit, 17 * unit);
                    canvas.drawPath(path, paint);
                    break;
                case TRASH:
                    canvas.drawRect(7 * unit, 8 * unit, 17 * unit, 20 * unit, paint);
                    canvas.drawLine(5 * unit, 8 * unit, 19 * unit, 8 * unit, paint);
                    canvas.drawLine(9 * unit, 5 * unit, 15 * unit, 5 * unit, paint);
                    break;
                case ROTATE_LEFT:
                case ROTATE_RIGHT:
                    paint.setStrokeWidth(2.2f * unit);
                    paint.setStyle(Paint.Style.STROKE);
                    if (icon == Icon.ROTATE_LEFT) {
                        canvas.scale(-1, 1, 12 * unit, 12 * unit);
                    }
                    path.reset();
                    path.moveTo(18 * unit, 18 * unit);
                    path.cubicTo(16.5f * unit, 19.5f * unit, 14.5f * unit, 20 * unit,
                            12 * unit, 20 * unit);
                    path.cubicTo(7.6f * unit, 20 * unit, 4 * unit, 16.4f * unit,
                            4 * unit, 12 * unit);
                    path.cubicTo(4 * unit, 7.6f * unit, 7.6f * unit, 4 * unit,
                            12 * unit, 4 * unit);
                    path.cubicTo(15.5f * unit, 4 * unit, 18 * unit, 5.5f * unit,
                            20 * unit, 8 * unit);
                    path.lineTo(20 * unit, 3 * unit);
                    path.moveTo(20 * unit, 8 * unit);
                    path.lineTo(15 * unit, 8 * unit);
                    canvas.drawPath(path, paint);
                    break;
            }
            canvas.restore();
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return size;
        }

        @Override
        public int getIntrinsicHeight() {
            return size;
        }
    }

    private static final class SpinnerArrowDrawable
            extends android.graphics.drawable.Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int size;

        SpinnerArrowDrawable(Context context) {
            size = dp(context, 18);
            paint.setColor(Color.rgb(105, 111, 119));
            paint.setStyle(Paint.Style.FILL);
        }

        @Override
        public void draw(Canvas canvas) {
            float right = getBounds().right - dpFromSize(10);
            float centerY = getBounds().centerY();
            float halfWidth = dpFromSize(7);
            float halfHeight = dpFromSize(5);
            Path arrow = new Path();
            arrow.moveTo(right - halfWidth, centerY - halfHeight / 2f);
            arrow.lineTo(right + halfWidth, centerY - halfHeight / 2f);
            arrow.lineTo(right, centerY + halfHeight);
            arrow.close();
            canvas.drawPath(arrow, paint);
        }

        private float dpFromSize(float value) {
            return value * size / 18f;
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return size;
        }

        @Override
        public int getIntrinsicHeight() {
            return size;
        }
    }
}
