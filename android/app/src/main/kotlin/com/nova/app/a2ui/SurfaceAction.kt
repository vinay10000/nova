package com.nova.app.a2ui

import androidx.a2ui.model.protocol.A2uiClientEventMessage

/**
 * §45b — a user action from a generated surface, reduced to what chat needs.
 *
 * The engine hands back a protocol message with a surface id, a component id, a
 * timestamp and a context map. None of that matters to the chat screen. What it
 * needs is one thing: a line of text the user can see and, if they want, edit
 * before it is sent. So the action is translated here, once, and nothing above
 * this layer ever touches the protocol types.
 */
data class SurfaceAction(
  /** Action name from the catalog, e.g. `add_packing_list`. */
  val name: String,
  /** The button's own label, e.g. "Add packing list". */
  val label: String,
  /** The surface it came from, so a stale one can be ignored. */
  val surfaceId: String,
)

/**
 * Read a client event into a [SurfaceAction], or null if it is not one.
 *
 * The engine lowers an `A2uiEventAction` into an `A2uiClientEventMessage`, which
 * renames `eventName` to `type` and copies the event's context across whole. So
 * the action name is [A2uiClientEventMessage.type] and everything the catalog
 * author put in the event context arrives intact.
 *
 * The label comes from the context, not from the name. The catalog already knew
 * the button's words when it rendered it and put them in the action context
 * precisely so they could come back; deriving a label from the name would be a
 * guess, and a wrong one would put the wrong sentence in the composer.
 */
fun A2uiClientEventMessage.toSurfaceAction(): SurfaceAction? {
  val name = type
  if (name.isBlank()) return null
  val label = (context["label"] as? String)?.takeIf { it.isNotBlank() }
    ?: name.replace('_', ' ').replaceFirstChar { it.uppercase() }
  return SurfaceAction(name = name, label = label, surfaceId = surfaceId)
}
