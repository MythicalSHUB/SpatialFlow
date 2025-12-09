package com.codetrio.spatialflow;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import com.codetrio.spatialflow.ui.EffectsFragment;
import com.codetrio.spatialflow.ui.PlayerFragment;
import com.codetrio.spatialflow.ui.SettingsFragment;

public class ViewPagerAdapter extends FragmentStateAdapter {

    public ViewPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return new PlayerFragment();
            case 1:
                return new EffectsFragment();
            case 2:
                return new SettingsFragment();
            default:
                return new PlayerFragment();
        }
    }

    @Override
    public int getItemCount() {
        // FIXED: Must return 3 to include Player (0), Effects (1), and Settings (2).
        return 3;
    }
}
