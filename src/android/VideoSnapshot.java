/*
Edited by MM
Copyright 2014 Sebible Limited

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

*/
package com.sebible.cordova.videosnapshot;

import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.util.ArrayList;
import java.io.File;
import java.lang.Exception;

import android.media.MediaMetadataRetriever;
import android.util.Log;
import android.os.Environment;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.provider.MediaStore;
import android.database.Cursor;
import android.app.Activity;
import android.content.Context;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Color;
import android.graphics.PorterDuffXfermode;
import android.graphics.PorterDuff;
import android.net.Uri;

import android.content.CursorLoader;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;



import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;


import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.apache.cordova.CordovaResourceApi;
import org.apache.cordova.file.FileUtils;
import java.net.MalformedURLException;


public class VideoSnapshot extends CordovaPlugin {

    private CallbackContext callbackContext;       
    
    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        String source = "";
        int count = 1;

        this.callbackContext = callbackContext;
        
        JSONObject options = args.optJSONObject(0);
        if (options == null) {
            fail("No options provided");
            return false;
        }


        if (action.equals("snapshot")) {
            // Run async
            snapshot(options);
            return true;
        }
       
        return false;
    }


    private void drawTimestamp(Bitmap bm, String prefix, long timeMs, int textSize) {
        float w = bm.getWidth(), h = bm.getHeight();
        float size = (float)(textSize * bm.getWidth()) / 1280;
        float margin = (float)(w < h ? w : h) * 0.05f;

        Canvas c = new Canvas(bm);
        Paint p = new Paint();
        p.setColor(Color.WHITE);
        p.setStrokeWidth((int)(size / 10));
        p.setTextSize((int)size); // Text Size
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)); // Text Overlapping Pattern
        
        long second = (timeMs / 1000) % 60;
        long minute = (timeMs / (1000 * 60)) % 60;
        long hour = (timeMs / (1000 * 60 * 60)) % 24;

        String text = String.format("%s %02d:%02d:%02d", prefix, hour, minute, second);
        Rect r = new Rect();
        p.getTextBounds(text, 0, text.length(), r);
        //c.drawBitmap(originalBitmap, 0, 0, paint);
        c.drawText(text, bm.getWidth() - r.width() - margin, bm.getHeight() - r.height() - margin, p);
    }

    /**
     * Take snapshots of a video file
     *
     * @param source path of the file
     * @param count of snapshots that are gonna be taken
     */
    private void snapshot(final JSONObject options) {
        final CallbackContext context = this.callbackContext;
        this.cordova.getThreadPool().execute(new Runnable() {   
            public void run() {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                try {
                    int count = options.optInt("count", 1);
                    int countPerMinute = options.optInt("countPerMinute", 0);
                    int quality = options.optInt("quality", 90);
                    String source = options.optString("source", "");
                    Boolean timestamp = options.optBoolean("timeStamp", true);
                    String prefix = options.optString("prefix", "");
                    int textSize = options.optInt("textSize", 48);
                    
                    if (source.isEmpty()) {
                        throw new Exception("No source provided");
                    }

                    JSONObject obj = new JSONObject();
                    obj.put("result", false);
                    JSONArray results = new JSONArray();
                    
                    Log.i("snapshot", "Got source: " + source);
                    Uri p = Uri.parse(source);
                    String filename = p.getLastPathSegment();
                    
                    FileInputStream in = new FileInputStream(resolveLocalFileSystemURI(source));
                    retriever.setDataSource(in.getFD());
                    String tmp = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    long duration = Long.parseLong(tmp);
                    if (countPerMinute > 0) {
                        count = (int)(countPerMinute * duration / (60 * 1000));
                    }
                    if (count < 1) {
                        count = 1;
                    }
                    long delta = duration / (count + 1); // Start at duration * 1 and ends at duration * count
                    if (delta < 1000) { // min 1s
                        delta = 1000;
                    }

                    Log.i("snapshot", "duration:" + duration + " delta:" + delta);

                    File storage = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                    for (int i = 1; delta * i < duration && i <= count; i++) {
                        String filename2 = filename.replace('.', '_') + "-snapshot" + i + ".jpg";
                        File dest = new File(storage, filename2);
                        if (!storage.exists() && !storage.mkdirs()) {
                            throw new Exception("Unable to access storage:" + storage.getPath());
                        }
                        FileOutputStream out = new FileOutputStream(dest);
                        Bitmap bm = retriever.getFrameAtTime(i * delta * 1000);
                        if (timestamp) {
                            drawTimestamp(bm, prefix, delta * i, textSize);
                        }
                        bm.compress(Bitmap.CompressFormat.JPEG, quality, out);
                        out.flush();
                        out.close();
                        // Get the File plugin from the plugin manager
                        FileUtils filePlugin = (FileUtils)webView.getPluginManager().getPlugin("File");
                        CordovaResourceApi resourceApi = webView.getResourceApi();                        
                        results.put(filePlugin.filesystemURLforLocalPath(dest.getCanonicalPath()));
                    }

                    obj.put("result", true);
                    obj.put("snapshots", results);
                    context.success(obj);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Log.e("snapshot", "Exception:", ex);
                    fail("Exception: " + ex.toString());
                }finally {
                    try {
                        retriever.release();
                    } catch (RuntimeException ex) {
                    }
                }
            }
        });
    }
 @SuppressWarnings("deprecation")
    private File resolveLocalFileSystemURI(String url) throws IOException, JSONException {
        String decoded = URLDecoder.decode(url, "UTF-8");

        File fp = null;

        // Handle the special case where you get an Android content:// uri.
        if (decoded.startsWith("content:")) {
            fp = new File(getPath(this.cordova.getActivity().getApplicationContext(), Uri.parse(decoded)));
        } else {
            // Test to see if this is a valid URL first
            @SuppressWarnings("unused")
            URL testUrl = new URL(decoded);

            if (decoded.startsWith("file://")) {
                int questionMark = decoded.indexOf("?");
                if (questionMark < 0) {
                    fp = new File(decoded.substring(7, decoded.length()));
                } else {
                    fp = new File(decoded.substring(7, questionMark));
                }
            } else if (decoded.startsWith("file:/")) {
                fp = new File(decoded.substring(6, decoded.length()));
            } else {
                fp = new File(decoded);
            }
        }

        if (!fp.exists()) {
            throw new FileNotFoundException();
        }
        if (!fp.canRead()) {
            throw new IOException();
        }
        return fp;
    }

    private void fail(String message) {
        this.callbackContext.error(message);
        PluginResult r = new PluginResult(PluginResult.Status.ERROR);
        callbackContext.sendPluginResult(r);
    }

    /**
     * Get a file path from a Uri. This will get the the path for Storage Access
     * Framework Documents, as well as the _data field for the MediaStore and
     * other file-based ContentProviders.
     *
     * @param context The context.
     * @param uri The Uri to query.
     * @author paulburke
     */
    public static String getPath(final Context context, final Uri uri) {

        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }

                // TODO handle non-primary volumes
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {

                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

                return getDataColumn(context, contentUri, null, null);
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[] {
                        split[1]
                };

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }

    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context The context.
     * @param uri The Uri to query.
     * @param selection (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     */
    public static String getDataColumn(Context context, Uri uri, String selection,
            String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {
                column
        };

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }


    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

}
