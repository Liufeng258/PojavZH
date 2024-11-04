package com.movtery.zalithlauncher.ui.dialog;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Window;
import android.widget.SeekBar;

import androidx.annotation.NonNull;

import com.movtery.zalithlauncher.databinding.DialogControlSettingsBinding;
import com.movtery.zalithlauncher.setting.AllSettings;
import com.movtery.zalithlauncher.setting.Settings;

public class ControlSettingsDialog extends FullScreenDialog implements DraggableDialog.DialogInitializationListener {
    private final DialogControlSettingsBinding binding = DialogControlSettingsBinding.inflate(getLayoutInflater());

    public ControlSettingsDialog(@NonNull Context context) {
        super(context);

        this.setCancelable(false);
        setContentView(binding.getRoot());
        init();
        DraggableDialog.initDialog(this);
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private void init() {
        //设置值
        binding.snappingSwitch.setChecked(AllSettings.getButtonSnapping());
        binding.snappingDistanceSeek.setProgress(AllSettings.getButtonSnappingDistance());
        String text = AllSettings.getButtonSnappingDistance() + "dp";
        binding.snappingDistanceText.setText(text);

        binding.confirmButton.setOnClickListener(v -> this.dismiss());
        binding.snappingSwitch.setOnCheckedChangeListener((compoundButton, b) -> Settings.Manager.Companion.put("buttonSnapping", b).save());
        binding.snappingDistanceSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                Settings.Manager.Companion.put("buttonSnappingDistance", i).save();
                String text = i + "dp";
                binding.snappingDistanceText.setText(text);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    @Override
    public Window onInit() {
        return getWindow();
    }
}
