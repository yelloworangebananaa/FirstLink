package com.example.firstlink;

import android.app.Application;

/** Applies the saved light/dark choice before the first screen is drawn. */
public class FirstLinkApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        SettingsActivity.applySavedAppearance(this);
    }
}
