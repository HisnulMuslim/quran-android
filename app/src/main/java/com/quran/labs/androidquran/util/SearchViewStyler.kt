package com.quran.labs.androidquran.util

import android.app.Activity
import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.SearchActivity

object SearchViewStyler {

  fun configure(
    activity: Activity,
    menu: Menu,
    searchItem: MenuItem?,
    hasAlwaysDarkToolbarSurface: Boolean,
    expandToFullToolbarWidth: Boolean,
    onExpandChanged: ((Boolean) -> Unit)? = null
  ) {
    if (searchItem == null) return
    val searchView = searchItem.actionView as? SearchView ?: return
    val searchManager = activity.getSystemService(Context.SEARCH_SERVICE) as SearchManager
    searchView.queryHint = activity.getString(R.string.search_hint)
    searchView.setSearchableInfo(
      searchManager.getSearchableInfo(ComponentName(activity, SearchActivity::class.java))
    )
    searchView.maxWidth = Integer.MAX_VALUE

    val searchAutoComplete =
      searchView.findViewById<SearchView.SearchAutoComplete>(androidx.appcompat.R.id.search_src_text)
    if (searchAutoComplete != null) {
      val inputTextColor = ContextCompat.getColor(
        activity,
        if (hasAlwaysDarkToolbarSurface) R.color.toolbar_text else R.color.title_color
      )
      val hintTextColor = ContextCompat.getColor(
        activity,
        if (hasAlwaysDarkToolbarSurface) R.color.search_text_hint else R.color.sura_details_color
      )
      searchAutoComplete.setTextColor(inputTextColor)
      searchAutoComplete.setHintTextColor(hintTextColor)

      val density = activity.resources.displayMetrics.density
      val popupBackground = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 10f * density
        setColor(ContextCompat.getColor(activity, R.color.secondary_dark_background))
        setStroke(
          (1f * density).toInt().coerceAtLeast(1),
          ContextCompat.getColor(activity, R.color.continue_reading_card_stroke)
        )
      }
      searchAutoComplete.setDropDownBackgroundDrawable(popupBackground)
      searchAutoComplete.dropDownVerticalOffset = (4f * density).toInt()
    }

    if (expandToFullToolbarWidth || onExpandChanged != null) {
      searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
        override fun onMenuItemActionExpand(item: MenuItem): Boolean {
          if (expandToFullToolbarWidth) {
            for (i in 0 until menu.size()) {
              val other = menu.getItem(i)
              if (other.itemId != item.itemId) {
                other.isVisible = false
              }
            }
          }
          onExpandChanged?.invoke(true)
          return true
        }

        override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
          onExpandChanged?.invoke(false)
          if (expandToFullToolbarWidth) {
            activity.invalidateOptionsMenu()
          }
          return true
        }
      })
    }
  }
}
