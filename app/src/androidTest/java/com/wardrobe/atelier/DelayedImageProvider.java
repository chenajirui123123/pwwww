package com.wardrobe.atelier;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.IOException;

/** Test APK only; pure Java because the provider runs outside instrumentation's class loader. */
public class DelayedImageProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "image/png"; }
    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws java.io.FileNotFoundException {
        try {
            final ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            new Thread(() -> {
                try (ParcelFileDescriptor.AutoCloseOutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    Thread.sleep(6000);
                    Bitmap image = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
                    image.eraseColor(android.graphics.Color.BLUE);
                    image.compress(Bitmap.CompressFormat.PNG, 100, out);
                    image.recycle();
                } catch (IOException | InterruptedException ignored) { }
            }).start();
            return pipe[0];
        } catch (IOException e) { throw new java.io.FileNotFoundException(e.toString()); }
    }
}
