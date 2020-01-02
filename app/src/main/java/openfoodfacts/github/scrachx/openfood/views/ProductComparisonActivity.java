package openfoodfacts.github.scrachx.openfood.views;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.File;
import java.util.ArrayList;

import butterknife.BindView;
import openfoodfacts.github.scrachx.openfood.R;
import openfoodfacts.github.scrachx.openfood.images.PhotoReceiver;
import openfoodfacts.github.scrachx.openfood.jobs.PhotoReceiverHandler;
import openfoodfacts.github.scrachx.openfood.models.Product;
import openfoodfacts.github.scrachx.openfood.utils.Utils;
import openfoodfacts.github.scrachx.openfood.views.adapters.ProductComparisonAdapter;
import openfoodfacts.github.scrachx.openfood.views.listeners.BottomNavigationListenerInstaller;

public class ProductComparisonActivity extends BaseActivity implements PhotoReceiver {
    @BindView(R.id.toolbar)
    Toolbar toolbar;
    private PhotoReceiverHandler photoReceiverHandler;
    private RecyclerView.Adapter productComparisonAdapter;
    private ArrayList<Product> products = new ArrayList<>();
    @BindView(R.id.bottom_navigation)
    BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_comparison);
        setTitle(getString(R.string.compare_products));

        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        photoReceiverHandler=new PhotoReceiverHandler(this);

        if (getIntent().getExtras() != null && getIntent().getBooleanExtra("product_found", false)) {
            products = (ArrayList<Product>) getIntent().getExtras().get("products_to_compare");
            if (getIntent().getBooleanExtra("product_already_exists", false)) {
                Toast.makeText(this, getString(R.string.product_already_exists_in_comparison), Toast.LENGTH_SHORT).show();
            }
        }

        Button productComparisonButton = findViewById(R.id.product_comparison_button);
        RecyclerView productComparisonRv = findViewById(R.id.product_comparison_rv);

        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        productComparisonRv.setLayoutManager(layoutManager);

        productComparisonAdapter = new ProductComparisonAdapter(products, this);
        productComparisonRv.setAdapter(productComparisonAdapter);

        productComparisonButton.setOnClickListener(v -> {
            if (Utils.isHardwareCameraInstalled(ProductComparisonActivity.this)) {
                if (ContextCompat.checkSelfPermission(ProductComparisonActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(ProductComparisonActivity.this, Manifest.permission.CAMERA)) {
                        new MaterialDialog.Builder(ProductComparisonActivity.this)
                            .title(R.string.action_about)
                            .content(R.string.permission_camera)
                            .neutralText(R.string.txtOk)
                            .onNeutral((dialog, which) -> ActivityCompat
                                .requestPermissions(ProductComparisonActivity.this, new String[]{Manifest.permission.CAMERA}, Utils.MY_PERMISSIONS_REQUEST_CAMERA))
                            .show();
                    } else {
                        ActivityCompat.requestPermissions(ProductComparisonActivity.this, new String[]{Manifest.permission.CAMERA}, Utils.MY_PERMISSIONS_REQUEST_CAMERA);
                    }
                } else {
                    Intent intent = new Intent(ProductComparisonActivity.this, ContinuousScanActivity.class);
                    intent.putExtra("compare_product", true);
                    intent.putExtra("products_to_compare", products);
                    startActivity(intent);
                }
            }
        });

        BottomNavigationListenerInstaller.selectNavigationItem(bottomNavigationView, R.id.compare_products);
        BottomNavigationListenerInstaller.install(bottomNavigationView, this, getBaseContext());
    }

    @Override
    public void onPhotoReturned(File newPhotoFile) {
        ((ProductComparisonAdapter) productComparisonAdapter).setImageOnPhotoReturn(newPhotoFile);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        photoReceiverHandler.onActivityResult(this,requestCode,resultCode,data);
    }
}
