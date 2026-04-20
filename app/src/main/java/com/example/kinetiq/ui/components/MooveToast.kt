package com.example.kinetiq.ui.components

import android.content.Context
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.example.kinetiq.R

object MooveToast {
    fun show(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        val inflater = LayoutInflater.from(context)
        val layout = inflater.inflate(R.layout.layout_custom_toast, null)

        val text: TextView = layout.findViewById(R.id.toast_text)
        text.text = message

        val icon: ImageView = layout.findViewById(R.id.toast_icon)
        icon.setImageResource(R.drawable.ic_hospital_logo)

        with(Toast(context)) {
            setDuration(duration)
            view = layout
            show()
        }
    }
}
