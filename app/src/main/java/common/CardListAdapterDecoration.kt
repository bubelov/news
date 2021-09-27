package common

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class CardListAdapterDecoration(private val gapInPixels: Int) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        super.getItemOffsets(outRect, view, parent, state);
        outRect.set(gapInPixels, gapInPixels, gapInPixels, gapInPixels)
    }
}