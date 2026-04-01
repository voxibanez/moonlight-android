package com.limelight;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.profiles.ProfilesManager;
import com.limelight.profiles.SettingsProfile;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Config(sdk = {33}, shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
@RunWith(RobolectricTestRunner.class)
public class ShortcutTrampolineResolutionOverrideTest {
    private static final String RESOLUTION_PREF_KEY = "list_resolution";
    private static final String FPS_PREF_KEY = "list_fps";

    private Context context;
    private SharedPreferences defaultPrefs;

    @BeforeClass
    public static void suppressInvalidIdLogs() {
        TestLogSuppressor.install();
    }

    @Before
    public void setUp() {
        resetProfilesManager();

        context = ApplicationProvider.getApplicationContext();
        defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        defaultPrefs.edit().clear().commit();

        File profilesDir = new File(context.getFilesDir(), "profiles");
        deleteRecursively(profilesDir);
    }

    @Test
    public void customResolutionExtraOverridesResolutionForCurrentLaunchOnly() {
        defaultPrefs.edit()
                .putString(RESOLUTION_PREF_KEY, PreferenceConfiguration.RES_1080P)
                .commit();

        Intent intent = new Intent()
                .putExtra(ShortcutTrampoline.EXTRA_STREAM_RESOLUTION, "3440x1440");

        PreferenceConfiguration launchConfig = PreferenceConfiguration.readPreferences(
                context,
                ShortcutTrampoline.getLaunchSharedPreferences(context, intent));

        assertEquals(3440, launchConfig.width);
        assertEquals(1440, launchConfig.height);
        assertEquals(
                PreferenceConfiguration.RES_1080P,
                defaultPrefs.getString(RESOLUTION_PREF_KEY, null));
    }

    @Test
    public void missingExtraLeavesSavedResolutionUnchanged() {
        defaultPrefs.edit()
                .putString(RESOLUTION_PREF_KEY, PreferenceConfiguration.RES_1440P)
                .commit();

        PreferenceConfiguration launchConfig = PreferenceConfiguration.readPreferences(
                context,
                ShortcutTrampoline.getLaunchSharedPreferences(context, new Intent()));

        assertEquals(2560, launchConfig.width);
        assertEquals(1440, launchConfig.height);
    }

    @Test
    public void invalidExtraIsIgnored() {
        defaultPrefs.edit()
                .putString(RESOLUTION_PREF_KEY, PreferenceConfiguration.RES_1080P)
                .commit();

        Intent intent = new Intent()
                .putExtra(ShortcutTrampoline.EXTRA_STREAM_RESOLUTION, "1024-by-768");

        PreferenceConfiguration launchConfig = PreferenceConfiguration.readPreferences(
                context,
                ShortcutTrampoline.getLaunchSharedPreferences(context, intent));

        assertEquals(1920, launchConfig.width);
        assertEquals(1080, launchConfig.height);
    }

    @Test
    public void customResolutionExtraDoesNotChangeActiveProfileResolution() {
        defaultPrefs.edit()
                .putString(RESOLUTION_PREF_KEY, PreferenceConfiguration.RES_1080P)
                .commit();

        ProfilesManager profilesManager = ProfilesManager.getInstance();
        Map<String, Object> options = new HashMap<>();
        options.put(RESOLUTION_PREF_KEY, PreferenceConfiguration.RES_1440P);

        SettingsProfile profile = new SettingsProfile(
                UUID.randomUUID(),
                "Dynamic Resolution",
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                options);
        profilesManager.add(profile);
        profilesManager.setActive(profile.getUuid());

        Intent intent = new Intent()
                .putExtra(ShortcutTrampoline.EXTRA_STREAM_RESOLUTION, "2400x1080");

        PreferenceConfiguration launchConfig = PreferenceConfiguration.readPreferences(
                context,
                ShortcutTrampoline.getLaunchSharedPreferences(context, intent));
        PreferenceConfiguration persistedConfig = PreferenceConfiguration.readPreferences(context);

        assertEquals(2400, launchConfig.width);
        assertEquals(1080, launchConfig.height);
        assertEquals(2560, persistedConfig.width);
        assertEquals(1440, persistedConfig.height);
        assertNotNull(profilesManager.getActive());
        assertEquals(profile.getUuid(), profilesManager.getActive().getUuid());
    }

    @Test
    public void autoResolutionAndFpsExtrasAreAccepted() {
        Intent intent = new Intent()
                .putExtra(ShortcutTrampoline.EXTRA_STREAM_RESOLUTION, PreferenceConfiguration.AUTO_STREAM_VALUE)
                .putExtra(ShortcutTrampoline.EXTRA_STREAM_FPS, PreferenceConfiguration.AUTO_STREAM_VALUE);

        SharedPreferences launchPrefs = ShortcutTrampoline.getLaunchSharedPreferences(context, intent);

        assertEquals(
                PreferenceConfiguration.AUTO_STREAM_VALUE,
                launchPrefs.getString(RESOLUTION_PREF_KEY, null));
        assertEquals(
                PreferenceConfiguration.AUTO_STREAM_VALUE,
                launchPrefs.getString(FPS_PREF_KEY, null));
    }

    @Test
    public void customFpsExtraOverridesSavedFpsForCurrentLaunchOnly() {
        defaultPrefs.edit()
                .putString(RESOLUTION_PREF_KEY, PreferenceConfiguration.RES_1080P)
                .putString(FPS_PREF_KEY, "60")
                .commit();

        Intent intent = new Intent()
                .putExtra(ShortcutTrampoline.EXTRA_STREAM_FPS, "120");

        PreferenceConfiguration launchConfig = PreferenceConfiguration.readPreferences(
                context,
                ShortcutTrampoline.getLaunchSharedPreferences(context, intent));

        assertEquals(120f, launchConfig.fps, 0.001f);
        assertEquals("60", defaultPrefs.getString(FPS_PREF_KEY, null));
    }

    @Test
    public void autoSettingsResolveAgainstCurrentDisplay() {
        defaultPrefs.edit()
                .putString(RESOLUTION_PREF_KEY, PreferenceConfiguration.AUTO_STREAM_VALUE)
                .putString(FPS_PREF_KEY, PreferenceConfiguration.AUTO_STREAM_VALUE)
                .commit();

        PreferenceConfiguration config = PreferenceConfiguration.readPreferences(context);

        assertTrue(config.width > 0);
        assertTrue(config.height > 0);
        assertTrue(config.fps > 0);
    }

    private static void resetProfilesManager() {
        try {
            java.lang.reflect.Field instanceField = ProfilesManager.class.getDeclaredField("instance");
            instanceField.setAccessible(true);
            instanceField.set(null, null);
        } catch (Exception ignored) {
        }
    }

    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        f.delete();
    }
}
