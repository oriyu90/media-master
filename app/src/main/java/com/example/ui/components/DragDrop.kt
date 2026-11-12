package com.example.ui.components

import android.content.ClipData
import android.content.ClipDescription
import android.view.View
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * v2.1.0 drag & drop plumbing shared by the Explorer list/grid, the desktop
 * sidebar and the breadcrumb bar.
 *
 * Design notes:
 *  - The payload is a plain-text [ClipData] holding one absolute path per
 *    item (multiple items = multi-select drag). Plain text keeps the payload
 *    readable by other apps (Explorer → Desktop, Files → Media Master) and
 *    needs no URI permission grants.
 *  - [View.DRAG_FLAG_GLOBAL] is only added for mouse drags: on touch a global
 *    drag is handed to the system and could leak the path to another app's
 *    drop target, while an in-app move is all we need.
 *  - The caller filters out the synthetic ".." entry before building paths.
 */
object FileDragDrop {

    const val LABEL = "Media Master paths"

    /** Builds the drag payload for [paths] (absolute paths only). */
    fun clipData(paths: List<String>, global: Boolean): ClipData? {
        val usable = paths.filter { it.startsWith("/") }.distinct()
        if (usable.isEmpty()) return null
        val clip = ClipData(LABEL, arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN), ClipData.Item(usable.first()))
        usable.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        return clip
    }

    /** Parses a drop event into absolute paths; empty when it is not ours. */
    fun pathsOf(event: DragAndDropEvent): List<String> {
        val clip = runCatching { event.toAndroidDragEvent().clipData }.getOrNull() ?: return emptyList()
        return (0 until clip.itemCount).mapNotNull { index ->
            clip.getItemAt(index)?.text?.toString()?.takeIf { it.startsWith("/") }
        }.distinct()
    }

    /**
     * True when the payload looks like filesystem paths we can move. Plain
     * text is accepted (so another file manager can drop into Media Master),
     * but the source must actually carry a path — [pathsOf] enforces that.
     */
    fun accepts(event: DragAndDropEvent): Boolean =
        ClipDescription.MIMETYPE_TEXT_PLAIN in event.mimeTypes()

    /**
     * Set while a drag started from Media Master is in flight. Row clicks are
     * ignored during that window: releasing a drag over empty space would
     * otherwise reach `combinedClickable.onClick` and open the file or enter
     * the folder the user just tried to move.
     */
    @Volatile
    var dragInProgress: Boolean = false
        private set

    internal fun beginDrag() { dragInProgress = true }

    internal fun endDrag() { dragInProgress = false }

    /** Set by the source's pointer observer; read by transferData. */
    @Volatile
    internal var mouseDrag = false
}

/**
 * Source modifier: starts a drag after a long-press (touch) or a small
 * movement (mouse). [paths] is evaluated at drag start so the current
 * selection is captured then, not at composition time.
 *
 * The tiny translucent disc is drawn under the pointer: without any
 * decoration the system shows nothing on touch, and users could not tell
 * a drag from a long-press menu.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.dragSource(
    enabled: Boolean,
    key: Any?,
    paths: () -> List<String>,
): Modifier = if (!enabled) this else this
    .dragAndDropSource(
        drawDragDecoration = decoration@{ drawDragDisc() },
        transferData = {
            val global = FileDragDrop.mouseDrag
            FileDragDrop.clipData(paths(), global)?.let { clip ->
                FileDragDrop.beginDrag()
                DragAndDropTransferData(
                    clipData = clip,
                    flags = if (global) View.DRAG_FLAG_GLOBAL else 0,
                )
            }
        },
    )
    .pointerInput(key) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.any { it.pressed }) {
                    FileDragDrop.mouseDrag = event.changes.any { it.type == PointerType.Mouse }
                }
            }
        }
    }

/**
 * Drag decoration: a small translucent disc at the pointer. Without any
 * decoration the system shows nothing on touch, so users could not tell a
 * drag from a long-press menu.
 */
internal fun DrawScope.drawDragDisc() {
    drawCircle(
        color = Color.Black.copy(alpha = 0.35f),
        radius = 16.dp.toPx(),
        center = Offset(size.width / 2f, size.height / 2f),
    )
}



/**
 * Target modifier: calls [onHover] while a compatible drag enters/leaves
 * (for highlight styling) and [onDrop] with the payload paths. [onDrop]
 * returns true when it consumed the drop (false shows the system's
 * "not allowed" cursor).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.dropTarget(
    enabled: Boolean,
    onHover: (Boolean) -> Unit = {},
    onDrop: (List<String>) -> Boolean,
): Modifier {
    if (!enabled) return this
    var hovered by remember { mutableStateOf(false) }
    val target = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                hovered = true
                onHover(true)
            }

            override fun onExited(event: DragAndDropEvent) {
                hovered = false
                onHover(false)
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                hovered = false
                onHover(false)
                val paths = FileDragDrop.pathsOf(event)
                if (paths.isEmpty()) return false
                return runCatching { onDrop(paths) }.getOrDefault(false)
            }

            override fun onEnded(event: DragAndDropEvent) {
                hovered = false
                onHover(false)
                FileDragDrop.endDrag()
            }
        }
    }
    // Read so the composable re-runs when hover changes (callers that
    // style from their own state still work: onHover is the contract).
    @Suppress("UNUSED_EXPRESSION") hovered
    return this.dragAndDropTarget(shouldStartDragAndDrop = { FileDragDrop.accepts(it) }, target = target)
}
