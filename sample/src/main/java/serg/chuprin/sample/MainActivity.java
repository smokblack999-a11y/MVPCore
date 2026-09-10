package serg.chuprin.sample;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import serg.chuprin.sample.repositories.list.view.RepositoriesListFragment;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.fragmentContainer, new RepositoriesListFragment())
                    .addToBackStack(null)
                    .commit();
        }
        findViewById(R.id.openMediaHub).setOnClickListener(v -> openMediaHub());
        setTitle("MVPCore • Media Ready");
    }

    private void openMediaHub() {
        startActivity(new Intent(this, media.MediaHubActivity.class));
    }

    @Override
    public void onBackPressed() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
        } else {
            super.onBackPressed();
        }
    }
}
