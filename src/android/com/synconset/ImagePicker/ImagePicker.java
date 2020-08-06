/**
* An Image Picker Plugin for Cordova/PhoneGap.
*/
package com.synconset;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;

import org.apache.cordova.PluginResult;
import org.apache.cordova.camera.*;
import android.os.Bundle;
import android.os.Environment;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Color;
import android.graphics.Matrix;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import android.net.Uri;
import android.media.ExifInterface;
import android.util.Base64;

import java.net.URI;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.io.InputStream;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import java.text.SimpleDateFormat;
import java.util.Date;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.opensooq.supernova.gligar.GligarPicker;
import com.opensooq.supernova.gligar.ui.ImagePickerActivity;

public class ImagePicker extends CordovaPlugin {

  private static final String ACTION_GET_PICTURES = "getPictures";
  private static final String ACTION_HAS_READ_PERMISSION = "hasReadPermission";
  private static final String ACTION_REQUEST_READ_PERMISSION = "requestReadPermission";

  private static final int PERMISSION_REQUEST_CODE = 100;
  private static final int PICKER_REQUEST_CODE = 30;
  private static int targetWidth = -1;
  private static int targetHeight = -1;
  private static int quality = 100;
  private static boolean correctOrientation = true;
  private static boolean orientationCorrected = false;
  private static final String TIME_FORMAT = "yyyyMMdd_HHmmss";
  private static final String PNG_MIME_TYPE = "image/png";
  private static final String JPEG_MIME_TYPE = "image/jpeg";
  private ExifHelper exifData;

  private CallbackContext callbackContext;

  public boolean execute(String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    this.callbackContext = callbackContext;

    if (ACTION_HAS_READ_PERMISSION.equals(action)) {
      callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, hasReadPermission()));
      return true;

    } else if (ACTION_REQUEST_READ_PERMISSION.equals(action)) {
      requestReadPermission();
      return true;

    } else if (ACTION_GET_PICTURES.equals(action)) {
      final JSONObject params = args.getJSONObject(0);
      int max = 20;
      targetWidth = 0;
      targetHeight = 0;
      quality = 100;
      int outputType = 0;
      if (params.has("maximumImagesCount")) {
        max = params.getInt("maximumImagesCount");
      }
      if (params.has("width")) {
        targetWidth = params.getInt("width");
      }
      if (params.has("height")) {
        targetHeight = params.getInt("height");
      }
      if (params.has("quality")) {
        quality = params.getInt("quality");
      }
      if (params.has("outputType")) {
        outputType = params.getInt("outputType");
      }

      cordova.setActivityResultCallback (this);
      new GligarPicker().limit(max).disableCamera(true).requestCode(this.PICKER_REQUEST_CODE)
      .withActivity(cordova.getActivity()).show();
      // final Intent imagePickerIntent = new Intent(cordova.getActivity(),ImagePickerActivity.class);
      // imagePickerIntent.putExtra("limit", max);
      // imagePickerIntent.putExtra("camera_direct", false);
      // // if (!cameraDirect) {
      // imagePickerIntent.putExtra("disable_camera", true);
      // // }
      // cordova.startActivityForResult(this, imagePickerIntent, PICKER_REQUEST_CODE);
      return true;
    }
    return false;
  }

  @SuppressLint("InlinedApi")
  private boolean hasReadPermission() {
    return Build.VERSION.SDK_INT < 23 ||
    PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(this.cordova.getActivity(), Manifest.permission.READ_EXTERNAL_STORAGE);
  }

  @SuppressLint("InlinedApi")
  private void requestReadPermission() {
    if (!hasReadPermission()) {
      ActivityCompat.requestPermissions(
      this.cordova.getActivity(),
      new String[] {Manifest.permission.READ_EXTERNAL_STORAGE},
      PERMISSION_REQUEST_CODE);
    }
    callbackContext.success();
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (resultCode != Activity.RESULT_OK) {
      return;
    }
    switch (requestCode){
      case PICKER_REQUEST_CODE : {
        String[] pathsList= data.getExtras().getStringArray(GligarPicker.IMAGES_RESULT);
        List<String> l = Arrays.asList(pathsList);
        ArrayList<String> fileNames = new ArrayList<String>(l);
        ArrayList<String> fileData = new ArrayList<String>();
        JSONArray res = new JSONArray(fileNames);
        try{
          for (int counter = 0; counter < fileNames.size(); counter++) {
            fileData.add(processPicture(getScaledAndRotatedBitmap(fileNames.get(counter))));
          }
          res = new JSONArray(fileData);
        }catch(IOException e){

        }
        callbackContext.success(res);
        break;
      }
    }
  }

  public String processPicture(Bitmap bitmap) {
    ByteArrayOutputStream jpeg_data = new ByteArrayOutputStream();
    CompressFormat compressFormat = CompressFormat.JPEG;
    try {
      if (bitmap.compress(compressFormat, quality, jpeg_data)) {
        byte[] code = jpeg_data.toByteArray();
        byte[] output = Base64.encode(code, Base64.NO_WRAP);
        String js_out = new String(output);
        bitmap.recycle();
        jpeg_data = null;
        return js_out;
      }
    } catch (Exception e) {
      return "";
    }
    return "";
  }

  private Bitmap getScaledAndRotatedBitmap(String imageUrl) throws IOException {
    // If no new width or height were specified, and orientation is not needed return the original bitmap
    if (this.targetWidth <= 0 && this.targetHeight <= 0 && !(this.correctOrientation)) {
      InputStream fileStream = null;
      Bitmap image = null;
      try {
        fileStream = FileHelper.getInputStreamFromUriString(imageUrl, cordova);
        image = BitmapFactory.decodeStream(fileStream);
      }  catch (OutOfMemoryError e) {
        callbackContext.error(e.getLocalizedMessage());
      } catch (Exception e){
        callbackContext.error(e.getLocalizedMessage());
      }
      finally {
        if (fileStream != null) {
          try {
            fileStream.close();
          } catch (IOException e) {
            // LOG.d(LOG_TAG, "Exception while closing file input stream.");
          }
        }
      }
      return image;
    }


    /*  Copy the inputstream to a temporary file on the device.
    We then use this temporary file to determine the width/height/orientation.
    This is the only way to determine the orientation of the photo coming from 3rd party providers (Google Drive, Dropbox,etc)
    This also ensures we create a scaled bitmap with the correct orientation

    We delete the temporary file once we are done
    */
    File localFile = null;
    Uri galleryUri = null;
    int rotate = 0;
    try {
      InputStream fileStream = FileHelper.getInputStreamFromUriString(imageUrl, cordova);
      if (fileStream != null) {
        // Generate a temporary file
        String timeStamp = new SimpleDateFormat(TIME_FORMAT).format(new Date());
        String fileName = "IMG_" + timeStamp + ".jpg";
        localFile = new File(getTempDirectoryPath() + fileName);
        galleryUri = Uri.fromFile(localFile);
        writeUncompressedImage(fileStream, galleryUri);
        try {
          String mimeType = FileHelper.getMimeType(imageUrl.toString(), cordova);
          // if (JPEG_MIME_TYPE.equalsIgnoreCase(mimeType)) {
          //  ExifInterface doesn't like the file:// prefix
          String filePath = galleryUri.toString().replace("file://", "");
          // read exifData of source
          exifData = new ExifHelper();
          exifData.createInFile(filePath);
          exifData.readExifData();
          // Use ExifInterface to pull rotation information
          if (this.correctOrientation) {
            ExifInterface exif = new ExifInterface(filePath);
            rotate = exifToDegrees(exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED));
          }
          // }
        } catch (Exception oe) {
          // LOG.w(LOG_TAG,"Unable to read Exif data: "+ oe.toString());
          rotate = 0;
        }
      }
    }
    catch (Exception e)
    {
      // LOG.e(LOG_TAG,"Exception while getting input stream: "+ e.toString());
      return null;
    }



    try {
      // figure out the original width and height of the image
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inJustDecodeBounds = true;
      InputStream fileStream = null;
      try {
        fileStream = FileHelper.getInputStreamFromUriString(galleryUri.toString(), cordova);
        BitmapFactory.decodeStream(fileStream, null, options);
      } finally {
        if (fileStream != null) {
          try {
            fileStream.close();
          } catch (IOException e) {
            // LOG.d(LOG_TAG, "Exception while closing file input stream.");
          }
        }
      }


      //CB-2292: WTF? Why is the width null?
      if (options.outWidth == 0 || options.outHeight == 0) {
        return null;
      }

      // User didn't specify output dimensions, but they need orientation
      if (this.targetWidth <= 0 && this.targetHeight <= 0) {
        this.targetWidth = options.outWidth;
        this.targetHeight = options.outHeight;
      }

      // Setup target width/height based on orientation
      int rotatedWidth, rotatedHeight;
      boolean rotated= false;
      if (rotate == 90 || rotate == 270) {
        rotatedWidth = options.outHeight;
        rotatedHeight = options.outWidth;
        rotated = true;
      } else {
        rotatedWidth = options.outWidth;
        rotatedHeight = options.outHeight;
      }

      // determine the correct aspect ratio
      int[] widthHeight = calculateAspectRatio(rotatedWidth, rotatedHeight);


      // Load in the smallest bitmap possible that is closest to the size we want
      options.inJustDecodeBounds = false;
      options.inSampleSize = calculateSampleSize(rotatedWidth, rotatedHeight,  widthHeight[0], widthHeight[1]);
      Bitmap unscaledBitmap = null;
      try {
        fileStream = FileHelper.getInputStreamFromUriString(galleryUri.toString(), cordova);
        unscaledBitmap = BitmapFactory.decodeStream(fileStream, null, options);
      } finally {
        if (fileStream != null) {
          try {
            fileStream.close();
          } catch (IOException e) {
            // LOG.d(LOG_TAG, "Exception while closing file input stream.");
          }
        }
      }
      if (unscaledBitmap == null) {
        return null;
      }

      int scaledWidth = (!rotated) ? widthHeight[0] : widthHeight[1];
      int scaledHeight = (!rotated) ? widthHeight[1] : widthHeight[0];

      Bitmap scaledBitmap = Bitmap.createScaledBitmap(unscaledBitmap, scaledWidth, scaledHeight, true);
      if (scaledBitmap != unscaledBitmap) {
        unscaledBitmap.recycle();
        unscaledBitmap = null;
      }
      if (this.correctOrientation && (rotate != 0)) {
        Matrix matrix = new Matrix();
        matrix.setRotate(rotate);
        try {
          scaledBitmap = Bitmap.createBitmap(scaledBitmap, 0, 0, scaledBitmap.getWidth(), scaledBitmap.getHeight(), matrix, true);
          this.orientationCorrected = true;
        } catch (OutOfMemoryError oom) {
          this.orientationCorrected = false;
        }
      }
      return scaledBitmap;
    }
    finally {
      // delete the temporary copy
      if (localFile != null) {
        localFile.delete();
      }
    }

  }

  private int exifToDegrees(int exifOrientation) {
    if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_90) {
      return 90;
    } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_180) {
      return 180;
    } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) {
      return 270;
    } else {
      return 0;
    }
  }

  /**
  * Write an inputstream to local disk
  *
  * @param fis - The InputStream to write
  * @param dest - Destination on disk to write to
  * @throws FileNotFoundException
  * @throws IOException
  */
  private void writeUncompressedImage(InputStream fis, Uri dest) throws FileNotFoundException,
  IOException {
    OutputStream os = null;
    try {
      os = this.cordova.getActivity().getContentResolver().openOutputStream(dest);
      byte[] buffer = new byte[4096];
      int len;
      while ((len = fis.read(buffer)) != -1) {
        os.write(buffer, 0, len);
      }
      os.flush();
    } finally {
      if (os != null) {
        try {
          os.close();
        } catch (IOException e) {
          // LOG.d(LOG_TAG, "Exception while closing output stream.");
        }
      }
      if (fis != null) {
        try {
          fis.close();
        } catch (IOException e) {
          // LOG.d(LOG_TAG, "Exception while closing file input stream.");
        }
      }
    }
  }

  private String getTempDirectoryPath() {
    File cache = null;

    // SD Card Mounted
    if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
      cache = cordova.getActivity().getExternalCacheDir();
    }
    // Use internal storage
    else {
      cache = cordova.getActivity().getCacheDir();
    }

    // Create the cache directory if it doesn't exist
    cache.mkdirs();
    return cache.getAbsolutePath();
  }

  /**
  * Maintain the aspect ratio so the resulting image does not look smooshed
  *
  * @param origWidth
  * @param origHeight
  * @return
  */
  public int[] calculateAspectRatio(int origWidth, int origHeight) {
    int newWidth = this.targetWidth;
    int newHeight = this.targetHeight;

    // If no new width or height were specified return the original bitmap
    if (newWidth <= 0 && newHeight <= 0) {
      newWidth = origWidth;
      newHeight = origHeight;
    }
    // Only the width was specified
    else if (newWidth > 0 && newHeight <= 0) {
      newHeight = (int)((double)(newWidth / (double)origWidth) * origHeight);
    }
    // only the height was specified
    else if (newWidth <= 0 && newHeight > 0) {
      newWidth = (int)((double)(newHeight / (double)origHeight) * origWidth);
    }
    // If the user specified both a positive width and height
    // (potentially different aspect ratio) then the width or height is
    // scaled so that the image fits while maintaining aspect ratio.
    // Alternatively, the specified width and height could have been
    // kept and Bitmap.SCALE_TO_FIT specified when scaling, but this
    // would result in whitespace in the new image.
    else {
      double newRatio = newWidth / (double) newHeight;
      double origRatio = origWidth / (double) origHeight;

      if (origRatio > newRatio) {
        newHeight = (newWidth * origHeight) / origWidth;
      } else if (origRatio < newRatio) {
        newWidth = (newHeight * origWidth) / origHeight;
      }
    }

    int[] retval = new int[2];
    retval[0] = newWidth;
    retval[1] = newHeight;
    return retval;
  }

  /**
  * Figure out what ratio we can load our image into memory at while still being bigger than
  * our desired width and height
  *
  * @param srcWidth
  * @param srcHeight
  * @param dstWidth
  * @param dstHeight
  * @return
  */
  public static int calculateSampleSize(int srcWidth, int srcHeight, int dstWidth, int dstHeight) {
    final float srcAspect = (float) srcWidth / (float) srcHeight;
    final float dstAspect = (float) dstWidth / (float) dstHeight;

    if (srcAspect > dstAspect) {
      return srcWidth / dstWidth;
    } else {
      return srcHeight / dstHeight;
    }
  }

  /**
  * Choosing a picture launches another Activity, so we need to implement the
  * save/restore APIs to handle the case where the CordovaActivity is killed by the OS
  * before we get the launched Activity's result.
  *
  * @see http://cordova.apache.org/docs/en/dev/guide/platforms/android/plugin.html#launching-other-activities
  */
  public void onRestoreStateForActivityResult(Bundle state, CallbackContext callbackContext) {
    this.callbackContext = callbackContext;
  }

  /*
  @Override
  public void onRequestPermissionResult(int requestCode,
  String[] permissions,
  int[] grantResults) throws JSONException {

  // For now we just have one permission, so things can be kept simple...
  if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
  cordova.startActivityForResult(this, imagePickerIntent, 0);
} else {
// Tell the JS layer that something went wrong...
callbackContext.error("Permission denied");
}
}
*/
}
