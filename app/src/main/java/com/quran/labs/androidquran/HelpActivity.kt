package com.quran.labs.androidquran

import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.quran.labs.androidquran.util.applyGreenStatusBarInsets
import com.quran.labs.androidquran.util.enableGreenStatusBarEdgeToEdge
import com.quran.labs.androidquran.util.openSupportEmail

class HelpActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    // override these to always be dark since the app doesn't really
    // have a light theme until now. without this, the clock color in
    // the status bar will be dark on a dark background.
    enableGreenStatusBarEdgeToEdge()

    super.onCreate(savedInstanceState)

    val actionBar = supportActionBar
    if (actionBar != null) {
      actionBar.setDisplayShowHomeEnabled(true)
      actionBar.setDisplayHomeAsUpEnabled(true)
      actionBar.setTitle(R.string.app_name)
    }

    setContentView(R.layout.help)

    val root = findViewById<ViewGroup>(R.id.root)
    applyGreenStatusBarInsets(root)

    val helpText = findViewById<TextView>(R.id.txtHelp)
    helpText.text = HtmlCompat.fromHtml(getString(R.string.help), HtmlCompat.FROM_HTML_MODE_COMPACT)
    val emailUs = findViewById<TextView>(R.id.email_us)
    emailUs?.setOnClickListener {
      openSupportEmail()
    }
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.itemId == android.R.id.home) {
      finish()
      return true
    }
    return false
  }

}
