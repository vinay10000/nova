package com.nova.app.a2ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nova.app.NovaSpace

/**
 * §45b — the chat-side surface row.
 *
 * A turn names its surfaces by id; the models themselves are engine state that
 * changes under us. This is the join. It reads the live surface list once and
 * renders the ones this turn owns, which means a card that fills in as the agent
 * streams updates the already-mounted transcript rather than re-entering it.
 *
 * The id set is matched on every recomposition rather than captured, so a surface
 * that arrives after its first frame still lands in the right turn.
 */
@Composable
fun A2uiSurfaceRow(
  surfaceIds: List<String>,
  modifier: Modifier = Modifier,
  controller: NovaA2uiController,
) {
  val surfaces by controller.activeSurfaces.collectAsStateWithLifecycle()
  if (surfaceIds.isEmpty()) return
  val byId = surfaces.associateBy { it.id }
  Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(NovaSpace.md)) {
    surfaceIds.forEach { id ->
      // A surface that has already been dismissed is simply not here any more.
      // That is the whole teardown story from the transcript's point of view —
      // no tombstone, no placeholder.
      byId[id]?.let { surface ->
        NovaA2uiSurface(surface)
      }
    }
  }
}
