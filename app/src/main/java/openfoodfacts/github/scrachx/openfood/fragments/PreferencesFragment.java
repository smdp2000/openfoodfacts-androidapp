package openfoodfacts.github.scrachx.openfood.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.provider.SearchRecentSuggestions;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.CheckBoxPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.fasterxml.jackson.core.type.TypeReference;

import net.steamcrafted.loadtoast.LoadToast;

import org.apache.commons.lang.StringUtils;
import org.greenrobot.greendao.async.AsyncSession;
import org.greenrobot.greendao.query.WhereCondition;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import openfoodfacts.github.scrachx.openfood.BuildConfig;
import openfoodfacts.github.scrachx.openfood.R;
import openfoodfacts.github.scrachx.openfood.models.Additive;
import openfoodfacts.github.scrachx.openfood.models.AdditiveDao;
import openfoodfacts.github.scrachx.openfood.models.AnalysisTagConfig;
import openfoodfacts.github.scrachx.openfood.models.AnalysisTagConfigDao;
import openfoodfacts.github.scrachx.openfood.models.CountryName;
import openfoodfacts.github.scrachx.openfood.models.CountryNameDao;
import openfoodfacts.github.scrachx.openfood.models.DaoSession;
import openfoodfacts.github.scrachx.openfood.utils.INavigationItem;
import openfoodfacts.github.scrachx.openfood.utils.JsonUtils;
import openfoodfacts.github.scrachx.openfood.utils.LocaleHelper;
import openfoodfacts.github.scrachx.openfood.utils.NavigationDrawerListener;
import openfoodfacts.github.scrachx.openfood.utils.NavigationDrawerListener.NavigationDrawerType;
import openfoodfacts.github.scrachx.openfood.utils.SearchSuggestionProvider;
import openfoodfacts.github.scrachx.openfood.utils.Utils;
import openfoodfacts.github.scrachx.openfood.views.LoadTaxonomiesService;
import openfoodfacts.github.scrachx.openfood.views.OFFApplication;
import openfoodfacts.github.scrachx.openfood.views.customtabs.CustomTabActivityHelper;
import openfoodfacts.github.scrachx.openfood.views.customtabs.WebViewFallback;

import static openfoodfacts.github.scrachx.openfood.utils.NavigationDrawerListener.ITEM_PREFERENCES;

public class PreferencesFragment extends PreferenceFragmentCompat implements INavigationItem {
    private AdditiveDao mAdditiveDao;
    private NavigationDrawerListener navigationDrawerListener;
    private Context context;

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        MenuItem item = menu.findItem(R.id.action_search);
        item.setVisible(false);
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        setHasOptionsMenu(true);
        context = getContext();

        ListPreference languagePreference = ((ListPreference) findPreference("Locale.Helper.Selected.Language"));

        SharedPreferences settings = getActivity().getSharedPreferences("prefs", 0);
        mAdditiveDao = Utils.getAppDaoSession(getActivity()).getAdditiveDao();

        String[] localeValues = getActivity().getResources().getStringArray(R.array.languages_array);
        String[] localeLabels = new String[localeValues.length];
        List<String> finalLocalValues = new ArrayList<>();
        List<String> finalLocalLabels = new ArrayList<>();

        for (int i = 0; i < localeValues.length; i++) {
            Locale current = LocaleHelper.getLocale(localeValues[i]);

            if (current != null) {
                localeLabels[i] = StringUtils.capitalize(current.getDisplayName(current));
                finalLocalLabels.add(localeLabels[i]);
                finalLocalValues.add(localeValues[i]);
            }
        }

        languagePreference.setEntries(finalLocalLabels.toArray(new String[finalLocalLabels.size()]));
        languagePreference.setEntryValues(finalLocalValues.toArray(new String[finalLocalValues.size()]));

        languagePreference.setOnPreferenceChangeListener((preference, locale) -> {

            FragmentActivity activity = PreferencesFragment.this.getActivity();
            Configuration configuration = activity.getResources().getConfiguration();
            Toast.makeText(getContext(), getString(R.string.changes_saved), Toast.LENGTH_SHORT).show();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                configuration.setLocale(LocaleHelper.getLocale((String) locale));
                new GetAdditives().execute();
            }
            return true;
        });

        Preference deleteSearchHistoryButton = findPreference("deleteSearchHistoryPreference");
        deleteSearchHistoryButton.setOnPreferenceClickListener(preference -> {
            Toast.makeText(getContext(), getString(R.string.preference_delete_search_history), Toast.LENGTH_SHORT).show();
            SearchRecentSuggestions suggestions = new SearchRecentSuggestions(getContext(), SearchSuggestionProvider.AUTHORITY, SearchSuggestionProvider.MODE);
            suggestions.clearHistory();
            return true;
        });

        ListPreference countryPreference = ((ListPreference) findPreference(LocaleHelper.USER_COUNTRY_PREFERENCE_KEY));
        List<String> countryLabels = new ArrayList<>();
        List<String> countryTags = new ArrayList<>();

        DaoSession daoSession = OFFApplication.getInstance().getDaoSession();
        AsyncSession asyncSessionCountries = daoSession.startAsyncSession();
        CountryNameDao countryNameDao = daoSession.getCountryNameDao();

        asyncSessionCountries.setListenerMainThread(operation -> {
            @SuppressWarnings("unchecked")
            List<CountryName> countryNames = (List<CountryName>) operation.getResult();
            for (int i = 0; i < countryNames.size(); i++) {
                countryLabels.add(countryNames.get(i).getName());
                countryTags.add(countryNames.get(i).getCountyTag());
            }
            countryPreference.setEntries(countryLabels.toArray(new String[0]));
            countryPreference.setEntryValues(countryTags.toArray(new String[0]));
        });

        asyncSessionCountries.queryList(countryNameDao.queryBuilder()
            .where(CountryNameDao.Properties.LanguageCode.eq(LocaleHelper.getLanguage(getActivity())))
            .orderAsc(CountryNameDao.Properties.Name).build());

        countryPreference.setOnPreferenceChangeListener(((preference, newValue) -> {
            if (preference instanceof ListPreference) {
                if (preference.getKey().equals(LocaleHelper.USER_COUNTRY_PREFERENCE_KEY)) {
                    String country = (String) newValue;
                    SharedPreferences.Editor editor = settings.edit();
                    editor.putString(preference.getKey(), country);
                    editor.apply();
                    Toast.makeText(getContext(), getString(R.string.changes_saved), Toast.LENGTH_SHORT).show();
                }
            }
            return true;
        }));

        Preference contactButton = findPreference("contact_team");
        contactButton.setOnPreferenceClickListener(preference -> {

            Intent contactIntent = new Intent(Intent.ACTION_SENDTO);
            contactIntent.setData(Uri.parse(getString(R.string.off_mail)));
            contactIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(contactIntent);
            } catch (android.content.ActivityNotFoundException e) {

                Toast.makeText(getActivity(), R.string.email_not_found, Toast.LENGTH_SHORT).show();
            }
            return true;
        });

        Preference rateus = findPreference("RateUs");
        rateus.setOnPreferenceClickListener(preference -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + context.getPackageName())));
            } catch (android.content.ActivityNotFoundException e) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + context.getPackageName())));
            }
            return true;
        });

        findPreference("FAQ").setOnPreferenceClickListener(preference -> openWebCustomTab(R.string.faq_url));
        findPreference("Terms").setOnPreferenceClickListener(preference -> openWebCustomTab(R.string.terms_url));
        findPreference("local_translate_help").setOnPreferenceClickListener(preference -> openWebCustomTab(R.string.translate_url));

        ListPreference energyUnitPreference = (ListPreference) findPreference("energyUnitPreference");
        String[] energyUnits = getActivity().getResources().getStringArray(R.array.energy_units);
        energyUnitPreference.setEntries(energyUnits);
        energyUnitPreference.setEntryValues(energyUnits);
        energyUnitPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            settings.edit().putString("energyUnitPreference", (String) newValue).apply();
            Toast.makeText(getActivity(), getString(R.string.changes_saved), Toast.LENGTH_SHORT).show();
            return true;
        });

        ListPreference volumeUnitPreference = (ListPreference) findPreference("volumeUnitPreference");
        String[] volumeUnits = getActivity().getResources().getStringArray(R.array.volume_units);
        volumeUnitPreference.setEntries(volumeUnits);
        volumeUnitPreference.setEntryValues(volumeUnits);
        volumeUnitPreference.setOnPreferenceChangeListener(((preference, newValue) -> {
            settings.edit().putString("volumeUnitPreference", (String) newValue).apply();
            Toast.makeText(getActivity(), getString(R.string.changes_saved), Toast.LENGTH_SHORT).show();
            return true;
        }));

        ListPreference imageUploadPref = ((ListPreference) findPreference("ImageUpload"));
        String[] values = getActivity().getResources().getStringArray(R.array.upload_image);
        imageUploadPref.setEntries(values);
        imageUploadPref.setEntryValues(values);
        imageUploadPref.setOnPreferenceChangeListener((preference, newValue) -> {
            settings.edit().putString("imageUpload", (String) newValue).apply();
            Toast.makeText(getActivity(), getString(R.string.changes_saved), Toast.LENGTH_SHORT).show();
            return true;
        });

        CheckBoxPreference photoPreference = (CheckBoxPreference) findPreference("photoMode");
        if (BuildConfig.FLAVOR.equals("opf")) {
            photoPreference.setVisible(false);
        }

        /*
            Preference to show version name
         */
        Preference versionPref = findPreference("Version");
        versionPref.setEnabled(false);
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            String version = pInfo.versionName;
            versionPref.setSummary(getString(R.string.version_string) + " " + version);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(PreferencesFragment.class.getSimpleName(), "onCreatePreferences", e);
        }

        if (BuildConfig.FLAVOR.equals("off")) {
            buildDisplayCategory(daoSession);
        } else {
            PreferenceScreen preferenceScreen = getPreferenceScreen();
            PreferenceCategory displayCategory = (PreferenceCategory) preferenceScreen.findPreference("display_category");
            preferenceScreen.removePreference(displayCategory);
        }
    }

    private void buildDisplayCategory(DaoSession daoSession) {
        AsyncSession asyncSessionAnalysisTags = daoSession.startAsyncSession();
        AnalysisTagConfigDao analysisTagConfigDao = daoSession.getAnalysisTagConfigDao();
        asyncSessionAnalysisTags.setListenerMainThread(operation -> {
            if (isAdded()) {
                PreferenceScreen preferenceScreen = getPreferenceScreen();
                PreferenceCategory displayCategory = (PreferenceCategory) preferenceScreen.findPreference("display_category");
                displayCategory.removeAll();
                preferenceScreen.addPreference(displayCategory);

                List<AnalysisTagConfig> analysisTagConfigs = (List<AnalysisTagConfig>) operation.getResult();
                // If analysis tag is empty show "Load ingredient detection data" option in order to manually reload taxonomies
                if (analysisTagConfigs.isEmpty()) {
                    Preference preference = new Preference(preferenceScreen.getContext());
                    preference.setTitle(R.string.load_ingredient_detection_data);
                    preference.setSummary(R.string.load_ingredient_detection_data_summary);
                    preference.setOnPreferenceClickListener(pref -> {
                        pref.setOnPreferenceClickListener(null);
                        //the service will load server resources only if newer than already downloaded...
                        Intent intent = new Intent(context, LoadTaxonomiesService.class);
                        intent.putExtra("receiver", new ResultReceiver(new Handler()) {
                            @Override
                            protected void onReceiveResult(int resultCode, Bundle resultData) {
                                super.onReceiveResult(resultCode, resultData);

                                if (resultCode == LoadTaxonomiesService.STATUS_RUNNING) {
                                    preference.setTitle(R.string.please_wait);
                                    preference.setIcon(R.drawable.ic_cloud_download_black_24dp);
                                    preference.setSummary(null);
                                    preference.setWidgetLayoutResource(R.layout.loading);
                                } else {
                                    buildDisplayCategory(daoSession);
                                }
                            }
                        });
                        context.startService(intent);
                        return true;
                    });
                    displayCategory.addPreference(preference);
                } else {
                    for (AnalysisTagConfig config :
                        analysisTagConfigs) {
                        CheckBoxPreference preference = new CheckBoxPreference(preferenceScreen.getContext());
                        preference.setKey(config.getType());
                        preference.setDefaultValue(true);
                        preference.setSummary(null);
                        preference.setSummaryOn(null);
                        preference.setSummaryOff(null);
                        preference.setTitle(getString(R.string.display_analysis_tag_status, config.getType()));
                        displayCategory.addPreference(preference);
                    }
                }

                displayCategory.setVisible(true);
            }
        });
        asyncSessionAnalysisTags.queryList(analysisTagConfigDao.queryBuilder()
            .where(new WhereCondition.StringCondition("1 GROUP BY type"))
            .orderAsc(AnalysisTagConfigDao.Properties.Type).build());
    }

    private boolean openWebCustomTab(int faqUrl) {
        CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
        customTabsIntent.intent.putExtra("android.intent.extra.REFERRER", Uri.parse("android-app://" + getContext().getPackageName()));
        CustomTabActivityHelper.openCustomTab(getActivity(), customTabsIntent, Uri.parse(getString(faqUrl)), new WebViewFallback());
        return true;
    }

    @Override
    public NavigationDrawerListener getNavigationDrawerListener() {
        if (navigationDrawerListener == null && getActivity() instanceof NavigationDrawerListener) {
            navigationDrawerListener = (NavigationDrawerListener) getActivity();
        }

        return navigationDrawerListener;
    }

    @Override
    @NavigationDrawerType
    public int getNavigationDrawerType() {
        return ITEM_PREFERENCES;
    }

    @Override
    public void onResume() {
        super.onResume();
        try {
            final AppCompatActivity activity = (AppCompatActivity) getActivity();
            if (activity != null && activity.getSupportActionBar() != null) {
                activity.getSupportActionBar().setTitle(getString(R.string.action_preferences));
            }
        } catch (NullPointerException e) {
            Log.e(getClass().getSimpleName(), "on resume error", e);
        }
    }

    private class GetAdditives extends AsyncTask<Void, Integer, Boolean> {
        private static final String ADDITIVE_IMPORT = "ADDITIVE_IMPORT";
        private LoadToast lt = new LoadToast(getActivity());

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            lt.setText(getActivity().getString(R.string.toast_retrieving));
            lt.setBackgroundColor(ContextCompat.getColor(getActivity(), R.color.blue));
            lt.setTextColor(ContextCompat.getColor(getActivity(), R.color.white));
            lt.show();
        }

        @Override
        protected Boolean doInBackground(Void... arg0) {
            final FragmentActivity activity = getActivity();
            if (activity == null) {
                return true;
            }
            boolean result = true;
            String additivesFile = "additives_" + LocaleHelper.getLanguage(activity) + ".json";
            try (InputStream is = activity.getAssets().open(additivesFile)) {
                List<Additive> frenchAdditives = JsonUtils.readFor(new TypeReference<List<Additive>>() {
                })
                    .readValue(is);
                mAdditiveDao.insertOrReplaceInTx(frenchAdditives);
            } catch (IOException e) {
                result = false;
                Log.e(ADDITIVE_IMPORT, "Unable to import additives from " + additivesFile, e);
            }
            return result;
        }

        @Override
        protected void onPostExecute(final Boolean result) {
            super.onPostExecute(result);
            lt.hide();
            final FragmentActivity activity = getActivity();
            if (activity != null) {
                activity.recreate();
            }
        }
    }
}
