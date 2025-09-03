package com.example.camera_x

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class PhotosAdapter(private val uris: List<Uri>) :
    RecyclerView.Adapter<PhotosAdapter.PhotoViewHolder>() {

    var onItemClick: ((Uri) -> Unit)? = null

    class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imageView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        Glide.with(holder.itemView.context)
            .load(uris[position])
            .into(holder.imageView)

        holder.itemView.setOnClickListener {
            onItemClick?.invoke(uris[position])
        }
    }

    override fun getItemCount() = uris.size
}