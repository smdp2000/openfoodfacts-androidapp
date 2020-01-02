package openfoodfacts.github.scrachx.openfood.fragments;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsIntent;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.afollestad.materialdialogs.MaterialDialog;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

import butterknife.BindView;
import butterknife.OnClick;
import io.reactivex.SingleObserver;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.ResponseBody;
import openfoodfacts.github.scrachx.openfood.R;
import openfoodfacts.github.scrachx.openfood.models.Search;
import openfoodfacts.github.scrachx.openfood.models.TaglineLanguageModel;
import openfoodfacts.github.scrachx.openfood.network.OpenFoodAPIClient;
import openfoodfacts.github.scrachx.openfood.network.OpenFoodAPIService;
import openfoodfacts.github.scrachx.openfood.utils.LocaleHelper;
import openfoodfacts.github.scrachx.openfood.utils.NavigationDrawerListener.NavigationDrawerType;
import openfoodfacts.github.scrachx.openfood.utils.Utils;
import openfoodfacts.github.scrachx.openfood.views.ContinuousScanActivity;
import openfoodfacts.github.scrachx.openfood.views.MainActivity;
import openfoodfacts.github.scrachx.openfood.views.OFFApplication;
import openfoodfacts.github.scrachx.openfood.views.customtabs.CustomTabActivityHelper;
import openfoodfacts.github.scrachx.openfood.views.customtabs.CustomTabsHelper;
import openfoodfacts.github.scrachx.openfood.views.customtabs.WebViewFallback;
import openfoodfacts.github.scrachx.openfood.views.listeners.BottomNavigationListenerInstaller;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import static openfoodfacts.github.scrachx.openfood.utils.NavigationDrawerListener.ITEM_HOME;

public class HomeFragment extends NavigationBaseFragment implements CustomTabActivityHelper.ConnectionCallback {
    @BindView(R.id.tvDailyFoodFact)
    TextView tvDailyFoodFact;
    @BindView(R.id.textHome)
    TextView textHome;
    @BindView(R.id.bottom_navigation)
    BottomNavigationView bottomNavigationView;
    private OpenFoodAPIService apiClient;
    private SharedPreferences sp;
    private String taglineURL;
    private Disposable disposable;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return createView(inflater, container, R.layout.fragment_home);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        apiClient = new OpenFoodAPIClient(getActivity()).getAPIService();
        checkUserCredentials();
        sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        BottomNavigationListenerInstaller.selectNavigationItem(bottomNavigationView, R.id.home_page);
        BottomNavigationListenerInstaller.install(bottomNavigationView, getActivity(), getContext());
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @OnClick(R.id.tvDailyFoodFact)
    protected void setDailyFoodFact() {
        // chrome custom tab init
        CustomTabsIntent customTabsIntent;
        CustomTabActivityHelper customTabActivityHelper = new CustomTabActivityHelper();
        customTabActivityHelper.setConnectionCallback(this);
        Uri dailyFoodFactUri = Uri.parse(taglineURL);
        customTabActivityHelper.mayLaunchUrl(dailyFoodFactUri, null, null);

        customTabsIntent = CustomTabsHelper.getCustomTabsIntent(getContext(),
            customTabActivityHelper.getSession());
        CustomTabActivityHelper.openCustomTab(getActivity(),
            customTabsIntent, dailyFoodFactUri, new WebViewFallback());
    }

    @Override
    @NavigationDrawerType
    public int getNavigationDrawerType() {
        return ITEM_HOME;
    }

    private void checkUserCredentials() {
        final SharedPreferences settings = OFFApplication.getInstance().getSharedPreferences("login", 0);
        String login = settings.getString("user", "");
        String password = settings.getString("pass", "");

        if (!login.isEmpty() && !password.isEmpty()) {
            apiClient.signIn(login, password, "Sign-in").enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                    String htmlNoParsed = null;
                    try {
                        htmlNoParsed = response.body().string();
                    } catch (IOException e) {
                        Log.e(HomeFragment.class.getSimpleName(), "signin", e);
                    }
                    if (htmlNoParsed != null && (htmlNoParsed.contains("Incorrect user name or password.")
                        || htmlNoParsed.contains("See you soon!"))) {
                        settings.edit()
                            .putString("user", "")
                            .putString("pass", "")
                            .apply();

                        if(getActivity()!=null) {
                            new MaterialDialog.Builder(getActivity())
                                .title(R.string.alert_dialog_warning_title)
                                .content(R.string.alert_dialog_warning_msg_user)
                                .positiveText(R.string.txtOk)
                                .show();
                        }
                    }
                }

                @Override
                public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                    Log.e(HomeFragment.class.getName(), "Unable to Sign-in");
                }
            });
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        //stop the call to off to get total product counts:
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
    }

    public void onResume() {

        super.onResume();
        BottomNavigationListenerInstaller.selectNavigationItem(bottomNavigationView, R.id.home_page);

        int productCount = sp.getInt("productCount", 0);
        apiClient.getTotalProductCount(Utils.getUserAgent())
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new SingleObserver<Search>() {
                @Override
                public void onSubscribe(Disposable d) {
                    disposable=d;
                    if(isAdded()) {
                        updateTextHome(productCount);
                    }
                }

                @Override
                public void onSuccess(Search search) {
                    if(isAdded()) {
                        int totalProductCount = productCount;
                        try {
                            totalProductCount = Integer.parseInt(search.getCount());
                        } catch (NumberFormatException e) {
                            Log.w(HomeFragment.class.getSimpleName(), "can parse " + search.getCount() + " as int", e);
                        }
                        updateTextHome(totalProductCount);
                        SharedPreferences.Editor editor = sp.edit();
                        editor.putInt("productCount", totalProductCount);
                        editor.apply();
                    }
                }

                @Override
                public void onError(Throwable e) {
                    if(isAdded()) {
                        updateTextHome(productCount);
                    }
                }
            });

        getTagline();

        if (getActivity() instanceof AppCompatActivity) {
            ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle("");
            }
        }
    }

    private void updateTextHome(int totalProductCount) {
        try {
            textHome.setText(R.string.txtHome);
            if (totalProductCount != 0) {
                String txtHomeOnline = getResources().getString(R.string.txtHomeOnline);

                textHome.setText(String.format(txtHomeOnline, totalProductCount));
            }
        } catch (Exception e) {
            Log.w(HomeFragment.class.getSimpleName(), "can format text for home", e);
        }
    }

    @Override
    public void onCustomTabsConnected() {

    }

    @Override
    public void onCustomTabsDisconnected() {

    }

    private void getTagline() {
        OpenFoodAPIService openFoodAPIService = new OpenFoodAPIClient(getActivity(), "https://ssl-api.openfoodfacts.org").getAPIService();
        Call<ArrayList<TaglineLanguageModel>> call = openFoodAPIService.getTagline(Utils.getUserAgent());
        call.enqueue(new Callback<ArrayList<TaglineLanguageModel>>() {
            @Override
            public void onResponse(Call<ArrayList<TaglineLanguageModel>> call, Response<ArrayList<TaglineLanguageModel>> response) {
                if (response.isSuccessful()) {
                    final Locale locale = LocaleHelper.getLocale(getContext());
                    String localAsString = locale.toString();
                    boolean isLanguageFound = false;
                    boolean isExactLanguageFound = false;
                    for (int i = 0; i < response.body().size(); i++) {
                        final String languageCountry = response.body().get(i).getLanguage();
                        if (!isExactLanguageFound && (languageCountry.equals(localAsString) || languageCountry.contains(localAsString))) {
                            isExactLanguageFound = languageCountry.equals(localAsString);
                            taglineURL = response.body().get(i).getTaglineModel().getUrl();
                            tvDailyFoodFact.setText(response.body().get(i).getTaglineModel().getMessage());
                            tvDailyFoodFact.setVisibility(View.VISIBLE);
                            isLanguageFound = true;
                        }
                    }
                    if (!isLanguageFound) {
                        taglineURL = response.body().get(response.body().size() - 1).getTaglineModel().getUrl();
                        tvDailyFoodFact.setText(response.body().get(response.body().size() - 1).getTaglineModel().getMessage());
                        tvDailyFoodFact.setVisibility(View.VISIBLE);
                    }
                }
            }

            @Override
            public void onFailure(Call<ArrayList<TaglineLanguageModel>> call, Throwable t) {
            }
        });
    }
}
