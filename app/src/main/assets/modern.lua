-- modern.lua - Modern AndroidX / Kotlin / OkHttp / CameraX helpers for AndroLua+
-- Ensures all modern dependencies work via luajava with robust classloader handling
-- Requires luajava.import / luajava.bindClass fixes in LuaJavaAPI (Android 14+)

local modern = {}

-- Import helper: tries luajava.import first (handles androidx/kotlin/okhttp prefix fallback), then bindClass
function modern.import(name)
  local ok, cls = pcall(function() return luajava.import(name) end)
  if ok and cls then return cls end
  return luajava.bindClass(name)
end

-- OkHttp helpers ---------------------------------------------------------------
-- Simple GET via ModernApi (LuaFunction callback friendly)
function modern.httpGet(url, callback)
  local ModernApi = modern.import("com.androlua.ModernApi")
  ModernApi:okhttpGet(url, callback)
end

function modern.httpPostJson(url, json, callback)
  local ModernApi = modern.import("com.androlua.ModernApi")
  ModernApi:okhttpPostJson(url, json, callback)
end

-- Direct OkHttpClient usage (advanced)
function modern.newOkHttpClient(timeoutMs)
  local ModernApi = modern.import("com.androlua.ModernApi")
  return ModernApi:newOkHttpClient(timeoutMs or 15000)
end

function modern.okhttpExample()
  -- Direct luajava usage; classloader now supports okhttp3.*
  local OkHttpClient = modern.import("okhttp3.OkHttpClient")
  local Request = modern.import("okhttp3.Request")
  local client = OkHttpClient()
  local request = Request.Builder():url("https://httpbin.org/get"):build()
  -- enqueue with proxy callback (Kotlin SAM)
  client:newCall(request):enqueue(luajava.createProxy("okhttp3.Callback", {
    onFailure = function(call, e) print("failure", e) end,
    onResponse = function(call, resp) print("code", resp:code(), resp:body():string()) end
  }))
end

-- CameraX helpers --------------------------------------------------------------
function modern.getCameraProvider(activity)
  local ModernApi = modern.import("com.androlua.ModernApi")
  return ModernApi:getCameraProvider(activity or activity)
end

function modern.bindCameraXPreview(activity, previewView)
  -- Example: bind CameraX preview to PreviewView (androidx.camera.view.PreviewView)
  local providerFuture = modern.getCameraProvider(activity)
  local Runnable = modern.import("java.lang.Runnable")
  local ContextCompat = modern.import("androidx.core.content.ContextCompat")
  providerFuture:addListener(luajava.createProxy("java.lang.Runnable", {
    run = function()
      local provider = providerFuture:get()
      local Preview = modern.import("androidx.camera.core.Preview")
      local CameraSelector = modern.import("androidx.camera.core.CameraSelector")
      local preview = Preview.Builder():build()
      preview:setSurfaceProvider(previewView:getSurfaceProvider())
      provider:unbindAll()
      provider:bindToLifecycle(activity, CameraSelector.DEFAULT_BACK_CAMERA, preview)
    end
  }), ContextCompat:getMainExecutor(activity))
end

-- Coil image loading -----------------------------------------------------------
function modern.coilLoad(imageView, url)
  local ModernApi = modern.import("com.androlua.ModernApi")
  ModernApi:coilLoad(imageView, url)
end

-- Media3 ExoPlayer -------------------------------------------------------------
function modern.newExoPlayer(ctx)
  local ModernApi = modern.import("com.androlua.ModernApi")
  return ModernApi:newExoPlayer(ctx)
end

function modern.exoPlay(ctx, playerView, url)
  local player = modern.newExoPlayer(ctx)
  local MediaItem = modern.import("androidx.media3.common.MediaItem")
  player:setMediaItem(MediaItem:fromUri(url))
  player:prepare()
  playerView:setPlayer(player)
  player:setPlayWhenReady(true)
  return player
end

-- Material3 / Compose entry points --------------------------------------------
function modern.showMaterialDialog(ctx, title, msg)
  local MaterialAlertDialogBuilder = modern.import("com.google.android.material.dialog.MaterialAlertDialogBuilder")
  MaterialAlertDialogBuilder(ctx):setTitle(title):setMessage(msg):setPositiveButton("OK", nil):show()
end

-- MLKit barcode (example) ------------------------------------------------------
function modern.scanBarcode(imageProxy, onResult)
  -- Uses com.google.mlkit.vision.barcode.BarcodeScanning
  local InputImage = modern.import("com.google.mlkit.vision.common.InputImage")
  local BarcodeScanning = modern.import("com.google.mlkit.vision.barcode.BarcodeScanning")
  -- imageProxy is androidx.camera.core.ImageProxy
  -- Caller should convert to InputImage via InputImage.fromMediaImage(mediaImage, rotation)
end

-- Kotlin coroutine helper ------------------------------------------------------
-- Demonstrates that kotlin.* and kotlinx.coroutines.* resolve via luajava
function modern.kotlinInfo()
  local Metadata = modern.import("kotlin.Metadata")
  local Dispatchers = modern.import("kotlinx.coroutines.Dispatchers")
  print("Kotlin metadata", Metadata)
  print("Dispatchers", Dispatchers)
end

return modern
