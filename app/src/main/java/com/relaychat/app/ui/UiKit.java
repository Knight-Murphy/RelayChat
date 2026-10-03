package com.relaychat.app.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.List;

public final class UiKit {
    public static final int INK = Color.rgb(23, 33, 31);
    public static final int MUTED = Color.rgb(102, 115, 111);
    public static final int ACCENT = Color.rgb(15, 118, 110);
    public static final int ACCENT_PRESSED = Color.rgb(13, 94, 88);
    public static final int WARM = Color.rgb(194, 65, 12);
    public static final int WARM_SOFT = Color.rgb(255, 242, 234);
    public static final int CANVAS = Color.rgb(244, 246, 243);
    public static final int SURFACE = Color.WHITE;
    public static final int FIELD = Color.rgb(247, 249, 247);
    public static final int BORDER = Color.rgb(218, 224, 220);
    public static final int DANGER = Color.rgb(185, 28, 28);

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
        input.setPadding(dp(context, 14), dp(context, 11), dp(context, 14), dp(context, 11));
        input.setBackground(rounded(FIELD, dp(context, 8), BORDER, dp(context, 1)));
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
        button.setMinHeight(dp(context, 42));
        button.setPadding(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 8));
        int normal = primary ? ACCENT : Color.WHITE;
        int pressed = primary ? ACCENT_PRESSED : Color.rgb(235, 239, 236);
        button.setTextColor(primary ? Color.WHITE : INK);
        button.setBackground(buttonBackground(normal, pressed, dp(context, 8),
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
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                context, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setPadding(dp(context, 4), 0, dp(context, 4), 0);
        return spinner;
    }

    public static void replaceSpinnerValues(Spinner spinner, List<String> values, String selected) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                spinner.getContext(), android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
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
}
