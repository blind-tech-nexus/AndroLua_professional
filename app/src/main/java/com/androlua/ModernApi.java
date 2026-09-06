package com.androlua;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;

import com.luajava.LuaFunction;
import com.luajava.LuaState;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * ModernApi - Bridges modern AndroidX / Kotlin / OkHttp / CameraX / Media3 / MLKit / Coil
 * for Lua via luajava. Ensures these deps are referenced from Java so R8 keeps them,
 * and provides Lua-friendly callbacks.
 * Also serves as documentation for Lua-side usage.
 *
 * Usage from Lua (via luajava.bindClass / import):
 *   local ModernApi = luajava.bindClass("com.androlua.ModernApi")
 *   ModernApi:okhttpGet("https://httpbin.org/get", function(code, body, err) print(code, body) end)
 *   local client = ModernApi:newOkHttpClient(15000)
 *   ModernApi:okhttpPostJson("https://httpbin.org/post", '{"hello":"world"}', callback)
 *
 * CameraX is accessed directly via androidx.camera.* but helpers exist for permission / provider.
 */
public class ModernApi {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static OkHttpClient defaultClient;

    public static OkHttpClient getDefaultClient() {
        if (defaultClient == null) {
            synchronized (ModernApi.class) {
                if (defaultClient == null) {
                    defaultClient = new OkHttpClient.Builder()
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(15, TimeUnit.SECONDS)
                            .writeTimeout(15, TimeUnit.SECONDS)
                            .retryOnConnectionFailure(true)
                            .build();
                }
            }
        }
        return defaultClient;
    }

    /** Create a new OkHttpClient with custom timeout (ms). Accessible via luajava. */
    public static OkHttpClient newOkHttpClient(int timeoutMs) {
        return new OkHttpClient.Builder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    /** Simple async GET, callback is LuaFunction(code, body, error). Runs on UI thread callback. */
    public static void okhttpGet(final String url, final LuaFunction callback) {
        okhttpGetWithClient(getDefaultClient(), url, callback);
    }

    public static void okhttpGetWithClient(OkHttpClient client, final String url, final LuaFunction callback) {
        Request req = new Request.Builder().url(url).get().build();
        client.newCall(req).enqueue(wrap(callback));
    }

    public static void okhttpPostJson(final String url, final String json, final LuaFunction callback) {
        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(json, JSON);
        Request req = new Request.Builder().url(url).post(body).build();
        getDefaultClient().newCall(req).enqueue(wrap(callback));
    }

    public static void okhttpPostForm(final String url, final String[] keys, final String[] values, final LuaFunction callback) {
        FormBody.Builder fb = new FormBody.Builder();
        for (int i = 0; i < keys.length && i < values.length; i++) fb.add(keys[i], values[i]);
        Request req = new Request.Builder().url(url).post(fb.build()).build();
        getDefaultClient().newCall(req).enqueue(wrap(callback));
    }

    private static Callback wrap(final LuaFunction cb) {
        return new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                postResult(cb, -1, null, e.toString());
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = "";
                try { if (response.body()!=null) body = response.body().string(); } catch (Exception ex){ body = ex.toString(); }
                postResult(cb, response.code(), body, null);
            }
        };
    }

    private static void postResult(final LuaFunction cb, final int code, final String body, final String err) {
        MAIN.post(new Runnable() {
            @Override public void run() {
                try { cb.call(new Object[]{code, body, err}); }
                catch (Exception e){ e.printStackTrace(); }
            }
        });
    }

    /** Helper to get androidx Camera provider - returns ListenableFuture for Lua to addListener */
    public static Object getCameraProvider(Context ctx) {
        try {
            Class<?> pc = Class.forName("androidx.camera.lifecycle.ProcessCameraProvider");
            java.lang.reflect.Method m = pc.getMethod("getInstance", Context.class);
            return m.invoke(null, ctx);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /** Coil helper - load image url into ImageView via Coil */
    public static void coilLoad(android.widget.ImageView view, String url) {
        try {
            // Use reflection to avoid hard compile dep if Coil not present, but we have it
            Class<?> imgReq = Class.forName("coil.request.ImageRequest$Builder");
            // However simpler: rely on coil-kt extension via Java - use coil.Coil
            // Fallback to direct Coil call if available
            coil.Coil.imageLoader(view.getContext()).enqueue(
                new coil.request.ImageRequest.Builder(view.getContext())
                    .data(url)
                    .target(view)
                    .build()
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Media3 helper - build ExoPlayer */
    public static Object newExoPlayer(Context ctx) {
        try {
            Class<?> exo = Class.forName("androidx.media3.exoplayer.ExoPlayer$Builder");
            Object builder = exo.getConstructor(Context.class).newInstance(ctx);
            return exo.getMethod("build").invoke(builder);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /** Ensure Kotlin coroutines dispatcher accessible */
    public static Object getMainDispatcher() {
        try {
            Class<?> d = Class.forName("kotlinx.coroutines.Dispatchers");
            return d.getField("Main").get(null);
        } catch (Exception e){ return null; }
    }

    /** Simple toast helper for Lua modern UI */
    public static void toast(Context ctx, String msg) {
        android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show();
    }

    /** DPI helper */
    public static int dp2px(Context ctx, float dp) {
        return (int)(dp * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }
}
