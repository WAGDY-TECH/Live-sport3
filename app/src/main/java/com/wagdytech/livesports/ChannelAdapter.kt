package com.wagdytech.livesports

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class ChannelAdapter(
    private var channels: List<Channel>,
    private val favorites: FavoritesStore,
    private val onPlay: (Channel) -> Unit,
    private val onToggleFavorite: (Channel) -> Unit
) : RecyclerView.Adapter<ChannelAdapter.ViewHolder>() {

    private var full: List<Channel> = channels

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val logo: ImageView = view.findViewById(R.id.channelLogo)
        val name: TextView = view.findViewById(R.id.channelName)
        val meta: TextView = view.findViewById(R.id.channelMeta)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_channel, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val channel = channels[position]
        holder.name.text = channel.name
        val flag = channel.country ?: ""
        holder.meta.text = listOf(channel.group, flag).filter { it.isNotBlank() }.joinToString(" • ")

        if (channel.logo != null) {
            Glide.with(holder.itemView.context).load(channel.logo)
                .placeholder(R.drawable.logo_placeholder)
                .error(R.drawable.logo_placeholder)
                .into(holder.logo)
        } else {
            holder.logo.setImageResource(R.drawable.logo_placeholder)
        }

        holder.itemView.setOnClickListener { onPlay(channel) }
        holder.itemView.setOnLongClickListener {
            onToggleFavorite(channel)
            true
        }
    }

    override fun getItemCount() = channels.size

    fun filter(query: String, country: String?, onlyFavorites: Boolean): List<Channel> {
        val q = query.trim().lowercase()
        val result = full.filter { ch ->
            (q.isEmpty() || ch.name.lowercase().contains(q)) &&
                (country == null || ch.country == country) &&
                (!onlyFavorites || favorites.isFavorite(ch.url))
        }
        update(result)
        return result
    }

    fun replaceSource(list: List<Channel>) {
        full = list
        update(full)
    }

    private fun update(newList: List<Channel>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = channels.size
            override fun getNewListSize() = newList.size
            override fun areItemsTheSame(o: Int, n: Int) = channels[o].url == newList[n].url
            override fun areContentsTheSame(o: Int, n: Int) = channels[o] == newList[n]
        })
        channels = newList
        diff.dispatchUpdatesTo(this)
    }
}
