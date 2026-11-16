package com.quran.labs.androidquran

import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.quran.labs.androidquran.util.applyGreenStatusBarInsets
import com.quran.labs.androidquran.util.enableGreenStatusBarEdgeToEdge

class TajweedActivity : AppCompatActivity() {

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
      actionBar.setTitle(R.string.tajweed_rules_title)
    }

    setContentView(R.layout.tajweed)

    val root = findViewById<ViewGroup>(R.id.root)
    applyGreenStatusBarInsets(root)

    val tajweedText = findViewById<TextView>(R.id.txtTajweed)
    tajweedText.text = HtmlCompat.fromHtml(getString(R.string.tajweed_content), HtmlCompat.FROM_HTML_MODE_COMPACT)
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.itemId == android.R.id.home) {
      finish()
      return true
    }
    return false
  }

}
