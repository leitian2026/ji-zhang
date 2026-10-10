@file:OptIn(ExperimentalFoundationApi::class)

package com.shopledger.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.util.lerp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.math.BigDecimal

/** 输入法弹出/收起时，累计条与滚动的时长：越长，每一帧走的距离越小 */
private const val TypingMs = 380

/** 接近匀速、只有两端稍微柔一点：中间每一帧走的距离几乎一样，结尾不会明显变慢、不会有"停一下"的感觉 */
private val TypingEasing = androidx.compose.animation.core.CubicBezierEasing(0.25f, 0.1f, 0.75f, 0.9f)

// 气泡里表格的列宽比例：名称、单价、数量、小计
private const val W_NAME = 1.4f
private const val W_PRICE = 0.9f
private const val W_QTY = 1.25f
private const val W_SUB = 1.15f

/** "待称"的颜色：和蓝色的金额区分开，一眼看出还有没定的 */
private val PendingColor = Color(0xFFD9822B)

/** 白色卡片（外框）离屏幕左右边缘的距离，和卡片离输入框的距离用同一个值；窄屏小一点 */
@Composable
private fun edgeGap(): androidx.compose.ui.unit.Dp =
    if (LocalConfiguration.current.screenWidthDp < 500) 32.dp else 48.dp

// 平板上气泡不会无限拉宽
private val BubbleMaxWidth = 560.dp

/**
 * 手机、平板通用：
 * - 版面撑满整个屏幕（和聊天软件一样，我的气泡靠右、结算气泡靠左）
 * - 字号随屏幕宽度小幅缩放（基准 360dp，最大约 1.2 倍），平板上字不会显得太小
 * - 系统字体放得很大时封顶，免得表格被撑坏
 */
@Composable
fun ScaleFrame(content: @Composable () -> Unit) {
    val widthDp = LocalConfiguration.current.screenWidthDp
    val d = LocalDensity.current
    val scale = (1f + (minOf(widthDp, 840) - 360) / 360f * 0.35f).coerceIn(0.9f, 1.2f)
    CompositionLocalProvider(
        LocalDensity provides Density(d.density, minOf(d.fontScale, 1.15f) * scale),
        content = content
    )
}

@Composable
fun LedgerScreen(vm: LedgerViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val stale by vm.stale.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var input by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var selectMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var pendingDelete by remember { mutableStateOf<List<Long>?>(null) }
    var showEnd by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var prevSize by remember { mutableIntStateOf(0) }
    var prevCount by remember { mutableIntStateOf(0) }
    var prevEnded by remember { mutableStateOf<Boolean?>(null) }

    // 回到最新这一张：用接近匀速的曲线自己滚（系统自带的 animateScrollToItem 是先快后慢的弹簧）。
    // 翻得很远、不在最新这一张附近时，距离算不准，才交给系统的滚动。
    suspend fun glideToLatest() {
        if (listState.firstVisibleItemIndex == 0) {
            val d0 = listState.firstVisibleItemScrollOffset.toFloat()
            if (d0 > 0f) {
                Animatable(0f).animateTo(1f, tween(durationMillis = 460, easing = TypingEasing)) {
                    val want = d0 * (1f - value)
                    val cur = listState.firstVisibleItemScrollOffset.toFloat()
                    if (cur > want) listState.dispatchRawDelta(want - cur)
                }
            }
        } else {
            listState.animateScrollToItem(0)
        }
    }

    // 有新内容时滚到最底；删除、修改不会乱跳
    // 记一笔、结束、继续记之后，都滚到最底，保证最新的整张表格完整露出来
    val lastEnded = (ui.items.lastOrNull() as? TripCard)?.ended
    LaunchedEffect(ui.items.size, ui.currentCount, lastEnded) {
        val size = ui.items.size
        val endChanged = prevEnded != null && lastEnded != prevEnded
        val grew = size > prevSize || ui.currentCount > prevCount || endChanged
        if (grew && size > 0) {
            if (prevSize == 0) listState.scrollToItem(0)
            else glideToLatest()
        }
        prevSize = size
        prevCount = ui.currentCount
        prevEnded = lastEnded
    }

    // 列表用"从底往上排"（reverseLayout）：最新的一张表格永远贴在底部，
    // 输入法弹出、收起时可视区变矮变高，贴底的内容由系统在同一帧里自动跟着走。
    // 正在改某一行时，由那一行自己滚到可见位置，这里不动。
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val editingNow by rememberUpdatedState(editingId)
    fun scrollToLatest() {
        if (editingNow == null) scope.launch { glideToLatest() }
    }
    // 滚回当前表格、顶端累计条，都直接跟着输入法自己的动画走（用输入法当前的高度算进度），
    // 三件事是同一条时间线、同一个起点，所以是同时开始、同时结束，不会"先滚完、输入法才出来"。
    // 输入法还没开始动的那一小段时间里，页面保持不动，等它一动，整个页面一起动。
    var imePeak by remember { mutableFloatStateOf(with(density) { 260.dp.toPx() }) }
    LaunchedEffect(listState) {
        var last = 0
        var d0 = 0f
        snapshotFlow { imeInsets.getBottom(density) }.collectLatest { b ->
            if (last == 0 && b > 0) {
                // 输入法刚开始弹出：记下离最新这一张还有多远
                val near = listState.firstVisibleItemIndex == 0
                d0 = if (editingNow == null && near) listState.firstVisibleItemScrollOffset.toFloat() else 0f
                if (editingNow == null && !near) launch { listState.animateScrollToItem(0) }
            }
            if (b > last && d0 > 0f) {
                // 输入法升到了几成，这段距离就同步滚掉几成
                val frac = (b / imePeak).coerceIn(0f, 1f)
                val want = d0 * (1f - frac)
                val cur = listState.firstVisibleItemScrollOffset.toFloat()
                if (cur > want) listState.dispatchRawDelta(want - cur)
            }
            // 输入法收回去了：输入框也取消焦点，光标不再闪，输入框边框恢复成没选中的样子
            if (b == 0 && last > 0) focusManager.clearFocus()
            last = b
            if (b > 0) {
                // 输入法不动了（连续 150ms 没变化）：记住它的最终高度，下次进度更准；
                // 如果还差一点没滚到位，补一小段
                delay(150)
                imePeak = maxOf(b.toFloat(), 1f)
                if (d0 > 0f && listState.firstVisibleItemScrollOffset > 0) glideToLatest()
                d0 = 0f
            } else {
                d0 = 0f
            }
        }
    }
    val cards = ui.items.filterIsInstance<TripCard>()
    val validIds = cards.map { it.tripId }.toSet()
    val sel = selected.filter { it in validIds }.toSet()
    val selecting = selectMode
    fun exitSelect() {
        selectMode = false
        selected = emptySet()
    }
    BackHandler(enabled = selecting) { exitSelect() }

    fun toggle(id: Long) {
        editingId = null
        selected = if (id in sel) sel - id else sel + id
    }

    fun submit() {
        when (val r = parseInput(input)) {
            is ParseResult.Err -> error = r.message
            is ParseResult.Ok -> {
                vm.add(r.items)
                input = ""
                error = null
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopBar(
                total = ui.currentTotal,
                entryCount = ui.currentCount,
                selecting = selecting,
                count = sel.size,
                allSelected = cards.isNotEmpty() && sel.size == cards.size,
                onSelectAll = {
                    if (sel.size == cards.size) {
                        selected = emptySet()
                    } else {
                        selected = validIds
                        pendingDelete = validIds.toList()
                    }
                },
                onDelete = { if (sel.isNotEmpty()) pendingDelete = sel.toList() },
                onCancel = { exitSelect() }
            )
        },
        bottomBar = {
            InputBar(
                value = input,
                error = error,
                onChange = {
                    input = it
                    error = null
                },
                onSend = { submit() },
                onInputTouch = {
                    // 输入法已经开着、只是你翻到了上面：点输入框回到最新这一张
                    // （输入法还没开时，由输入法的动画带着一起动，这里不用管）
                    if (imeInsets.getBottom(density) > 0) scrollToLatest()
                },
                onEnd = {
                    if (ui.currentCount == 0) {
                        Toast.makeText(context, "这一趟还没有记录", Toast.LENGTH_SHORT).show()
                    } else {
                        showEnd = true
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                verticalArrangement = Arrangement.Top,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(top = 8.dp, bottom = edgeGap())
            ) {
                if (ui.loaded && ui.items.isEmpty()) {
                    item(key = "empty") {
                        Box(
                            Modifier.fillMaxWidth().padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "在下面输入就会出账\n苹果5　　面条10，2　　5\n米6.5/斤（称完再补数量）",
                                color = Color(0xFFB9C1CE),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                items(ui.items.asReversed(), key = { it.key }) { item ->
                    // 新增 / 删除 / 结束后卡片的出现、消失、挪位都带过渡，不会一下子跳
                    Box(
                        Modifier.animateItem(
                            fadeInSpec = tween(420),
                            placementSpec = tween(460, easing = TypingEasing),
                            fadeOutSpec = tween(320)
                        )
                    ) {
                    when (item) {
                        is HeaderItem -> TripHeader(item.label)
                        is TripCard -> TripCardView(
                            item = item,
                            checked = item.tripId in sel,
                            selecting = selecting,
                            editingId = editingId,
                            onToggle = { toggle(item.tripId) },
                            onLongPress = {
                                editingId = null
                                selectMode = true
                                selected = sel + item.tripId
                            },
                            onRowClick = { e -> if (selecting) toggle(item.tripId) else editingId = e.id },
                            onSave = {
                                vm.update(it)
                                editingId = null
                            },
                            onDeleteEntry = {
                                vm.delete(it)
                                editingId = null
                            },
                            onCancelEdit = { editingId = null },
                            onCopy = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("settlement", item.text))
                                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                            },
                            onReopen = { vm.reopen(item.tripId) }
                        )
                                        }
                }
                }
            }
        }
    }

    pendingDelete?.let { ids ->
        val title = when {
            ids.size == 1 -> "删除这个表格？"
            ids.size == cards.size -> "是否全部删除？"
            else -> "删除选中的 ${ids.size} 个表格？"
        }
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(title) },
            text = {
                Text(
                    if (ids.size == 1) "里面的记录会一起删掉，删了就找不回来。"
                    else "共 ${ids.size} 个表格，里面的记录会一起删掉，删了就找不回来。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteTrips(ids)
                        selected = selected - ids.toSet()
                        editingId = null
                        pendingDelete = null
                        if (selected.isEmpty()) selectMode = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }
        )
    }

    if (showEnd) {
        EndDialog(
            count = ui.currentCount,
            total = ui.currentTotal,
            pending = ui.currentPending,
            onConfirm = {
                vm.end(it)
                showEnd = false
            },
            onDismiss = { showEnd = false }
        )
    }

    stale?.let { last ->
        AlertDialog(
            onDismissRequest = { vm.dismissStale() },
            title = { Text("上一趟还没结束") },
            text = { Text("最后一笔是 ${fmtDate(last)} 记的。要结束并结算吗？") },
            confirmButton = {
                TextButton(onClick = {
                    vm.end("")
                    vm.dismissStale()
                }) { Text("结束并结算") }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissStale() }) { Text("继续记") }
            }
        )
    }
}

@Composable
private fun TopBar(
    total: BigDecimal,
    entryCount: Int,
    selecting: Boolean,
    count: Int,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(BrandStart, BrandEnd)))
            .statusBarsPadding()
    ) {
        // 固定高度：里面的内容用 fillMaxSize，外层必须有确定的高度，不能用 heightIn(min)，否则会撑满整个屏幕
        // 平时只留够放下标题的高度（42dp，标题紧贴状态栏、字不会被裁）；
        // 选择模式里有"全选/删除/取消"按钮，需要 56dp，两者之间平滑过渡
        val barHeight by animateDpAsState(if (selecting) 56.dp else 42.dp, tween(320), label = "barHeight")
        Box(Modifier.fillMaxWidth().height(barHeight), contentAlignment = Alignment.CenterStart) {
            Crossfade(
                targetState = selecting,
                animationSpec = tween(320),
                label = "topBar",
                modifier = Modifier.fillMaxSize()
            ) { sel ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            if (!sel) {
                // 顶栏只放标题；累计金额在每张表格里已经有了，不再重复。
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "超市记账",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (count > 0) "已选 $count 个" else "选择表格",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        Modifier.clickable(onClick = onSelectAll),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TickBox(allSelected, onSelectAll, tint = Color.White, markColor = Brand)
                        Text("全选", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = onDelete, enabled = count > 0) {
                        Text(
                            "删除",
                            color = Color.White.copy(alpha = if (count > 0) 1f else 0.45f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    TextButton(onClick = onCancel) { Text("取消", color = Color.White) }
                }
            }
            }
            }
        }
    }
}

/** 方框：点一下勾选 / 取消 */
@Composable
private fun TickBox(
    checked: Boolean,
    onClick: () -> Unit,
    tint: Color = Color(0xFFB7C2D2),
    markColor: Color = Color.White
) {
    Box(Modifier.clickable(onClick = onClick).padding(6.dp)) {
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (checked) tint else Color.Transparent)
                .border(1.5.dp, tint, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (checked) Text("✓", color = markColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TripHeader(label: String) {
    Box(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 12.sp, color = Muted)
    }
}

@Composable
private fun HLine() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(BubbleLine))
}

@Composable
private fun RowScope.Txt(
    text: String,
    weight: Float,
    align: TextAlign = TextAlign.End,
    bold: Boolean = false,
    color: Color = Color.Unspecified,
    size: TextUnit = 15.sp,
    softWrap: Boolean = true
) {
    Text(
        text = text,
        modifier = Modifier.weight(weight).padding(horizontal = 8.dp, vertical = 11.dp),
        textAlign = align,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        color = color,
        fontSize = size,
        softWrap = softWrap,
        maxLines = if (softWrap) Int.MAX_VALUE else 1
    )
}

@Composable
private fun TableHead() {
    Row(Modifier.fillMaxWidth().background(TableHeadBg)) {
        Txt("名称", W_NAME, TextAlign.Start, size = 12.sp, color = Muted, bold = true)
        Txt("单价", W_PRICE, size = 12.sp, color = Muted, bold = true)
        Txt("数量", W_QTY, size = 12.sp, color = Muted, bold = true)
        Txt("小计", W_SUB, size = 12.sp, color = Muted, bold = true)
    }
}

@Composable
private fun EntryLine(e: Entry, onClick: () -> Unit, onLongClick: () -> Unit) {
    val unnamed = e.name.isBlank()
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Txt(
            if (unnamed) "未命名" else e.name,
            W_NAME,
            TextAlign.Start,
            color = if (unnamed) Muted else Color.Unspecified
        )
        Txt(
            fmtNum(e.unitPrice) + if (e.pending) "/" + e.unit else "",
            W_PRICE,
            softWrap = false
        )
        Txt(fmtQty(e), W_QTY, softWrap = false, color = if (e.pending) PendingColor else Color.Unspecified)
        if (e.pending) {
            Txt("待定", W_SUB, softWrap = false, color = Muted)
        } else {
            Txt(fmtMoney(e.subtotal()), W_SUB, bold = true, color = DeepOrange, softWrap = false)
        }
    }
}

@Composable
private fun RowScope.CellField(
    value: String,
    onChange: (String) -> Unit,
    weight: Float,
    align: TextAlign,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    modifier: Modifier = Modifier,
    onDone: () -> Unit = {}
) {
    Box(Modifier.weight(weight).padding(horizontal = 4.dp, vertical = 6.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 15.sp, color = Ink, textAlign = align),
            cursorBrush = SolidColor(Brand),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(8.dp))
                .border(1.dp, Brand, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 9.dp)
        )
    }
}

/** 表格里的一行变成可以直接改：名称、单价、数量（可带单位，如 2斤、1把；只写单位如"斤"表示待称） */
@Composable
private fun EditRow(e: Entry, onSave: (Entry) -> Unit, onDelete: () -> Unit, onCancel: () -> Unit) {
    var name by remember(e.id) { mutableStateOf(e.name) }
    var price by remember(e.id) { mutableStateOf(fmtNum(e.unitPrice)) }
    // 待称的行：数量格里放单位（如"斤"），称完把它改成 2.3斤 / 2.3 就行
    var qty by remember(e.id) { mutableStateOf(if (e.pending) e.unit else fmtQty(e)) }
    var err by remember(e.id) { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val requester = remember { BringIntoViewRequester() }

    fun save() {
        val p = parseNum(price)
        if (p == null) {
            err = "单价要填数字"
            return
        }
        val qi = parseQtyInput(qty)
        // 称完没写单位时，沿用这一行原来的单位
        val unit = qi.unit.ifBlank { if (e.pending) e.unit else "" }
        val n = qi.number
        if (n == null) {
            if (unit.isBlank()) {
                err = "数量要填数字；只写单位（如 斤）表示还没称"
                return
            }
            val msg = validate(p, BigDecimal.ONE)
            if (msg != null) {
                err = msg
                return
            }
            onSave(e.copy(name = name.trim().take(20), unitPrice = p, quantity = BigDecimal.ONE, unit = unit, pending = true))
            return
        }
        val msg = validate(p, n)
        if (msg != null) {
            err = msg
            return
        }
        onSave(e.copy(name = name.trim().take(20), unitPrice = p, quantity = n, unit = unit, pending = false))
    }

    LaunchedEffect(e.id) {
        runCatching { focus.requestFocus() }
        delay(350)
        requester.bringIntoView()
    }

    val p = parseNum(price)
    val qn = parseQtyInput(qty).number
    val sub = when {
        p == null -> "—"
        qn == null -> "待定"
        else -> fmtMoney(p.multiply(qn))
    }

    Column(Modifier.fillMaxWidth().background(Color(0xFFF2F9FF)).bringIntoViewRequester(requester)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CellField(
                name, { name = it; err = null }, W_NAME, TextAlign.Start,
                KeyboardType.Text, ImeAction.Next, Modifier.focusRequester(focus)
            )
            CellField(price, { price = it; err = null }, W_PRICE, TextAlign.End, KeyboardType.Decimal, ImeAction.Next)
            CellField(
                qty, { qty = it; err = null }, W_QTY, TextAlign.End,
                KeyboardType.Text, ImeAction.Done, onDone = { save() }
            )
            Txt(sub, W_SUB, bold = true, color = DeepOrange, softWrap = false)
        }
        err?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp))
        }
        Row(Modifier.fillMaxWidth().padding(end = 4.dp), horizontalArrangement = Arrangement.End) {
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("删除这行") }
            TextButton(onClick = onCancel) { Text("取消") }
            TextButton(onClick = { save() }) { Text("保存", fontWeight = FontWeight.Bold) }
        }
    }
}

/** 表格：白色圆角卡片，表头淡蓝底，行与行之间一条细线。点某一行就地修改。 */
@Composable
private fun LedgerTable(
    entries: List<Entry>,
    editingId: Long?,
    onRowClick: (Entry) -> Unit,
    onLongPress: () -> Unit,
    onSave: (Entry) -> Unit,
    onDelete: (Entry) -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, BubbleLine),
        shadowElevation = 2.dp
    ) {
        Column {
            TableHead()
            entries.forEach { e ->
                if (e.id == editingId) {
                    EditRow(e, onSave, { onDelete(e) }, onCancel)
                } else {
                    EntryLine(e, { onRowClick(e) }, onLongPress)
                }
                HLine()
            }
        }
    }
}

/** 一趟的卡片：记账中是表格 + 累计；点结束后，同一张卡片直接变成"本趟结算"。 */
@Composable
private fun TripCardView(
    item: TripCard,
    checked: Boolean,
    selecting: Boolean,
    editingId: Long?,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
    onRowClick: (Entry) -> Unit,
    onSave: (Entry) -> Unit,
    onDeleteEntry: (Entry) -> Unit,
    onCancelEdit: () -> Unit,
    onCopy: () -> Unit,
    onReopen: () -> Unit
) {
    Box(Modifier.fillMaxWidth().padding(horizontal = edgeGap(), vertical = 6.dp), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = BubbleMaxWidth),
            shape = RoundedCornerShape(18.dp),
            color = Color.White,
            border = if (checked) BorderStroke(2.dp, Brand) else null,
            shadowElevation = 3.dp
        ) {
            Column(
                Modifier
                    // 加一行、改某一行（展开）、删一行、结束变结算：高度都是平滑变化，不是一下子跳
                    .animateContentSize(tween(420, easing = TypingEasing))
                    .combinedClickable(onClick = { if (selecting) onToggle() }, onLongClick = onLongPress)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 34.dp).padding(start = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (item.ended) "本趟结算" else "本趟记账",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (item.ended) {
                        // 长按照样交给整张卡片的长按选中，不被这两个按钮吃掉
                        if (item.canReopen) {
                            Text(
                                "继续记这一趟",
                                color = Brand,
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .combinedClickable(onClick = onReopen, onLongClick = onLongPress)
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                        Text(
                            "复制",
                            color = Brand,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .combinedClickable(onClick = onCopy, onLongClick = onLongPress)
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                    if (selecting) {
                        TickBox(checked, onToggle, tint = if (checked) Brand else Color(0xFFB7C2D2))
                    }
                }
                Column(Modifier.padding(horizontal = 4.dp)) {
                    LedgerTable(
                        entries = item.entries,
                        editingId = editingId,
                        onRowClick = onRowClick,
                        onLongPress = onLongPress,
                        onSave = onSave,
                        onDelete = onDeleteEntry,
                        onCancel = onCancelEdit
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            androidx.compose.ui.text.buildAnnotatedString {
                                append("共 ${item.count} 笔")
                                if (item.pendingCount > 0) {
                                    append("　")
                                    pushStyle(androidx.compose.ui.text.SpanStyle(color = PendingColor))
                                    append("${item.pendingCount} 项待称，未计入")
                                    pop()
                                }
                            },
                            fontSize = 13.sp,
                            color = Muted,
                            modifier = Modifier.weight(1f).alignByBaseline()
                        )
                        Text(
                            if (item.ended) "总计 " else "累计 ",
                            fontSize = 12.sp,
                            color = DeepOrange,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.alignByBaseline()
                        )
                        Text(
                            "¥ ${fmtMoney(item.total)}",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = DeepOrange,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.alignByBaseline()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InputBar(
    value: String,
    error: String?,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
    onInputTouch: () -> Unit,
    onEnd: () -> Unit
) {
    Column(Modifier.fillMaxWidth().background(Color(0xFFF1F3F9)).navigationBarsPadding().imePadding()) {
        if (error != null) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp)
            )
        }
        Row(Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = onEnd,
                border = BorderStroke(1.dp, Brand),
                contentPadding = PaddingValues(horizontal = 14.dp)
            ) { Text("结束") }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier.weight(1f).pointerInput(Unit) {
                    // 只“看”一下手指按下，不拦截，输入框照常获得焦点
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        onInputTouch()
                    }
                },
                placeholder = { Text("苹果5　面条10，2　米6.5/斤", color = Color(0xFFB9C1CE)) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = Brand,
                    unfocusedBorderColor = Color(0xFFA9C9E8)
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() })
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = onSend, enabled = value.isNotBlank()) { Text("记账") }
        }
    }
}

@Composable
private fun EndDialog(count: Int, total: BigDecimal, pending: Int, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("结束这一趟？") },
        text = {
            Column {
                Text("共 $count 笔，总计 ¥${fmtMoney(total)}")
                if (pending > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "还有 $pending 项没称，不会计入总计。点\"取消\"回去补数量，或直接结束，之后也可以点那一行补。",
                        color = PendingColor,
                        fontSize = 13.sp
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注，比如店名（可不填）") },
                    singleLine = true
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(note) }) { Text("结束并结算") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
