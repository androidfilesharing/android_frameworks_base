/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.systemui.statusbar.phone;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.ServiceManager;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;

import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.util.pac.AwesomeConstants.AwesomeConstant;
import com.android.systemui.R;
import com.android.systemui.statusbar.policy.KeyButtonView;
import com.android.internal.util.omni.ColorUtils;

public final class NavigationBarTransitions extends BarTransitions {

    private static final float KEYGUARD_QUIESCENT_ALPHA = 0.5f;
    private static final int CONTENT_FADE_DURATION = 200;

    private final NavigationBarView mView;
    private final IStatusBarService mBarService;

    private View mStatusBarBlocker;

    private boolean mLightsOut;
    private boolean mVertical;
    private boolean mLeftIfVertical;
    private int mRequestedMode;
    private boolean mStickyTransparent;
    private int mCurrentColor;
    private int mCurrentBg;

    public NavigationBarTransitions(NavigationBarView view) {
        super(view, R.drawable.nav_background, R.color.navigation_bar_background_opaque,
                R.color.navigation_bar_background_semi_transparent);
        mView = view;
        mBarService = IStatusBarService.Stub.asInterface(
                ServiceManager.getService(Context.STATUS_BAR_SERVICE));
    }

    public void init(boolean isVertical) {
        mStatusBarBlocker = mView.findViewById(R.id.status_bar_blocker);
        setVertical(isVertical);
        applyModeBackground(-1, getMode(), false /*animate*/);
        applyMode(getMode(), false /*animate*/, true /*force*/);
    }

    public void setVertical(boolean isVertical) {
        if (mVertical != isVertical) {
            setIsVertical(isVertical);
            mVertical = isVertical;
            updateBackgroundResource();
        }
    }

    public void setLeftIfVertical(boolean leftIfVertical) {
        if (mLeftIfVertical != leftIfVertical) {
            setIsVertical(leftIfVertical);
            mLeftIfVertical = leftIfVertical;
            updateBackgroundResource();
        }
    }

    private void updateBackgroundResource() {
        if (mVertical && mLeftIfVertical) {
            setGradientResourceId(R.drawable.nav_background_land_left);
        } else if (mVertical) {
            setGradientResourceId(R.drawable.nav_background_land);
        } else {
            setGradientResourceId(R.drawable.nav_background);
        }
        transitionTo(mRequestedMode, false /*animate*/);
    }

    @Override
    public void transitionTo(int mode, boolean animate) {
        mRequestedMode = mode;
        if (mStickyTransparent) {
            mode = MODE_TRANSPARENT;
        }
        super.transitionTo(mode, animate);
    }

    @Override
    protected void onTransition(int oldMode, int newMode, boolean animate) {
        super.onTransition(oldMode, newMode, animate);
        applyMode(newMode, animate, false /*force*/);
    }

    private void applyMode(int mode, boolean animate, boolean force) {
        // apply to key buttons
        final float alpha = alphaForMode(mode);
        View[] views = mView.getAllButtons();

        for(View v : views) {
            if (AwesomeConstant.ACTION_BACK.value().equals(v.getTag())) {
                // back button was skipped in original calculations
                continue;
            }
            setKeyButtonViewQuiescentAlpha(v, alpha, animate);
        }

        setKeyButtonViewQuiescentAlpha(mView.getSearchLight(), KEYGUARD_QUIESCENT_ALPHA, animate);
        setKeyButtonViewQuiescentAlpha(mView.getCameraButton(), KEYGUARD_QUIESCENT_ALPHA, animate);
        setKeyButtonViewQuiescentAlpha(mView.getNotifsButton(), KEYGUARD_QUIESCENT_ALPHA, animate);
        setKeyButtonViewQuiescentAlpha(mView.getApplicationWidgetButton(),
                KEYGUARD_QUIESCENT_ALPHA, animate);
        applyBackButtonQuiescentAlpha(mode, animate);

        // apply to lights out
        applyLightsOut(mode == MODE_LIGHTS_OUT, animate, force);

        final boolean isTranslucent = mode != MODE_OPAQUE && mode != MODE_LIGHTS_OUT;
        fadeContent(mStatusBarBlocker, isTranslucent ? 1f : 0f);
    }

    private float alphaForMode(int mode) {
        final boolean isOpaque = mode == MODE_OPAQUE || mode == MODE_LIGHTS_OUT;
        return isOpaque ? KeyButtonView.DEFAULT_QUIESCENT_ALPHA : 1f;
    }

    public void applyBackButtonQuiescentAlpha(int mode, boolean animate) {
        float backAlpha = 0;
        View[] views = mView.getAllButtons();
        for(View v : views) {
            if (AwesomeConstant.ACTION_BACK.value().equals(v.getTag())) {
                // back button was skipped in original calculations
                continue;
            }
            backAlpha = maxVisibleQuiescentAlpha(backAlpha, v);
        }

        if (backAlpha > 0) {
            setKeyButtonViewQuiescentAlpha(mView.getBackButton(), backAlpha, animate);
        }
    }

    public void applyTransparent(boolean sticky) {
        if (sticky != mStickyTransparent) {
            mStickyTransparent = sticky;
            if (!mStickyTransparent) {
                transitionTo(mRequestedMode, false);
            } else {
                transitionTo(MODE_TRANSPARENT, false);
            }
        }
    }

    private static float maxVisibleQuiescentAlpha(float max, View v) {
        if ((v instanceof KeyButtonView) && v.isShown()) {
            return Math.max(max, ((KeyButtonView)v).getQuiescentAlpha());
        }
        return max;
    }

    @Override
    public void setContentVisible(boolean visible) {
        final float alpha = visible ? 1 : 0;
        fadeContent(mView.getCameraButton(), alpha);
        fadeContent(mView.getNotifsButton(), alpha);
        fadeContent(mView.getSearchLight(), alpha);
        fadeContent(mView.getApplicationWidgetButton(), alpha);
    }

    private void fadeContent(View v, float alpha) {
        if (v != null) {
            v.animate().alpha(alpha).setDuration(CONTENT_FADE_DURATION);
        }
    }

    private void setKeyButtonViewQuiescentAlpha(View button, float alpha, boolean animate) {
        if (button instanceof KeyButtonView) {
            ((KeyButtonView) button).setQuiescentAlpha(alpha, animate);
        }
    }

    @Override
    public void finishAnimations() {
        setColorButtonNavigationBar(-3);
        super.finishAnimations();
    }

    @Override
    public void changeColorIconBackground(int bg_color, int ic_color) {
        if (mCurrentBg == bg_color) {
            return;
        }
        mCurrentBg = bg_color;
        if (ColorUtils.isBrightColor(bg_color)) {
            ic_color = Color.BLACK;
        }
        mCurrentColor = ic_color;
        setColorButtonNavigationBar(ic_color);
        super.changeColorIconBackground(bg_color, ic_color);
    }

    public int getCurrentIconColor() {
        return mCurrentColor;
    }

    private void setColorButtonNavigationBar(int ic_color) {
        setKeyButtonViewButtonColor(mView.getSearchLight(), ic_color);
        setKeyButtonViewButtonColor(mView.getCameraButton(), ic_color);
        setKeyButtonViewButtonColor(mView.getRecentsButton(), ic_color);
        setKeyButtonViewButtonColor(mView.getLeftCursorButton(), ic_color);
        setKeyButtonViewButtonColor(mView.getRightCursorButton(), ic_color);
        setKeyButtonViewButtonColor(mView.getMenuButton(), ic_color);
        setKeyButtonViewButtonColor(mView.getMenuButtonTwo(), ic_color);
        setKeyButtonViewButtonColor(mView.getBackButton(), ic_color);
        setKeyButtonViewButtonColor(mView.getHomeButton(), ic_color);
        setKeyButtonViewButtonColor(mView.getNotifsButton(), ic_color);
        setKeyButtonViewColor(ic_color);
    }

    private void setKeyButtonViewColor(int ic_color) {
        if (mView == null) return;
        for (final AwesomeConstant k : AwesomeConstant.values()) {
            final View child = mView.findViewWithTag(k.value());

            if (child instanceof KeyButtonView) {
                if (ic_color == -3) {
                    ((KeyButtonView) child).clearColorFilterBg();
                } else {
                    ((KeyButtonView) child).setColorFilterBg(ic_color, PorterDuff.Mode.SRC_ATOP);
                }
            }
        }
    }

    private void setKeyButtonViewButtonColor(View button, int ic_color) {
        if (button instanceof KeyButtonView) {
            if (ic_color == -3) {
                ((KeyButtonView) button).clearColorFilterBg();
            } else {
                ((KeyButtonView) button).setColorFilterBg(ic_color, PorterDuff.Mode.SRC_ATOP);
            }
        }
    }

    private void applyLightsOut(boolean lightsOut, boolean animate, boolean force) {
        if (!force && lightsOut == mLightsOut) return;

        mLightsOut = lightsOut;

        final View navButtons = mView.getCurrentView().findViewById(R.id.nav_buttons);
        final View lowLights = mView.getCurrentView().findViewById(R.id.lights_out);

        // ok, everyone, stop it right there
        navButtons.animate().cancel();
        lowLights.animate().cancel();

        final float navButtonsAlpha = lightsOut ? 0f : 1f;
        final float lowLightsAlpha = lightsOut ? 1f : 0f;

        if (!animate) {
            navButtons.setAlpha(navButtonsAlpha);
            lowLights.setAlpha(lowLightsAlpha);
            lowLights.setVisibility(lightsOut ? View.VISIBLE : View.GONE);
        } else {
            final int duration = lightsOut ? LIGHTS_OUT_DURATION : LIGHTS_IN_DURATION;
            navButtons.animate()
                .alpha(navButtonsAlpha)
                .setDuration(duration)
                .start();

            lowLights.setOnTouchListener(mLightsOutListener);
            if (lowLights.getVisibility() == View.GONE) {
                lowLights.setAlpha(0f);
                lowLights.setVisibility(View.VISIBLE);
            }
            lowLights.animate()
                .alpha(lowLightsAlpha)
                .setDuration(duration)
                .setInterpolator(new AccelerateInterpolator(2.0f))
                .setListener(lightsOut ? null : new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator _a) {
                        lowLights.setVisibility(View.GONE);
                    }
                })
                .start();
        }
    }

    private final View.OnTouchListener mLightsOutListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent ev) {
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                // even though setting the systemUI visibility below will turn these views
                // on, we need them to come up faster so that they can catch this motion
                // event
                applyLightsOut(false, false, false);

                try {
                    mBarService.setSystemUiVisibility(0, View.SYSTEM_UI_FLAG_LOW_PROFILE);
                } catch (android.os.RemoteException ex) {
                }
            }
            return false;
        }
    };
}
