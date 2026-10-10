package com.shopledger.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

// 气泡里表格的列宽比例：名称、单价、数量、小计
private const val W_NAME = 1.9f
private const val W_PRICE = 1f
private const val W_QTY = 0.8f
private const val W_SUB = 1.2f

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
    var editing by remember { mutableStateOf<Entry?>(null) }
    var showEnd by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var prevSize by remember { mutableIntStateOf(0) }
    var prevCount by remember { mutableIntStateOf(0) }

    // 有新内容时滚到最底；删除、修改不会乱跳
    LaunchedEffect(ui.items.size, ui.currentCount) {
        val size = ui.items.size
        val grew = size > prevSize || ui.currentCount > prevCount
        if (grew && size > 0) {
            if (prevSize == 0) listState.scrollToItem(size - 1)
            else listState.animateScrollToItem(size - 1)
        }
        prevSize = size
        prevCount = ui.currentCount
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
        topBar = { TopBar() },
        bottomBar = {
            InputBar(
                value = input,
                error = error,
                onChange = {
                    input = it
                    error = null
                },
                onSend = { submit() },
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
            TotalBar(ui.currentTotal, ui.currentCount)
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                if (ui.loaded && ui.items.isEmpty()) {
                    item(key = "empty") {
                        Box(
                            Modifier.fillMaxWidth().padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "在下面输入就会出账\n苹果5　　面条10，2　　5",
                                color = Muted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                items(ui.items, key = { it.key }) { item ->
                    when (item) {
                        is HeaderItem -> TripHeader(item.label)
                        is BatchItem -> MyBubble(item) { editing = it }
                        is FooterItem -> SysBubble(
                            item = item,
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

    editing?.let { e ->
        EditDialog(
            entry = e,
            onSave = {
                vm.update(it)
                editing = null
            },
            onDelete = {
                vm.delete(e)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }

    if (showEnd) {
        EndDialog(
            count = ui.currentCount,
            total = ui.currentTotal,
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
private fun TopBar() {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(BrandStart, BrandEnd)))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            "超市记账",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TotalBar(total: BigDecimal, count: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(TotalStrip)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("当前累计", fontSize = 12.sp, color = Muted)
            Text(
                "¥ ${fmtMoney(total)}",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = DeepOrange
            )
        }
        Text("共 $count 笔", color = Muted)
    }
}

@Composable
private fun Avatar(label: String, color: Color) {
    Box(
        Modifier.size(38.dp).background(color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TripHeader(label: String) {
    Box(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFD3E6F8)) {
            Text(
                label,
                fontSize = 12.sp,
                color = Muted,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
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
        modifier = Modifier.weight(weight).padding(horizontal = 2.dp),
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
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Txt("名称", W_NAME, TextAlign.Start, size = 11.sp, color = Muted)
        Txt("单价", W_PRICE, size = 11.sp, color = Muted)
        Txt("数量", W_QTY, size = 11.sp, color = Muted)
        Txt("小计", W_SUB, size = 11.sp, color = Muted)
    }
    HLine()
}

@Composable
private fun EntryLine(e: Entry, editable: Boolean, onClick: () -> Unit) {
    val unnamed = e.name.isBlank()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = editable, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Txt(
            if (unnamed) "未命名" else e.name,
            W_NAME,
            TextAlign.Start,
            color = if (unnamed) Muted else Color.Unspecified
        )
        Txt(fmtNum(e.unitPrice), W_PRICE, softWrap = false)
        Txt(fmtNum(e.quantity), W_QTY, softWrap = false)
        Txt(fmtMoney(e.subtotal()), W_SUB, bold = true, softWrap = false)
    }
    HLine()
}

/** 我的气泡：靠右，里面是这一次输入的小表格，下面是累计。点某一行可以修改。 */
@Composable
private fun MyBubble(item: BatchItem, onEdit: (Entry) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Bottom
    ) {
        Surface(
            modifier = Modifier.weight(1f, fill = false).widthIn(max = BubbleMaxWidth),
            shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
            color = BubbleMine
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                TableHead()
                item.entries.forEach { e -> EntryLine(e, item.editable) { onEdit(e) } }
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        fmtTime(item.time),
                        fontSize = 11.sp,
                        color = Muted,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "累计 ¥${fmtMoney(item.cumulative)}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeepOrange
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Avatar("我", Brand)
    }
}

/** 结算气泡：靠左，整趟的明细和总计。 */
@Composable
private fun SysBubble(item: FooterItem, onCopy: () -> Unit, onReopen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        Avatar("账", SysAvatar)
        Spacer(Modifier.width(8.dp))
        Surface(
            modifier = Modifier.weight(1f, fill = false).widthIn(max = BubbleMaxWidth),
            shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
            color = Color.White
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text("本趟结算", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                TableHead()
                item.entries.forEach { e -> EntryLine(e, editable = false) {} }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("共 ${item.count} 笔", fontSize = 13.sp, color = Muted, modifier = Modifier.weight(1f))
                    Text(
                        "总计 ¥${fmtMoney(item.total)}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeepOrange
                    )
                }
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onCopy) { Text("复制") }
                    if (item.canReopen) {
                        TextButton(onClick = onReopen) { Text("继续记这一趟") }
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
    onEnd: () -> Unit
) {
    Column(Modifier.fillMaxWidth().background(Color.White)) {
        HLine()
        if (error != null) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp)
            )
        }
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = onEnd,
                border = BorderStroke(1.dp, Brand),
                contentPadding = PaddingValues(horizontal = 14.dp)
            ) { Text("结束") }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("苹果5　面条10，2") },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() })
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = onSend, enabled = value.isNotBlank()) { Text("记账") }
        }
    }
}

@Composable
private fun EditDialog(entry: Entry, onSave: (Entry) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(entry.name) }
    var price by remember { mutableStateOf(fmtNum(entry.unitPrice)) }
    var qty by remember { mutableStateOf(fmtNum(entry.quantity)) }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改这一笔") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称（可留空）") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("单价") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = qty,
                    onValueChange = { qty = it },
                    label = { Text("数量") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                if (err != null) {
                    Text(err ?: "", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val p = price.trim().toBigDecimalOrNull()
                val q = qty.trim().toBigDecimalOrNull()
                if (p == null || q == null) {
                    err = "单价和数量要填数字"
                } else {
                    val msg = validate(p, q)
                    if (msg != null) {
                        err = msg
                    } else {
                        onSave(entry.copy(name = name.trim().take(20), unitPrice = p, quantity = q))
                    }
                }
            }) { Text("保存") }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

@Composable
private fun EndDialog(count: Int, total: BigDecimal, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("结束这一趟？") },
        text = {
            Column {
                Text("共 $count 笔，总计 ¥${fmtMoney(total)}")
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
