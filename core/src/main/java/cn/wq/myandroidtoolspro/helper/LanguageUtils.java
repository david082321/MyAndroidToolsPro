package cn.wq.myandroidtoolspro.helper;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;
import android.preference.PreferenceManager;

import java.util.Locale;

import cn.wq.myandroidtoolspro.BaseActivity;

public class LanguageUtils {
    public static int getChoosedLanguage(SharedPreferences sharedPreferences) {
        int defaultLanguage = sharedPreferences.getInt(BaseActivity.PREFERENCE_LANGUAGE, -1);
        if (defaultLanguage < 0 || defaultLanguage > 3) {
            boolean usingEnglish = sharedPreferences.getBoolean(BaseActivity.PREFERENCE_ENGLISH, false);
            if (usingEnglish) {
                defaultLanguage = 0;
            } else {
                defaultLanguage = getLanguageForLocale(getSystemLocale());
            }
            sharedPreferences.edit()
                    .putInt(BaseActivity.PREFERENCE_LANGUAGE, defaultLanguage)
                    .remove(BaseActivity.PREFERENCE_ENGLISH)
                    .apply();
        }
        return defaultLanguage;
    }

    public static Locale getChoosedLocale(SharedPreferences sharedPreferences) {
        int language = getChoosedLanguage(sharedPreferences);
        Locale locale;
        if (language == 0) {
            locale = Locale.ENGLISH;
        } else if (language == 1) {
            locale = Locale.CHINA;
        } else if (language == 2) {
            locale = new Locale("ru", "RU");
        } else {
            locale = Locale.TRADITIONAL_CHINESE;
        }
        return locale;
    }

    static int getLanguageForLocale(Locale locale) {
        if ("zh".equals(locale.getLanguage())) {
            String country = locale.getCountry();
            boolean traditional = "TW".equals(country) || "HK".equals(country) || "MO".equals(country)
                    || (Build.VERSION.SDK_INT >= 21 && "Hant".equals(locale.getScript()));
            return traditional ? 3 : 1;
        }
        return "ru".equals(locale.getLanguage()) ? 2 : 0;
    }

    public static Context wrapContext(Context context) {
        Configuration configuration = getLocalizedConfiguration(context);
        if (Build.VERSION.SDK_INT >= 17) {
            return context.createConfigurationContext(configuration);
        }
        context.getResources().updateConfiguration(configuration, context.getResources().getDisplayMetrics());
        return context;
    }

    public static void applyLanguage(Context context) {
        Resources resources = context.getResources();
        resources.updateConfiguration(getLocalizedConfiguration(context), resources.getDisplayMetrics());
    }

    private static Configuration getLocalizedConfiguration(Context context) {
        Locale locale = getChoosedLocale(PreferenceManager.getDefaultSharedPreferences(context));
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        if (Build.VERSION.SDK_INT >= 24) {
            configuration.setLocales(new LocaleList(locale));
        } else if (Build.VERSION.SDK_INT >= 17) {
            configuration.setLocale(locale);
        } else {
            configuration.locale = locale;
        }
        return configuration;
    }

    private static Locale getSystemLocale() {
        if (Build.VERSION.SDK_INT >= 24) {
            return Resources.getSystem().getConfiguration().getLocales().get(0);
        } else {
            return Resources.getSystem().getConfiguration().locale;
        }
    }

}
