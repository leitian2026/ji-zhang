package com.shopledger.app

import java.math.BigDecimal
import java.math.RoundingMode

data class Parsed(
    val name: String,
    val price: BigDecimal,
    val qty: BigDecimal,
    val unit: String = "",
    val pending: Boolean = false
)

sealed interface ParseResult {
    data class Ok(val items: List<Parsed>) : ParseResult
    data class Err(val message: String) : ParseResult
}

/** 小计。待称的还没有数量，按 0 算：不会混进总计里。 */
fun Entry.subtotal(): BigDecimal =
    if (pending) BigDecimal.ZERO.setScale(2)
    else unitPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP)

/** 数量 + 单位，比如 2斤、1把；没写单位就只显示数字；待称的显示"待称" */
fun fmtQty(e: Entry): String = if (e.pending) "待称" else fmtNum(e.quantity) + e.unit

/** 表格里"数量"格的结果：number 为空表示还没称（只写了单位，或什么都没写） */
data class QtyInput(val number: BigDecimal?, val unit: String)

fun parseQtyInput(s: String): QtyInput {
    val t = normalize(s).trim()
    if (t.isEmpty()) return QtyInput(null, "")
    val m = QTY_WITH_UNIT.find(t)
    val n = m?.groupValues?.get(1)?.toBigDecimalOrNull()
    if (m != null && n != null) return QtyInput(n, m.groupValues[2].trim().take(4))
    return QtyInput(null, t.trimStart('/', ' ').take(4))
}

/** 表格里手改"支出"用：支持全角数字 */
fun parseNum(s: String): BigDecimal? = normalize(s).trim().toBigDecimalOrNull()

private val QTY_WITH_UNIT = Regex("""^\s*(-?(?:\d+(?:\.\d+)?|\.\d+))\s*(.*)$""")

/** 表格里手改"数量"用：2 / 2斤 / 1把 → (数量, 单位) */
fun parseQty(s: String): Pair<BigDecimal, String>? {
    val m = QTY_WITH_UNIT.find(normalize(s)) ?: return null
    val n = m.groupValues[1].toBigDecimalOrNull() ?: return null
    return n to m.groupValues[2].trim().take(4)
}

/** 金额：固定两位小数 */
fun fmtMoney(value: BigDecimal): String =
    value.setScale(2, RoundingMode.HALF_UP).toPlainString()

/** 单价、数量：去掉多余的 0（10.00 → 10，2.50 → 2.5） */
fun fmtNum(value: BigDecimal): String {
    val s = value.stripTrailingZeros()
    return (if (s.scale() < 0) s.setScale(0) else s).toPlainString()
}

fun validate(price: BigDecimal, qty: BigDecimal): String? = when {
    price.signum() == 0 -> "金额不能是 0"
    qty.signum() <= 0 -> "数量要大于 0"
    price.abs() >= BigDecimal("1000000") -> "金额太大"
    qty >= BigDecimal("100000") -> "数量太大"
    else -> null
}

private val NUM = Regex("""(?<![\d.])-?(?:\d+(?:\.\d+)?|\.\d+)""")
private val UNIT_AFTER = Regex("""(?<=\d)\s*(?:块钱|块|元)""")
// 单价后面紧跟 /单位：米6.5/斤、6.5元/斤（元/块已先被去掉）
private val SLASH_UNIT = Regex("""(?<=\d)\s*/\s*([^\d\s,/.;]{1,4})""")
private val TIMES = Regex("""(?<=\d)\s*[xX*]\s*(?=\d)""")

private fun normalize(s: String): String = buildString {
    for (c in s) {
        append(
            when {
                c in '０'..'９' -> '0' + (c - '０')
                c == '，' || c == '、' -> ','
                c == '．' || c == '。' -> '.'
                c == '－' || c == '−' -> '-'
                c == '＊' || c == '×' -> '*'
                c == '／' -> '/'
                c == '￥' || c == '¥' -> ' '
                c == '\u3000' || c == '\r' -> ' '
                else -> c
            }
        )
    }
}

private fun cleanName(s: String): String =
    s.trim { it.isWhitespace() || it in ",*:：.、-" }.replace(Regex("\\s+"), " ").take(20)

/**
 * 一行 = 一笔。规则：
 *  5            → 未命名，单价 5，数量 1
 *  苹果5 / 苹果 5 / 5苹果 → 苹果，5
 *  面条10，2 / 面条 10 2 / 面条10x2 → 面条，单价 10，数量 2
 *  苹果6，2斤 / 葱3 1把 → 数量后面跟单位（斤、把……）
 *  米6.5/斤 → 单价 6.5 元每斤，数量还没称：待称，小计待定、不计入总计
 *  米6.5/斤，2.3 → 单价 6.5 每斤，称出来 2.3 斤
 * 第一个数字是单价，第二个是数量。
 */
private fun parseLine(raw: String): ParseResult {
    var line = UNIT_AFTER.replace(raw, "")
    line = TIMES.replace(line, ",")
    // 米6.5/斤：把"/斤"摘出来当单位；只有这一个数字时，就是"单价已知、待称"
    val slashUnit = SLASH_UNIT.find(line)?.groupValues?.get(1)?.trim().orEmpty()
    if (slashUnit.isNotEmpty()) line = SLASH_UNIT.replace(line, "")
    val ms = NUM.findAll(line).toList()
    if (ms.isEmpty()) return ParseResult.Err("没有找到金额：$raw")
    if (ms.size > 2) return ParseResult.Err("一笔最多两个数字（单价、数量）：$raw")

    val price = BigDecimal(ms[0].value)
    val qty = if (ms.size == 2) BigDecimal(ms[1].value) else BigDecimal.ONE
    val pending = ms.size == 1 && slashUnit.isNotEmpty()
    validate(price, qty)?.let { return ParseResult.Err("$it：$raw") }

    val prefix = line.substring(0, ms.first().range.first)
    val suffix = line.substring(ms.last().range.last + 1)
    val between =
        if (ms.size == 2) line.substring(ms[0].range.last + 1, ms[1].range.first) else ""
    // 米6.5/斤,2.3斤 这种：结尾的"斤"是单位，不是名称
    val suffixName = if (slashUnit.isNotEmpty() && cleanName(suffix) == slashUnit) "" else suffix
    val name = listOf(prefix, suffixName, between)
        .map { cleanName(it) }
        .firstOrNull { it.isNotEmpty() } ?: ""

    // 两个数字时，第二个数字后面紧跟的文字是单位：面条10，2斤 → 2 斤
    val hasName = cleanName(prefix).isNotEmpty() || cleanName(between).isNotEmpty()
    val unit = when {
        slashUnit.isNotEmpty() -> slashUnit
        ms.size == 2 && hasName -> cleanName(suffix).take(4)
        else -> ""
    }

    return ParseResult.Ok(listOf(Parsed(name, price, qty, unit, pending)))
}

/** 整个输入框的内容。换行或分号分隔的多段，会拆成多笔。有一段不对就整体不入账。 */
fun parseInput(text: String): ParseResult {
    val lines = normalize(text)
        .split('\n', ';', '；')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (lines.isEmpty()) return ParseResult.Err("请输入内容")

    val out = ArrayList<Parsed>()
    for (l in lines) {
        when (val r = parseLine(l)) {
            is ParseResult.Err -> return r
            is ParseResult.Ok -> out.addAll(r.items)
        }
    }
    return ParseResult.Ok(out)
}
