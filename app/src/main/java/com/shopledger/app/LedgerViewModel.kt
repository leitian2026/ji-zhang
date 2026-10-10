package com.shopledger.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

sealed interface Item {
    val key: String
}

data class HeaderItem(val label: String, val tripId: Long) : Item {
    override val key: String get() = "h$tripId"
}

/** 一趟 = 一个白色卡片：没结束时是记账表，结束后同一张卡片变成结算 */
data class TripCard(
    val tripId: Long,
    val entries: List<Entry>,
    val total: BigDecimal,
    val count: Int,
    val ended: Boolean,
    val text: String,
    val canReopen: Boolean
) : Item {
    override val key: String get() = "t$tripId"
}

data class UiState(
    val items: List<Item> = emptyList(),
    val currentTotal: BigDecimal = BigDecimal.ZERO.setScale(2),
    val currentCount: Int = 0,
    val loaded: Boolean = false
)

fun fmtTime(ms: Long): String =
    SimpleDateFormat("HH:mm", Locale.CHINA).format(ms)

fun fmtDate(ms: Long): String =
    SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(ms)

private fun headerLabel(index: Int, trip: Trip): String {
    val base = "第${index}趟 · ${fmtDate(trip.startedAt)}"
    return if (trip.name.isNotBlank()) "$base · ${trip.name}" else base
}

private fun settlementText(label: String, entries: List<Entry>, total: BigDecimal): String {
    val sb = StringBuilder("【$label】\n")
    for (e in entries) {
        val n = e.name.ifBlank { "未命名" }
        sb.append("$n  ${fmtNum(e.unitPrice)} × ${fmtQty(e)} = ${fmtMoney(e.subtotal())}\n")
    }
    sb.append("共${entries.size}笔，总计 ${fmtMoney(total)}")
    return sb.toString()
}

private fun buildState(trips: List<Trip>, entries: List<Entry>): UiState {
    val byTrip = entries.groupBy { it.tripId }
    val shown = trips.filter { !byTrip[it.id].isNullOrEmpty() }
    val hasActive = shown.any { it.endedAt == null }
    val items = ArrayList<Item>()
    var curTotal = BigDecimal.ZERO.setScale(2)
    var curCount = 0

    shown.forEachIndexed { idx, trip ->
        val es = byTrip.getValue(trip.id)
        val ended = trip.endedAt != null
        val label = headerLabel(idx + 1, trip)
        items.add(HeaderItem(label, trip.id))
        var cum = BigDecimal.ZERO.setScale(2)
        for (e in es) cum = cum.add(e.subtotal())
        items.add(
            TripCard(
                tripId = trip.id,
                entries = es,
                total = cum,
                count = es.size,
                ended = ended,
                text = settlementText(label, es, cum),
                canReopen = ended && !hasActive && idx == shown.lastIndex
            )
        )
        if (!ended) {
            curTotal = cum
            curCount = es.size
        }
    }
    return UiState(items, curTotal, curCount, true)
}

private fun sameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
        ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

class LedgerViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = LedgerDb.get(app).dao()

    val ui: StateFlow<UiState> = combine(dao.trips(), dao.entries()) { t, e -> buildState(t, e) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    /** 上一趟没结束、而且不是今天记的：值是上一笔的时间，用来弹提示。 */
    private val _stale = MutableStateFlow<Long?>(null)
    val stale: StateFlow<Long?> = _stale.asStateFlow()

    init {
        viewModelScope.launch {
            val trip = dao.activeTrip() ?: return@launch
            val last = dao.lastEntryAt(trip.id) ?: return@launch
            if (!sameDay(last, System.currentTimeMillis())) _stale.value = last
        }
    }

    fun add(items: List<Parsed>) {
        viewModelScope.launch { dao.addEntries(items, System.currentTimeMillis()) }
    }

    fun update(entry: Entry) {
        viewModelScope.launch { dao.updateEntry(entry) }
    }

    fun delete(entry: Entry) {
        viewModelScope.launch { dao.deleteEntry(entry) }
    }

    fun deleteTrips(ids: List<Long>) {
        viewModelScope.launch { dao.deleteTrips(ids) }
    }

    fun end(name: String) {
        viewModelScope.launch { dao.endActive(System.currentTimeMillis(), name) }
    }

    fun reopen(tripId: Long) {
        viewModelScope.launch { dao.reopen(tripId) }
    }

    fun dismissStale() {
        _stale.value = null
    }
}
