package com.shopledger.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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

private const val W_SEQ = 0.45f
private const val W_NAME = 1.7f
private const val W_PRICE = 1f
private const val W_QTY = 0.7f
private const val W_SUB = 1.2f
private const val W_CUM = 1.4f

private val HeaderBg = Color(0xFFE3EDE8)
private val Line = Color(0xFFC5D6CD)

/**
 * 按手机屏幕排版：
 * - 宽屏（平板、折叠屏展开）时内容限制在 480dp 宽，居中，和手机一样的版面
 * - 字号按屏幕宽度小幅缩放（基准 360dp），窄屏不挤、宽屏不空
 * - 系统字体放得很大时封顶，免得表格被撑坏
 */
@Composable
fun PhoneFrame(content: @Composable () -> Unit) {
    val widthDp = LocalConfiguration.current.screenWidthDp
    val d = LocalDensity.current
    val scale = (1f + (minOf(widthDp, 480) - 360) / 360f * 0.5f).coerceIn(0.88f, 1.1f)
    CompositionLocalProvider(
        LocalDensity provides Density(d.density, minOf(d.fontScale, 1.15f) * scale)
    ) {
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(Modifier.widthIn(max = 480.dp).fillMaxHeight()) { content() }
        }
    }
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

    // 有新内容时滚到最底；删除、修改不会乱跳
    LaunchedEffect(ui.items.size) {
        val size = ui.items.size
        if (size > prevSize && size > 0) {
            if (prevSize == 0) listState.scrollToItem(size - 1)
            else listState.animateScrollToItem(size - 1)
        }
        prevSize = size
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
            Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
            TableHeader()
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                if (ui.loaded && ui.items.isEmpty()) {
                    item(key = "empty") {
                        Box(
                            Modifier.fillMaxWidth().padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "在下面输入就会出账\n苹果5　　面条10，2　　5",
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                items(ui.items, key = { it.key }) { item ->
                    when (item) {
                        is HeaderItem -> TripHeader(item.label)
                        is RowItem -> EntryRow(item) { editing = item.entry }
                        is FooterItem -> TripFooter(
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
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
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
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("当前累计", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                "¥ ${fmtMoney(total)}",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text("共 $count 笔", color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun TableRow(
    bg: Color,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(bg)
            .drawBehind {
                val w = 1.dp.toPx()
                drawLine(Line, Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), w)
                drawLine(Line, Offset(w / 2, 0f), Offset(w / 2, size.height), w)
            },
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun RowScope.Cell(
    text: String,
    weight: Float,
    align: TextAlign = TextAlign.End,
    bold: Boolean = false,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = 14.sp,
    softWrap: Boolean = true
) {
    Box(
        Modifier
            .weight(weight)
            .fillMaxHeight()
            .drawBehind {
                val w = 1.dp.toPx()
                drawLine(Line, Offset(size.width - w / 2, 0f), Offset(size.width - w / 2, size.height), w)
            }
            .padding(horizontal = 4.dp, vertical = 9.dp),
        contentAlignment = when (align) {
            TextAlign.Start -> Alignment.CenterStart
            TextAlign.Center -> Alignment.Center
            else -> Alignment.CenterEnd
        }
    ) {
        Text(
            text = text,
            textAlign = align,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = color,
            fontSize = fontSize,
            softWrap = softWrap,
            maxLines = if (softWrap) Int.MAX_VALUE else 1
        )
    }
}

@Composable
private fun TableHeader() {
    TableRow(HeaderBg) {
        Cell("#", W_SEQ, TextAlign.Center, true, fontSize = 12.sp)
        Cell("名称", W_NAME, TextAlign.Start, true, fontSize = 12.sp)
        Cell("单价", W_PRICE, bold = true, fontSize = 12.sp)
        Cell("数量", W_QTY, bold = true, fontSize = 12.sp)
        Cell("小计", W_SUB, bold = true, fontSize = 12.sp)
        Cell("累计", W_CUM, bold = true, fontSize = 12.sp)
    }
}

@Composable
private fun EntryRow(item: RowItem, onClick: () -> Unit) {
    val e = item.entry
    val bg = if (item.seq % 2 == 1) Color.White else Color(0xFFF7FAF8)
    val unnamed = e.name.isBlank()
    TableRow(bg, Modifier.clickable(enabled = item.editable, onClick = onClick)) {
        Cell(item.seq.toString(), W_SEQ, TextAlign.Center, color = Color.Gray)
        Cell(
            if (unnamed) "未命名" else e.name,
            W_NAME,
            TextAlign.Start,
            color = if (unnamed) Color.Gray else Color.Unspecified
        )
        Cell(fmtNum(e.unitPrice), W_PRICE, softWrap = false)
        Cell(fmtNum(e.quantity), W_QTY, softWrap = false)
        Cell(fmtMoney(e.subtotal()), W_SUB, softWrap = false)
        Cell(fmtMoney(item.cumulative), W_CUM, bold = true, color = MaterialTheme.colorScheme.primary, softWrap = false)
    }
}

@Composable
private fun TripHeader(label: String) {
    Box(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(12.dp), color = HeaderBg) {
            Text(
                label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun TripFooter(item: FooterItem, onCopy: () -> Unit, onReopen: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("结算", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("共 ${item.count} 笔", fontSize = 12.sp)
                Spacer(Modifier.width(12.dp))
                Text(
                    "¥ ${fmtMoney(item.total)}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
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

@Composable
private fun InputBar(
    value: String,
    error: String?,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
    onEnd: () -> Unit
) {
    Column(Modifier.fillMaxWidth().background(Color.White)) {
        if (error != null) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp)
            )
        }
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onEnd, contentPadding = PaddingValues(horizontal = 14.dp)) {
                Text("结束")
            }
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
