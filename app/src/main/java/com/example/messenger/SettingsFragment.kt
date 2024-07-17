package com.example.messenger

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.messenger.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {
    private lateinit var binding: FragmentSettingsBinding

    @SuppressLint("DiscouragedApi")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val prefs = requireContext().getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        val wallpaper = prefs.getString(PREF_WALLPAPER, "")
        if(wallpaper != "") {
            binding.wallpaperName.text = wallpaper
            val resId = resources.getIdentifier(wallpaper, "drawable", requireContext().packageName)
            if (resId != 0) binding.settingsLayout.background =
                ContextCompat.getDrawable(requireContext(), resId)
        }
        else {
            binding.wallpaperName.text = "Classic"
        }
        val themeNumber = prefs.getInt(PREF_THEME, 0)
        if(themeNumber != 0) binding.colorThemeName.text = "Theme ${themeNumber+1}" else binding.colorThemeName.text = "Classic"
        binding.changeColorTheme.setOnClickListener {
            showColorThemePopupMenu(it, container as ViewGroup)
        }
        binding.changeWallpaper.setOnClickListener {
            showWallpapersPopupMenu(it, container as ViewGroup)
        }
        return binding.root
    }
    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        if (key == PREF_WALLPAPER) {
            val tmp = sharedPreferences.getString(PREF_WALLPAPER, "")
            updateWallpapers(tmp!!)
        }
    }

    private fun showWallpapersPopupMenu(view: View, container: ViewGroup) {
        val inflater = layoutInflater
        val popupView = inflater.inflate(R.layout.popup_menu_wallpaper_layout, container, false)

        val popupWindow = PopupWindow(
            popupView,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        )

        popupWindow.showAtLocation(requireView(), Gravity.CENTER, 0, 0)
        val recyclerView = popupView.findViewById<RecyclerView>(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val menuItems = listOf(
            MenuItemData("Classic", R.drawable.whitequad),
            MenuItemData("1.", R.drawable.wallpaper1),
            MenuItemData("2.", R.drawable.wallpaper2),
            MenuItemData("3.", R.drawable.wallpaper3),
            MenuItemData("4.", R.drawable.wallpaper4),
            MenuItemData("5.", R.drawable.wallpaper5),
            MenuItemData("6.", R.drawable.wallpaper6),
            MenuItemData("7.", R.drawable.wallpaper7),
            MenuItemData("8.", R.drawable.wallpaper8),
            MenuItemData("9.", R.drawable.wallpaper9),
            MenuItemData("10.", R.drawable.wallpaper10)
        )
        var temp = ""
        val adapter = PopupMenuWallpaperAdapter(menuItems) { menuItem ->
            temp = when(menuItem.title) {
                "Classic" -> ""
                "1." -> "wallpaper1"
                "2." -> "wallpaper2"
                "3." -> "wallpaper3"
                "4." -> "wallpaper4"
                "5." -> "wallpaper5"
                "6." -> "wallpaper6"
                "7." -> "wallpaper7"
                "8." -> "wallpaper8"
                "9." -> "wallpaper9"
                "10." -> "wallpaper10"
                else -> ""
            }
            val preferences: SharedPreferences =
                requireContext().getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE)
            preferences.edit().putString(PREF_WALLPAPER, temp).apply()
            popupWindow.dismiss()
        }

        recyclerView.adapter = adapter

        popupWindow.showAsDropDown(view)
    }

    private fun showColorThemePopupMenu(view: View, container: ViewGroup) {
        val inflater = layoutInflater
        val popupView = inflater.inflate(R.layout.popup_menu_wallpaper_layout, container, false)

        val popupWindow = PopupWindow(
            popupView,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        )

        popupWindow.showAtLocation(requireView(), Gravity.CENTER, 0, 0)
        val recyclerView = popupView.findViewById<RecyclerView>(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val menuItems = listOf(
            ColorThemeMenuItem(R.color.colorPrimary, R.color.colorAccent, 0),
            ColorThemeMenuItem(R.color.color1_main, R.color.color1_secondary, 1),
            ColorThemeMenuItem(R.color.color2_main, R.color.color2_secondary, 2),
            ColorThemeMenuItem(R.color.color3_main, R.color.color3_secondary, 3),
            ColorThemeMenuItem(R.color.color4_main, R.color.color4_secondary, 4),
            ColorThemeMenuItem(R.color.color5_main, R.color.color5_secondary, 5),
            ColorThemeMenuItem(R.color.color6_main, R.color.color6_secondary, 6),
            ColorThemeMenuItem(R.color.color7_main, R.color.color7_secondary, 7),
            ColorThemeMenuItem(R.color.color8_main, R.color.color8_secondary, 8)
        )
        val adapter = ColorThemeMenuAdapter(menuItems) { menuItem ->
            val preferences: SharedPreferences =
                requireContext().getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE)
            preferences.edit().putInt(PREF_THEME, menuItem.themeNumber).apply()
            requireActivity().recreate()
            popupWindow.dismiss()
        }

        recyclerView.adapter = adapter

        popupWindow.showAsDropDown(view)
    }

    @SuppressLint("DiscouragedApi")
    private fun updateWallpapers(wallpaper: String) {
        binding.wallpaperName.text = wallpaper
        if(wallpaper != "") {
            val resId = resources.getIdentifier(wallpaper, "drawable", requireContext().packageName)
            if(resId != 0)
                binding.settingsLayout.background = ContextCompat.getDrawable(requireContext(), resId)
        }
        else {
            binding.settingsLayout.background = null
            binding.wallpaperName.text = "Classic"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val prefs = requireContext().getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE)
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }
}