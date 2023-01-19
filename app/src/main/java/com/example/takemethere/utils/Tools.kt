package com.example.takemethere.utils

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.location.LocationManager
import android.os.Build
import android.util.Log
import android.widget.Toast

object Tools {
    fun showToast(context: Context, message:String){
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun debugMessage(message: String, tag:String="DEBUG-MESSAGE") {
        Log.e(tag,message,)
    }

    fun drawableToBitmap(drawable: Drawable): Bitmap {
        var bitmap: Bitmap? = null
        if (drawable is BitmapDrawable) {
            if (drawable.bitmap != null) {
                return drawable.bitmap
            }
        }
        bitmap = if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
            Bitmap.createBitmap(
                1,
                1,
                Bitmap.Config.ARGB_8888
            ) // Single color bitmap will be created of 1x1 pixel
        } else {
            Bitmap.createBitmap(
                drawable.intrinsicWidth,
                drawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888
            )
        }
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    fun isLocationEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) return locationManager.isLocationEnabled
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    fun popUpWindow(
        context: Context,
        message: String,
        title: String = "",
        lambda: ((AlertDialog.Builder) -> Unit)? = null
    ): Boolean {
        AlertDialog.Builder(context).apply {
            this.setCancelable(false)
            this.setTitle(title)
            this.setMessage(message)
            lambda!!(this)
        }.show()
        return false
    }

}