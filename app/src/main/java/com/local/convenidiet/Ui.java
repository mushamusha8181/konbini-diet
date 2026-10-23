package com.local.convenidiet;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
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

    static TextView title(Context c, String text) {
        TextView v = new TextView(c);
        v.setText(text);
        v.setTextSize(compact(c) ? 20 : 22);
        v.setTextColor(Color.rgb(28, 38, 31));
        v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    static TextView heading(Context c, String text) {
        TextView v = new TextView(c);
        v.setText(text);
        v.setTextSize(compact(c) ? 15 : 16);
        v.setTextColor(Color.rgb(39, 53, 43));
        v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    static TextView body(Context c, String text) {
        TextView v = new TextView(c);
        v.setText(text);
        v.setTextSize(compact(c) ? 13 : 14);
        v.setLineSpacing(0, 1.12f);
        v.setTextColor(Color.rgb(75, 84, 77));
        return v;
    }

    static EditText field(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setTextSize(compact(c) ? 14 : 15);
        e.setSingleLine(true);
        e.setMinHeight(dp(c, 48));
        e.setPadding(responsive(c, 12, 10), responsive(c, 8, 6), responsive(c, 12, 10), responsive(c, 8, 6));
        e.setBackgroundResource(com.local.convenidiet.R.drawable.field_bg);
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
        b.setTextColor(Color.WHITE);
        b.setTextSize(compact(c) ? 14 : 15);
        b.setAllCaps(false);
        b.setMinHeight(dp(c, 48));
        b.setMinimumHeight(dp(c, 48));
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(responsive(c, 12, 9), 0, responsive(c, 12, 9), 0);
        b.setBackgroundResource(com.local.convenidiet.R.drawable.button_primary);
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
