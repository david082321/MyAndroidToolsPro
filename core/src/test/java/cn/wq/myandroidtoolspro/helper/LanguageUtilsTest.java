package cn.wq.myandroidtoolspro.helper;

import android.content.SharedPreferences;

import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class LanguageUtilsTest {
    @Test
    public void migratingEnglishSettingPersistsItBeforeRemovingLegacyFlag() {
        Preferences preferences = new Preferences();
        preferences.values.put("english", true);

        assertEquals(0, LanguageUtils.getChoosedLanguage(preferences.proxy));
        assertEquals(0, preferences.proxy.getInt("language", -1));
        assertFalse(preferences.proxy.contains("english"));
        assertEquals(0, LanguageUtils.getChoosedLanguage(preferences.proxy));
    }

    @Test
    public void existingLanguageIndexesKeepTheirMeanings() {
        Preferences preferences = new Preferences();
        Locale[] locales = {Locale.ENGLISH, Locale.SIMPLIFIED_CHINESE, new Locale("ru", "RU")};
        for (int index = 0; index < locales.length; index++) {
            preferences.values.put("language", index);
            assertEquals(locales[index], LanguageUtils.getChoosedLocale(preferences.proxy));
        }
    }

    @Test
    public void traditionalChineseChoiceSurvivesRepeatedReads() {
        Preferences preferences = new Preferences();
        preferences.values.put("language", 3);
        assertEquals(Locale.TRADITIONAL_CHINESE, LanguageUtils.getChoosedLocale(preferences.proxy));
        assertEquals(Locale.TRADITIONAL_CHINESE, LanguageUtils.getChoosedLocale(preferences.proxy));
        assertEquals(3, preferences.proxy.getInt("language", -1));
    }

    @Test
    public void systemChineseRegionsSelectTheMatchingScript() {
        assertEquals(1, LanguageUtils.getLanguageForLocale(Locale.CHINA));
        assertEquals(3, LanguageUtils.getLanguageForLocale(Locale.TAIWAN));
        assertEquals(3, LanguageUtils.getLanguageForLocale(new Locale("zh", "HK")));
        assertEquals(3, LanguageUtils.getLanguageForLocale(new Locale("zh", "MO")));
        assertEquals(2, LanguageUtils.getLanguageForLocale(new Locale("ru", "RU")));
        assertEquals(0, LanguageUtils.getLanguageForLocale(Locale.ENGLISH));
    }

    private static final class Preferences implements InvocationHandler {
        private final Map<String, Object> values = new HashMap<>();
        private final SharedPreferences proxy = (SharedPreferences) Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(), new Class<?>[]{SharedPreferences.class}, this);
        private final SharedPreferences.Editor editor = (SharedPreferences.Editor) Proxy.newProxyInstance(
                SharedPreferences.Editor.class.getClassLoader(), new Class<?>[]{SharedPreferences.Editor.class}, this);

        @Override
        public Object invoke(Object object, Method method, Object[] args) {
            String name = method.getName();
            if ("edit".equals(name)) return editor;
            if ("contains".equals(name)) return values.containsKey(args[0]);
            if ("getInt".equals(name) || "getBoolean".equals(name)) {
                return values.containsKey(args[0]) ? values.get(args[0]) : args[1];
            }
            if ("putInt".equals(name) || "putBoolean".equals(name)) {
                values.put((String) args[0], args[1]);
                return editor;
            }
            if ("remove".equals(name)) {
                values.remove(args[0]);
                return editor;
            }
            if ("apply".equals(name)) return null;
            if ("commit".equals(name)) return true;
            throw new UnsupportedOperationException(name);
        }
    }
}
