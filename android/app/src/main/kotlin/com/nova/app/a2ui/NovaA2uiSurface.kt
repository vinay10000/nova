package com.nova.app.a2ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.a2ui.compose.runtime.A2uiComponentState
import androidx.a2ui.model.protocol.A2uiException
import androidx.a2ui.model.processor.A2uiSurfaceModel
import androidx.compose.material3.a2ui.A2uiSurface
import androidx.compose.material3.a2ui.A2uiSurfaceDefaults
import com.nova.app.NovaCard
import com.nova.app.NovaEyebrow
import com.nova.app.NovaMotion
import com.nova.app.NovaRadius
import com.nova.app.NovaSkeleton
import com.nova.app.NovaSpace
import com.nova.app.NovaThinkingIndicator
import com.nova.app.novaErrorText
import com.nova.app.novaFaint

/**
 * §45b — the render surface.
 *
 * A surface is a tree the agent built, handed to us whole. This composable is
 * the only place a surface enters the Nova hierarchy, and it is deliberately
 * thin: every decision about what a surface looks like belongs to the
 * components, not to the host. If this file started branching on component type
 * it would be re-implementing the catalog by hand.
 *
 * The host's only jobs are the three the engine cannot do itself:
 *
 *   1. Provide a loading state that matches the app (a spinner would read as a
 *      dropped frame in a chat stream that is otherwise visibly working).
 *   2. Provide an error boundary that matches the app, so a bad frame reads as
 *      a quiet inline note instead of a crash or a blank hole.
 *   3. Sit inside the message column without imposing its own padding.
 */
@Composable
fun NovaA2uiSurface(
  surface: A2uiSurfaceModel,
  modifier: Modifier = Modifier,
  contentPadding: androidx.compose.foundation.layout.PaddingValues =
    androidx.compose.foundation.layout.PaddingValues(horizontal = NovaSpace.xs),
) {
  A2uiSurface(
    surfaceModel = surface,
    modifier = modifier.fillMaxWidth(),
    loadingContent = { NovaSurfaceLoading() },
    errorContent = { error -> NovaSurfaceError(error) },
    transitionSpec = { NovaSurfaceTransition() },
  )
}

/**
 * Waiting for the first component to become ready.
 *
 * Skeleton cards, not a spinner. The turn is visibly alive — the caret is
 * running, the step line is turning — so a spinner inside a card would be one
 * more thing spinning on a screen that already has plenty going on. The bars
 * hold the shape the real cards will take, so nothing jumps on arrival.
 */
@Composable
private fun NovaSurfaceLoading() {
  Column(verticalArrangement = Arrangement.spacedBy(NovaSpace.md)) {
    repeat(2) {
      NovaCard {
        NovaSkeleton(Modifier.fillMaxWidth(0.35f).height(12.dp))
        Spacer(Modifier.height(NovaSpace.sm))
        NovaSkeleton(Modifier.fillMaxWidth(0.7f).height(22.dp))
        Spacer(Modifier.height(NovaSpace.sm))
        NovaSkeleton(Modifier.fillMaxWidth(0.5f).height(14.dp))
      }
    }
  }
}

/**
 * A surface that failed to build.
 *
 * Shown inline, in a card, in the quiet tone. A generative-UI failure is not a
 * chat failure: the text around it is still true, so the message stays and this
 * becomes a footnote. Swallowing it silently would be worse — the user would
 * think the agent simply had nothing to show.
 */
@Composable
private fun NovaSurfaceError(error: A2uiException) {
  NovaCard {
    NovaEyebrow("Preview unavailable", novaFaint())
    Spacer(Modifier.height(NovaSpace.xs))
    Text(
      novaErrorText(error.message),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/**
 * How a surface changes state: loading to content, or content to new content.
 *
 * A cross-fade, not a slide. A slide implies the surface is a screen being
 * navigated to, and in a chat transcript it is a paragraph of the answer being
 * rewritten — the reading position must not move. Kept at [NovaMotion.Standard]
 * and on the app's own curve so generated UI never moves at a different speed
 * from the rest of Nova.
 */
private fun AnimatedContentTransitionScope<A2uiComponentState>.NovaSurfaceTransition(): ContentTransform =
  ContentTransform(
    targetContentEnter = fadeIn(tween(NovaMotion.Standard, easing = NovaMotion.Ease)),
    initialContentExit = fadeOut(tween(NovaMotion.Quick, easing = NovaMotion.Ease)),
    sizeTransform = SizeTransform(clip = false),
  )

/**
 * A live thinking line for a surface whose engine is still pumping.
 *
 * Not used by the surface itself — the engine decides when it is loading. This
 * exists for the chat column, which shows this while a turn has sent A2UI
 * frames but the first surface has not resolved yet, so the turn is never a
 * blank gap.
 */
@Composable
fun NovaA2uiPending(modifier: Modifier = Modifier) {
  Box(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
    NovaThinkingIndicator()
  }
}

/** Radius lock re-exported for the surface chrome, so nothing here invents one. */
internal val A2uiSurfaceRadius = NovaRadius.md
