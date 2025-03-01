/*
**
** Copyright 2019, Pearl Project
** Copyright 2019, Descendant
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

package com.android.keyguard.clocks;

import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.provider.Settings;
import android.support.v7.graphics.Palette;
import android.text.format.DateUtils;
import android.text.format.DateFormat;
import android.text.format.Time;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.TextView;
import android.provider.Settings;
import android.app.WallpaperManager;
import android.graphics.Color;
import android.app.WallpaperColors;
import android.support.v7.graphics.Palette;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import java.lang.NullPointerException;
import java.lang.IllegalStateException;
import android.graphics.Paint;
import android.os.ParcelFileDescriptor;
import android.graphics.BitmapFactory;

import com.android.internal.util.ArrayUtils;
import com.android.keyguard.clocks.ColorText;
import com.android.keyguard.clocks.LangGuard;

import java.lang.String;
import java.util.Locale;
import java.util.TimeZone;

import com.android.systemui.R;

public class CustomTextClock extends TextView {

    private String[] TensString = getResources().getStringArray(R.array.TensString);
    private String[] UnitsString = getResources().getStringArray(R.array.UnitsString);
    private String[] langExceptions = getResources().getStringArray(R.array.langExceptions);
    private String curLang = Locale.getDefault().getLanguage();
    private boolean langHasChanged;
    private String topText = getResources().getString(R.string.custom_text_clock_top_text_default);
    private String highNoonFirstRow = getResources().getString(R.string.high_noon_first_row);
    private String highNoonSecondRow = getResources().getString(R.string.high_noon_second_row);
    private String ZeroClock = getResources().getString(R.string.twelve_am);

    private Time mCalendar;

    private boolean mAttached;

    private int handType;

    private Context mContext;

    private boolean h24;

    private int mClockColor = 0xffffffff;
    private int mClockSize = 32;
    private SettingsObserver mSettingsObserver;
    private int mWallpaperColor;

    public CustomTextClock(Context context) {
        this(context, null);
    }

    public CustomTextClock(Context context, AttributeSet attrs) {
        super(context, attrs);

        final TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.CustomTextClock);

        handType = a.getInteger(R.styleable.CustomTextClock_HandType, 2);

        mContext = context;
        mCalendar = new Time();

        refreshLockFont();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!mAttached) {
            mAttached = true;
            IntentFilter filter = new IntentFilter();

            filter.addAction(Intent.ACTION_TIME_TICK);
            filter.addAction(Intent.ACTION_TIME_CHANGED);
            filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            filter.addAction(Intent.ACTION_LOCALE_CHANGED);

            // OK, this is gross but needed. This class is supported by the
            // remote views machanism and as a part of that the remote views
            // can be inflated by a context for another user without the app
            // having interact users permission - just for loading resources.
            // For exmaple, when adding widgets from a user profile to the
            // home screen. Therefore, we register the receiver as the current
            // user not the one the context is for.
            getContext().registerReceiverAsUser(mIntentReceiver,
                    android.os.Process.myUserHandle(), filter, null, getHandler());

        }

        // NOTE: It's safe to do these after registering the receiver since the receiver always runs
        // in the main thread, therefore the receiver can't run before this method returns.

        // The time zone may have changed while the receiver wasn't registered, so update the Time
        mCalendar = new Time();

        // Make sure we update to the current time
        onTimeChanged();

        if (mSettingsObserver == null) {
            mSettingsObserver = new SettingsObserver(new Handler());
        }
        mSettingsObserver.observe();
        updateClockColor();
        updateClockSize();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAttached) {
            getContext().unregisterReceiver(mIntentReceiver);
            mAttached = false;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (handType == 2) {
            //setText(topText);
            setTextColor(ColorText.getWallColor(mContext));
        } else {
	    setTextColor(mClockColor);
	}
	refreshLockFont();
    }

    private void onTimeChanged() {
        mCalendar.setToNow();
        h24 = DateFormat.is24HourFormat(getContext());

        int hour = mCalendar.hour;
        int minute = mCalendar.minute;

        if (!h24) {
            if (hour > 12) {
                hour = hour - 12;
            }
        }

        switch(handType){
            case 0:
                if (hour == 0) {
                setText(ZeroClock);
                } else if (hour == 12 && minute == 0) {
                setText(highNoonFirstRow);
                } else {
                setText(getIntString(hour, true));
                }
                break;
            case 1:
                if (hour == 12 && minute == 0) {
                setText(R.string.high_noon_second_row);
                } else {
                setText(getIntString(minute, false));
                }
                break;
            default:
                break;
        }

        updateContentDescription(mCalendar, getContext());
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_TIMEZONE_CHANGED)) {
                String tz = intent.getStringExtra("time-zone");
                mCalendar = new Time(TimeZone.getTimeZone(tz).getID());
            }
            if (intent.getAction().equals(Intent.ACTION_LOCALE_CHANGED)) {
                TensString = getResources().getStringArray(R.array.TensString);
                UnitsString = getResources().getStringArray(R.array.UnitsString);
                curLang = Locale.getDefault().getLanguage();
                topText = getResources().getString(R.string.custom_text_clock_top_text_default);
                highNoonFirstRow = getResources().getString(R.string.high_noon_first_row);
                highNoonSecondRow = getResources().getString(R.string.high_noon_second_row);
                ZeroClock = getResources().getString(R.string.twelve_am);
                langHasChanged = true;
            }
            onTimeChanged();

            invalidate();
        }
    };

    private void updateContentDescription(Time time, Context mContext) {
        final int flags = DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_24HOUR;
        String contentDescription = DateUtils.formatDateTime(mContext,
                time.toMillis(false), flags);
        setContentDescription(contentDescription);
    }

    private String getIntString (int num, boolean hours) {
        int tens, units;
        String NumString = "";
        if(num >= 20) {
            units = num % 10 ;
            tens =  num / 10;
            if ( units == 0 ) {
                NumString = TensString[tens];
            } else {
                if (LangGuard.isAvailable(langExceptions, curLang)) {
                    NumString = LangGuard.evaluateEx(curLang, units, TensString, UnitsString, tens, hours, num);
                } else {
                    NumString = TensString[tens]+" "+UnitsString[units].substring(2, UnitsString[units].length());
                }
            }
        } else if (num < 10 ) {
            if (curLang == "en") {
                if (hours) {
                    NumString = UnitsString[num].substring(2, UnitsString[num].length());
                } else {
                    NumString = UnitsString[num];
                }
            } else if (LangGuard.isAvailable(langExceptions, curLang)) {
                NumString = LangGuard.evaluateEx(curLang, 0, TensString, UnitsString, 0, hours, num);
            } else {
                NumString = UnitsString[num];
            }
        } else if (num < 20 && num >= 10) {
                if (LangGuard.isAvailable(langExceptions, curLang)) {
                    NumString = LangGuard.evaluateEx(curLang, 0, TensString, UnitsString, 0, hours, num);
                } else {
                    NumString = UnitsString[num];
                }
        }

        return NumString;
    }

    private void updateClockColor() {
        mClockColor = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.LOCKSCREEN_CLOCK_COLOR, 0xFFFFFFFF,
                UserHandle.USER_CURRENT);
            setTextColor(mClockColor);
            onTimeChanged();
    }

    public void updateClockSize() {
        mClockSize = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.CUSTOM_TEXT_CLOCK_FONT_SIZE, 32,
                UserHandle.USER_CURRENT);
            setTextSize(mClockSize);
            onTimeChanged();
    }

    private int getLockClockFont() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LOCK_CLOCK_FONTS, 29);
    }

    private void refreshLockFont() {
        final Resources res = getContext().getResources();
        boolean isPrimary = UserHandle.getCallingUserId() == UserHandle.USER_OWNER;
        int lockClockFont = isPrimary ? getLockClockFont() : 29;

        if (lockClockFont == 0) {
            setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        }
        if (lockClockFont == 1) {
            setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        }
        if (lockClockFont == 2) {
            setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
        }
        if (lockClockFont == 3) {
            setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
        }
        if (lockClockFont == 4) {
            setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
        }
        if (lockClockFont == 5) {
            setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        }
        if (lockClockFont == 6) {
            setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
        }
        if (lockClockFont == 7) {
            setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        }
        if (lockClockFont == 8) {
            setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
        }
        if (lockClockFont == 9) {
            setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
        }
        if (lockClockFont == 10) {
            setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        }
        if (lockClockFont == 11) {
            setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
        }
        if (lockClockFont == 12) {
            setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        if (lockClockFont == 13) {
            setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
        }
        if (lockClockFont == 14) {
            setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
        }
        if (lockClockFont == 15) {
            setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
        }
        if (lockClockFont == 16) {
            setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        }
        if (lockClockFont == 17) {
            setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
        }
        if (lockClockFont == 18) {
            setTypeface(Typeface.create("cursive", Typeface.NORMAL));
        }
        if (lockClockFont == 19) {
            setTypeface(Typeface.create("cursive", Typeface.BOLD));
        }
        if (lockClockFont == 20) {
            setTypeface(Typeface.create("casual", Typeface.NORMAL));
        }
        if (lockClockFont == 21) {
            setTypeface(Typeface.create("serif", Typeface.NORMAL));
        }
        if (lockClockFont == 22) {
            setTypeface(Typeface.create("serif", Typeface.ITALIC));
        }
        if (lockClockFont == 23) {
            setTypeface(Typeface.create("serif", Typeface.BOLD));
        }
        if (lockClockFont == 24) {
            setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
        }
        if (lockClockFont == 25) {
            setTypeface(Typeface.create("gobold-light-sys", Typeface.NORMAL));
        }
        if (lockClockFont == 26) {
            setTypeface(Typeface.create("roadrage-sys", Typeface.NORMAL));
        }
        if (lockClockFont == 27) {
            setTypeface(Typeface.create("snowstorm-sys", Typeface.NORMAL));
        }
        if (lockClockFont == 28) {
            setTypeface(Typeface.create("googlesans-sys", Typeface.NORMAL));
        }
	if (lockClockFont == 29) {
            setTypeface(Typeface.create("neoneon-sys", Typeface.NORMAL));
        }
        if (lockClockFont == 30) {
            setTypeface(Typeface.create("themeable-sys", Typeface.NORMAL));
	}
	if (lockClockFont == 31) {
            setTypeface(Typeface.create("samsung-sys", Typeface.NORMAL));
        }
	if (lockClockFont == 32) {
            setTypeface(Typeface.create("mexcellent-sys", Typeface.NORMAL));
        }
	if (lockClockFont == 33) {
            setTypeface(Typeface.create("burnstown-sys", Typeface.NORMAL));
        }
        if (lockClockFont == 34) {
            setTypeface(Typeface.create("dumbledor-sys", Typeface.NORMAL));
        }
	if (lockClockFont == 35) {
            setTypeface(Typeface.create("phantombold-sys", Typeface.NORMAL));
	}
    }

    protected class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }
        void observe() {
            ContentResolver resolver = mContext.getContentResolver();

            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.LOCKSCREEN_CLOCK_COLOR),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.CUSTOM_TEXT_CLOCK_FONT_SIZE),
                    false, this, UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange) {
	    updateClockColor();
	    updateClockSize();
        }
    }
}

