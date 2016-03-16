package me.ashram.bearingfinder.activity.settings;

import android.preference.PreferenceActivity;
import android.os.Bundle;

import me.ashram.bearingfinder.R;

public class SettingsActivity extends PreferenceActivity {

    private static final String USE_BEACON_KEY_PREFIX = "pref_use_beacon_";
    private static final String BEACON_ADDRESS_KEY_PREFIX = "pref_key_beacon_address_";
    private static final String BEARING_FINDER_ADDRESS_KEY = "pref_key_bearing_finder_address";
    private static final String DELIMITER_KEY = "pref_key_delimiter_name";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
    }

    public static String getUseBeaconKey(int i) {
        return USE_BEACON_KEY_PREFIX + i;
    }

    public static String getBeaconAddressKey(int i) {
        return BEACON_ADDRESS_KEY_PREFIX + i;
    }

    public static String getBearingFinderAddressKey() {
        return BEARING_FINDER_ADDRESS_KEY;
    }

    public static String getCorrectDelimiterKey() {
        return DELIMITER_KEY;
    }
}
