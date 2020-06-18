/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.widget.inline;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.graphics.PixelFormat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceControl;
import android.view.SurfaceControlViewHost;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnPreDrawListener;

import java.util.function.Consumer;

/**
 * This class represents a view that holds opaque content from another app that you can inline in
 * your UI.
 *
 * <p>Since the content presented by this view is from another security domain,it is
 * shown on a remote surface preventing the host application from accessing that content. Also the
 * host application cannot interact with the inlined content by injecting touch events or clicking
 * programmatically.
 *
 * <p>This view can be overlaid by other windows, i.e. redressed, but if this is the case
 * the inlined UI would not be interactive. Sometimes this is desirable, e.g. animating transitions.
 *
 * <p>By default the surface backing this view is shown on top of the hosting window such
 * that the inlined content is interactive. However, you can temporarily move the surface under the
 * hosting window which could be useful in some cases, e.g. animating transitions. At this point the
 * inlined content will not be interactive and the touch events would be delivered to your app.
 *
 * <p> Instances of this class are created by the platform and can be programmatically attached to
 * your UI. Once the view is attached to the window, you may detach and reattach it to the window.
 * It should work seamlessly from the hosting process's point of view.
 */
public class InlineContentView extends ViewGroup {

    private static final String TAG = "InlineContentView";

    private static final boolean DEBUG = false;

    /**
     * Callback for observing the lifecycle of the surface control that manipulates the backing
     * secure embedded UI surface.
     */
    public interface SurfaceControlCallback {
        /**
         * Called when the backing surface is being created.
         *
         * @param surfaceControl The surface control to manipulate the surface.
         */
        void onCreated(@NonNull SurfaceControl surfaceControl);

        /**
         * Called when the backing surface is being destroyed.
         *
         * @param surfaceControl The surface control to manipulate the surface.
         */
        void onDestroyed(@NonNull SurfaceControl surfaceControl);
    }

    /**
     * Callback for sending an updated surface package in case the previous one is released
     * from the detached from window event, and for getting notified of such event.
     *
     * This is expected to be provided to the {@link InlineContentView} so it can get updates
     * from and send updates to the remote content (i.e. surface package) provider.
     *
     * @hide
     */
    public interface SurfacePackageUpdater {

        /**
         * Called when the previous surface package is released due to view being detached
         * from the window.
         */
        void onSurfacePackageReleased();

        /**
         * Called to request an updated surface package.
         *
         * @param consumer consumes the updated surface package.
         */
        void getSurfacePackage(Consumer<SurfaceControlViewHost.SurfacePackage> consumer);
    }

    @NonNull
    private final SurfaceHolder.Callback mSurfaceCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(@NonNull SurfaceHolder holder) {
            mSurfaceControlCallback.onCreated(mSurfaceView.getSurfaceControl());
        }

        @Override
        public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width,
                int height) {
            /* do nothing */
        }

        @Override
        public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
            mSurfaceControlCallback.onDestroyed(mSurfaceView.getSurfaceControl());
        }
    };

    @NonNull
    private final SurfaceView mSurfaceView;

    @Nullable
    private SurfaceControlCallback mSurfaceControlCallback;

    @Nullable
    private SurfacePackageUpdater mSurfacePackageUpdater;

    @NonNull
    private final OnPreDrawListener mDrawListener = new OnPreDrawListener() {
        @Override
        public boolean onPreDraw() {
            int visibility = InlineContentView.this.isShown() ? VISIBLE : GONE;
            mSurfaceView.setVisibility(visibility);
            return true;
        }
    };

    /**
     * @inheritDoc
     * @hide
     */
    public InlineContentView(@NonNull Context context) {
        this(context, null);
    }

    /**
     * @inheritDoc
     * @hide
     */
    public InlineContentView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * @inheritDoc
     * @hide
     */
    public InlineContentView(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    /**
     * Gets the surface control. If the surface is not created this method returns {@code null}.
     *
     * @return The surface control.
     * @see #setSurfaceControlCallback(SurfaceControlCallback)
     */
    @Nullable
    public SurfaceControl getSurfaceControl() {
        return mSurfaceView.getSurfaceControl();
    }

    /**
     * @inheritDoc
     * @hide
     */
    public InlineContentView(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mSurfaceView = new SurfaceView(context, attrs, defStyleAttr, defStyleRes);
        mSurfaceView.setZOrderOnTop(true);
        mSurfaceView.getHolder().setFormat(PixelFormat.TRANSPARENT);
        addView(mSurfaceView);
    }

    /**
     * Sets the embedded UI provider.
     *
     * @hide
     */
    public void setChildSurfacePackageUpdater(
            @Nullable SurfacePackageUpdater surfacePackageUpdater) {
        mSurfacePackageUpdater = surfacePackageUpdater;
    }

    @Override
    protected void onAttachedToWindow() {
        if (DEBUG) Log.v(TAG, "onAttachedToWindow");
        super.onAttachedToWindow();
        if (mSurfacePackageUpdater != null) {
            mSurfacePackageUpdater.getSurfacePackage(
                    sp -> {
                        if (DEBUG) Log.v(TAG, "Received new SurfacePackage");
                        if (getViewRootImpl() != null) {
                            mSurfaceView.setChildSurfacePackage(sp);
                        }
                    });
        }
        mSurfaceView.setVisibility(VISIBLE);
        getViewTreeObserver().addOnPreDrawListener(mDrawListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (DEBUG) Log.v(TAG, "onDetachedFromWindow");
        super.onDetachedFromWindow();
        if (mSurfacePackageUpdater != null) {
            mSurfacePackageUpdater.onSurfacePackageReleased();
        }
        getViewTreeObserver().removeOnPreDrawListener(mDrawListener);
    }

    @Override
    public void onLayout(boolean changed, int l, int t, int r, int b) {
        mSurfaceView.layout(0, 0, getMeasuredWidth(), getMeasuredHeight());
    }

    /**
     * Sets a callback to observe the lifecycle of the surface control for managing the backing
     * surface.
     *
     * @param callback The callback to set or {@code null} to clear.
     */
    public void setSurfaceControlCallback(@Nullable SurfaceControlCallback callback) {
        if (mSurfaceControlCallback != null) {
            mSurfaceView.getHolder().removeCallback(mSurfaceCallback);
        }
        mSurfaceControlCallback = callback;
        if (mSurfaceControlCallback != null) {
            mSurfaceView.getHolder().addCallback(mSurfaceCallback);
        }
    }

    /**
     * @return Whether the surface backing this view appears on top of its parent.
     * @see #setZOrderedOnTop(boolean)
     */
    public boolean isZOrderedOnTop() {
        return mSurfaceView.isZOrderedOnTop();
    }

    /**
     * Controls whether the backing surface is placed on top of this view's window. Normally, it is
     * placed on top of the window, to allow interaction with the inlined UI. Via this method, you
     * can place the surface below the window. This means that all of the contents of the window
     * this view is in will be visible on top of its surface.
     *
     * <p> The Z ordering can be changed dynamically if the backing surface is
     * created, otherwise the ordering would be applied at surface construction time.
     *
     * @param onTop Whether to show the surface on top of this view's window.
     * @see #isZOrderedOnTop()
     */
    public boolean setZOrderedOnTop(boolean onTop) {
        return mSurfaceView.setZOrderedOnTop(onTop, /*allowDynamicChange*/ true);
    }
}
