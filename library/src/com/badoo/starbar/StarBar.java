package com.badoo.starbar;

import java.util.Arrays;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * A rating bar showing 10 stars. The user can touch or swipe to select a rating between 1 and 10.
 * The StarBar supports three rating ranges (red, yellow and green) that are indicated by different
 * colored stars.
 * 
 * @author Erik Andre
 */
public class StarBar extends View {

    private static final int NO_RATING = -1;

    private static final String[] LABELS = { "1", "2", "3", "4", "5", "6", "7", "8", "9", "10" };

    private static final float BASE_STAR_SIZE = 1.0f;

    private static final float FOCUSED_STAR_SIZE = 2.0f;

    private static final float TARGET_ANIMATION_THRESHOLD = 0.01f;

    private static final int NUMBER_OF_STARS = 10;

    private boolean isSliding;

    private boolean ratingCanceled;

    private float slidePosition;

    // Current size for each of the stars
    private float[] currentSize;

    // Target size for each of the stars. The AnimationRunnable will update currentSize until it
    // reaches targetSize.
    private float[] targetSize;

    private PointF[] points;

    private Paint labelPaint;

    private Paint backgroundPaint;

    private float itemWidth;

    private Handler handler;

    private AnimationRunnable animationRunnable;

    private Drawable[] smallStars;

    private Drawable defaultStar;

    private Drawable[] largeStars;

    private Rect textBounds;

    private OnRatingSliderChangeListener listener;

    private long ratingStartTime;

    private int currentRating = NO_RATING;

    // Default ranges, can be changes by calling setRanges()
    private int yellowRangeStart = 4;

    private int greenRangeStart = 7;

    /**
     * A callback that notifies clients when the user starts rating, changes the rating
     * value and when the rating has ended.
     */
    public interface OnRatingSliderChangeListener {

        /**
         * Notification that the user has started rating by touching a part of the
         * rating bar. This notification will be followed by one or more calls
         * to onPendingRating() followed by one call to onFinalRating().
         * 
         * @return true to let the rating start or false to abort.
         */
        boolean onStartRating();

        /**
         * Notification that the user has moved over to a different rating value.
         * The rating value is only temporary and might change again before the
         * rating is finalized.
         * 
         * @param rating
         *            the pending rating. A value between 1 and 10.
         */
        void onPendingRating(int rating);

        /**
         * Notification that the user has selected a final rating.
         * 
         * @param rating
         *            the final rating selected. A value between 1 and 10.
         * @param swipe
         *            true if the rating was done by swiping.
         */
        void onFinalRating(int rating, boolean swipe);

        /**
         * Notification that the user has canceled the rating.
         */
        void onCancelRating();
    }

    private class AnimationRunnable implements Runnable {

        private long mLastUpdate;

        public AnimationRunnable() {
            mLastUpdate = System.currentTimeMillis();
        }

        @Override
        public void run() {
            update();
            if (!isAtRest()) {
                handler.postDelayed(this, 1);
            }
            else {
                // All stars are at target size, no need to animate any more for now
                animationRunnable = null;
            }
        }

        private void update() {
            long now = System.currentTimeMillis();
            long deltaTime = now - mLastUpdate;
            mLastUpdate = now;
            if (deltaTime == 0) {
                // Avoid updating the view if not enough time has passed
                return;
            }
            float maxStep = ((float) deltaTime / 150);
            for (int i = 0; i < NUMBER_OF_STARS; i++) {
                float step = 0;
                // How far are we from the target size?
                float targetDistance = targetSize[i] - currentSize[i];
                if (targetDistance != 0) {
                    if (targetDistance > 0) {
                        step = Math.min(targetDistance, maxStep);
                    }
                    else if (targetDistance < 0) {
                        step = Math.max(targetDistance, -maxStep);
                    }
                    currentSize[i] += step;
                }
            }
            invalidate();
        }

    }

    public StarBar(Context context) {
        super(context);
        init();
    }

    public StarBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public StarBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        handler = new Handler();
        isSliding = false;

        points = new PointF[NUMBER_OF_STARS];
        for (int i = 0; i < NUMBER_OF_STARS; i++) {
            points[i] = new PointF();
        }

        targetSize = new float[NUMBER_OF_STARS];
        resetTargetSize();

        currentSize = new float[NUMBER_OF_STARS];
        resetCurrentSize();

        labelPaint = new Paint();
        labelPaint.setTextAlign(Align.CENTER);
        labelPaint.setTypeface(Typeface.DEFAULT_BOLD);

        backgroundPaint = new Paint();
        backgroundPaint.setColor(0x80000000); // Could be Color.BLACK, and then calling setAlpha(128), but setAlpha method is not supported on older phones
        backgroundPaint.setStyle(Style.FILL);

        loadStars();
        updateFontSize();
    }

    /**
     * Set the start (lowest) values for the yellow and green ranges.
     * The ranges are as follows:
     * 
     * <p>
     * <code>
     * 0 >= (Red range) < yellow >= (Yellow range) >= green >= 10
     * </code>
     * 
     * @param yellow
     *            Starting point of the yellow range
     * @param green
     *            Starting point of the green range
     */
    public void setRanges(int yellow, int green) {
        yellowRangeStart = yellow;
        greenRangeStart = green;
        invalidate();
    }

    /**
     * Set a listener that will be invoked whenever the users interacts with the StarBar.
     * 
     * @param listener
     *            Listener to set.
     */
    public void setOnRatingSliderChangeListener(OnRatingSliderChangeListener listener) {
        this.listener = listener;
    }

    /**
     * Should be called by the fragment during onConfigurationChanged.
     */
    public void configurationUpdated() {
        loadStars();
        updateFontSize();
    }

    private void loadStars() {
        Resources res = getContext().getResources();
        defaultStar = res.getDrawable(R.drawable.photorating_slider_normal_grey);
        smallStars = new Drawable[] { res.getDrawable(R.drawable.photorating_slider_normal_red), res.getDrawable(R.drawable.photorating_slider_normal_orange),
                res.getDrawable(R.drawable.photorating_slider_normal_green) };
        largeStars = new Drawable[] { res.getDrawable(R.drawable.photorating_slider_pressed_red),
                res.getDrawable(R.drawable.photorating_slider_pressed_orange), res.getDrawable(R.drawable.photorating_slider_pressed_green) };
        textBounds = new Rect();
    }

    private void updateFontSize() {
        // Different font sizes are used for portrait and landscape so we need to reload the dimension value
        Resources res = getContext().getResources();
        int fontSize = res.getDimensionPixelSize(R.dimen.ratingSliderFontSize);
        labelPaint.setTextSize(fontSize);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            // Disable all input if the slider is disabled
            return false;
        }
        int action = event.getAction();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                ratingStartTime = System.currentTimeMillis();
                if (listener != null) {
                    ratingCanceled = !listener.onStartRating();
                }
                //$FALL-THROUGH$
            case MotionEvent.ACTION_MOVE: {
                if (!ratingCanceled) {
                    isSliding = true;
                    slidePosition = getRelativePosition(event.getX());
                    setTargetSize((int) slidePosition, FOCUSED_STAR_SIZE);
                    int newRating = (int) slidePosition + 1;
                    if (listener != null && newRating != currentRating) {
                        currentRating = newRating;
                        listener.onPendingRating(newRating);
                    }
                }
                break;
            }
            case MotionEvent.ACTION_UP:
                if (!ratingCanceled) {
                    currentRating = NO_RATING;
                    postDelayed(new Runnable() {

                        @Override
                        public void run() {
                            resetTargetSize();
                            isSliding = false;
                            // The old animation will have stopped when this is executing
                            startAnimationWorker();
                        }
                    }, 1700);
                    if (listener != null) {
                        // If the user release too far from the bar it is counted as a cancelled rating
                        if (event.getY() < -getHeight()) {
                            // Release to cancel
                            listener.onCancelRating();
                            resetTargetSize();
                            isSliding = false;
                        }
                        else {
                            // If the rating took more than 500 ms from start to finish we consider it to be a swipe rating.
                            boolean swipe = (System.currentTimeMillis() - ratingStartTime) > 500;
                            listener.onFinalRating((int) getRelativePosition(event.getX()) + 1, swipe);
                        }
                    }
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                if (!ratingCanceled) {
                    currentRating = NO_RATING;
                    listener.onCancelRating();
                    resetTargetSize();
                    isSliding = false;
                }
                break;
            default:
                break;
        }
        startAnimationWorker();
        return true;
    }

    private float getRelativePosition(float x) {
        float position = x / itemWidth;
        position = Math.max(position, 0);
        return Math.min(position, NUMBER_OF_STARS - 1);
    }

    private void startAnimationWorker() {
        if (animationRunnable == null) {
            animationRunnable = new AnimationRunnable();
            handler.post(animationRunnable);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        itemWidth = w / (float) NUMBER_OF_STARS;
        updatePositions();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = defaultStar.getIntrinsicHeight();
        // Some extra rooms is added so that the stars can grow when they are selected
        height = (int) (height * 1.2f);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        // The top third of the view is left transparent
        canvas.drawRect(0, height / 3, width, height, backgroundPaint);

        updatePositions();
        for (int i = 0; i < NUMBER_OF_STARS; i++) {
            PointF pos = points[i];
            canvas.save();
            canvas.translate(pos.x, pos.y);
            float size = currentSize[i];
            drawStar(canvas, i, size);
            canvas.restore();
        }
    }

    private void drawStar(Canvas canvas, int position, float size) {

        // Draw the star
        if (isSliding && position <= slidePosition) {
            Drawable[] stars = null;
            if (size > BASE_STAR_SIZE) {
                stars = largeStars;
            }
            else {
                stars = smallStars;
            }

            int starIndex = stars.length - 1;
            int rating = (int) Math.ceil(slidePosition);
            if (rating < yellowRangeStart) {
                starIndex = 0;
            }
            else if (rating < greenRangeStart) {
                starIndex = 1;
            }
            drawStar(canvas, stars[starIndex], position, size);
        }
        else {
            // Draw the default grey star
            drawStar(canvas, defaultStar, position, size);
        }
    }

    private void drawStar(Canvas canvas, Drawable star, int position, float size) {

        int drawableWidth = star.getIntrinsicWidth();
        int drawableHeight = star.getIntrinsicHeight();

        float adjustedSize;

        // The colored stars have a different amount of padding when selected
        // due to the highlight effect so we need to scale them down before
        // drawing them.
        if (size > BASE_STAR_SIZE && star != defaultStar) {
            adjustedSize = size * 0.37f;
        }
        else {
            adjustedSize = BASE_STAR_SIZE;
        }

        canvas.save();
        canvas.scale(adjustedSize, adjustedSize);
        canvas.translate(-drawableWidth / 2, -drawableHeight / 2);
        star.setBounds(0, 0, star.getIntrinsicWidth(), star.getIntrinsicHeight());
        star.draw(canvas);
        canvas.restore();

        float fontSize = labelPaint.getTextSize();
        labelPaint.setTextSize(fontSize * size);
        String label = LABELS[position];
        labelPaint.getTextBounds(label, 0, 1, textBounds);
        int textOffset = (int) ((textBounds.bottom - textBounds.top) / 1.8f);
        canvas.drawText(label, 0, textOffset, labelPaint);
        labelPaint.setTextSize(fontSize);

    }

    private void updatePositions() {
        float totalSize = 0;
        for (int i = 0; i < NUMBER_OF_STARS; i++) {
            totalSize += Math.sqrt(currentSize[i]);
        }
        float left = 0;
        for (int i = 0; i < NUMBER_OF_STARS; i++) {
            float currentItemSize = (float) Math.sqrt(currentSize[i]);
            float adjustedItemSize = getWidth() * (currentItemSize / totalSize);
            float relativeSize = (float) (1 - (Math.sqrt(FOCUSED_STAR_SIZE) - currentItemSize) / (Math.sqrt(FOCUSED_STAR_SIZE) - Math.sqrt(BASE_STAR_SIZE)));
            float posY = (getHeight() * 2) / (float) 3;
            // Make the selected star move up a bit and out of the way
            posY -= relativeSize * getHeight() * 0.2;
            float posX = left + adjustedItemSize / 2;
            points[i].set(posX, posY);
            left += adjustedItemSize;
        }
    }

    private boolean isAtRest() {
        for (int i = 0; i < NUMBER_OF_STARS; i++) {
            if (Math.abs(currentSize[i] - targetSize[i]) > TARGET_ANIMATION_THRESHOLD) {
                return false;
            }
        }
        return true;
    }

    private void setTargetSize(int position, float size) {
        resetTargetSize();
        targetSize[position] = size;
    }

    private void resetTargetSize() {
        Arrays.fill(targetSize, BASE_STAR_SIZE);
    }

    private void resetCurrentSize() {
        Arrays.fill(currentSize, BASE_STAR_SIZE);
    }

}
