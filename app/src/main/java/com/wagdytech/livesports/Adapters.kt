package com.wagdytech.livesports

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MatchAdapter : RecyclerView.Adapter<MatchAdapter.VH>() {

    private val items = ArrayList<Match>()

    fun set(list: List<Match>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val home: TextView = v.findViewById(R.id.matchHome)
        val away: TextView = v.findViewById(R.id.matchAway)
        val score: TextView = v.findViewById(R.id.matchScore)
        val meta: TextView = v.findViewById(R.id.matchMeta)
        val live: TextView = v.findViewById(R.id.matchLive)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_match, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val m = items[position]
        holder.home.text = m.home
        holder.away.text = m.away
        holder.score.text = if (m.homeScore != null && m.awayScore != null) "${m.homeScore} : ${m.awayScore}" else "- : -"
        holder.meta.text = listOf(m.league, m.date, m.time?.take(5)).filterNotNull().joinToString(" • ")
        holder.live.visibility = if (m.isLive) View.VISIBLE else View.GONE
    }

    override fun getItemCount() = items.size
}

class NewsAdapter(private val onClick: (NewsItem) -> Unit) : RecyclerView.Adapter<NewsAdapter.VH>() {

    private val items = ArrayList<NewsItem>()

    fun set(list: List<NewsItem>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.newsTitle)
        val date: TextView = v.findViewById(R.id.newsDate)
        val summary: TextView = v.findViewById(R.id.newsSummary)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_news, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val n = items[position]
        holder.title.text = n.title
        holder.date.text = n.date ?: ""
        holder.summary.text = n.summary ?: ""
        holder.itemView.setOnClickListener { onClick(n) }
    }

    override fun getItemCount() = items.size
}
