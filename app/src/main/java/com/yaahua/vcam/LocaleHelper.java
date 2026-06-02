package com.yaahua.vcam;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;

import java.util.Locale;

public class LocaleHelper {
    private static String currentLanguage = "";

    public static Context onAttach(Context context) {
        if (currentLanguage == null || currentLanguage.isEmpty()) return context;
        return setLocale(context, currentLanguage);
    }

    public static Context setLocale(Context context, String language) {
        if (language == null || language.isEmpty()) return context;
        Locale locale = "zh".equals(language) ? Locale.CHINESE : new Locale(language);
        Locale.setDefault(locale);
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(locale);
        return context.createConfigurationContext(config);
    }
}