package com.androlua;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.Gravity;
import android.widget.TextView;

import com.luajava.LuaFunction;
import com.luajava.LuaState;
import com.luajava.LuaStateFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


public class Welcome extends Activity {

    private static final int REQUEST_RUNTIME_PERMISSIONS = 1001;

    private boolean isUpdata;

    private LuaApplication app;

    private String luaMdDir;

    private String localDir;

    private long mLastTime;

    private long mOldLastTime;

    private ProgressDialog pd;

    private boolean isVersionChanged;

    private String mVersionName;

    private String mOldVersionName;

    private ArrayList<String> permissions;

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        TextView view = new TextView(this);
        view.setText(new String(new char[]{'P', 'o', 'w', 'e', 'r', 'e', 'd', ' ', 'b', 'y', ' ', 'A', 'n', 'd', 'r', 'o', 'L', 'u', 'a'}));
        view.setTextColor(0xff888888);
        view.setGravity(Gravity.TOP);
        setContentView(view);
        app = (LuaApplication) getApplication();
        luaMdDir = app.luaMdDir;
        localDir = app.localDir;
        try {
            if (new File(app.getLuaPath("setup.png")).exists())
                getWindow().setBackgroundDrawable(new LuaBitmapDrawable(app, app.getLuaPath("setup.png"), getResources().getDrawable(R.drawable.welcome)));
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (checkInfo()) {
            if (Build.VERSION.SDK_INT >= 23) {
                try {
                    permissions = collectRuntimePermissions();
                    if (!permissions.isEmpty()) {
                        String[] ps = permissions.toArray(new String[0]);
                        try {
                            requestPermissions(ps, REQUEST_RUNTIME_PERMISSIONS);
                            return;
                        } catch (RuntimeException e) {
                            // A malformed/unsupported permission must never prevent the app from starting.
                            e.printStackTrace();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            new UpdateTask().execute();
        } else {
            startActivity();
        }
    }

    private ArrayList<String> collectRuntimePermissions() {
        Set<String> result = new LinkedHashSet<String>();
        try {
            PackageInfo info = getPackageManager().getPackageInfo(
                    getPackageName(), PackageManager.GET_PERMISSIONS);
            String[] requested = info.requestedPermissions;
            if (requested == null)
                return new ArrayList<String>(result);

            for (String permission : requested) {
                if (permission == null || !isRuntimePermission(permission))
                    continue;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                        && "android.permission.WRITE_EXTERNAL_STORAGE".equals(permission))
                    continue;
                if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED)
                    result.add(permission);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<String>(result);
    }

    private boolean isRuntimePermission(String permission) {
        try {
            PermissionInfo info = getPackageManager().getPermissionInfo(permission, 0);
            int protection = info.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE;
            return protection == PermissionInfo.PROTECTION_DANGEROUS;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RUNTIME_PERMISSIONS)
            new UpdateTask().execute();
    }

    public void startActivity() {
        Intent intent = new Intent(getIntent());
        intent.setClass(Welcome.this, Main.class);
        if (isVersionChanged) {
            intent.putExtra("isVersionChanged", isVersionChanged);
            intent.putExtra("newVersionName", mVersionName);
            intent.putExtra("oldVersionName", mOldVersionName);
        }
        startActivity(intent);
        finish();

    }

    public boolean checkInfo() {
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(this.getPackageName(), 0);
            long lastTime = packageInfo.lastUpdateTime;
            String versionName = packageInfo.versionName;
            SharedPreferences info = getSharedPreferences("appInfo", 0);
            String oldVersionName = info.getString("versionName", "");
            if (!versionName.equals(oldVersionName)) {
                SharedPreferences.Editor edit = info.edit();
                edit.putString("versionName", versionName);
                edit.apply();
                isVersionChanged = true;
                mVersionName = versionName;
                mOldVersionName = oldVersionName;
            }
            long oldLastTime = info.getLong("lastUpdateTime", 0);
            if (oldLastTime != lastTime) {
                SharedPreferences.Editor edit = info.edit();
                edit.putLong("lastUpdateTime", lastTime);
                edit.apply();
                isUpdata = true;
                mLastTime = lastTime;
                mOldLastTime = oldLastTime;
                return true;
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return false;
    }


    @SuppressLint("StaticFieldLeak")
    private class UpdateTask extends AsyncTask<String, String, String> {
        @Override
        protected String doInBackground(String[] p1) {
            onUpdate(mLastTime, mOldLastTime);
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            startActivity();
        }

        private void onUpdate(long lastTime, long oldLastTime) {

            LuaState L = LuaStateFactory.newLuaState();
            L.openLibs();
            try {
                if (L.LloadBuffer(LuaUtil.readAsset(Welcome.this, "update.lua"), "update") == 0) {
                    if (L.pcall(0, 0, 0) == 0) {
                        LuaFunction func = L.getFunction("onUpdate");
                        if (func != null)
                            func.call(mVersionName, mOldVersionName);
                    }
                    ;
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                // Prefer the APK's compressed asset entries, matching the original layout.
                unApk("assets", localDir);
            } catch (Exception e) {
                // Some modern APK/ROM combinations expose assets more reliably through AssetManager.
                e.printStackTrace();
                try {
                    copyAssetTree("", new File(localDir));
                } catch (IOException copyError) {
                    copyError.printStackTrace();
                }
            }

            // Never continue with a half-initialized runtime: main.lua is required by Main.
            if (!new File(localDir, "main.lua").isFile()) {
                try {
                    copyAssetTree("", new File(localDir));
                } catch (IOException e) {
                    sendMsg(e.getMessage());
                }
            }

            try {
                unApk("lua", luaMdDir);
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    copyAssetTree("lua", new File(luaMdDir));
                } catch (IOException ignored) {
                    // The lua directory is optional for installations without separately packaged modules.
                }
            }
        }

        private void sendMsg(String message) {
            // TODO: Implement this method

        }

        private void copyAssetTree(String assetPath, File outputDirectory) throws IOException {
            if (!outputDirectory.exists() && !outputDirectory.mkdirs())
                throw new IOException("Cannot create " + outputDirectory);

            String[] children = getAssets().list(assetPath);
            if (children == null)
                return;

            for (String child : children) {
                String childAssetPath = assetPath.length() == 0 ? child : assetPath + "/" + child;
                File output = new File(outputDirectory, child);
                String[] nested = getAssets().list(childAssetPath);
                if (nested != null && nested.length > 0) {
                    copyAssetTree(childAssetPath, output);
                } else {
                    copyAssetFile(childAssetPath, output);
                }
            }
        }

        private void copyAssetFile(String assetPath, File output) throws IOException {
            File parent = output.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs())
                throw new IOException("Cannot create " + parent);

            InputStream in = getAssets().open(assetPath);
            FileOutputStream out = new FileOutputStream(output);
            byte[] buffer = new byte[8192];
            int count;
            try {
                while ((count = in.read(buffer)) != -1)
                    out.write(buffer, 0, count);
            } finally {
                try {
                    out.close();
                } finally {
                    in.close();
                }
            }
        }

        private void unApk(String dir, String extDir) throws IOException {
            int i = dir.length() + 1;
            ZipFile zip = new ZipFile(getApplicationInfo().publicSourceDir);
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.indexOf(dir) != 0)
                    continue;
                String path = name.substring(i);
                if (entry.isDirectory()) {
                    File f = new File(extDir + File.separator + path);
                    if (!f.exists()) {
                        //noinspection ResultOfMethodCallIgnored
                        f.mkdirs();
                    }
                } else {
                    String fname = extDir + File.separator + path;
                    File ff = new File(fname);
                    File temp = new File(fname).getParentFile();
                    if (!temp.exists()) {
                        if (!temp.mkdirs()) {
                            throw new RuntimeException("create file " + temp.getName() + " fail");
                        }
                    }
                    try {
                        if (ff.exists() && entry.getSize() == ff.length() && LuaUtil.getFileMD5(zip.getInputStream(entry)).equals(LuaUtil.getFileMD5(ff)))
                            continue;
                    } catch (NullPointerException ignored) {
                    }
                    FileOutputStream out = new FileOutputStream(extDir + File.separator + path);
                    InputStream in = zip.getInputStream(entry);
                    byte[] buf = new byte[4096];
                    int count = 0;
                    while ((count = in.read(buf)) != -1) {
                        out.write(buf, 0, count);
                    }
                    out.close();
                    in.close();
                }
            }
            zip.close();
        }

    }
}