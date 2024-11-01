package net.kdt.pojavlaunch;

import static net.kdt.pojavlaunch.Tools.currentDisplayMetrics;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.kdt.mcgui.ProgressLayout;
import com.movtery.anim.AnimPlayer;
import com.movtery.anim.animations.Animations;
import com.movtery.zalithlauncher.event.single.*;
import com.movtery.zalithlauncher.event.sticky.*;
import com.movtery.zalithlauncher.event.value.*;
import com.movtery.zalithlauncher.feature.notice.CheckNewNotice;
import com.movtery.zalithlauncher.feature.notice.NoticeInfo;
import com.movtery.zalithlauncher.feature.update.UpdateLauncher;
import com.movtery.zalithlauncher.feature.accounts.AccountsManager;
import com.movtery.zalithlauncher.feature.accounts.LocalAccountUtils;
import com.movtery.zalithlauncher.feature.background.BackgroundManager;
import com.movtery.zalithlauncher.feature.background.BackgroundType;
import com.movtery.zalithlauncher.feature.download.item.ModLoaderWrapper;
import com.movtery.zalithlauncher.feature.log.Logging;
import com.movtery.zalithlauncher.feature.mod.modpack.install.InstallExtra;
import com.movtery.zalithlauncher.feature.mod.modpack.install.InstallLocalModPack;
import com.movtery.zalithlauncher.feature.mod.modpack.install.ModPackUtils;
import com.movtery.zalithlauncher.setting.AllSettings;
import com.movtery.zalithlauncher.setting.Settings;
import com.movtery.zalithlauncher.task.Task;
import com.movtery.zalithlauncher.task.TaskExecutors;
import com.movtery.zalithlauncher.ui.activity.BaseActivity;
import com.movtery.zalithlauncher.ui.dialog.TipDialog;
import com.movtery.zalithlauncher.ui.fragment.AccountFragment;
import com.movtery.zalithlauncher.ui.fragment.DownloadFragment;
import com.movtery.zalithlauncher.ui.fragment.SettingsFragment;
import com.movtery.zalithlauncher.ui.subassembly.settingsbutton.ButtonType;
import com.movtery.zalithlauncher.ui.subassembly.settingsbutton.SettingsButtonWrapper;
import com.movtery.zalithlauncher.ui.subassembly.view.DraggableViewWrapper;
import com.movtery.zalithlauncher.ui.view.AnimButton;
import com.movtery.zalithlauncher.utils.ZHTools;
import com.movtery.zalithlauncher.utils.anim.ViewAnimUtils;
import com.movtery.zalithlauncher.utils.stringutils.ShiftDirection;
import com.movtery.zalithlauncher.utils.stringutils.StringUtils;

import net.kdt.pojavlaunch.authenticator.microsoft.MicrosoftBackgroundLogin;
import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension;
import net.kdt.pojavlaunch.databinding.ActivityPojavLauncherBinding;
import net.kdt.pojavlaunch.fragments.MainMenuFragment;
import net.kdt.pojavlaunch.fragments.MicrosoftLoginFragment;
import net.kdt.pojavlaunch.lifecycle.ContextAwareDoneListener;
import net.kdt.pojavlaunch.lifecycle.ContextExecutor;
import net.kdt.pojavlaunch.modloaders.modpacks.ModloaderInstallTracker;
import net.kdt.pojavlaunch.modloaders.modpacks.api.NotificationDownloadListener;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener;
import net.kdt.pojavlaunch.services.ProgressServiceKeeper;
import net.kdt.pojavlaunch.tasks.AsyncMinecraftDownloader;
import net.kdt.pojavlaunch.tasks.AsyncVersionList;
import net.kdt.pojavlaunch.tasks.MinecraftDownloader;
import net.kdt.pojavlaunch.utils.NotificationUtils;
import net.kdt.pojavlaunch.value.MinecraftAccount;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.concurrent.Future;

public class LauncherActivity extends BaseActivity {
    @SuppressLint("StaticFieldLeak") private static Activity activity;
    private final AnimPlayer noticeAnimPlayer = new AnimPlayer();
    private final AccountsManager accountsManager = AccountsManager.getInstance();
    public final ActivityResultLauncher<Object> modInstallerLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("jar"), (data)->{
                if(data != null) Tools.launchModInstaller(this, data);
            });

    private ActivityPojavLauncherBinding binding;
    private SettingsButtonWrapper mSettingsButtonWrapper;
    private ProgressServiceKeeper mProgressServiceKeeper;
    private ModloaderInstallTracker mInstallTracker;
    private NotificationManager mNotificationManager;
    private boolean mIsInDownloadFragment = false;
    private Future<?> checkNotice;

    public static Activity getActivity() {
        return LauncherActivity.activity;
    }

    /* Allows to switch from one button "type" to another */
    private final FragmentManager.FragmentLifecycleCallbacks mFragmentCallbackListener = new FragmentManager.FragmentLifecycleCallbacks() {
        @Override
        public void onFragmentResumed(@NonNull FragmentManager fm, @NonNull Fragment f) {
            if (f instanceof MainMenuFragment) {
                mSettingsButtonWrapper.setButtonType(ButtonType.SETTINGS);
            } else {
                mSettingsButtonWrapper.setButtonType(ButtonType.HOME);
            }
        }
    };

    private final TaskCountListener mDoubleLaunchPreventionListener = taskCount -> {
        // Hide the notification that starts the game if there are tasks executing.
        // Prevents the user from trying to launch the game with tasks ongoing.
        if(taskCount > 0) {
            TaskExecutors.Companion.runInUIThread(() ->
                    mNotificationManager.cancel(NotificationUtils.NOTIFICATION_ID_GAME_START)
            );
        }
    };

    private ActivityResultLauncher<String> mRequestNotificationPermissionLauncher;
    private WeakReference<Runnable> mRequestNotificationPermissionRunnable;

    @Subscribe()
    public void event(PageOpacityChangeEvent event) {
        setPageOpacity();
    }

    @Subscribe()
    public void event(MainBackgroundChangeEvent event) {
        refreshBackground();
    }

    @Subscribe()
    public void event(InDownloadFragmentEvent event) {
        mIsInDownloadFragment = event.isIn();
        if (event.isIn()) ViewAnimUtils.setViewAnim(binding.downloadButton, Animations.Pulse);
    }

    @Subscribe()
    public void event(SwapToLoginEvent event) {
        Fragment fragment = getSupportFragmentManager().findFragmentById(binding.containerFragment.getId());
        if (!(fragment instanceof AccountFragment)) return;
        // 如果当前不是AccountFragment，那么将切换到AccountFragment要求用户登录
        ZHTools.swapFragmentWithAnim(fragment, AccountFragment.class, AccountFragment.TAG, null);
    }

    @Subscribe()
    public void event(LaunchGameEvent event) {
        if (binding.progressLayout.hasProcesses()) {
            Toast.makeText(this, R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
            return;
        }

        String selectedProfile = AllSettings.Companion.getCurrentProfile();
        if (LauncherProfiles.mainProfileJson == null || !LauncherProfiles.mainProfileJson.profiles.containsKey(selectedProfile)) {
            Toast.makeText(this, R.string.error_no_version, Toast.LENGTH_LONG).show();
            return;
        }
        MinecraftProfile prof = LauncherProfiles.mainProfileJson.profiles.get(selectedProfile);
        if (prof == null || prof.lastVersionId == null || "Unknown".equals(prof.lastVersionId)) {
            Toast.makeText(this, R.string.error_no_version, Toast.LENGTH_LONG).show();
            return;
        }

        if (accountsManager.getAllAccount().isEmpty()) {
            Toast.makeText(this, R.string.account_no_saved_accounts, Toast.LENGTH_LONG).show();
            EventBus.getDefault().post(new SwapToLoginEvent());
            return;
        }

        LocalAccountUtils.checkUsageAllowed(new LocalAccountUtils.CheckResultListener() {
            @Override
            public void onUsageAllowed() {
                launchGame(prof);
            }

            @Override
            public void onUsageDenied() {
                if (!AllSettings.Companion.getLocalAccountReminders()) {
                    launchGame(prof);
                } else {
                    LocalAccountUtils.openDialog(LauncherActivity.this, () -> launchGame(prof),
                            getString(R.string.account_no_microsoft_account) + getString(R.string.account_purchase_minecraft_account_tip),
                            R.string.account_continue_to_launch_the_game);
                }
            }
        });
    }

    @Subscribe()
    public void event(MicrosoftLoginEvent event) {
        new MicrosoftBackgroundLogin(false, event.getUri().getQueryParameter("code")).performLogin(
                accountsManager.getProgressListener(), accountsManager.getDoneListener(), accountsManager.getErrorListener());
    }

    @Subscribe()
    public void event(OtherLoginEvent event) {
        try {
            event.getAccount().save();
            Logging.i("McAccountSpinner", "Saved the account : " + event.getAccount().username);
        } catch (IOException e) {
            Logging.e("McAccountSpinner", "Failed to save the account : " + e);
        }
        accountsManager.getDoneListener().onLoginDone(event.getAccount());
    }

    @Subscribe()
    public void event(LocalLoginEvent event) {
        String userName = event.getUserName();
        MinecraftAccount localAccount = new MinecraftAccount();
        localAccount.username = userName;
        localAccount.accountType = "Local";
        try {
            localAccount.save();
            Logging.i("McAccountSpinner", "Saved the account : " + localAccount.username);
        } catch (IOException e) {
            Logging.e("McAccountSpinner", "Failed to save the account : " + e);
        }

        accountsManager.getDoneListener().onLoginDone(localAccount);
    }

    @Subscribe()
    public void event(InstallLocalModpackEvent event) {
        InstallExtra installExtra = event.getInstallExtra();
        if (!installExtra.startInstall) return;

        if (binding.progressLayout.hasProcesses()) {
            Toast.makeText(this, R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
            return;
        }

        File dirGameModpackFile = new File(installExtra.modpackPath);
        ModPackUtils.ModPackEnum type;
        type = ModPackUtils.determineModpack(dirGameModpackFile);

        Task.Companion.runTask(() -> {
                    ModLoaderWrapper loaderInfo = InstallLocalModPack.installModPack(this, type, dirGameModpackFile,
                            () -> runOnUiThread(installExtra.dialog::dismiss));
                    if (loaderInfo == null) return null;
                    return loaderInfo.getDownloadTask(new NotificationDownloadListener(this, loaderInfo));
                }).beforeStart(TaskExecutors.Companion.getAndroidUI(),
                        () -> ProgressLayout.setProgress(ProgressLayout.INSTALL_RESOURCE, 0, R.string.generic_waiting))
                .ended(task -> {
                    if (task != null) {
                        task.run();
                    }
                }).onThrowable(TaskExecutors.Companion.getAndroidUI(), e -> {
                    installExtra.dialog.dismiss();
                    Tools.showErrorRemote(this, R.string.modpack_install_download_failed, e);
                })
                .finallyTask(() -> ProgressLayout.clearProgress(ProgressLayout.INSTALL_RESOURCE))
                .execute();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPojavLauncherBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        processFragment();
        processViews();

        mRequestNotificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isAllowed -> {
                    if(!isAllowed) handleNoNotificationPermission();
                    else {
                        Runnable runnable = Tools.getWeakReference(mRequestNotificationPermissionRunnable);
                        if(runnable != null) runnable.run();
                    }
                }
        );
        checkNotificationPermission();

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        ProgressKeeper.addTaskCountListener(mDoubleLaunchPreventionListener);
        ProgressKeeper.addTaskCountListener((mProgressServiceKeeper = new ProgressServiceKeeper(this)));
        ProgressKeeper.addTaskCountListener(binding.progressLayout);

        new AsyncVersionList().getVersionList(versions -> EventBus.getDefault().postSticky(
                        new MinecraftVersionValueEvent(versions)),
                false
        );

        mInstallTracker = new ModloaderInstallTracker(this);

        checkNotice();

        //检查已经下载后的包，或者检查更新
        Task.Companion.runTask(() -> {
            UpdateLauncher.CheckDownloadedPackage(this, true);
            return null;
        }).execute();

        LauncherActivity.activity = this;
    }

    private void processFragment() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                MicrosoftLoginFragment fragment = (MicrosoftLoginFragment) getVisibleFragment(MicrosoftLoginFragment.TAG);
                if (fragment != null) {
                    if (fragment.canGoBack()) {
                        fragment.goBack();
                        return;
                    }
                }
                //如果栈中只剩下1个或没有Fragment，则直接退出启动器
                if (getSupportFragmentManager().getBackStackEntryCount() <= 1) {
                    finish();
                } else {
                    getSupportFragmentManager().popBackStackImmediate();
                }
            }
        });

        FragmentManager fragmentManager = getSupportFragmentManager();
        //如果栈中没有Fragment，那么就将主Fragment添加进来
        if (fragmentManager.getBackStackEntryCount() < 1) {
            fragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .addToBackStack(MainMenuFragment.TAG)
                    .add(R.id.container_fragment, MainMenuFragment.class, null, MainMenuFragment.TAG).commit();
        }
    }

    private void processViews() {
        setPageOpacity();
        refreshBackground();
        mSettingsButtonWrapper = new SettingsButtonWrapper(binding.settingButton);
        mSettingsButtonWrapper.setOnTypeChangeListener(type -> ViewAnimUtils.setViewAnim(binding.settingButton, Animations.Pulse));
        binding.downloadButton.setOnClickListener(v -> {
            if (mIsInDownloadFragment) return;
            Fragment fragment = getSupportFragmentManager().findFragmentById(binding.containerFragment.getId());
            if (fragment != null) {
                ZHTools.swapFragmentWithAnim(fragment, DownloadFragment.class, DownloadFragment.TAG, null);
            }
        });
        binding.settingButton.setOnClickListener(v -> {
            ViewAnimUtils.setViewAnim(binding.settingButton, Animations.Pulse);
            Fragment fragment = getSupportFragmentManager().findFragmentById(binding.containerFragment.getId());
            if (fragment instanceof MainMenuFragment) {
                ZHTools.swapFragmentWithAnim(fragment, SettingsFragment.class, SettingsFragment.TAG, null);
            } else {
                // The setting button doubles as a home button now
                Tools.backToMainMenu(this);
            }
        });
        binding.appTitleText.setOnClickListener(v ->
                binding.appTitleText.setText(StringUtils.shiftString(binding.appTitleText.getText().toString(), ShiftDirection.RIGHT, 1))
        );

        binding.progressLayout.observe(ProgressLayout.DOWNLOAD_MINECRAFT);
        binding.progressLayout.observe(ProgressLayout.UNPACK_RUNTIME);
        binding.progressLayout.observe(ProgressLayout.INSTALL_RESOURCE);
        binding.progressLayout.observe(ProgressLayout.AUTHENTICATE_MICROSOFT);
        binding.progressLayout.observe(ProgressLayout.DOWNLOAD_VERSION_LIST);

        binding.noticeLayout.findViewById(R.id.notice_got_button).setOnClickListener(v -> {
            setNotice(false);
            Settings.Manager.Companion.put("noticeDefault", false)
                    .save();
        });
        new DraggableViewWrapper(binding.noticeLayout, new DraggableViewWrapper.AttributesFetcher() {
            @NonNull
            @Override
            public DraggableViewWrapper.ScreenPixels getScreenPixels() {
                return new DraggableViewWrapper.ScreenPixels(0, 0,
                        currentDisplayMetrics.widthPixels - binding.noticeLayout.getWidth(),
                        currentDisplayMetrics.heightPixels - binding.noticeLayout.getHeight());
            }

            @NonNull
            @Override
            public int[] get() {
                return new int[]{(int) binding.noticeLayout.getX(), (int) binding.noticeLayout.getY()};
            }

            @Override
            public void set(int x, int y) {
                binding.noticeLayout.setX(x);
                binding.noticeLayout.setY(y);
            }
        }).init();

        //愚人节彩蛋
        if (ZHTools.checkDate(4, 1)) binding.hair.setVisibility(View.VISIBLE);
        else binding.hair.setVisibility(View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ContextExecutor.setActivity(this);
        mInstallTracker.attach();
        setPageOpacity();
    }

    @Override
    protected void onPause() {
        super.onPause();
        ContextExecutor.clearActivity();
        mInstallTracker.detach();
    }

    @Override
    protected void onStart() {
        super.onStart();
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(mFragmentCallbackListener, true);
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding.progressLayout.cleanUpObservers();
        ProgressKeeper.removeTaskCountListener(binding.progressLayout);
        ProgressKeeper.removeTaskCountListener(mProgressServiceKeeper);

        getSupportFragmentManager().unregisterFragmentLifecycleCallbacks(mFragmentCallbackListener);
    }

    @Override
    public void onAttachedToWindow() {
        LauncherPreferences.computeNotchSize(this);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        LauncherActivity.activity = this;
    }

    private void checkNotice() {
        checkNotice = TaskExecutors.Companion.getDefault().submit(() -> CheckNewNotice.checkNewNotice(this, noticeInfo -> {
            if (checkNotice.isCancelled() || noticeInfo == null) {
                return;
            }
            //当偏好设置内是开启通知栏 或者 检测到通知编号不为偏好设置里保存的值时，显示通知栏
            if (AllSettings.Companion.getNoticeDefault() ||
                    (noticeInfo.numbering != AllSettings.Companion.getNoticeNumbering())) {
                TaskExecutors.Companion.runInUIThread(() -> setNotice(true));
                Settings.Manager.Companion.put("noticeDefault", true)
                        .put("noticeNumbering", noticeInfo.numbering)
                        .save();
            }
        }));
    }

    private void setNotice(boolean show) {
        AnimButton gotButton = binding.noticeLayout.findViewById(R.id.notice_got_button);

        if (show) {
            NoticeInfo noticeInfo = CheckNewNotice.getNoticeInfo();
            if (noticeInfo != null) {
                TextView title = binding.noticeLayout.findViewById(R.id.notice_title_view);
                TextView message = binding.noticeLayout.findViewById(R.id.notice_message_view);
                TextView date = binding.noticeLayout.findViewById(R.id.notice_date_view);

                gotButton.setClickable(true);

                title.setText(noticeInfo.title);
                message.setText(noticeInfo.content);
                date.setText(noticeInfo.date);

                Linkify.addLinks(message, Linkify.WEB_URLS);
                message.setMovementMethod(LinkMovementMethod.getInstance());

                noticeAnimPlayer.clearEntries();
                noticeAnimPlayer.apply(new AnimPlayer.Entry(binding.noticeLayout, Animations.BounceEnlarge))
                        .setOnStart(() -> binding.noticeLayout.setVisibility(View.VISIBLE))
                        .start();
            }
        } else {
            gotButton.setClickable(false);

            noticeAnimPlayer.clearEntries();
            noticeAnimPlayer.apply(new AnimPlayer.Entry(binding.noticeLayout, Animations.BounceShrink))
                    .setOnStart(() -> binding.noticeLayout.setVisibility(View.VISIBLE))
                    .setOnEnd(() -> binding.noticeLayout.setVisibility(View.GONE))
                    .start();
        }
    }

    private void refreshBackground() {
        BackgroundManager.setBackgroundImage(this, BackgroundType.MAIN_MENU, findViewById(R.id.background_view));
    }

    private void launchGame(MinecraftProfile prof) {
        String normalizedVersionId = AsyncMinecraftDownloader.normalizeVersionId(prof.lastVersionId);
        JMinecraftVersionList.Version mcVersion = AsyncMinecraftDownloader.getListedVersion(normalizedVersionId);
        new MinecraftDownloader().start(
                mcVersion,
                normalizedVersionId,
                new ContextAwareDoneListener(this, normalizedVersionId)
        );
    }

    @SuppressWarnings("SameParameterValue")
    private Fragment getVisibleFragment(String tag){
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(tag);
        if(fragment != null && fragment.isVisible()) {
            return fragment;
        }
        return null;
    }

    @SuppressWarnings("unused")
    private Fragment getVisibleFragment(int id){
        Fragment fragment = getSupportFragmentManager().findFragmentById(id);
        if(fragment != null && fragment.isVisible()) {
            return fragment;
        }
        return null;
    }

    private void checkNotificationPermission() {
        if(AllSettings.Companion.getSkipNotificationPermissionCheck() ||
            checkForNotificationPermission()) {
            return;
        }

        if(ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.POST_NOTIFICATIONS)) {
            showNotificationPermissionReasoning();
            return;
        }
        askForNotificationPermission(null);
    }

    private void showNotificationPermissionReasoning() {
        new TipDialog.Builder(this)
                .setTitle(R.string.notification_permission_dialog_title)
                .setMessage(R.string.notification_permission_dialog_text)
                .setConfirmClickListener(() -> askForNotificationPermission(null))
                .setCancelClickListener(this::handleNoNotificationPermission)
                .buildDialog();
    }

    private void handleNoNotificationPermission() {
        Settings.Manager.Companion
                .put("skipNotificationPermissionCheck", true)
                .save();
        Toast.makeText(this, R.string.notification_permission_toast, Toast.LENGTH_LONG).show();
    }

    public boolean checkForNotificationPermission() {
        return Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_DENIED;
    }

    public void askForNotificationPermission(Runnable onSuccessRunnable) {
        if(Build.VERSION.SDK_INT < 33) return;
        if(onSuccessRunnable != null) {
            mRequestNotificationPermissionRunnable = new WeakReference<>(onSuccessRunnable);
        }
        mRequestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    private void setPageOpacity() {
        float v = (float) AllSettings.Companion.getPageOpacity() / 100;
        if (binding.containerFragment.getAlpha() != v) binding.containerFragment.setAlpha(v);
    }
}
