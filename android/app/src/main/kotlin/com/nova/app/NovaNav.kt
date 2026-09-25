package com.nova.app

import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.*
import com.nova.app.data.SessionToken
import com.nova.app.data.createNovaApi
import kotlin.math.abs

/**
 * App shell. §5 top-level destinations live in a floating pill bar over the
 * current screen. Screens read [LocalBottomChrome] for bottom clearance.
 *
 * Navigation rules kept here so screens never touch the controller:
 *   - Tab tap  : single instance, per-tab state saved and restored.
 *   - Reselect : unwinds that tab to its root.
 *   - Hold-drag: press and slide across the bar to land on a tab live.
 *   - Back     : on any tab other than Chat, goes to Chat before exiting.
 *   - IME open : the bar slides away so the composer owns the keyboard inset.
 */
private data class NovaTab(val route: String, val label: String, val icon: ImageVector)

/** Routes that are children of a tab: the bar stays as the way out. */
private val childRoutes = mapOf("allchats" to "chat", "connections" to "settings")

internal fun shouldHandleTabBack(route: String?, tabRoutes: Set<String>, imeVisible: Boolean): Boolean =
  !imeVisible && route != null && route != "chat" && route in tabRoutes

internal fun shouldShowBottomBar(imeVisible: Boolean, drawerOpen: Boolean): Boolean = !imeVisible && !drawerOpen

/** Floating pill geometry — screens reserve this via LocalBottomChrome. */
private val TabBarHeight = 42.dp
private val TabBarMargin = 12.dp
/** Fixed slot width — keeps a ~48dp hit area even though the pill is short. */
private val TabSlotWidth = 48.dp
/** Gap between slots. Zero: icons cluster instead of spreading across the screen. */
private val TabSlotGap = 0.dp
private val TabBarHPad = 4.dp
/** Visual height of the sliding selection wash — inset so the pill rim reads. */
private val TabIndicatorHeight = 34.dp

@Composable
fun NovaNav(session: SessionToken, onPreferencesChanged: () -> Unit = {}) {
  val nav = rememberNavController()
  val api = remember { createNovaApi(session) }
  // One ChatViewModel for the whole session, so All Chats can open a thread
  // into the same chat screen state.
  val chatVm: ChatViewModel = viewModel()
  var authenticated by remember { mutableStateOf(session.get() != null) }
  var agentBuilderSeed by remember { mutableStateOf<String?>(null) }
  var drawerOpen by remember { mutableStateOf(false) }
  if (!authenticated) {
    // Logged-out chrome is zero: no bar, no clearance. Status bar still cleared.
    CompositionLocalProvider(LocalBottomChrome provides 0.dp) {
      Box(Modifier.fillMaxSize().statusBarsPadding()) {
        LoginScreen(api) { token -> session.save(token); authenticated = true }
      }
    }
    return
  }

  // §38: OAuth deep link return (nova://connections/{provider}/{status}) —
  // land on Connections so the user sees the result instead of the chat.
  val oauthPending = OAuthDeepLink.pending.value
  LaunchedEffect(oauthPending) {
    val raw = oauthPending ?: return@LaunchedEffect
    OAuthDeepLink.pending.value = null
    val uri = Uri.parse(raw)
    if (uri.pathSegments.getOrNull(1) == "error") {
      OAuthDeepLink.notice.value = uri.getQueryParameter("error") ?: "unknown_error"
    }
    nav.navigate("connections") {
      popUpTo("chat") { saveState = true }
      launchSingleTop = true
    }
  }

  val tabs = remember {
    listOf(
      NovaTab("chat", "Chat", Icons.AutoMirrored.Filled.Chat),
      NovaTab("agents", "Agents", Icons.Default.AccountTree),
      NovaTab("activity", "Activity", Icons.Default.History),
      NovaTab("settings", "Settings", Icons.Default.Settings),
    )
  }
  val backStackEntry by nav.currentBackStackEntryAsState()
  val route = backStackEntry?.destination?.route
  val tabRoutes = tabs.mapTo(mutableSetOf()) { it.route }
  val selectedTab = when {
    route == null -> "chat"
    route in tabRoutes -> route
    else -> childRoutes[route] ?: "chat"
  }
  // Keyboard visible => the bar yields. Chat composer and search fields then
  // own the whole bottom inset instead of stacking two paddings.
  val density = LocalDensity.current
  val imeVisible = WindowInsets.ime.getBottom(density) > 0
  val showBar = shouldShowBottomBar(imeVisible, drawerOpen)

  // Clearance for scroll content under the floating bar (bar + margin + nav inset).
  val navBars = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  val bottomChrome = if (showBar) TabBarHeight + TabBarMargin + navBars else 0.dp

  fun goToTab(target: String) {
    if (target == selectedTab) {
      // Reselect: unwind the tab to its root instead of stacking duplicates.
      nav.popBackStack(target, inclusive = false)
      return
    }
    nav.navigate(target) {
      // Route-based popUpTo keeps one instance per tab on every navigation
      // version (no findStartDestination dependency).
      popUpTo("chat") { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  // Back from a secondary tab lands on Chat first — the standard Android
  // bottom-nav contract, and cheaper than killing the app.
  BackHandler(enabled = shouldHandleTabBack(route, tabRoutes, imeVisible)) {
    nav.navigate("chat") {
      popUpTo("chat") { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  CompositionLocalProvider(LocalBottomChrome provides bottomChrome) {
    Box(Modifier.fillMaxSize()) {
      // Screens content. The pill bar sits above it.
      Box(
        Modifier
          .fillMaxSize()
          .statusBarsPadding(),
      ) {
        NavHost(nav, startDestination = "chat") {
          tabComposable("chat") {
            ChatScreen(
              api, session,
              onSettingsClick = { goToTab("settings") },
              onConnectionsClick = { nav.navigate("connections") { launchSingleTop = true } },
               onAgentsClick = { seed ->
                 agentBuilderSeed = seed
                 goToTab("agents")
               },

              onActivityClick = { goToTab("activity") },
              onAllChatsClick = { nav.navigate("allchats") { launchSingleTop = true } },
              onDrawerOpenChanged = { drawerOpen = it },
              vm = chatVm,
            )
          }
          childComposable("allchats") {
            AllChatsScreen(
              api,
              onOpen = { id, history ->
                chatVm.openConversation(id, history)
                nav.popBackStack()
              },
              onBack = { nav.popBackStack() },
            )
          }
           tabComposable("agents") {
             AgentsScreen(
               api,
               initialPrompt = agentBuilderSeed,
               onPromptConsumed = { agentBuilderSeed = null },
             )
           }

          tabComposable("activity") { ActivityScreen(api) }
          childComposable("connections") { ConnectionsScreen(api, session, onBack = { nav.popBackStack() }) }
          tabComposable("settings") {
            SettingsScreen(
              session,
              onLogout = { session.clear(); authenticated = false },
              onPreferencesChanged = onPreferencesChanged,
              onConnectionsClick = { nav.navigate("connections") { launchSingleTop = true } },
            )
          }
        }
      }

      AnimatedVisibility(
        visible = showBar,
        enter = slideInVertically(spring(dampingRatio = 0.92f, stiffness = Spring.StiffnessMediumLow)) { it } +
          fadeIn(tween(NovaMotion.Standard, easing = NovaMotion.Ease)),
        exit = slideOutVertically(tween(NovaMotion.Quick, easing = NovaMotion.Ease)) { it } +
          fadeOut(tween(NovaMotion.Quick, easing = NovaMotion.Ease)),
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(horizontal = NovaSpace.lg)
          .padding(bottom = TabBarMargin + navBars),
      ) {
        NovaBottomBar(
          tabs = tabs,
          selectedRoute = selectedTab,
          onSelect = ::goToTab,
        )
      }
    }
  }
}

/** Tab destination: siblings cross-fade. A slide here would imply a hierarchy. */
private fun NavGraphBuilder.tabComposable(route: String, content: @Composable () -> Unit) {
  composable(
    route,
    enterTransition = { fadeIn(tween(NovaMotion.Standard, easing = NovaMotion.Ease)) },
    exitTransition = { fadeOut(tween(NovaMotion.Quick, easing = NovaMotion.Ease)) },
    popEnterTransition = { fadeIn(tween(NovaMotion.Standard, easing = NovaMotion.Ease)) },
    popExitTransition = { fadeOut(tween(NovaMotion.Quick, easing = NovaMotion.Ease)) },
  ) { content() }
}

/** Child destination: slides in from the edge, so "one level deeper" reads. */
private fun NavGraphBuilder.childComposable(route: String, content: @Composable () -> Unit) {
  composable(
    route,
    enterTransition = {
      slideInHorizontally(tween(NovaMotion.Standard, easing = NovaMotion.Ease)) { it / 4 } +
        fadeIn(tween(NovaMotion.Standard, easing = NovaMotion.Ease))
    },
    exitTransition = { fadeOut(tween(NovaMotion.Quick, easing = NovaMotion.Ease)) },
    popEnterTransition = { fadeIn(tween(NovaMotion.Standard, easing = NovaMotion.Ease)) },
    popExitTransition = {
      slideOutHorizontally(tween(NovaMotion.Standard, easing = NovaMotion.Ease)) { it / 4 } +
        fadeOut(tween(NovaMotion.Standard, easing = NovaMotion.Ease))
    },
  ) { content() }
}

/**
 * Floating pill tab bar. Icon only — active state is a tonal wash that
 * springs between slots (never a dot). Solid spec fill so scroll content
 * cannot read through it on any device.
 *
 * Compact by design: a 42dp-tall pill of fixed 48dp slots packed edge to
 * edge (no spread weight), so the icons cluster instead of stretching
 * across the screen. Input: tap a slot, or press-and-slide across slots
 * to land on a tab live (one selection tick per boundary crossed).
 */
@Composable
private fun NovaBottomBar(
  tabs: List<NovaTab>,
  selectedRoute: String,
  onSelect: (String) -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  val shape = RoundedCornerShape(NovaRadius.xl)
  val view = LocalView.current
  val density = LocalDensity.current
  val tabCount = tabs.size

  // Critically damped springs: glide without bounce (momentum-free chrome).
  val settle = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
  val settleDp = spring<Dp>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
  val settleInk = spring<Color>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
  val pressSpec = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)

  val selIndex = tabs.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)
  val washX by animateDpAsState(
    TabBarHPad + (TabSlotWidth + TabSlotGap) * selIndex,
    settleDp,
    label = "tabWashX",
  )
  val washW by animateDpAsState(TabSlotWidth, settleDp, label = "tabWashW")

  // Slot under the finger for press feedback (tap and drag both set this).
  var pressedIndex by remember { mutableStateOf(-1) }
  val onSelectLatest = rememberUpdatedState(onSelect)

  fun tick() {
    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
  }

  Surface(
    shape = shape,
    color = novaGlassFill(),
    border = BorderStroke(1.dp, novaGlassEdge()),
  ) {
    Box(Modifier.height(TabBarHeight)) {
      // Sliding selection wash — one animated layer, so switching tabs
      // glides instead of popping two static fills on and off.
      Box(
        Modifier
          .align(Alignment.CenterStart)
          .offset(x = washX)
          .width(washW)
          .height(TabIndicatorHeight)
          .clip(RoundedCornerShape(NovaRadius.lg))
          .background(scheme.primary.copy(alpha = 0.12f)),
      )

      // Wraps content width (no fillMaxWidth / weight) so the pill hugs
      // the icons instead of spanning the screen.
      Row(
        Modifier
          .height(TabBarHeight)
          .padding(horizontal = TabBarHPad)
          // Hold + slide: past touch-slop on X the gesture owns the pointer,
          // selects live under the finger, and ticks once per slot crossed.
          // Initial pass consumes first so the slot's clickable cancels and
          // a drag never fires a phantom tap on release.
          .pointerInput(tabCount) {
            fun indexAt(x: Float): Int {
              if (tabCount == 0) return -1
              val slotPx = with(density) { TabSlotWidth.toPx() }
              val gapPx = with(density) { TabSlotGap.toPx() }
              val stride = slotPx + gapPx
              if (stride <= 0f) return -1
              val clamped = x.coerceIn(0f, size.width.toFloat())
              return (clamped / stride).toInt().coerceIn(0, tabCount - 1)
            }

            awaitEachGesture {
              val down = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.pressed }
                ?: return@awaitEachGesture
              pressedIndex = indexAt(down.position.x)
              var dragging = false
              var lastIndex = pressedIndex
              while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial)
                  .changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if (!dragging) {
                  val dx = abs(change.position.x - down.position.x)
                  val dy = abs(change.position.y - down.position.y)
                  if (dx > viewConfiguration.touchSlop && dx > dy) dragging = true
                }
                if (dragging) {
                  change.consume()
                  val idx = indexAt(change.position.x)
                  pressedIndex = idx
                  if (idx in tabs.indices && idx != lastIndex) {
                    lastIndex = idx
                    tick()
                    onSelectLatest.value(tabs[idx].route)
                  }
                }
              }
              pressedIndex = -1
            }
          },
        horizontalArrangement = Arrangement.spacedBy(TabSlotGap),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        tabs.forEachIndexed { index, tab ->
          val isSelected = tab.route == selectedRoute
          val ink by animateColorAsState(
            if (isSelected) scheme.primary else scheme.onSurfaceVariant,
            settleInk,
            label = "tabInk",
          )
          val iconScale by animateFloatAsState(if (isSelected) 1.06f else 1f, settle, label = "tabIcon")
          val pressScale by animateFloatAsState(
            if (pressedIndex == index) 0.97f else 1f,
            pressSpec,
            label = "tabPress",
          )
          Surface(
            onClick = {
              tick()
              onSelect(tab.route)
            },
            shape = RoundedCornerShape(NovaRadius.lg),
            color = Color.Transparent,
            modifier = Modifier
              .width(TabSlotWidth)
              .fillMaxHeight()
              .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
              }
              .semantics {
                role = Role.Tab
                this.selected = isSelected
                contentDescription = tab.label
              },
          ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Icon(
                tab.icon,
                contentDescription = null,
                tint = ink,
                modifier = Modifier
                  .size(22.dp)
                  .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                  },
              )
            }
          }
        }
      }
    }
  }
}
