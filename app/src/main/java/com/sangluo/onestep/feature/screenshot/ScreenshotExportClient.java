package com.sangluo.onestep.feature.screenshot;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Writes the finished screenshot to the clipboard or the system gallery. */
public final class ScreenshotExportClient {
    private static final String TAG = "OneStepScreenshot";
    private static final String CLIPBOARD_DIRECTORY = "screenshots";
    private static final long CLIPBOARD_FILE_MAX_AGE_MS = 24L * 60L * 60L * 1000L;

    private ScreenshotExportClient() {
    }

    /** Places the bitmap on the clipboard as a content URI. */
    public static boolean copyToClipboard(Context context, Bitmap bitmap) {
        if (context == null || bitmap == null) {
            return false;
        }
        File directory = new File(context.getCacheDir(), CLIPBOARD_DIRECTORY);
        //noinspection ResultOfMethodCallIgnored
        directory.mkdirs();
        cleanOldClipboardFiles(directory);
        File file = new File(directory, nextFileName("OneStep-clip-"));
        try (FileOutputStream output = new FileOutputStream(file)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw new IOException("PNG compression failed");
            }
        } catch (IOException | RuntimeException error) {
            Log.w(TAG, "Cannot write clipboard screenshot", error);
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            return false;
        }
        try {
            Uri uri = FileProvider.getUriForFile(
                    context, context.getPackageName() + ".drag-files", file);
            ClipboardManager clipboard =
                    (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) {
                return false;
            }
            clipboard.setPrimaryClip(ClipData.newUri(
                    context.getContentResolver(), "OneStep 截图", uri));
            Log.i(TAG, "Screenshot copied to clipboard: " + file.getName());
            return true;
        } catch (RuntimeException error) {
            Log.w(TAG, "Cannot place screenshot on clipboard", error);
            return false;
        }
    }

    /** Inserts the bitmap into {@code Pictures/OneStep/} through MediaStore. */
    public static boolean saveToGallery(Context context, Bitmap bitmap) {
        if (context == null || bitmap == null) {
            return false;
        }
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, nextFileName("OneStep-"));
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/OneStep/");
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri uri = null;
        try {
            uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                return false;
            }
            try (OutputStream output = resolver.openOutputStream(uri, "w")) {
                if (output == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw new IOException("MediaStore output failed");
                }
            }
            ContentValues completed = new ContentValues();
            completed.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(uri, completed, null, null);
            Log.i(TAG, "Screenshot saved to gallery: " + uri);
            return true;
        } catch (IOException | RuntimeException error) {
            Log.w(TAG, "Cannot save screenshot to gallery", error);
            if (uri != null) {
                try {
                    resolver.delete(uri, null, null);
                } catch (RuntimeException cleanupError) {
                    Log.w(TAG, "Cannot remove incomplete gallery entry", cleanupError);
                }
            }
            return false;
        }
    }

    private static String nextFileName(String prefix) {
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US)
                .format(new Date());
        return prefix + timestamp + ".png";
    }

    private static void cleanOldClipboardFiles(File directory) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        long cutoff = System.currentTimeMillis() - CLIPBOARD_FILE_MAX_AGE_MS;
        for (File file : files) {
            if (file.isFile() && file.lastModified() < cutoff) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }
}
