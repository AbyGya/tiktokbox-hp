package com.tiktokbox.hp.chaos;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Helper tampilan gaming: kartu gelap + aksen neon, tanpa library tambahan. */
public class ChaosUI {
    public static final int BG = 0xFF0B0E17;
    public static final int CARD = 0xFF141A33;
    public static final int STROKE = 0xFF2A3563;
    public static final int TEXT = 0xFFEEF1FF;
    public static final int DIM = 0xFF8A93B8;
    public static final int NEON = 0xFF00E5FF;
    public static final int GREEN = 0xFF1B7A43;
    public static final int RED = 0xFFB3261E;
    public static final int AMBER = 0xFF8A5A00;
    public static final int BLUE = 0xFF27408B;

    public static int dp(Context c, int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics());
    }

    public static GradientDrawable cardBg() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(CARD);
        d.setCornerRadius(28);
        d.setStroke(2, STROKE);
        return d;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(cardBg());
        int p = dp(c, 14);
        l.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(c, 12));
        l.setLayoutParams(lp);
        return l;
    }

    public static TextView title(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(15);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(NEON);
        t.setPadding(0, 0, 0, dp(c, 8));
        return t;
    }

    public static TextView body(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(14);
        t.setTextColor(TEXT);
        t.setLineSpacing(dp(c, 2), 1f);
        return t;
    }

    public static TextView hint(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(12);
        t.setTextColor(DIM);
        t.setPadding(0, dp(c, 6), 0, 0);
        return t;
    }

    public static Button button(Context c, String s, int bg) {
        Button b = new Button(c);
        b.setText(s);
        b.setTextSize(15);
        b.setTextColor(Color.WHITE);
        GradientDrawable d = new GradientDrawable();
        d.setColor(bg);
        d.setCornerRadius(20);
        b.setBackground(d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(c, 4), 0, dp(c, 4));
        b.setLayoutParams(lp);
        b.setPadding(dp(c, 8), dp(c, 12), dp(c, 8), dp(c, 12));
        return b;
    }

    public static LinearLayout row2(Context c, Button a, Button b2) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(c, 3), dp(c, 4), dp(c, 3), dp(c, 4));
        a.setLayoutParams(lp);
        b2.setLayoutParams(lp);
        r.addView(a);
        r.addView(b2);
        return r;
    }
}
