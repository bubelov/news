package entries

import android.content.Context
import android.graphics.Canvas
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.news.R

abstract class SwipeHelper(
    private val context: Context,
    @DrawableRes
    swipeLeftIconResId: Int,
    @DrawableRes
    swipeRightIconResId: Int,
) : ItemTouchHelper.SimpleCallback(
    0,
    ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
) {

    private val swipeLeftIcon = ContextCompat.getDrawable(context, swipeLeftIconResId)!!
    private val swipeRightIcon = ContextCompat.getDrawable(context, swipeRightIconResId)!!

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ): Boolean {
        return false
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean,
    ) {
        val itemView = viewHolder.itemView
        val itemHeight = itemView.bottom - itemView.top

        val iconMargin = context.resources.getDimensionPixelSize(R.dimen.dp_8) * 2

        if (dX < 0) {
            val top = itemView.top + (itemHeight - swipeLeftIcon.intrinsicHeight) / 2

            swipeLeftIcon.setBounds(
                itemView.right - iconMargin - swipeLeftIcon.intrinsicWidth,
                top,
                itemView.right - iconMargin,
                top + swipeLeftIcon.intrinsicHeight,
            )

            swipeLeftIcon.draw(c)
        } else if (dX > 0) {
            val iconLeft = itemView.left + iconMargin
            val iconTop = itemView.top + (itemHeight - swipeRightIcon.intrinsicHeight) / 2
            val iconRight = itemView.left + iconMargin + swipeRightIcon.intrinsicWidth
            val iconBottom = iconTop + swipeRightIcon.intrinsicHeight

            swipeRightIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
            swipeRightIcon.draw(c)
        }

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }
}