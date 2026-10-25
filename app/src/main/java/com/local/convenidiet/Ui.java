package com.local.convenidiet;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import jp.mushamusha.mushinglass.MushinGlass;

final class Ui {
    private Ui() {}

    static void applySystemInsets(View view) {
        final int left = view.getPaddingLeft();
        final int top = view.getPaddingTop();
        final int right = view.getPaddingRight();
        final int bottom = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    static boolean compact(Context c) {
        float widthDp = c.getResources().getDisplayMetrics().widthPixels / c.getResources().getDisplayMetrics().density;
        return widthDp <= 360f;
    }

    static int responsive(Context c, int normalDp, int compactDp) {
        return dp(c, compact(c) ? compactDp : normalDp);
    }

    private static void applyTextStyle(Context c, TextView v, boolean bold) {
        MushinGlass.Config cfg = MushinGlass.config(c);
        v.setTextColor(cfg.textColor);
        if (MushinGlass.typeface(c) != null) {
            v.setTypeface(MushinGlass.typeface(c), bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
        v.setIncludeFontPadding(false);
    }

    static TextView title(Context c, String text) {
        TextView v = new TextView(c);
        v.setText(text);
        v.setTextSize(compact(c) ? 20 : 22);
        applyTextStyle(c, v, true);
        return v;
    }

    static TextView heading(Context c, String text) {
        TextView v = new TextView(c);
        v.setText(text);
        v.setTextSize(compact(c) ? 15 : 16);
        applyTextStyle(c, v, true);
        return v;
    }

    static TextView body(Context c, String text) {
        TextView v = new TextView(c);
        v.setText(text);
        v.setTextSize(compact(c) ? 13 : 14);
        v.setLineSpacing(0, 1.12f);
        MushinGlass.Config cfg = MushinGlass.config(c);
        v.setTextColor(cfg.auxiliary);
        if (MushinGlass.typeface(c) != null) v.setTypeface(MushinGlass.typeface(c));
        v.setIncludeFontPadding(false);
        return v;
    }

    static EditText field(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setTextSize(compact(c) ? 14 : 15);
        e.setSingleLine(true);
        e.setMinHeight(dp(c, 48));
        e.setPadding(responsive(c, 12, 10), responsive(c, 8, 6), responsive(c, 12, 10), responsive(c, 8, 6));
        MushinGlass.Config cfg = MushinGlass.config(c);
        e.setTextColor(cfg.textColor);
        e.setHintTextColor(cfg.auxiliary);
        e.setBackground(MushinGlass.glassDrawable(c, android.graphics.Color.WHITE, Math.max(42, cfg.glassOpacity - 16), 12));
        if (MushinGlass.typeface(c) != null) e.setTypeface(MushinGlass.typeface(c));
        return e;
    }

    static EditText numberField(Context c, String hint) {
        EditText e = field(c, hint);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return e;
    }

    static Button primaryButton(Context c, String text) {
        Button b = new Button(c);
        b.setText(text);
        b.setTextSize(compact(c) ? 14 : 15);
        b.setAllCaps(false);
        b.setMinHeight(dp(c, 48));
        b.setMinimumHeight(dp(c, 48));
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(responsive(c, 12, 9), 0, responsive(c, 12, 9), 0);
        MushinGlass.Config cfg = MushinGlass.config(c);
        b.setTextColor(cfg.textColor);
        b.setBackground(MushinGlass.glassDrawable(c, cfg.palette.primary, Math.max(44, cfg.glassOpacity - 16), 14));
        b.setElevation(0);
        if (MushinGlass.typeface(c) != null) b.setTypeface(MushinGlass.typeface(c));
        return b;
    }

    static Button compactButton(Context c, String text) {
        Button b = new Button(c);
        b.setText(text);
        b.setTextSize(compact(c) ? 12 : 13);
        b.setAllCaps(false);
        b.setMinHeight(dp(c, 48));
        b.setMinimumHeight(dp(c, 48));
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(responsive(c, 8, 5), 0, responsive(c, 8, 5), 0);
        MushinGlass.Config cfg = MushinGlass.config(c);
        b.setTextColor(cfg.textColor);
        b.setBackground(MushinGlass.glassDrawable(c, android.graphics.Color.WHITE, Math.max(48, cfg.glassOpacity - 8), 14));
        b.setElevation(0);
        if (MushinGlass.typeface(c) != null) b.setTypeface(MushinGlass.typeface(c));
        return b;
    }

    static Space spacer(Context c, int dp) {
        Space s = new Space(c);
        s.setLayoutParams(new LinearLayout.LayoutParams(1, Ui.dp(c, dp)));
        return s;
    }

    static void match(View v) {
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }
}
