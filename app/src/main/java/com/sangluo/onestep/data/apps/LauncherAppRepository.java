package com.sangluo.onestep.data.apps;

import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.Log;

import com.sangluo.onestep.model.LauncherApp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loads launchable applications with their system-provided labels and icons. */
public final class LauncherAppRepository {
    private static final String TAG = "OneStepApps";
    private static final int ZTE_CLONE_USER_ID = 999;
    private static final Object APP_LOAD_LOCK = new Object();
    private static final ExecutorService PRELOAD_EXECUTOR =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "OneStep-app-preload");
                thread.setPriority(Thread.NORM_PRIORITY - 1);
                return thread;
            });
    private static volatile List<LauncherApp> cachedLauncherApps;
    private static boolean preloadScheduled;

    private final Context context;
    private final PackageManager packageManager;
    private final LauncherApps launcherApps;
    private final UserManager userManager;
    private final SystemThemedIconLoader themedIconLoader;
    private volatile List<LauncherApp> cachedUnlistedEntryApps;
    /**
     * Kept apart from {@link #APP_LOAD_LOCK}: the picker may load this list on the main thread
     * and must not wait behind a full desktop icon rescan.
     */
    private final Object unlistedEntryLock = new Object();

    public LauncherAppRepository(Context context) {
        this.context = context.getApplicationContext();
        packageManager = context.getPackageManager();
        launcherApps = context.getSystemService(LauncherApps.class);
        userManager = context.getSystemService(UserManager.class);
        themedIconLoader = new SystemThemedIconLoader(context);
    }

    /** Starts loading the shared process cache before the HOME activity is created. */
    public static void preloadLauncherApps(Context context) {
        if (context == null || cachedLauncherApps != null) {
            return;
        }
        synchronized (APP_LOAD_LOCK) {
            if (cachedLauncherApps != null || preloadScheduled) {
                return;
            }
            preloadScheduled = true;
        }
        Context applicationContext = context.getApplicationContext();
        try {
            PRELOAD_EXECUTOR.execute(() -> {
                try {
                    new LauncherAppRepository(applicationContext).loadLauncherApps();
                } catch (RuntimeException e) {
                    Log.w(TAG, "Launcher app preload failed", e);
                } finally {
                    synchronized (APP_LOAD_LOCK) {
                        preloadScheduled = false;
                    }
                }
            });
        } catch (RuntimeException e) {
            synchronized (APP_LOAD_LOCK) {
                preloadScheduled = false;
            }
            Log.w(TAG, "Unable to schedule launcher app preload", e);
        }
    }

    /** Returns a non-blocking snapshot when process preloading has already completed. */
    public static List<LauncherApp> getCachedLauncherApps() {
        List<LauncherApp> cached = cachedLauncherApps;
        return cached == null ? Collections.emptyList() : new ArrayList<>(cached);
    }

    public List<LauncherApp> loadLauncherApps() {
        List<LauncherApp> cached = cachedLauncherApps;
        if (cached != null) {
            return new ArrayList<>(cached);
        }
        return loadLauncherApps(false, true);
    }

    /**
     * Lists installed entry points that {@link LauncherApps} never reports: pages an app
     * publishes with an explicit {@code ACTION_MAIN} filter but no launcher icon (module
     * settings, OEM feature screens, hidden maintenance pages).
     *
     * <p>Only the floating ball's "add app" picker consumes this list. The desktop catalog and
     * the top app strip stay driven by {@link #loadLauncherApps()}.</p>
     */
    public List<LauncherApp> loadUnlistedEntryApps() {
        return loadUnlistedEntryApps(false);
    }

    public List<LauncherApp> loadUnlistedEntryApps(boolean invalidateCache) {
        List<LauncherApp> cached = cachedUnlistedEntryApps;
        if (cached != null && !invalidateCache) {
            return new ArrayList<>(cached);
        }
        synchronized (unlistedEntryLock) {
            cached = cachedUnlistedEntryApps;
            if (cached != null && !invalidateCache) {
                return new ArrayList<>(cached);
            }
            List<LauncherApp> apps = queryUnlistedEntryApps();
            cachedUnlistedEntryApps = Collections.unmodifiableList(new ArrayList<>(apps));
            Log.i(TAG, "Loaded unlisted entry apps: count=" + apps.size());
            return apps;
        }
    }

    /** Drops the cached picker list, e.g. after a package was installed or removed. */
    public void invalidateUnlistedEntryApps() {
        cachedUnlistedEntryApps = null;
    }

    private List<LauncherApp> queryUnlistedEntryApps() {
        Set<String> launcherPackages = new HashSet<>();
        for (LauncherActivityEntry entry : queryLauncherActivities(null)) {
            launcherPackages.add(entry.componentName().getPackageName());
        }

        // No category on the query: module settings pages declare MODULE_SETTINGS or no
        // category at all, and MATCH_DEFAULT_ONLY would skip both.
        List<ResolveInfo> entries;
        try {
            entries = packageManager.queryIntentActivities(new Intent(Intent.ACTION_MAIN), 0);
        } catch (RuntimeException e) {
            Log.w(TAG, "Querying unlisted entry apps failed", e);
            return Collections.emptyList();
        }

        Map<String, ResolveInfo> preferredEntries = new LinkedHashMap<>();
        for (ResolveInfo info : entries) {
            ActivityInfo activityInfo = info.activityInfo;
            if (activityInfo == null || !UnlistedEntryPolicy.isCandidate(
                    activityInfo.exported, activityInfo.enabled, activityInfo.packageName,
                    context.getPackageName(), launcherPackages)) {
                continue;
            }
            String packageName = activityInfo.packageName;
            ResolveInfo current = preferredEntries.get(packageName);
            if (current != null && !UnlistedEntryPolicy.prefer(
                    info.priority, info.isDefault, activityInfo.name,
                    current.priority, current.isDefault, current.activityInfo.name)) {
                continue;
            }
            preferredEntries.put(packageName, info);
        }

        List<LauncherApp> result = new ArrayList<>(preferredEntries.size());
        for (ResolveInfo info : preferredEntries.values()) {
            LauncherApp app = createUnlistedEntryApp(info);
            if (app != null) {
                result.add(app);
            }
        }
        result.sort(Comparator.comparing(app -> app.label, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private LauncherApp createUnlistedEntryApp(ResolveInfo info) {
        ActivityInfo activityInfo = info.activityInfo;
        if (activityInfo == null || activityInfo.applicationInfo == null) {
            return null;
        }
        Drawable icon;
        try {
            icon = themedIconLoader.loadIcon(info);
        } catch (RuntimeException e) {
            icon = activityInfo.applicationInfo.loadIcon(packageManager);
        }
        return LauncherApp.createUnlistedEntry(loadEntryLabel(info),
                activityInfo.packageName, icon, resolveEntryIntent(activityInfo));
    }

    private String loadEntryLabel(ResolveInfo info) {
        try {
            CharSequence label = info.loadLabel(packageManager);
            if (!TextUtils.isEmpty(label)) {
                return label.toString();
            }
        } catch (RuntimeException ignored) {
            // Fall through to the application label.
        }
        try {
            CharSequence appLabel = info.activityInfo.applicationInfo.loadLabel(packageManager);
            if (!TextUtils.isEmpty(appLabel)) {
                return appLabel.toString();
            }
        } catch (RuntimeException ignored) {
            // Fall through to the package name.
        }
        return info.activityInfo.packageName;
    }

    /**
     * A page can guard itself with a signature permission OneStep will never hold. Such a row
     * stays visible, but opens the app's info screen instead of retrying a launch the system
     * would refuse.
     */
    private Intent resolveEntryIntent(ActivityInfo activityInfo) {
        String requiredPermission = activityInfo.permission;
        boolean launchable = TextUtils.isEmpty(requiredPermission)
                || context.checkSelfPermission(requiredPermission)
                == PackageManager.PERMISSION_GRANTED;
        if (!launchable) {
            return LauncherApp.createAppInfoIntent(activityInfo.packageName);
        }
        return new Intent(Intent.ACTION_MAIN)
                .setComponent(new ComponentName(activityInfo.packageName, activityInfo.name))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    }

    /** Reloads the installed app catalog without clearing OEM theme caches. */
    public List<LauncherApp> reloadLauncherApps() {
        return loadLauncherApps(false, false);
    }

    public List<LauncherApp> refreshLauncherApps() {
        return loadLauncherApps(true, false);
    }

    public List<LauncherApp> loadHomeApps() {
        return loadHomeApps(false);
    }

    /** Returns every selectable HOME candidate, including OneStep itself. */
    public List<LauncherApp> loadDefaultHomeApps() {
        return loadHomeApps(true);
    }

    private List<LauncherApp> loadHomeApps(boolean includeOneStep) {
        List<ResolveInfo> resolveInfos = queryHomeActivities();
        Collections.sort(resolveInfos, new ResolveInfo.DisplayNameComparator(packageManager));
        List<LauncherApp> apps = new ArrayList<>();
        for (ResolveInfo resolveInfo : resolveInfos) {
            if (!isSelectableHomeActivity(resolveInfo, includeOneStep)) {
                continue;
            }
            apps.add(createHomeApp(resolveInfo));
        }
        return apps;
    }

    public LauncherApp loadHomeApp(ComponentName componentName) {
        if (componentName == null
                || TextUtils.equals(componentName.getPackageName(), context.getPackageName())) {
            return null;
        }
        for (ResolveInfo resolveInfo : queryHomeActivities()) {
            if (isSelectableHomeActivity(resolveInfo, false)
                    && componentName.equals(componentNameOf(resolveInfo))) {
                return createHomeApp(resolveInfo);
            }
        }
        return null;
    }

    public LauncherApp loadLauncherApp(String packageName) {
        Intent launchIntent = packageManager.getLaunchIntentForPackage(packageName);
        ComponentName preferredComponent = launchIntent == null
                ? null : launchIntent.getComponent();
        List<LauncherActivityEntry> launcherActivities = queryLauncherActivities(packageName);
        if (preferredComponent != null) {
            for (LauncherActivityEntry entry : launcherActivities) {
                if (entry.isCurrentUser()
                        && preferredComponent.equals(entry.componentName())) {
                    return createLauncherApp(entry);
                }
            }
        }
        return launcherActivities.isEmpty()
                ? null : createLauncherApp(preferCurrentUser(launcherActivities));
    }

    /** Returns every launcher instance of one package across visible user profiles. */
    public List<LauncherApp> loadLauncherAppsForPackage(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return Collections.emptyList();
        }
        List<LauncherActivityEntry> entries = queryLauncherActivities(packageName);
        List<LauncherApp> apps = new ArrayList<>(entries.size());
        for (LauncherActivityEntry entry : entries) {
            apps.add(createLauncherApp(entry));
        }
        return apps;
    }

    /** Applies the same clone marker used by launcher entries to custom shortcut artwork. */
    public android.graphics.drawable.Drawable addCloneBadge(
            android.graphics.drawable.Drawable icon) {
        return themedIconLoader.addCloneBadge(icon);
    }

    private List<LauncherApp> loadLauncherApps(
            boolean invalidateThemeCaches, boolean useCachedResult) {
        synchronized (APP_LOAD_LOCK) {
            if (useCachedResult && cachedLauncherApps != null) {
                return new ArrayList<>(cachedLauncherApps);
            }
            long startedAt = SystemClock.elapsedRealtime();
            List<LauncherActivityEntry> entries = queryLauncherActivities(null);
            entries.sort(Comparator
                    .comparing(LauncherActivityEntry::label, String.CASE_INSENSITIVE_ORDER)
                    .thenComparingInt(entry -> entry.userHandle.hashCode()));
            long queryFinishedAt = SystemClock.elapsedRealtime();
            if (invalidateThemeCaches) {
                List<ComponentName> components = new ArrayList<>(entries.size());
                for (LauncherActivityEntry entry : entries) {
                    components.add(entry.componentName());
                }
                themedIconLoader.invalidateThemeCaches(components);
            }

            List<LauncherApp> apps = new ArrayList<>();
            for (LauncherActivityEntry entry : entries) {
                String packageName = entry.componentName().getPackageName();
                if (TextUtils.equals(packageName, context.getPackageName())) {
                    continue;
                }
                apps.add(createLauncherApp(entry));
            }
            cachedLauncherApps = Collections.unmodifiableList(new ArrayList<>(apps));
            long finishedAt = SystemClock.elapsedRealtime();
            Log.i(TAG, "Loaded launcher apps: count=" + apps.size()
                    + ", queryMs=" + (queryFinishedAt - startedAt)
                    + ", iconMs=" + (finishedAt - queryFinishedAt)
                    + ", themeCacheInvalidated=" + invalidateThemeCaches);
            return apps;
        }
    }

    private List<LauncherActivityEntry> queryLauncherActivities(String packageName) {
        List<LauncherActivityEntry> desktopActivities = queryLauncherService(packageName);
        if (!desktopActivities.isEmpty()) {
            return desktopActivities;
        }

        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        if (!TextUtils.isEmpty(packageName)) {
            mainIntent.setPackage(packageName);
        }
        List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(mainIntent, 0);
        List<LauncherActivityEntry> entries = new ArrayList<>(resolveInfos.size());
        for (ResolveInfo resolveInfo : resolveInfos) {
            if (resolveInfo.activityInfo != null) {
                entries.add(new LauncherActivityEntry(
                        resolveInfo, null, Process.myUserHandle()));
            }
        }
        return entries;
    }

    private List<ResolveInfo> queryHomeActivities() {
        Intent homeIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME);
        return packageManager.queryIntentActivities(
                homeIntent, PackageManager.MATCH_DEFAULT_ONLY);
    }

    private boolean isSelectableHomeActivity(ResolveInfo resolveInfo,
                                             boolean includeOneStep) {
        return resolveInfo != null
                && resolveInfo.activityInfo != null
                && resolveInfo.priority >= 0
                && (includeOneStep || !TextUtils.equals(
                resolveInfo.activityInfo.packageName, context.getPackageName()));
    }

    private List<LauncherActivityEntry> queryLauncherService(String packageName) {
        if (launcherApps == null) {
            return Collections.emptyList();
        }
        List<UserHandle> profiles = userManager == null
                ? Collections.singletonList(Process.myUserHandle())
                : userManager.getUserProfiles();
        List<LauncherActivityEntry> result = new ArrayList<>();
        for (UserHandle profile : profiles) {
            try {
                List<LauncherActivityInfo> activityInfos = launcherApps.getActivityList(
                        packageName, profile);
                for (LauncherActivityInfo activityInfo : activityInfos) {
                    ResolveInfo resolveInfo;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        resolveInfo = new ResolveInfo();
                        resolveInfo.activityInfo = activityInfo.getActivityInfo();
                    } else {
                        resolveInfo = resolveLauncherActivity(activityInfo.getComponentName());
                    }
                    result.add(new LauncherActivityEntry(
                            resolveInfo, activityInfo, profile));
                }
            } catch (RuntimeException ignored) {
                // A profile may be visible to UserManager but unavailable to LauncherApps.
            }
        }
        return result;
    }

    private ResolveInfo resolveLauncherActivity(ComponentName componentName) {
        Intent intent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(componentName);
        return packageManager.resolveActivity(intent, 0);
    }

    private ComponentName componentNameOf(ResolveInfo resolveInfo) {
        return new ComponentName(resolveInfo.activityInfo.packageName,
                resolveInfo.activityInfo.name);
    }

    private LauncherApp createLauncherApp(LauncherActivityEntry entry) {
        return new LauncherApp(
                entry.label(),
                entry.componentName(),
                entry.loadIcon(themedIconLoader, context),
                entry.userHandle);
    }

    private LauncherApp createHomeApp(ResolveInfo resolveInfo) {
        return LauncherApp.createHomeEntry(
                String.valueOf(resolveInfo.loadLabel(packageManager)),
                componentNameOf(resolveInfo),
                themedIconLoader.loadIcon(resolveInfo), isSystemHome(resolveInfo));
    }

    private boolean isSystemHome(ResolveInfo resolveInfo) {
        if (resolveInfo == null || resolveInfo.activityInfo == null
                || resolveInfo.activityInfo.applicationInfo == null) {
            return false;
        }
        android.content.pm.ApplicationInfo applicationInfo =
                resolveInfo.activityInfo.applicationInfo;
        return HomeActivityPolicy.isSystemHome(
                applicationInfo.flags, applicationInfo.sourceDir);
    }

    private LauncherActivityEntry preferCurrentUser(List<LauncherActivityEntry> entries) {
        for (LauncherActivityEntry entry : entries) {
            if (entry.isCurrentUser()) {
                return entry;
            }
        }
        return entries.get(0);
    }

    private final class LauncherActivityEntry {
        final ResolveInfo resolveInfo;
        final LauncherActivityInfo activityInfo;
        final UserHandle userHandle;

        LauncherActivityEntry(ResolveInfo resolveInfo, LauncherActivityInfo activityInfo,
                              UserHandle userHandle) {
            this.resolveInfo = resolveInfo;
            this.activityInfo = activityInfo;
            this.userHandle = userHandle;
        }

        ComponentName componentName() {
            return activityInfo != null
                    ? activityInfo.getComponentName() : componentNameOf(resolveInfo);
        }

        String label() {
            return String.valueOf(activityInfo != null
                    ? activityInfo.getLabel() : resolveInfo.loadLabel(packageManager));
        }

        boolean isCurrentUser() {
            return Process.myUserHandle().equals(userHandle);
        }

        android.graphics.drawable.Drawable loadIcon(
                SystemThemedIconLoader iconLoader, Context iconContext) {
            if (isCurrentUser() && resolveInfo != null) {
                return iconLoader.loadIcon(resolveInfo);
            }
            if (activityInfo != null) {
                if (userHandle.hashCode() == ZTE_CLONE_USER_ID) {
                    android.graphics.drawable.Drawable baseIcon = resolveInfo != null
                            ? iconLoader.loadIcon(resolveInfo)
                            : activityInfo.getIcon(iconContext.getResources()
                            .getDisplayMetrics().densityDpi);
                    return iconLoader.addCloneBadge(baseIcon);
                }
                return activityInfo.getBadgedIcon(
                        iconContext.getResources().getDisplayMetrics().densityDpi);
            }
            return iconLoader.loadIcon(resolveInfo);
        }
    }
}
