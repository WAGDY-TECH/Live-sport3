package com.wagdytech.livesports

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: ChannelAdapter
    private lateinit var favorites: FavoritesStore
    private lateinit var settings: SettingsStore
    private lateinit var countView: TextView
    private lateinit var progress: ProgressBar
    private lateinit var toolbar: MaterialToolbar
    private var remote: AppConfig? = null

    private var allChannels: List<Channel> = emptyList()
    private var selectedCountry: String? = null
    private var onlyFavorites = false

    private val query get() = findViewById<EditText>(R.id.searchInput).text.toString()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        favorites = FavoritesStore(this)
        settings = SettingsStore(this)
        progress = findViewById(R.id.progressBar)
        toolbar = findViewById(R.id.toolbar)

        adapter = ChannelAdapter(emptyList(), favorites, onPlay = ::openPlayer, onToggleFavorite = { ch ->
            favorites.toggle(ch.url)
            adapter.filter(query, selectedCountry, onlyFavorites)
            Toast.makeText(
                this,
                if (favorites.isFavorite(ch.url)) R.string.added_favorites else R.string.removed_favorites,
                Toast.LENGTH_SHORT
            ).show()
        })

        countView = findViewById(R.id.countText)

        val recycler = findViewById<RecyclerView>(R.id.recyclerChannels)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        findViewById<EditText>(R.id.searchInput).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = applyFilters()
        })

        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_favorites -> {
                    onlyFavorites = !onlyFavorites
                    item.isChecked = onlyFavorites
                    applyFilters()
                    true
                }
                R.id.action_load_url -> {
                    askPlaylistUrl()
                    true
                }
                R.id.action_reset -> {
                    settings.playlistUrl = null
                    loadPlaylist()
                    true
                }
                else -> false
            }
        }

        RemoteConfig.loadCached(this)?.let { applyRemoteConfig(it) }
        loadPlaylist()
        AdsManager.load(this)

        lifecycleScope.launch {
            val fresh = RemoteConfig.fetch(this@MainActivity)
            if (fresh != null) {
                applyRemoteConfig(fresh)
                showRemoteDialogs(fresh)
            }
        }
    }

    private fun applyRemoteConfig(config: AppConfig) {
        remote = config
        config.appName?.let { toolbar.title = it }
        val accent = config.accentColor?.let { safeColor(it) }
        val background = config.backgroundColor?.let { safeColor(it) }
        if (accent != null) {
            toolbar.backgroundTintList = ColorStateList.valueOf(accent)
            countView.setTextColor(accent)
            findViewById<MaterialCardView>(R.id.searchCard).setStrokeColor(ColorStateList.valueOf(accent))
        }
        if (background != null) {
            findViewById<View>(android.R.id.content).setBackgroundColor(background)
            toolbar.backgroundTintList = ColorStateList.valueOf(
                accent ?: ContextCompat.getColor(this, R.color.surface)
            )
        }
        if (config.playlistUrl != null && settings.playlistUrl == null) {
            loadPlaylist()
        }
    }

    private fun showRemoteDialogs(config: AppConfig) {
        when {
            config.hasUpdate && config.latestVersionCode > BuildConfig.VERSION_CODE -> {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.update_title))
                    .setMessage(config.updateMessage ?: getString(R.string.update_message))
                    .setCancelable(!config.forceUpdate)
                    .setPositiveButton(R.string.update_now) { _, _ ->
                        openUrl(config.updateApkUrl ?: return@setPositiveButton)
                    }
                    .show()
            }
            config.hasAnnouncement -> {
                AlertDialog.Builder(this)
                    .setTitle(config.announcementTitle)
                    .setMessage(config.announcementMessage)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
    }

    private fun safeColor(hex: String): Int? = runCatching { Color.parseColor(hex) }.getOrNull()

    private fun openUrl(url: String) {
        runCatching {
            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        }.onFailure {
            Toast.makeText(this, R.string.load_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun askPlaylistUrl() {
        val input = EditText(this).apply {
            hint = getString(R.string.url_hint)
            setText(settings.playlistUrl ?: remote?.playlistUrl ?: "")
            setSingleLine()
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.load_playlist)
            .setView(input)
            .setPositiveButton(R.string.load) { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) {
                    settings.playlistUrl = url
                    loadPlaylist()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun playlistSource(): String? = settings.playlistUrl ?: remote?.playlistUrl

    private fun loadPlaylist() {
        progress.visibility = View.VISIBLE
        val url = playlistSource()
        lifecycleScope.launch {
            val content = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    if (url == null) {
                        assets.open("channels.m3u").bufferedReader().use { it.readText() }
                    } else {
                        URL(url).openStream().bufferedReader().use { it.readText() }
                    }
                }.getOrNull()
            }
            progress.visibility = View.GONE
            if (content == null) {
                Toast.makeText(this@MainActivity, R.string.load_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            allChannels = M3UParser.parse(content)
            adapter.replaceSource(allChannels)
            buildCountryChips()
            applyFilters()
        }
    }

    private fun buildCountryChips() {
        val chipGroup = findViewById<ChipGroup>(R.id.chipGroup)
        chipGroup.removeAllViews()
        val counts = allChannels.mapNotNull { it.country }
            .groupingBy { it }.eachCount()
        val countries = counts.entries.sortedByDescending { it.value }.take(20).map { it.key }

        chipGroup.addView(makeChip(getString(R.string.all), null, true))
        chipGroup.addView(makeChip(getString(R.string.favorites), "FAV", false))
        countries.forEach { code ->
            chipGroup.addView(makeChip(code, code, false))
        }
    }

    private fun makeChip(label: String, value: String?, checked: Boolean): Chip {
        val chip = Chip(this)
        chip.text = label
        chip.isCheckable = true
        chip.isChecked = checked
        chip.isClickable = true
        chip.setOnClickListener {
            val group = findViewById<ChipGroup>(R.id.chipGroup)
            if (group.checkedChipId == chip.id) {
                if (value == "FAV") {
                    onlyFavorites = true; selectedCountry = null
                } else if (value == null) {
                    onlyFavorites = false; selectedCountry = null
                } else {
                    onlyFavorites = false; selectedCountry = value
                }
            } else {
                onlyFavorites = false; selectedCountry = null
            }
            applyFilters()
        }
        return chip
    }

    private fun applyFilters() {
        val list = adapter.filter(query, selectedCountry, onlyFavorites)
        countView.text = getString(R.string.channels_count, list.size)
        findViewById<View>(R.id.emptyState).visibility =
            if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openPlayer(channel: Channel) {
        AdsManager.maybeShowBeforeOpen(this) {
            PlayerActivity.start(this, channel.name, channel.url)
        }
    }
}
