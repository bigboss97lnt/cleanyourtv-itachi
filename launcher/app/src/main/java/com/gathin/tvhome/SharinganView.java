package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewTreeObserver;

/** Decorative eyes: observes Home focus without consuming remote input. */
public final class SharinganView extends View implements ViewTreeObserver.OnGlobalFocusChangeListener {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path outline = new Path();
    private final Rect target = new Rect();
    private float gazeX, gazeY, drawnX;
    private float drawnY = 3f;
    private float glowX, glowY, shownGlowX, shownGlowY, glowRadius, shownGlowRadius;
    private boolean glowInitialized;
    private final Path hair = new Path();
    private android.graphics.Shader hairTexture;
    private boolean tileFocused;
    static boolean enabled(Context context) {
        try {
            java.io.File f = new java.io.File(context.getExternalFilesDir(null), "config.json");
            return new org.json.JSONObject(new String(java.nio.file.Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8)).optBoolean("sharingan", false);
        } catch (Exception ignored) { return false; }
    }
    private long started;
    private boolean active;
    private boolean wakeSpinPending;
    private boolean screenReceiverRegistered;
    private final android.content.BroadcastReceiver screenReceiver = new android.content.BroadcastReceiver() {
        @Override public void onReceive(Context context, android.content.Intent intent) {
            if (android.content.Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                wakeSpinPending = true;
                active = false;
                removeCallbacks(frame);
            } else if (android.content.Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                wakeSpinPending = true;
                updateRunning();
            }
        }
    };
    private final boolean themeEnabled;
    private final Runnable frame = new Runnable() {
        @Override public void run() {
            if (!active) return;
            invalidate();
            postDelayed(this, 33);
        }
    };
    public SharinganView(Context c, AttributeSet a) {
        super(c, a);
        themeEnabled = enabled(c);
        if (themeEnabled) {
            try {
                java.io.File file = new java.io.File(c.getExternalFilesDir(null), "wallpaper.jpg");
                android.graphics.BitmapRegionDecoder decoder = android.graphics.BitmapRegionDecoder.newInstance(file.getPath(), false);
                android.graphics.Bitmap region = decoder.decodeRegion(new Rect(900,280,1050,380), null);
                int[] colors = new int[150*90];
                // Continue the existing strand texture through the eye using hair-only rows
                // above and below it. No original iris pixels enter this opaque foreground.
                for (int y=0;y<90;y++) for(int x=0;x<150;x++) {
                    float mix=Math.max(0f,Math.min(1f,(y+290-285)/80f));
                    int top=region.getPixel(x,5), bottom=region.getPixel(x,85);
                    colors[y*150+x]=Color.rgb(Math.round(Color.red(top)*(1-mix)+Color.red(bottom)*mix),
                        Math.round(Color.green(top)*(1-mix)+Color.green(bottom)*mix),
                        Math.round(Color.blue(top)*(1-mix)+Color.blue(bottom)*mix));
                }
                android.graphics.Bitmap texture=android.graphics.Bitmap.createBitmap(colors,150,90,android.graphics.Bitmap.Config.ARGB_8888);
                hairTexture=new android.graphics.BitmapShader(texture,android.graphics.Shader.TileMode.CLAMP,android.graphics.Shader.TileMode.CLAMP);
                android.graphics.Matrix matrix=new android.graphics.Matrix(); matrix.setTranslate(900,290); hairTexture.setLocalMatrix(matrix);
                region.recycle(); decoder.recycle();
            } catch(Exception ignored) { }
        }
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        started = android.os.SystemClock.uptimeMillis();
        getViewTreeObserver().addOnGlobalFocusChangeListener(this);
        if (themeEnabled) {
            android.content.IntentFilter filter = new android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_ON);
            filter.addAction(android.content.Intent.ACTION_SCREEN_OFF);
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                getContext().registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                getContext().registerReceiver(screenReceiver, filter);
            }
            screenReceiverRegistered = true;
        }
        updateRunning();
    }
    @Override protected void onDetachedFromWindow() {
        active = false;
        removeCallbacks(frame);
        if (screenReceiverRegistered) {
            getContext().unregisterReceiver(screenReceiver);
            screenReceiverRegistered = false;
        }
        if (getViewTreeObserver().isAlive()) getViewTreeObserver().removeOnGlobalFocusChangeListener(this);
        super.onDetachedFromWindow();
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        updateRunning();
    }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateRunning();
    }
    private void updateRunning() {
        android.os.PowerManager power = getContext().getSystemService(android.os.PowerManager.class);
        active = themeEnabled && isAttachedToWindow() && hasWindowFocus() && getWindowVisibility() == VISIBLE
                && (power == null || power.isInteractive());
        removeCallbacks(frame);
        if (active) {
            if (wakeSpinPending) {
                started = android.os.SystemClock.uptimeMillis();
                wakeSpinPending = false;
            }
            post(frame);
        }
    }
    @Override public void onGlobalFocusChanged(View oldFocus, View newFocus) {
        if (newFocus == null || getWidth() == 0) return;
        newFocus.getGlobalVisibleRect(target);
        int id = newFocus.getId();
        tileFocused = id == R.id.card || id == R.id.btnInputs || id == R.id.btnWifi || id == R.id.btnSettings || id == R.id.btnLibraryTop;
        glowRadius = getWidth() * (id == R.id.card ? .26f : .15f);
        glowX = target.exactCenterX(); glowY = target.exactCenterY();
        if (!glowInitialized) { shownGlowX=glowX; shownGlowY=glowY; shownGlowRadius=glowRadius; glowInitialized=true; }
        if (active) { removeCallbacks(frame); post(frame); }
        gazeX = Math.max(-1f, Math.min(1f, (target.centerX() / (float)getWidth() - .5f) * 2));
        gazeY = Math.max(-1f, Math.min(1f, (target.centerY() / (float)getHeight() - .38f) * 2));
        invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!themeEnabled) return;
        float t = (android.os.SystemClock.uptimeMillis() - started) / 1000f;
        float phase = t % 7.1f;
        boolean motion = android.animation.ValueAnimator.areAnimatorsEnabled();
        float blink = motion && phase > 6.85f ? Math.abs((phase - 6.975f) / .125f) : 1f;
        float spin = motion && t < 1.4f ? 360f * (1f - (float)Math.pow(1f - t / 1.4f, 3)) : 0;
        drawnX += (gazeX * 12f - drawnX) * .2f;
        float targetY = Math.max(-5f, Math.min(3f, gazeY * 10f));
        drawnY += (targetY - drawnY) * (motion ? .2f : 1f);
        float ease = motion ? .17f : 1f;
        shownGlowX += (glowX-shownGlowX)*ease; shownGlowY += (glowY-shownGlowY)*ease;
        shownGlowRadius += (glowRadius-shownGlowRadius)*ease;
        if (tileFocused && shownGlowRadius > 0) {
            paint.setShader(new android.graphics.RadialGradient(shownGlowX, shownGlowY, shownGlowRadius,
                new int[]{0x1c7fc4ff, 0x0a7fc4ff, 0x007fc4ff}, new float[]{0,.4f,1}, android.graphics.Shader.TileMode.CLAMP));
            canvas.drawCircle(shownGlowX, shownGlowY, shownGlowRadius, paint); paint.setShader(null);
        }
        canvas.save(); canvas.scale(getWidth()/1672f, getHeight()/941f);
        eye(canvas, false, blink, spin); eye(canvas, true, blink, spin); canvas.restore();
        if (active && Math.abs(glowX-shownGlowX)<.5f && Math.abs(glowY-shownGlowY)<.5f && Math.abs(glowRadius-shownGlowRadius)<.5f && Math.abs(gazeX*12f-drawnX)<.1f && Math.abs(targetY-drawnY)<.1f && (!motion || (t>=1.4f && phase<6.8f))) {
            removeCallbacks(frame); postDelayed(frame, motion ? (long)((6.8f-phase)*1000) : 6000);
        }
    }
    private void eye(Canvas c, boolean right, float blink, float spin) {
        outline.reset();
        if (right) {
            outline.moveTo(910,340); outline.cubicTo(949,310,1000,298,1035,301); outline.cubicTo(1040,333,994,354,910,340);
        } else {
            // Cover the old wallpaper iris fringe with the upper eyelid, not extra eye white.
            outline.moveTo(671,332); outline.cubicTo(700,323,740,328,762,360);
            outline.lineTo(759,358); outline.cubicTo(735,337,705,327,674,332); outline.close();
            paint.setStyle(Paint.Style.FILL); paint.setColor(0xff080c17); paint.setShader(null);
            c.drawPath(outline,paint);
            outline.reset(); outline.moveTo(674,332);
            outline.cubicTo(705,327,735,337,759,358); outline.cubicTo(728,379,689,374,674,332);
        }
        outline.close(); c.save(); c.clipPath(outline);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new android.graphics.LinearGradient(0,300,0,380,0xff253554,0xff6883b2,android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(650,290,1050,385,paint); paint.setShader(null);
        c.save();
        c.translate(right ? 1006 + drawnX*.55f : 727 + drawnX, (right?319:349)+drawnY);
        float r=right?26:21;
        paint.setShader(new android.graphics.RadialGradient(0,0,r,new int[]{0xffb71029,0xfff3434b,0xff9d1429},new float[]{0,.7f,1},android.graphics.Shader.TileMode.CLAMP));
        c.drawCircle(0,0,r,paint); paint.setShader(null);
        paint.setColor(0xff260b15); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.5f); c.drawCircle(0,0,r,paint); c.drawCircle(0,0,r*.62f,paint);
        paint.setStyle(Paint.Style.FILL); c.drawCircle(0,0,r*.22f,paint); c.rotate(spin);
        for(int i=0;i<3;i++) { c.save(); c.rotate(i*120); c.drawCircle(0,-r*.62f,r*.14f,paint);
            Path tail=new Path(); tail.moveTo(-r*.14f,-r*.62f); tail.quadTo(-r*.32f,-r*.93f,r*.07f,-r*.86f); tail.lineTo(r*.1f,-r*.6f); tail.close(); c.drawPath(tail,paint); c.restore(); }
        c.restore();
        if(blink<1) { paint.setColor(0xff344968); float top=right?300:330; c.drawRect(right?906:670,top,right?1040:762,top+(1-blink)*(right?47:44),paint); }
        if (right) drawForegroundHair(c);
        c.restore();
    }
    /** Fixed foreground strands: never derive their mask from dark iris/pupil pixels. */
    private void drawForegroundHair(Canvas c) {
        hair.reset();
        // Main lock joins the hair above the lid to the tapered lock below it.
        hair.moveTo(966,292); hair.cubicTo(966,312,966,333,966,355);
        hair.lineTo(979,355); hair.cubicTo(982,333,984,311,986,292); hair.close();
        // Left and right locks cross the eyelids independently of the moving iris.
        hair.moveTo(910,310); hair.cubicTo(912,327,915,345,917,360);
        hair.lineTo(922,360); hair.cubicTo(919,341,918,324,916,310); hair.close();
        hair.moveTo(1027,290); hair.cubicTo(1029,312,1030,334,1031,351);
        hair.lineTo(1038,351); hair.cubicTo(1036,330,1035,309,1033,290); hair.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xff07101f);
        paint.setShader(hairTexture);
        c.drawPath(hair,paint); paint.setShader(null);
        // The two fine strands left of the main lock remain continuous as well.
        paint.setColor(0xff101b30); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.2f);
        hair.reset(); hair.moveTo(951,303); hair.cubicTo(952,317,954,333,956,351);
        hair.moveTo(958,300); hair.cubicTo(958,316,959,331,960,351); c.drawPath(hair,paint);
        paint.setStyle(Paint.Style.FILL);
    }

}
