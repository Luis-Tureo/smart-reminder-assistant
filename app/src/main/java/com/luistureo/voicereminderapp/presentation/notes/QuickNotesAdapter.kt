package com.luistureo.voicereminderapp.presentation.notes

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.domain.notes.model.QuickNote
import com.luistureo.voicereminderapp.domain.notes.model.QuickNoteColorTag
import java.text.DateFormat
import java.util.Date

class QuickNotesAdapter(
    private val onOpen: (QuickNote) -> Unit,
    private val onPin: (QuickNote) -> Unit,
    private val onArchive: (QuickNote) -> Unit,
    private val onDelete: (QuickNote) -> Unit
) : ListAdapter<QuickNote, QuickNotesAdapter.NoteViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_quick_note, parent, false)
        return NoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: MaterialCardView = itemView.findViewById(R.id.cardQuickNoteItem)
        private val title: TextView = itemView.findViewById(R.id.tvQuickNoteTitle)
        private val preview: TextView = itemView.findViewById(R.id.tvQuickNotePreview)
        private val pinned: TextView = itemView.findViewById(R.id.tvQuickNotePinned)
        private val updated: TextView = itemView.findViewById(R.id.tvQuickNoteUpdated)
        private val colorMarker: View = itemView.findViewById(R.id.viewQuickNoteColor)
        private val colorLabel: TextView = itemView.findViewById(R.id.tvQuickNoteColorLabel)
        private val pinButton: MaterialButton = itemView.findViewById(R.id.btnQuickNotePin)
        private val archiveButton: MaterialButton = itemView.findViewById(R.id.btnQuickNoteArchive)
        private val deleteButton: MaterialButton = itemView.findViewById(R.id.btnQuickNoteDelete)

        fun bind(note: QuickNote) {
            val context = itemView.context
            title.isVisible = !note.title.isNullOrBlank()
            title.text = note.title.orEmpty()
            preview.isVisible = note.content.isNotBlank()
            preview.text = note.content
            pinned.isVisible = note.isPinned
            updated.text = context.getString(
                R.string.quick_notes_updated,
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(note.updatedAt))
            )

            val color = note.colorTag
            colorMarker.isVisible = color != null
            colorLabel.isVisible = color != null
            if (color != null) {
                colorMarker.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(context, color.colorRes())
                )
                colorLabel.setText(color.labelRes())
            }

            pinButton.setText(
                if (note.isPinned) R.string.quick_notes_unpin else R.string.quick_notes_pin
            )
            archiveButton.setText(
                if (note.isArchived) R.string.quick_notes_restore else R.string.quick_notes_archive
            )
            card.contentDescription = context.getString(
                R.string.quick_notes_card_description,
                note.title?.takeIf(String::isNotBlank)
                    ?: context.getString(R.string.quick_notes_untitled),
                if (note.isPinned) context.getString(R.string.quick_notes_pinned) else ""
            )

            card.setOnClickListener { onOpen(note) }
            pinButton.setOnClickListener { onPin(note) }
            archiveButton.setOnClickListener { onArchive(note) }
            deleteButton.setOnClickListener { onDelete(note) }
        }

        private fun QuickNoteColorTag.colorRes(): Int = when (this) {
            QuickNoteColorTag.YELLOW -> R.color.quick_note_yellow
            QuickNoteColorTag.BLUE -> R.color.quick_note_blue
            QuickNoteColorTag.GREEN -> R.color.quick_note_green
            QuickNoteColorTag.PINK -> R.color.quick_note_pink
        }

        private fun QuickNoteColorTag.labelRes(): Int = when (this) {
            QuickNoteColorTag.YELLOW -> R.string.quick_notes_color_yellow
            QuickNoteColorTag.BLUE -> R.string.quick_notes_color_blue
            QuickNoteColorTag.GREEN -> R.string.quick_notes_color_green
            QuickNoteColorTag.PINK -> R.string.quick_notes_color_pink
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<QuickNote>() {
        override fun areItemsTheSame(oldItem: QuickNote, newItem: QuickNote): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: QuickNote, newItem: QuickNote): Boolean =
            oldItem == newItem
    }
}
