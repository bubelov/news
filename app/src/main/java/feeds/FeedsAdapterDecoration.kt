package feeds

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class FeedsAdapterDecoration(private val gapInPixels: Int) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val adapter = parent.adapter

        if (adapter == null || adapter.itemCount == 0) {
            super.getItemOffsets(outRect, view, parent, state)
            return
        }

        val position = parent.getChildLayoutPosition(view)

        val left = 0
        val top = if (position == 0) gapInPixels else 0
        val right = 0
        val bottom = if (position == adapter.itemCount - 1) gapInPixels else 0

        outRect.set(left, top, right, bottom)
    }
}