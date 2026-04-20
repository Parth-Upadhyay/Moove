package com.example.kinetiq.utils

import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.kinetiq.R
import com.google.android.material.snackbar.Snackbar

/**
 * Shows a Snackbar with the app logo and themed colors.
 */
fun View.showLogoSnackbar(message: String, duration: Int = Snackbar.LENGTH_LONG) {
    val snackbar = Snackbar.make(this, message, duration)
    val snackbarView = snackbar.view
    
    // Get the TextView from the Snackbar
    val textView = snackbarView.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
    
    // Load the logo
    val logo = ContextCompat.getDrawable(context, R.drawable.ic_hospital_logo)
    
    // Resize the logo to a reasonable size for a Snackbar (e.g., 24dp)
    val pixelSize = (24 * context.resources.displayMetrics.density).toInt()
    logo?.setBounds(0, 0, pixelSize, pixelSize)
    
    textView.setCompoundDrawables(logo, null, null, null)
    textView.compoundDrawablePadding = (12 * context.resources.displayMetrics.density).toInt()
    
    // Style the snackbar to match the app theme (OlivePrimary and CreamBackground)
    snackbarView.setBackgroundResource(R.drawable.bg_toast) 
    textView.setTextColor(ContextCompat.getColor(context, R.color.white))
    
    snackbar.show()
}
