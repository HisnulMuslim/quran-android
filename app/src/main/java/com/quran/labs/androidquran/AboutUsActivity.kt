package com.quran.labs.androidquran

import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.ViewGroup
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.quran.labs.androidquran.ui.fragment.AboutFragment
import com.quran.labs.androidquran.util.applyGreenStatusBarInsets
import com.quran.labs.androidquran.util.enableGreenStatusBarEdgeToEdge

class AboutUsActivity : AppCompatActivity() {

  public override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // override these to always be dark since the app doesn't really
    // have a light theme until now. without this, the clock color in
    // the status bar will be dark on a dark background.
    enableGreenStatusBarEdgeToEdge()

    setContentView(R.layout.about_us)

    val root = findViewById<ViewGroup>(R.id.root)
    applyGreenStatusBarInsets(root)

    val toolbar = findViewById<Toolbar>(R.id.toolbar)
    toolbar.setTitle(R.string.menu_about)
    setSupportActionBar(toolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)

    val fragmentManager = supportFragmentManager
    val fragment = fragmentManager.findFragmentById(R.id.content)
    if (fragment == null) {
      fragmentManager.beginTransaction()
        .replace(R.id.content, AboutFragment())
        .commit()
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
