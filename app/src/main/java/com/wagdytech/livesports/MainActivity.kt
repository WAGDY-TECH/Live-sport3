package com.wagdytech.livesports

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
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
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch
import java.net.URL

class MainActivity : AppCompatActivity() {

    private val isStore get() = BuildConfig.FLAVOR == "store"

    private lateinit var adapter: ChannelAdapter
    private lateinit var favorites: FavoritesStore
    private lateinit var settings: SettingsStore
    private lateinit var pro: ProStore
    private lateinit var countView: TextView
    private lateinit var progress: ProgressBar
    private lateinit var toolbar: MaterialToolbar
    private var remote: AppConfig? = null

    private val matchAdapter = MatchAdapter()
    private val newsAdapter = NewsAdapter { item -> openUrl(item.link) }
    private var matchTab = "live"
    private var matchLeague = 0

    private var allChannels: List<Channel> = emptyList()
    private var selectedCountry: String? = null
    private var onlyFavorites = false

    private val query get() = findViewById<EditText>(R.id.searchInput).text.toString()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        favorites = FavoritesStore(this)
        settings = SettingsStore(this)
        pro = ProStore(this)
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

        setupMatches()
        setupNews()
        setupPro()

        findViewById<BottomNavigationView>(R.id.bottomNav).setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_channels -> showPage(R.id.pageChannels)
                R.id.nav_matches -> { showPage(R.id.pageMatches); refreshMatches() }
                R.id.nav_news -> { showPage(R.id.pageNews); refreshNews() }
                R.id.nav_pro -> { showPage(R.id.pagePro); refreshProUi() }
            }
            true
        }

        setupNavigationVisibility()
        adapter.unlocked = { pro.isActive() }
        RemoteConfig.loadCached(this)?.let { applyRemoteConfig(it) }
        if (playlistSource() == null && isStore) askPlaylistUrl()
        loadPlaylist()
        loadProPlaylist()
        AdsManager.init(this)

        lifecycleScope.launch {
            val fresh = RemoteConfig.fetch(this@MainActivity)
            if (fresh != null) {
                applyRemoteConfig(fresh)
                showRemoteDialogs(fresh)
            }
        }
    }

    private fun setupNavigationVisibility() {
        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        if (isStore) nav.menu.findItem(R.id.nav_pro).isVisible = false
    }

    private fun showPage(pageId: Int) {
        val pages = if (isStore) listOf(R.id.pageChannels, R.id.pageMatches, R.id.pageNews)
        else listOf(R.id.pageChannels, R.id.pageMatches, R.id.pageNews, R.id.pagePro)
        pages.forEach {
            findViewById<View>(it).visibility = if (it == pageId) View.VISIBLE else View.GONE
        }
    }

    private fun setupMatches() {
        val tabs = findViewById<ChipGroup>(R.id.matchTabs)
        val defs = listOf(
            getString(R.string.tab_live) to "live",
            getString(R.string.tab_upcoming) to "upcoming",
            getString(R.string.tab_results) to "results"
        )
        defs.forEachIndexed { i, (label, key) ->
            val chip = Chip(this).apply {
                text = label
                isCheckable = true
                isChecked = i == 0
                setOnClickListener {
                    matchTab = key
                    refreshMatches()
                }
            }
            tabs.addView(chip)
        }
        findViewById<RecyclerView>(R.id.recyclerMatches).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = matchAdapter
        }
    }

    private fun refreshMatches() {
        val status = findViewById<TextView>(R.id.matchesStatus)
        status.visibility = View.GONE
        lifecycleScope.launch {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                when (matchTab) {
                    "live" -> MatchesLoader.live()
                    "upcoming" -> MatchesLoader.upcoming(leagueIds())
                    else -> MatchesLoader.results(leagueIds())
                }
            }
            matchAdapter.set(result)
            if (result.isEmpty()) {
                status.text = getString(R.string.matches_empty)
                status.visibility = View.VISIBLE
            }
        }
    }

    private fun leagueIds(): List<Int> =
        if (matchLeague == 0) Leagues.ALL.values.first()
        else listOf(matchLeague)

    private fun setupNews() {
        findViewById<RecyclerView>(R.id.recyclerNews).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = newsAdapter
        }
    }

    private fun refreshNews() {
        val status = findViewById<TextView>(R.id.newsStatus)
        status.visibility = View.GONE
        lifecycleScope.launch {
            val items = NewsLoader.load()
            newsAdapter.set(items)
            if (items.isEmpty()) {
                status.text = getString(R.string.news_error)
                status.visibility = View.VISIBLE
            }
        }
    }

    private fun setupPro() {
        findViewById<View>(R.id.btnSubscribe).setOnClickListener {
            val url = remote?.proContactUrl
            if (url.isNullOrEmpty()) {
                Toast.makeText(this, R.string.news_error, Toast.LENGTH_SHORT).show()
            } else openUrl(url)
        }
        findViewById<View>(R.id.btnActivate).setOnClickListener {
            val code = findViewById<EditText>(R.id.proCodeInput).text.toString().trim()
            if (code.isEmpty()) return@setOnClickListener
            lifecycleScope.launch {
                val codes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    Net.getText(BuildConfig.PRO_CODES_URL)
                }
                val valid = runCatching {
                    val arr = org.json.JSONArray(codes ?: "[]")
                    (0 until arr.length()).any { arr.optString(it) == code }
                }.getOrDefault(false)
                if (valid) {
                    pro.activate(code)
                    Toast.makeText(this@MainActivity, R.string.pro_activated_ok, Toast.LENGTH_LONG).show()
                    refreshProUi()
                    adapter.unlocked = { pro.isActive() }
                    adapter.filter(query, selectedCountry, onlyFavorites)
                } else {
                    Toast.makeText(this@MainActivity, R.string.pro_code_wrong, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun refreshProUi() {
        val active = pro.isActive()
        findViewById<View>(R.id.proActiveBox).visibility = if (active) View.VISIBLE else View.GONE
        findViewById<View>(R.id.proOfferBox).visibility = if (active) View.GONE else View.VISIBLE
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
        if (!isStore && config.playlistUrl != null && settings.playlistUrl == null) {
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
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
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

    private fun loadProPlaylist() {
        if (isStore) return
        lifecycleScope.launch {
            val proChannels = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    assets.open("channels-pro.m3u").bufferedReader().use { M3UParser.parse(it.readText(), isPro = true) }
                }.getOrDefault(emptyList())
            }
            if (proChannels.isNotEmpty()) {
                allChannels = allChannels + proChannels
                adapter.replaceSource(allChannels)
                applyFilters()
            }
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
        if (channel.isPro && !pro.isActive()) {
            Toast.makeText(this, R.string.pro_blocked_toast, Toast.LENGTH_LONG).show()
            findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId = R.id.nav_pro
            return
        }
        if (pro.isActive() || channel.isPro) {
            PlayerActivity.start(this, channel.name, channel.url)
        } else {
            AdsManager.maybeShowBeforeOpen(this) {
                PlayerActivity.start(this, channel.name, channel.url)
            }
        }
    }
}
