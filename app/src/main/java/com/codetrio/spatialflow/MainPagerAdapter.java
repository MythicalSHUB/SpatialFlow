package com.codetrio.spatialflow;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.codetrio.spatialflow.ui.PlayerFragment;
import com.codetrio.spatialflow.ui.EffectsFragment;
import com.codetrio.spatialflow.ui.SettingsFragment;

public class MainPagerAdapter extends FragmentStateAdapter {

    public MainPagerAdapter(@NonNull FragmentActivity fa) {
        super(fa);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0: return new PlayerFragment();
            case 1: return new EffectsFragment();
            default: return new SettingsFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}
