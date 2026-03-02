package eu.darken.capod.common.debug.recording.ui

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import eu.darken.capod.databinding.DebugRecorderLogfileItemBinding
import java.io.File

class LogFileAdapter : ListAdapter<LogFileAdapter.Item, LogFileAdapter.VH>(DIFF) {

    data class Item(
        val file: File,
        val size: Long,
    )

    class VH(val binding: DebugRecorderLogfileItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = DebugRecorderLogfileItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.binding.apply {
            fileName.text = item.file.name
            filePath.text = item.file.path
            fileSize.text = Formatter.formatShortFileSize(root.context, item.size)
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Item>() {
            override fun areItemsTheSame(oldItem: Item, newItem: Item) = oldItem.file == newItem.file
            override fun areContentsTheSame(oldItem: Item, newItem: Item) = oldItem == newItem
        }
    }
}
