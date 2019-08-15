package openfoodfacts.github.scrachx.openfood.views.product;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MenuItem;
import androidx.annotation.RequiresApi;
import androidx.appcompat.widget.Toolbar;
import androidx.viewpager.widget.ViewPager;
import butterknife.BindView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.tabs.TabLayout;
import io.reactivex.SingleObserver;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import openfoodfacts.github.scrachx.openfood.BuildConfig;
import openfoodfacts.github.scrachx.openfood.R;
import openfoodfacts.github.scrachx.openfood.fragments.ContributorsFragment;
import openfoodfacts.github.scrachx.openfood.fragments.ProductPhotosFragment;
import openfoodfacts.github.scrachx.openfood.models.HistoryProductDao;
import openfoodfacts.github.scrachx.openfood.models.Nutriments;
import openfoodfacts.github.scrachx.openfood.models.Product;
import openfoodfacts.github.scrachx.openfood.models.State;
import openfoodfacts.github.scrachx.openfood.network.OpenFoodAPIClient;
import openfoodfacts.github.scrachx.openfood.utils.ShakeDetector;
import openfoodfacts.github.scrachx.openfood.utils.Utils;
import openfoodfacts.github.scrachx.openfood.views.AddProductActivity;
import openfoodfacts.github.scrachx.openfood.views.BaseActivity;
import openfoodfacts.github.scrachx.openfood.views.MainActivity;
import openfoodfacts.github.scrachx.openfood.views.adapters.ProductFragmentPagerAdapter;
import openfoodfacts.github.scrachx.openfood.views.listeners.BottomNavigationListenerInstaller;
import openfoodfacts.github.scrachx.openfood.views.listeners.OnRefreshListener;
import openfoodfacts.github.scrachx.openfood.views.product.environment.EnvironmentProductFragment;
import openfoodfacts.github.scrachx.openfood.views.product.ingredients.IngredientsProductFragment;
import openfoodfacts.github.scrachx.openfood.views.product.ingredients_analysis.IngredientsAnalysisProductFragment;
import openfoodfacts.github.scrachx.openfood.views.product.nutrition.NutritionProductFragment;
import openfoodfacts.github.scrachx.openfood.views.product.summary.SummaryProductFragment;

public class ProductActivity extends BaseActivity implements OnRefreshListener {

	private static final int LOGIN_ACTIVITY_REQUEST_CODE = 1;
	@BindView( R.id.pager )
	ViewPager viewPager;
	@BindView( R.id.toolbar )
	Toolbar toolbar;
	@BindView( R.id.tabs )
	TabLayout tabLayout;
    @BindView( R.id.bottom_navigation )
	BottomNavigationView bottomNavigationView;

    private ProductFragmentPagerAdapter adapterResult;

    private OpenFoodAPIClient api;
    private Disposable disposable;
    private State mState;
    private HistoryProductDao mHistoryProductDao;
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private ShakeDetector mShakeDetector;
    // boolean to determine if scan on shake feature should be enabled
    private boolean scanOnShake;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getResources().getBoolean(R.bool.portrait_only)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        setContentView(R.layout.activity_product);
        setTitle(getString(R.string.app_name_long));

        setSupportActionBar(toolbar);
        if(getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

		api = new OpenFoodAPIClient( this );

		mState = (State) getIntent().getSerializableExtra("state" );

        if (getIntent().getAction().equals(Intent.ACTION_VIEW)) {
            // handle opening the app via product page url
            Uri data = getIntent().getData();
            String[] paths = data.toString().split("/"); // paths[4]
            mState = new State();
            loadProductDataFromUrl(paths[4]);
        } else if (mState == null) {
            final Intent intent = new Intent(getApplicationContext(), MainActivity.class);
            startActivity(intent);
        } else {
            initViews();
        }
	}

    /**
     * Initialise the content that shows the content on the device.
     */
	private void initViews(){
        mHistoryProductDao = Utils.getAppDaoSession(ProductActivity.this).getHistoryProductDao();

        setupViewPager(viewPager);

        tabLayout.setupWithViewPager(viewPager);

        // Get the user preference for scan on shake feature and open ContinuousScanActivity if the user has enabled the feature
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (mSensorManager != null) {
            mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
        mShakeDetector = new ShakeDetector();

        scanOnShake = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("shakeScanMode", false);

        mShakeDetector.setOnShakeListener(count -> {
            if (scanOnShake) {
                Utils.scan(ProductActivity.this);
            }
        });

        BottomNavigationListenerInstaller.install(bottomNavigationView,this,this);
    }

    /**
     * Get the product data from the barcode. This takes the barcode and retrieves the information.
     * @param barcode from the URL.
     */
	private void loadProductDataFromUrl(String barcode){
        if (disposable != null && !disposable.isDisposed()) {
            //dispose the previous call if not ended.
            disposable.dispose();
        }

        api.getProductFullSingle(barcode, Utils.HEADER_USER_AGENT_SCAN)
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe(a -> {
            })
            .subscribe(new SingleObserver<State>() {
                @Override
                public void onSubscribe(Disposable d) {
                    disposable = d;
                }

                @Override
                public void onSuccess(State state) {
                    mState = state;
                    new HistoryTask(mHistoryProductDao).execute(mState.getProduct());
                    getIntent().putExtra("state", state);
                    if (mState != null) {
                        initViews();
                    } else {
                        finish();
                    }
                }

                @Override
                public void onError(Throwable e) {
                    Log.i(getClass().getSimpleName(), "Failed to load product data", e);
                    finish();
                }
            });
    }

	@Override
	protected void onActivityResult( int requestCode, int resultCode, Intent data )
	{
		super.onActivityResult( requestCode, resultCode, data );
		if(requestCode == LOGIN_ACTIVITY_REQUEST_CODE && resultCode == RESULT_OK ) {
			Intent intent = new Intent( ProductActivity.this, AddProductActivity.class );
			intent.putExtra( AddProductActivity.KEY_EDIT_PRODUCT, mState.getProduct() );
			startActivity( intent );
		}
	}


	private void setupViewPager(ViewPager viewPager) {
		adapterResult = setupViewPager(viewPager, new ProductFragmentPagerAdapter(getSupportFragmentManager()), mState, this);
    }

    /**
     * CAREFUL ! YOU MUST INSTANTIATE YOUR OWN ADAPTERRESULT BEFORE CALLING THIS METHOD
     * @param viewPager
     * @param adapterResult
     * @param mState
     * @param activity
     * @return
     */
    public static ProductFragmentPagerAdapter setupViewPager (ViewPager viewPager, ProductFragmentPagerAdapter adapterResult, State mState, Activity activity) {
        String[] menuTitles = activity.getResources().getStringArray( R.array.nav_drawer_items_product );
        String[] newMenuTitles = activity.getResources().getStringArray(R.array.nav_drawer_new_items_product);

        adapterResult.addFragment( new SummaryProductFragment(), menuTitles[0] );
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences( activity );
        if( BuildConfig.FLAVOR.equals( "off" ) || BuildConfig.FLAVOR.equals( "obf" ) || BuildConfig.FLAVOR.equals( "opff" ) ) {
            adapterResult.addFragment( new IngredientsProductFragment(), menuTitles[1] );
        }

        if( BuildConfig.FLAVOR.equals( "off" ) ) {
            adapterResult.addFragment( new NutritionProductFragment(), menuTitles[2] );
            if( (mState.getProduct().getNutriments() != null &&
                    mState.getProduct().getNutriments().contains(Nutriments.CARBON_FOOTPRINT)) ||
                    (mState.getProduct().getEnvironmentInfocard() != null && !mState.getProduct().getEnvironmentInfocard().isEmpty()))
            {
                adapterResult.addFragment(new EnvironmentProductFragment(), "Environment");
            }
            if(isPhotoMode(activity))
            {
                adapterResult.addFragment( new ProductPhotosFragment(), newMenuTitles[0] );
            }
        }
        if( BuildConfig.FLAVOR.equals( "opff" ) ) {
            adapterResult.addFragment( new NutritionProductFragment(), menuTitles[2] );
            if(isPhotoMode(activity))
            {
                adapterResult.addFragment( new ProductPhotosFragment(), newMenuTitles[0] );
            }
        }

        if( BuildConfig.FLAVOR.equals( "obf" ) )
        {
            if(isPhotoMode(activity))
            {
                adapterResult.addFragment( new ProductPhotosFragment(), newMenuTitles[0] );
            }
            adapterResult.addFragment( new IngredientsAnalysisProductFragment(), newMenuTitles[1] );
        }

        if( BuildConfig.FLAVOR.equals( "opf" ) )
        {
            adapterResult.addFragment( new ProductPhotosFragment(), newMenuTitles[0] );
        }
        if( preferences.getBoolean( "contributionTab", false ) )
        {
            adapterResult.addFragment( new ContributorsFragment(), activity.getString( R.string.contribution_tab ) );
        }

        viewPager.setAdapter(adapterResult);
        return adapterResult;
    }

    private static boolean isPhotoMode(Activity activity) {
        return PreferenceManager.getDefaultSharedPreferences( activity ).getBoolean( "photoMode", false );
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return onOptionsItemSelected(item, this);
    }

    public static boolean onOptionsItemSelected(MenuItem item, Activity activity) {
        // Respond to the action bar's Up/Home button
        if (item.getItemId() == android.R.id.home) {
            activity.finish();
        }
        return true;
    }


    @Override
    public void onRefresh() {
        api.getProduct(mState.getProduct().getCode(), this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        mState = (State) intent.getSerializableExtra("state");
        adapterResult.refresh(mState);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (scanOnShake) {
            //unregister the listener
            mSensorManager.unregisterListener(mShakeDetector, mAccelerometer);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (scanOnShake) {
            //register the listener
            mSensorManager.registerListener(mShakeDetector, mAccelerometer, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
    }

    private static class HistoryTask extends AsyncTask<Product, Void, Void> {
        private final HistoryProductDao mHistoryProductDao;

        private HistoryTask(HistoryProductDao mHistoryProductDao) {
            this.mHistoryProductDao = mHistoryProductDao;
        }

        @Override
        protected Void doInBackground(Product... products) {
            OpenFoodAPIClient.addToHistory(mHistoryProductDao, products[0]);
            return null;
        }
    }
}
