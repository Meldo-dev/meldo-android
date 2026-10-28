package app.meldeplanner.android.surfaces

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import app.meldeplanner.android.ui.components.sfSymbol
import java.util.concurrent.ConcurrentHashMap

/**
 * The app's symbols (SF Symbol names mapped to Material icons by [sfSymbol]) as
 * bitmaps, for surfaces that cannot draw a Compose `ImageVector`: Glance
 * widgets and notification icons. White by default, so Glance can tint them.
 */
internal object SymbolBitmaps {
    private val cache = ConcurrentHashMap<String, Bitmap>()

    fun get(name: String, sizePx: Int = 96, color: Int = android.graphics.Color.WHITE): Bitmap =
        cache.getOrPut("$name|$sizePx|$color") { render(sfSymbol(name), sizePx, color) }

    private fun render(vector: ImageVector, sizePx: Int, color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(sizePx / vector.viewportWidth, sizePx / vector.viewportHeight)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        draw(canvas, vector.root, paint)
        return bitmap
    }

    private fun draw(canvas: Canvas, group: VectorGroup, paint: Paint) {
        canvas.save()
        canvas.translate(group.translationX + group.pivotX, group.translationY + group.pivotY)
        canvas.rotate(group.rotation)
        canvas.scale(group.scaleX, group.scaleY)
        canvas.translate(-group.pivotX, -group.pivotY)
        for (node in group) {
            when (node) {
                is VectorGroup -> draw(canvas, node, paint)
                is VectorPath -> {
                    val path = node.pathData.toPath().asAndroidPath()
                    path.fillType = if (node.pathFillType == PathFillType.EvenOdd) {
                        android.graphics.Path.FillType.EVEN_ODD
                    } else {
                        android.graphics.Path.FillType.WINDING
                    }
                    paint.alpha = (node.fillAlpha * 255).toInt().coerceIn(0, 255)
                    if (node.fill != null) canvas.drawPath(path, paint)
                }
            }
        }
        canvas.restore()
    }
}
