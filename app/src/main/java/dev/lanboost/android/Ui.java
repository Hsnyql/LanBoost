package dev.lanboost.android;

import android.app.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

final class Ui {
  static final int BG = 0xff0d1511,
      CARD = 0xff18231d,
      RAISED = 0xff202e26,
      LINE = 0xff2f4136,
      TEXT = 0xffeff6f0,
      MUTED = 0xff94aa9c,
      ACCENT = 0xffb4f180,
      AMBER = 0xfff3be72;
  static final int NETWORK = 0,
      SPEED = 1,
      SERVER = 2,
      ACTIVITY = 3,
      SETTINGS = 4,
      REFRESH = 5,
      PLUS = 6,
      COPY = 7,
      ARROW = 8,
      PLAY = 9,
      STOP = 10,
      CLOSE = 11,
      IMPORT = 12,
      CHEVRON = 13,
      CHECK = 14,
      SEARCH = 15,
      TRASH = 16,
      HELP = 17,
      LOCK = 18;
  final Activity a;

  Ui(Activity a) {
    this.a = a;
  }

  int dp(float v) {
    return Math.round(v * a.getResources().getDisplayMetrics().density);
  }

  LinearLayout column() {
    LinearLayout v = new LinearLayout(a);
    v.setOrientation(LinearLayout.VERTICAL);
    return v;
  }

  LinearLayout row() {
    LinearLayout v = new LinearLayout(a);
    v.setGravity(Gravity.CENTER_VERTICAL);
    v.setOrientation(LinearLayout.HORIZONTAL);
    return v;
  }

  GradientDrawable shape(int color, int radius, int stroke) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(radius));
    if (stroke != 0) d.setStroke(dp(1), stroke);
    return d;
  }

  Drawable ripple(int color, int radius, int stroke) {
    return new RippleDrawable(
        ColorStateList.valueOf(0x22b4f180),
        shape(color, radius, stroke),
        shape(Color.WHITE, radius, 0));
  }

  TextView text(String text, int size, int color, boolean bold) {
    TextView t = new TextView(a);
    t.setText(text);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
    t.setIncludeFontPadding(false);
    t.setLineSpacing(dp(3), 1f);
    return t;
  }

  TextView mono(String text, int size, int color) {
    TextView t = text(text, size, color, false);
    t.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
    return t;
  }

  TextView pill(String value, boolean active) {
    TextView t = text(value, 11, active ? ACCENT : MUTED, true);
    t.setPadding(dp(10), dp(6), dp(10), dp(6));
    t.setBackground(shape(active ? 0xff263e27 : RAISED, 20, 0));
    return t;
  }

  Button button(String label, int icon, boolean primary, View.OnClickListener click) {
    Button b = new Button(a);
    b.setText(label);
    b.setTextSize(14);
    b.setAllCaps(false);
    b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    b.setTextColor(primary ? BG : TEXT);
    b.setBackground(ripple(primary ? ACCENT : RAISED, 14, primary ? 0 : LINE));
    b.setStateListAnimator(null);
    b.setElevation(0);
    b.setMinHeight(dp(48));
    b.setMinimumHeight(dp(48));
    b.setMinWidth(0);
    b.setMinimumWidth(0);
    b.setPadding(dp(16), dp(10), dp(16), dp(10));
    b.setGravity(Gravity.CENTER);
    if (icon >= 0) {
      Drawable d = new Glyph(icon, primary ? BG : ACCENT);
      d.setBounds(0, 0, dp(18), dp(18));
      b.setCompoundDrawables(d, null, null, null);
      b.setCompoundDrawablePadding(dp(8));
    }
    b.setOnClickListener(click);
    return b;
  }

  ImageButton icon(int kind, String label, View.OnClickListener click) {
    ImageButton b = new ImageButton(a);
    b.setImageDrawable(new Glyph(kind, MUTED));
    b.setPadding(dp(12), dp(12), dp(12), dp(12));
    b.setBackground(ripple(Color.TRANSPARENT, 14, 0));
    b.setContentDescription(label);
    b.setTooltipText(label);
    b.setOnClickListener(click);
    b.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
    return b;
  }

  ImageView image(int kind, int color, int size) {
    ImageView i = new ImageView(a);
    i.setImageDrawable(new Glyph(kind, color));
    i.setLayoutParams(new LinearLayout.LayoutParams(dp(size), dp(size)));
    return i;
  }

  LinearLayout card() {
    LinearLayout v = column();
    v.setPadding(dp(18), dp(18), dp(18), dp(18));
    v.setBackground(shape(CARD, 22, LINE));
    return v;
  }

  void gap(LinearLayout box, int h) {
    box.addView(new View(a), new LinearLayout.LayoutParams(1, dp(h)));
  }

  void divider(LinearLayout box) {
    View line = new View(a);
    line.setBackgroundColor(LINE);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(1));
    p.setMargins(0, dp(14), 0, dp(14));
    box.addView(line, p);
  }

  EditText input(String hint, String value, boolean multi) {
    EditText e = new EditText(a);
    e.setTextSize(14);
    e.setTextColor(TEXT);
    e.setHintTextColor(MUTED);
    e.setText(value);
    e.setHint(hint);
    e.setSingleLine(!multi);
    e.setSelectAllOnFocus(!multi);
    e.setPadding(dp(14), dp(13), dp(14), dp(13));
    e.setBackground(shape(BG, 12, LINE));
    e.setMinimumHeight(dp(48));
    return e;
  }

  void enabled(View v, boolean enabled) {
    if (v != null && v.isEnabled() != enabled) {
      v.setEnabled(enabled);
      v.setAlpha(enabled ? 1 : .4f);
    }
  }

  void dialog(AlertDialog dialog) {
    dialog.show();
    if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(shape(CARD, 22, LINE));
    for (int id : new int[] {-1, -2, -3})
      if (dialog.getButton(id) != null) dialog.getButton(id).setTextColor(ACCENT);
  }

  static final class Glyph extends Drawable {
    private final int kind, color;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    Glyph(int kind, int color) {
      this.kind = kind;
      this.color = color;
    }

    @Override
    public void draw(Canvas c) {
      c.save();
      Rect b = getBounds();
      c.translate(b.left, b.top);
      c.scale(b.width() / 24f, b.height() / 24f);
      p.setColor(color);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(1.7f);
      p.setStrokeCap(Paint.Cap.ROUND);
      p.setStrokeJoin(Paint.Join.ROUND);
      Path q = new Path();
      switch (kind) {
        case NETWORK:
          line(c, 7, 7, 17, 7);
          line(c, 7, 7, 12, 17);
          line(c, 17, 7, 12, 17);
          circle(c, 6, 6, 3);
          circle(c, 18, 6, 3);
          circle(c, 12, 18, 3);
          break;
        case SPEED:
          c.drawArc(3, 3, 21, 21, 140, 260, false, p);
          line(c, 12, 12, 17, 6);
          circle(c, 12, 12, 2);
          break;
        case SERVER:
          c.drawRoundRect(3, 3, 21, 10, 2, 2, p);
          c.drawRoundRect(3, 14, 21, 21, 2, 2, p);
          line(c, 7, 6.5f, 7, 6.5f);
          line(c, 7, 17.5f, 7, 17.5f);
          line(c, 12, 6.5f, 17, 6.5f);
          line(c, 12, 17.5f, 17, 17.5f);
          break;
        case ACTIVITY:
          q.moveTo(2, 13);
          q.lineTo(6, 13);
          q.lineTo(9, 5);
          q.lineTo(14, 20);
          q.lineTo(17, 10);
          q.lineTo(20, 13);
          q.lineTo(22, 13);
          c.drawPath(q, p);
          break;
        case SETTINGS:
          line(c, 4, 6, 20, 6);
          line(c, 4, 12, 20, 12);
          line(c, 4, 18, 20, 18);
          fillCircle(c, 9, 6);
          fillCircle(c, 16, 12);
          fillCircle(c, 8, 18);
          break;
        case REFRESH:
          c.drawArc(4, 4, 20, 20, 45, 285, false, p);
          line(c, 20, 4, 20, 9);
          line(c, 15, 9, 20, 9);
          break;
        case PLUS:
          line(c, 12, 5, 12, 19);
          line(c, 5, 12, 19, 12);
          break;
        case COPY:
          c.drawRoundRect(8, 8, 21, 21, 2, 2, p);
          q.moveTo(16, 4);
          q.lineTo(16, 3);
          q.lineTo(3, 3);
          q.lineTo(3, 16);
          q.lineTo(4, 16);
          c.drawPath(q, p);
          break;
        case ARROW:
          line(c, 4, 12, 20, 12);
          line(c, 14, 6, 20, 12);
          line(c, 14, 18, 20, 12);
          break;
        case PLAY:
          q.moveTo(8, 4);
          q.lineTo(20, 12);
          q.lineTo(8, 20);
          q.close();
          c.drawPath(q, p);
          break;
        case STOP:
          c.drawRoundRect(5, 5, 19, 19, 2, 2, p);
          break;
        case CLOSE:
          line(c, 6, 6, 18, 18);
          line(c, 6, 18, 18, 6);
          break;
        case IMPORT:
          line(c, 12, 3, 12, 15);
          line(c, 7, 10, 12, 15);
          line(c, 17, 10, 12, 15);
          q.moveTo(4, 15);
          q.lineTo(4, 21);
          q.lineTo(20, 21);
          q.lineTo(20, 15);
          c.drawPath(q, p);
          break;
        case CHEVRON:
          line(c, 9, 5, 16, 12);
          line(c, 9, 19, 16, 12);
          break;
        case CHECK:
          line(c, 4, 12, 9, 17);
          line(c, 9, 17, 20, 6);
          break;
        case SEARCH:
          circle(c, 10, 10, 6);
          line(c, 15, 15, 21, 21);
          break;
        case TRASH:
          line(c, 4, 6, 20, 6);
          line(c, 9, 3, 15, 3);
          q.moveTo(6, 6);
          q.lineTo(7, 21);
          q.lineTo(17, 21);
          q.lineTo(18, 6);
          c.drawPath(q, p);
          line(c, 10, 10, 10, 17);
          line(c, 14, 10, 14, 17);
          break;
        case HELP:
          circle(c, 12, 12, 9);
          c.drawArc(8, 6, 16, 12, 180, 240, false, p);
          line(c, 12, 12, 12, 14);
          line(c, 12, 18, 12, 18);
          break;
        case LOCK:
          c.drawRoundRect(5, 10, 19, 21, 2, 2, p);
          c.drawArc(8, 2, 16, 16, 180, 180, false, p);
          line(c, 12, 14, 12, 17);
          break;
      }
      c.restore();
    }

    private void line(Canvas c, float a, float b, float d, float e) {
      c.drawLine(a, b, d, e, p);
    }

    private void circle(Canvas c, float x, float y, float r) {
      c.drawCircle(x, y, r, p);
    }

    private void fillCircle(Canvas c, float x, float y) {
      p.setStyle(Paint.Style.FILL);
      p.setColor(CARD);
      c.drawCircle(x, y, 3, p);
      p.setStyle(Paint.Style.STROKE);
      p.setColor(color);
      c.drawCircle(x, y, 3, p);
    }

    @Override
    public void setAlpha(int alpha) {
      p.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter f) {
      p.setColorFilter(f);
    }

    @Override
    public int getOpacity() {
      return PixelFormat.TRANSLUCENT;
    }
  }
}
