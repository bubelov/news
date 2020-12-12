package entries

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class EntriesAdapterDecoration(private val gapInPixels: Int) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val position = parent.getChildLayoutPosition(view)

        val left = gapInPixels
        val top = if (position == 0) gapInPixels else 0
        val right = gapInPixels
        val bottom = gapInPixels

        outRect.set(left, top, right, bottom)
    }
}