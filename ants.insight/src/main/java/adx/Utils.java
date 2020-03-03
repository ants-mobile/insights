package adx;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import java.lang.ref.WeakReference;

public class Utils {

    static int dpToPx(int dp) {
        return (int) (dp * Resources.getSystem().getDisplayMetrics().density);
    }

    public static int getHeightScreen(Context context) {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        return displayMetrics.heightPixels;
    }

    public static int getWidthScreen(Context context) {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        return displayMetrics.widthPixels;
    }

    static void runOnMainUIThread(Runnable runnable) {
        if (Looper.getMainLooper().getThread() == Thread.currentThread())
            runnable.run();
        else {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(runnable);
        }
    }

    public static boolean isActivityFullyReady(@NonNull Activity activity) {
        boolean hasToken = activity.getWindow().getDecorView().getApplicationWindowToken() != null;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return hasToken;

        View decorView = activity.getWindow().getDecorView();
        boolean insetsAttached = decorView.getRootWindowInsets() != null;

        return hasToken && insetsAttached;
    }

    public static Activity getActivity(Context context) {
        if (context == null) return null;
        if (context instanceof Activity) return (Activity) context;
        if (context instanceof ContextWrapper)
            return getActivity(((ContextWrapper) context).getBaseContext());
        return null;
    }

    static boolean hasConfigChangeFlag(Activity activity, int configChangeFlag) {
        boolean hasFlag = false;
        try {
            int configChanges = activity.getPackageManager().getActivityInfo(activity.getComponentName(), 0).configChanges;
            int flagInt = configChanges & configChangeFlag;
            hasFlag = flagInt != 0;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return hasFlag;
    }

    private static final int MARGIN_ERROR_PX_SIZE = dpToPx(24);

    static boolean isKeyboardUp(WeakReference<Activity> activityWeakReference) {
        DisplayMetrics metrics = new DisplayMetrics();
        Rect visibleBounds = new Rect();
        View view = null;
        boolean isOpen = false;

        if (activityWeakReference.get() != null) {
            Window window = activityWeakReference.get().getWindow();
            view = window.getDecorView();
            view.getWindowVisibleDisplayFrame(visibleBounds);
            window.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        }

        if (view != null) {
            int heightDiff = metrics.heightPixels - visibleBounds.bottom;
            isOpen = heightDiff > MARGIN_ERROR_PX_SIZE;
        }

        return isOpen;
    }


    // Due to differences in accounting for keyboard, navigation bar, and status bar between
    //   Android versions have different implementation here
    static int getWindowHeight(@NonNull Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            return getWindowHeightAPI23Plus(activity);
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            return getWindowHeightLollipop(activity);
        else
            return getDisplaySizeY(activity);
    }

    // Requirement: Ensure DecorView is ready by using OSViewUtils.decorViewReady
    @TargetApi(Build.VERSION_CODES.M)
    private static int getWindowHeightAPI23Plus(@NonNull Activity activity) {
        View decorView = activity.getWindow().getDecorView();
        // Use use stable heights as SystemWindowInset subtracts the keyboard
        WindowInsets windowInsets = decorView.getRootWindowInsets();
        if (windowInsets == null)
            return decorView.getHeight();

        return decorView.getHeight() -
                windowInsets.getStableInsetBottom() -
                windowInsets.getStableInsetTop();
    }

    private static int getWindowHeightLollipop(@NonNull Activity activity) {
        // getDisplaySizeY - works correctly expect for landscape due to a bug.
        if (activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
            return getWindowVisibleDisplayFrame(activity).height();
        //  getWindowVisibleDisplayFrame - Doesn't work for portrait as it subtracts the keyboard height.

        return getDisplaySizeY(activity);
    }

    private static int getDisplaySizeY(@NonNull Activity activity) {
        Point point = new Point();
        activity.getWindowManager().getDefaultDisplay().getSize(point);
        return point.y;
    }

    private static @NonNull
    Rect getWindowVisibleDisplayFrame(@NonNull Activity activity) {
        Rect rect = new Rect();
        activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(rect);
        return rect;
    }

    // Ensures the root decor view is ready by checking the following;
    //   1. Is fully attach to the root window and insets are available
    //   2. Ensure if any Activities are changed while waiting we use the updated one
    static void decorViewReady(@NonNull Activity activity, final @NonNull Runnable runnable) {
        final String listenerKey = "decorViewReady:" + runnable;
        activity.getWindow().getDecorView().post(() -> ActivityLifecycleHandler.setActivityAvailableListener(listenerKey, new ActivityLifecycleHandler.ActivityAvailableListener() {
            @Override
            void available(@NonNull Activity currentActivity) {
                ActivityLifecycleHandler.removeActivityAvailableListener(listenerKey);
                if (isActivityFullyReady(currentActivity))
                    runnable.run();
                else
                    decorViewReady(currentActivity, runnable);
            }
        }));
    }

    static int getWindowWidth(@NonNull Activity activity) {
        return getWindowVisibleDisplayFrame(activity).width();
    }

    static void runOnMainThreadDelayed(Runnable runnable, int delay) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(runnable, delay);
    }
}