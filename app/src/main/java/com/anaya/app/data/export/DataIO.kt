@file:Suppress("BlockingMethodInNonBlockingContext")

package com.anaya.app.data.export

import android.content.Context
import android.net.Uri
import com.anaya.app.data.local.dao.AccountDao
import com.anaya.app.data.local.dao.CategoryDao
import com.anaya.app.data.local.dao.TransactionDao
import com.anaya.app.data.local.entity.TransactionEntity
import com.anaya.app.data.local.entity.CategoryEntity
import com.anaya.app.domain.model.Account
import com.anaya.app.domain.model.Category
import com.anaya.app.domain.model.Transaction
import com.anaya.app.domain.model.TransactionType
import com.anaya.app.domain.repository.AccountRepository
import com.anaya.app.domain.repository.CategoryRepository
import com.anaya.app.domain.repository.TransactionRepository
import com.anaya.app.ml.LocalModelInterface
import com.anaya.app.ml.ParsedTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

// ═══════════════════════════════════════════════════════
// 数据格式
// ═══════════════════════════════════════════════════════

enum class ExportFormat { JSON, EXCEL }

/** 导出结果 */
data class ExportResult(
    val success: Boolean,
    val message: String,
    val fileUri: Uri? = null,
    val transactionCount: Int = 0
)

/** 导入结果 */
data class ImportResult(
    val success: Boolean,
    val message: String,
    val importedCount: Int = 0,
    val skippedCount: Int = 0,
    val errors: List<String> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList()
)

/** 智能导入预览 — 展示模型/规则解析出的交易供用户确认 */
data class SmartImportPreview(
    val success: Boolean,
    val message: String,
    val transactions: List<TransactionEntity> = emptyList(),
    val errors: List<String> = emptyList()
) {
    val count: Int get() = transactions.size
}

/** 导出数据的封装 */
private data class ExportData(
    val transactions: List<TransactionEntity>,
    val accounts: List<Account>,
    val categories: List<Category>,
    val exportVersion: Int = 1,
    val exportDate: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
)

// ═══════════════════════════════════════════════════════
// 核心导出/导入引擎
// ═══════════════════════════════════════════════════════

@Singleton
class DataExportImport @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val localModel: LocalModelInterface
) {

    // ── 导出 ──

    /**
     * 导出交易数据到文件
     * @param uri 用户通过 SAF 选择的输出文件 Uri
     * @param format JSON 或 EXCEL
     * @param dateRange 可选的时间范围（null = 全部）
     */
    suspend fun export(
        uri: Uri,
        format: ExportFormat,
        dateRange: Pair<Long, Long>? = null
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            // 收集数据
            val transactions = if (dateRange != null) {
                transactionDao.getTransactionsByDateRangeSync(dateRange.first, dateRange.second)
            } else {
                transactionDao.getAllTransactionsSync()
            }
            if (transactions.isEmpty()) {
                return@withContext ExportResult(false, "没有可导出的交易记录")
            }

            val accounts = accountRepository.getAllAccounts().first()
            val categories = categoryRepository.getAllCategories().first()

            val data = ExportData(
                transactions = transactions,
                accounts = accounts,
                categories = categories
            )

            // 写入文件
            val outputStream = context.contentResolver.openOutputStream(uri)
                ?: return@withContext ExportResult(false, "无法打开文件")

            when (format) {
                ExportFormat.JSON -> writeJson(outputStream, data)
                ExportFormat.EXCEL -> writeExcel(outputStream, data)
            }

            ExportResult(
                success = true,
                message = "成功导出 ${transactions.size} 条交易记录",
                fileUri = uri,
                transactionCount = transactions.size
            )
        } catch (e: Exception) {
            ExportResult(false, "导出失败：${e.message}")
        }
    }

    // ── 导入 ──

    /**
     * 从文件导入交易数据
     * @param uri 用户通过 SAF 选择的输入文件 Uri
     * @param format JSON 或 EXCEL
     */
    suspend fun import(uri: Uri, format: ExportFormat): ImportResult = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext ImportResult(false, "无法打开文件")

            val result = when (format) {
                ExportFormat.JSON -> readJson(inputStream)
                ExportFormat.EXCEL -> readExcel(inputStream)
            }

            if (result.importedCount == 0) {
                return@withContext result.copy(success = false, message = "未能解析到有效交易记录")
            }

            // 保存到数据库
            saveTransactionEntities(result.transactions)

            result
        } catch (e: Exception) {
            ImportResult(false, "导入失败：${e.message}")
        }
    }

    // ── 智能导入（识别任意格式）──

    /**
     * 智能导入 — 读取文件内容，先尝试结构化解析（CSV/TSV），失败则使用模型推理。
     * 返回预览数据供用户确认，不直接写入数据库。
     */
    suspend fun smartImport(uri: Uri): SmartImportPreview = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext SmartImportPreview(false, "无法打开文件")

            val rawText = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
                .readText()
                .trim()

            if (rawText.isBlank()) {
                return@withContext SmartImportPreview(false, "文件内容为空")
            }

            // 加载已有分类（用于智能匹配）
            val existingCategories = categoryDao.getAllCategoriesSync()

            // 阶段一：JSON 数组解析（钱迹/QianJi 等 App 的导出格式）
            if (rawText.startsWith("[")) {
                val jsonResult = tryParseJsonArray(rawText)
                if (jsonResult.count > 0) {
                    return@withContext resolveCategoryIds(jsonResult, existingCategories).copy(
                        message = "通过 JSON 解析识别了 ${jsonResult.count} 条交易记录"
                    )
                }
            }

            // 阶段二：启发式结构化解析（CSV / TSV / 分隔符文本）
            val structured = tryParseStructured(rawText)
            if (structured.count > 0) {
                return@withContext resolveCategoryIds(structured, existingCategories).copy(
                    message = "通过结构化解析识别了 ${structured.count} 条交易记录"
                )
            }

            // 阶段三：模型提取（模型可用时）
            if (localModel.isModelLoaded()) {
                val parsed = localModel.extractTransactions(rawText)
                if (parsed.isNotEmpty()) {
                    val entities = parsed.map { parsedToEntity(it) }
                    val errors = parsed.filter { it.amount == null }
                        .map { "无法解析金额：${it.note ?: it.merchant ?: "未知"}" }
                    return@withContext resolveCategoryIds(
                        SmartImportPreview(
                            success = true,
                            message = "通过AI模型识别了 ${entities.size} 条交易记录",
                            transactions = entities,
                            errors = errors
                        ),
                        existingCategories
                    )
                }
            }

            // 阶段四：逐行正则回退
            val lines = rawText.lines().filter { it.isNotBlank() && !isHeaderLine(it) }
            val parsed = lines.mapNotNull { line ->
                val result = com.anaya.app.ml.RuleBasedParser.parsePaymentText(line)
                if (result.amount != null && result.amount > 0) parsedToEntity(result) else null
            }

            if (parsed.isNotEmpty()) {
                resolveCategoryIds(
                    SmartImportPreview(
                        success = true,
                        message = "通过正则匹配识别了 ${parsed.size} 条交易记录",
                        transactions = parsed
                    ),
                    existingCategories
                )
            } else {
                SmartImportPreview(
                    success = false,
                    message = "未能从文件中识别出任何交易记录。文件内容：${rawText.take(100)}${if (rawText.length > 100) "..." else ""}"
                )
            }
        } catch (e: Exception) {
            SmartImportPreview(false, "智能导入失败：${e.message}")
        }
    }

    /** 确认智能导入：将预览的交易写入数据库 */
    suspend fun confirmSmartImport(preview: SmartImportPreview): ImportResult =
        withContext(Dispatchers.IO) {
            if (preview.transactions.isEmpty()) {
                return@withContext ImportResult(false, "没有可导入的交易记录")
            }
            try {
                saveTransactionEntities(preview.transactions)
                ImportResult(
                    success = true,
                    message = "成功导入 ${preview.transactions.size} 条交易记录",
                    importedCount = preview.transactions.size
                )
            } catch (e: Exception) {
                ImportResult(false, "保存失败：${e.message}")
            }
        }

    // ── 智能导入辅助方法 ──

    /** 智能分类解析 — 多级匹配策略 */
    private suspend fun resolveCategoryIds(
        preview: SmartImportPreview,
        existingCategories: List<CategoryEntity>
    ): SmartImportPreview {
        if (preview.transactions.isEmpty()) return preview

        // 构建查找表：
        //   exactMap: 全名匹配 (学习 → 教育/学习)
        //   suffixMap: 后缀匹配 (学习 → 教育/学习)
        //   parentNames: 所有父级名称 (餐饮/教育/交通...)
        val exactMap = mutableMapOf<String, CategoryEntity>()
        val suffixMap = mutableMapOf<String, CategoryEntity>()    // 匹配 "/子类名"
        val parentNames = mutableSetOf<String>()                  // 所有父级名称

        for (cat in existingCategories) {
            exactMap[cat.name] = cat
            val slashIdx = cat.name.indexOf('/')
            if (slashIdx >= 0) {
                val subName = cat.name.substring(slashIdx + 1)
                suffixMap[subName] = cat
                parentNames.add(cat.name.substring(0, slashIdx))
            } else {
                parentNames.add(cat.name)
            }
        }

        // 规则映射：常见钱迹分类 → 建议的父级
        val parentHints = mapOf(
            "三餐" to "餐饮", "早餐" to "餐饮", "午餐" to "餐饮", "晚餐" to "餐饮",
            "零食" to "购物", "饮料" to "餐饮",
            "学习" to "教育", "培训" to "教育", "考试" to "教育", "书本" to "教育",
            "水电煤" to "住房", "房租" to "住房", "物业" to "住房",
            "收红包" to "红包", "红包" to "红包",
            "转账" to "转账",
            "话费" to "通讯", "流量" to "通讯",
            "电影" to "娱乐", "游戏" to "娱乐",
            "看病" to "医疗", "药品" to "医疗", "医院" to "医疗",
            "服装" to "购物", "服饰" to "购物", "日用" to "购物"
        )

        val createdCats = mutableListOf<CategoryEntity>()
        var matchedCount = 0
        var modelUsed = false

        val enriched = preview.transactions.map { tx ->
            val note = tx.note ?: return@map tx
            val prefix = "分类:"
            val idx = note.indexOf(prefix)
            if (idx < 0) return@map tx

            // 提取分类名称
            val afterPrefix = note.substring(idx + prefix.length)
            val catName = afterPrefix.split(" | ", "|").first().trim().take(50)
            if (catName.isBlank()) return@map tx

            // ── 策略 1: 精确全名匹配 ──
            var category = exactMap[catName]
            if (category != null) {
                matchedCount++
                return@map tx.copy(
                    categoryId = category.id,
                    note = cleanCategoryFromNote(note, catName)
                )
            }

            // ── 策略 2: 后缀匹配 — 已有分类格式为 "父/子" ──
            category = suffixMap[catName]
            if (category != null) {
                matchedCount++
                return@map tx.copy(
                    categoryId = category.id,
                    note = cleanCategoryFromNote(note, catName)
                )
            }

            // ── 策略 3: 规则匹配父级，创建子分类 ──
            val suggestedParent = parentHints[catName]
            if (suggestedParent != null && suggestedParent in parentNames) {
                // 创建"父/子"分类
                val newCatName = "$suggestedParent/$catName"
                val newCat = CategoryEntity(
                    name = newCatName, type = when (tx.type) {
                        "INCOME" -> "INCOME"; else -> "EXPENSE"
                    }, icon = null
                )
                val newId = categoryDao.insert(newCat)
                createdCats.add(newCat.copy(id = newId))
                matchedCount++
                return@map tx.copy(
                    categoryId = newId,
                    note = cleanCategoryFromNote(note, catName)
                )
            }

            // ── 策略 4: AI 模型分类（模型已加载时） ──
            if (localModel.isModelLoaded() && !modelUsed) {
                try {
                    val modelResult = localModel.classifyTransaction(
                        merchant = null,
                        note = catName,
                        amount = tx.amount,
                        existingCategories = existingCategories.map { it.name }
                    )
                    if (modelResult.confidence >= 0.5f && modelResult.categoryId != null) {
                        matchedCount++
                        modelUsed = true
                        return@map tx.copy(
                            categoryId = modelResult.categoryId,
                            note = cleanCategoryFromNote(note, catName)
                        )
                    }
                } catch (_: Exception) {}
            }

            // ── 策略 5: 直接创建为独立分类 ──
            val newCat = CategoryEntity(
                name = catName, type = when (tx.type) {
                    "INCOME" -> "INCOME"; else -> "EXPENSE"
                }, icon = null
            )
            val newId = categoryDao.insert(newCat)
            createdCats.add(newCat.copy(id = newId))
            matchedCount++
            tx.copy(
                categoryId = newId,
                note = cleanCategoryFromNote(note, catName)
            )
        }

        return if (matchedCount > 0) {
            val created = createdCats.size
            val extra = buildString {
                if (created > 0) append("，新建 $created 个分类")
                if (modelUsed) append("，AI辅助分类")
            }
            preview.copy(transactions = enriched, message = preview.message + extra)
        } else preview
    }

    /** 从 note 中移除 "分类:xxx" 前缀 */
    private fun cleanCategoryFromNote(note: String, catName: String): String? {
        return note
            .replace(Regex("""\s*\|\s*分类:$catName"""), "")
            .replace(Regex("""分类:$catName\s*\|\s*"""), "")
            .replace("分类:$catName", "")
            .trim()
            .takeIf { it.isNotBlank() }
    }

    /** 尝试 JSON 数组解析（钱迹等 App 的导出格式） */
    private fun tryParseJsonArray(text: String): SmartImportPreview {
        return try {
            val arr = JSONArray(text)
            if (arr.length() == 0) return SmartImportPreview(false, "JSON 数组为空")

            val transactions = mutableListOf<TransactionEntity>()
            val errors = mutableListOf<String>()

            val dateFormats = listOf(
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()),
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()),
                java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss", java.util.Locale.getDefault()),
                java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.getDefault()),
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()),
                java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault())
            )

            for (i in 0 until arr.length()) {
                try {
                    val obj = arr.getJSONObject(i)

                    // 金额（钱迹：money 字段，单位为元）
                    val money = obj.optDouble("money", -1.0)
                    if (money <= 0) {
                        errors.add("第 ${i + 1} 条：金额无效「$money」")
                        continue
                    }
                    val amountCents = (money * 100).toLong()

                    // 类型
                    val typeStr = obj.optString("type", "")
                    val type = when {
                        typeStr.contains("收") || typeStr.contains("收入") -> "INCOME"
                        typeStr.contains("支") || typeStr.contains("支出") -> "EXPENSE"
                        typeStr.contains("转") -> "TRANSFER"
                        else -> "EXPENSE"
                    }

                    // 日期
                    val dateStr = obj.optString("date", "").trim()
                    val dateMs = if (dateStr.isNotBlank()) {
                        dateFormats.firstNotNullOfOrNull { fmt ->
                            try { fmt.parse(dateStr)?.time } catch (_: Exception) { null }
                        }
                    } else null

                    // 分类名称（暂存到 note，后续用于自动匹配分类）
                    val category = obj.optString("category", "").trim()

                    // 备注
                    var note = obj.optString("remark", "").trim()
                    if (note.isBlank()) {
                        note = obj.optString("note", "").trim()
                    }

                    // 构建备注（含分类信息）
                    val fullNote = buildString {
                        if (note.isNotBlank()) append(note)
                        if (category.isNotBlank()) {
                            if (isNotEmpty()) append(" | ")
                            append("分类:$category")
                        }
                    }.takeIf { it.isNotBlank() }

                    transactions.add(TransactionEntity(
                        amount = amountCents,
                        type = type,
                        categoryId = 0,
                        accountId = 1,
                        note = fullNote,
                        date = dateMs ?: System.currentTimeMillis()
                    ))
                } catch (e: Exception) {
                    errors.add("第 ${i + 1} 条解析异常：${e.message}")
                }
            }

            if (transactions.isEmpty()) {
                SmartImportPreview(false, "JSON 未识别到有效交易，${errors.size} 个解析错误", errors = errors)
            } else {
                SmartImportPreview(true, "识别了 ${transactions.size} 条", transactions = transactions, errors = errors)
            }
        } catch (e: Exception) {
            SmartImportPreview(false, "JSON 解析失败：${e.message}")
        }
    }

    /** 尝试结构化解析 CSV / TSV 等分隔符文本 */
    private fun tryParseStructured(text: String): SmartImportPreview {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.size < 2) return SmartImportPreview(false, "行数不足")

        // 探测分隔符
        val delimiters = listOf('\t', ',', '|', ';')
        val firstLine = lines.first()
        val delim = delimiters.firstOrNull { d -> firstLine.count { c -> c == d } >= 2 }
            ?: return SmartImportPreview(false, "未检测到分隔符")

        val headerCols = firstLine.split(delim).map { it.trim() }
        if (headerCols.size < 2) return SmartImportPreview(false, "列数不足")

        // 匹配列索引
        val dateIdx = headerCols.indexOfFirst { it.contains(Regex("日期|时间|日付|Date|date|time|Time")) }
        val amountIdx = headerCols.indexOfFirst { it.contains(Regex("金额|金额|金额|Amount|amount|价格|Price|price")) }
        val typeIdx = headerCols.indexOfFirst { it.contains(Regex("类型|类别|收支|Type|type|方向|收入|支出")) }
        val noteIdx = headerCols.indexOfFirst { it.contains(Regex("备注|说明|描述|摘要|商户|商家|Note|note|Desc|desc|Merchant|merchant")) }
        val categoryIdx = headerCols.indexOfFirst { it.contains(Regex("分类|Category|category|类目")) }

        if (amountIdx < 0) return SmartImportPreview(false, "未找到金额列（表头：${headerCols.joinToString(", ")}）")

        val dateFormats = listOf(
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()),
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()),
            java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.getDefault()),
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()),
            java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault()),
            java.text.SimpleDateFormat("yyyy.MM.dd", java.util.Locale.getDefault())
        )

        val transactions = mutableListOf<TransactionEntity>()
        val errors = mutableListOf<String>()

        for ((rowIdx, line) in lines.drop(1).withIndex()) {
            try {
                val cols = line.split(delim).map { it.trim() }
                if (cols.size < 2) continue

                // 解析金额
                val amountStr = if (amountIdx < cols.size) cols[amountIdx] else ""
                val amountYuan = amountStr.replace(Regex("[¥￥,，\\s]"), "").toDoubleOrNull()
                if (amountYuan == null || amountYuan <= 0) {
                    errors.add("第 ${rowIdx + 2} 行：金额无效「$amountStr」")
                    continue
                }
                val amountCents = (amountYuan * 100).toLong()

                // 解析类型
                val typeStr = if (typeIdx >= 0 && typeIdx < cols.size) cols[typeIdx] else ""
                val type = when {
                    typeStr.contains("收") || typeStr.contains("INCOME") || typeStr.contains("income") || typeStr == "+" -> "INCOME"
                    typeStr.contains("支") || typeStr.contains("EXPENSE") || typeStr.contains("expense") || typeStr == "-" -> "EXPENSE"
                    typeStr.contains("转") || typeStr.contains("TRANSFER") || typeStr.contains("transfer") -> "TRANSFER"
                    else -> "EXPENSE"
                }

                // 解析日期
                val dateStr = if (dateIdx >= 0 && dateIdx < cols.size) cols[dateIdx] else ""
                val dateMs = if (dateStr.isNotBlank()) {
                    dateFormats.firstNotNullOfOrNull { fmt ->
                        try { fmt.parse(dateStr)?.time } catch (_: Exception) { null }
                    }
                } else null

                // 备注
                val note = if (noteIdx >= 0 && noteIdx < cols.size) cols[noteIdx].take(200) else null
                val categoryName = if (categoryIdx >= 0 && categoryIdx < cols.size) cols[categoryIdx] else null

                transactions.add(TransactionEntity(
                    amount = amountCents,
                    type = type,
                    categoryId = 0,
                    accountId = 1,
                    note = buildString {
                        if (note != null) append(note)
                        if (categoryName != null && categoryName.isNotBlank()) {
                            if (isNotEmpty()) append(" | ")
                            append("分类:$categoryName")
                        }
                    }.takeIf { it.isNotBlank() },
                    date = dateMs ?: System.currentTimeMillis()
                ))
            } catch (e: Exception) {
                errors.add("第 ${rowIdx + 2} 行解析异常：${e.message}")
            }
        }

        return if (transactions.isEmpty()) {
            SmartImportPreview(false, "结构化解析未识别到有效交易，${errors.size} 个解析错误", errors = errors)
        } else {
            SmartImportPreview(true, "识别了 ${transactions.size} 条", transactions = transactions, errors = errors)
        }
    }

    /** 判断是否为表头行 */
    private fun isHeaderLine(line: String): Boolean {
        val headers = listOf("金额", "日期", "时间", "备注", "商户", "类型", "分类", "收入", "支出",
            "amount", "date", "time", "note", "merchant", "type", "category")
        return headers.any { line.contains(it, ignoreCase = true) } && line.count { it == ',' || it == '\t' || it == '|' } >= 2
    }

    /** 将模型解析结果转换为数据库实体 */
    private fun parsedToEntity(p: ParsedTransaction): TransactionEntity {
        val type = when (p.type) {
            "INCOME" -> "INCOME"
            "TRANSFER" -> "TRANSFER"
            else -> "EXPENSE"
        }
        val note = listOfNotNull(p.merchant, p.note).joinToString(" | ")
        return TransactionEntity(
            amount = p.amount ?: 0L,
            type = type,
            categoryId = 0,
            accountId = 1,
            note = note.take(500).ifBlank { null },
            date = p.dateMs ?: System.currentTimeMillis()
        )
    }

    // ══════════════════════════════════════════════════
    // 写入数据库
    // ══════════════════════════════════════════════════

    /** 直接将实体列表写入数据库 */
    private suspend fun saveTransactionEntities(entities: List<TransactionEntity>) {
        entities.forEach { tx ->
            try {
                val domainTx = Transaction(
                    id = tx.id,
                    amount = tx.amount,
                    type = try { TransactionType.valueOf(tx.type) } catch (_: Exception) { TransactionType.EXPENSE },
                    categoryId = tx.categoryId,
                    accountId = if (tx.accountId == 0L) 1 else tx.accountId,
                    targetAccountId = tx.targetAccountId,
                    note = tx.note,
                    date = tx.date,
                    createdAt = tx.createdAt,
                    updatedAt = tx.updatedAt
                )
                transactionRepository.insert(domainTx)
            } catch (_: Exception) { /* 忽略单条失败 */ }
        }
    }

    // ══════════════════════════════════════════════════
    // JSON 读写
    // ══════════════════════════════════════════════════

    private fun writeJson(stream: OutputStream, data: ExportData) {
        val root = JSONObject().apply {
            put("version", data.exportVersion)
            put("exportDate", data.exportDate)
            put("app", "Anaya")
            put("totalTransactions", data.transactions.size)

            // 账户映射（id → name）
            val accountMap = JSONObject()
            data.accounts.forEach { accountMap.put(it.id.toString(), it.name) }
            put("accounts", accountMap)

            // 分类映射（id → name）
            val categoryMap = JSONObject()
            data.categories.forEach { categoryMap.put(it.id.toString(), it.name) }
            put("categories", categoryMap)

            // 交易记录
            val txArray = JSONArray()
            data.transactions.forEach { tx ->
                txArray.put(JSONObject().apply {
                    put("id", tx.id)
                    put("amount", tx.amount)
                    put("type", tx.type)
                    put("categoryId", tx.categoryId)
                    put("accountId", tx.accountId)
                    if (tx.targetAccountId != null) put("targetAccountId", tx.targetAccountId)
                    if (tx.note != null) put("note", tx.note)
                    put("date", tx.date)
                    put("createdAt", tx.createdAt)
                    put("updatedAt", tx.updatedAt)
                })
            }
            put("transactions", txArray)
        }

        BufferedWriter(OutputStreamWriter(stream, Charsets.UTF_8)).use { writer ->
            writer.write(root.toString(2))
        }
    }

    private fun readJson(stream: InputStream): ImportResult {
        val text = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
        val root = JSONObject(text)
        val txArray = root.optJSONArray("transactions")
            ?: return ImportResult(false, "JSON 格式错误：找不到 transactions 字段")

        val transactions = mutableListOf<TransactionEntity>()
        val errors = mutableListOf<String>()

        for (i in 0 until txArray.length()) {
            try {
                val obj = txArray.getJSONObject(i)
                val tx = TransactionEntity(
                    id = 0,  // 新建，不保留旧 ID
                    amount = obj.optLong("amount", 0),
                    type = obj.optString("type", "EXPENSE"),
                    categoryId = obj.optLong("categoryId", 0),
                    accountId = obj.optLong("accountId", 1),
                    targetAccountId = if (obj.has("targetAccountId") && !obj.isNull("targetAccountId"))
                        obj.getLong("targetAccountId") else null,
                    note = obj.optString("note", "").ifBlank { null },
                    date = obj.optLong("date", System.currentTimeMillis()),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                )
                if (tx.amount > 0) {
                    transactions.add(tx)
                } else {
                    errors.add("第 ${i + 1} 条记录金额无效，已跳过")
                }
            } catch (e: Exception) {
                errors.add("第 ${i + 1} 条记录解析失败：${e.message}")
            }
        }

        return ImportResult(
            success = transactions.isNotEmpty(),
            message = "解析到 ${transactions.size} 条交易记录",
            importedCount = transactions.size,
            skippedCount = errors.size,
            errors = errors,
            transactions = transactions
        )
    }

    // ══════════════════════════════════════════════════
    // Excel 读写
    // ══════════════════════════════════════════════════

    private fun writeExcel(stream: OutputStream, data: ExportData) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val workbook = XSSFWorkbook()

        // 样式
        val headerStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.GREY_25_PERCENT.getIndex()
            fillPattern = FillPatternType.SOLID_FOREGROUND
            borderBottom = BorderStyle.THIN
            borderTop = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
            setFont(workbook.createFont().apply { bold = true })
        }
        val dataStyle = workbook.createCellStyle().apply {
            borderBottom = BorderStyle.THIN
            borderTop = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
        }

        // ── Sheet 1: 交易记录 ──
        val sheet = workbook.createSheet("交易记录")
        val headers = listOf("日期", "类型", "金额(元)", "分类", "账户", "备注", "创建时间")

        // 表头
        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { i, h ->
            val cell = headerRow.createCell(i)
            cell.setCellValue(h)
            cell.setCellStyle(headerStyle)
        }

        // 数据行
        data.transactions.forEachIndexed { index, tx ->
            val row = sheet.createRow(index + 1)
            row.createCell(0).apply {
                setCellValue(dateFormat.format(Date(tx.date)))
                setCellStyle(dataStyle)
            }
            row.createCell(1).apply {
                setCellValue(when (tx.type) {
                    "EXPENSE" -> "支出"
                    "INCOME" -> "收入"
                    "TRANSFER" -> "转账"
                    else -> tx.type
                })
                setCellStyle(dataStyle)
            }
            row.createCell(2).apply {
                setCellValue(tx.amount / 100.0)
                setCellStyle(dataStyle)
            }
            row.createCell(3).apply {
                setCellValue(data.categories.find { it.id == tx.categoryId }?.name ?: "未分类")
                setCellStyle(dataStyle)
            }
            row.createCell(4).apply {
                setCellValue(data.accounts.find { it.id == tx.accountId }?.name ?: "未知账户")
                setCellStyle(dataStyle)
            }
            row.createCell(5).apply {
                setCellValue(tx.note ?: "")
                setCellStyle(dataStyle)
            }
            row.createCell(6).apply {
                setCellValue(dateFormat.format(Date(tx.createdAt)))
                setCellStyle(dataStyle)
            }
        }

        // 自动调整列宽
        (0..6).forEach { sheet.autoSizeColumn(it) }

        // ── Sheet 2: 账户信息 ──
        if (data.accounts.isNotEmpty()) {
            val acctSheet = workbook.createSheet("账户")
            val acctHeaders = listOf("名称", "类型", "余额(元)", "初始金额(元)")
            val hRow = acctSheet.createRow(0)
            acctHeaders.forEachIndexed { i, h ->
                val c = hRow.createCell(i)
                c.setCellValue(h)
                c.setCellStyle(headerStyle)
            }
            data.accounts.forEachIndexed { index, acct ->
                val row = acctSheet.createRow(index + 1)
                row.createCell(0).apply { setCellValue(acct.name); setCellStyle(dataStyle) }
                row.createCell(1).apply { setCellValue(acct.type.name); setCellStyle(dataStyle) }
                row.createCell(2).apply { setCellValue(acct.balance / 100.0); setCellStyle(dataStyle) }
                row.createCell(3).apply { setCellValue(acct.initialBalance / 100.0); setCellStyle(dataStyle) }
            }
            (0..3).forEach { acctSheet.autoSizeColumn(it) }
        }

        // ── Sheet 3: 分类信息 ──
        if (data.categories.isNotEmpty()) {
            val catSheet = workbook.createSheet("分类")
            val catHeaders = listOf("名称", "类型", "图标")
            val hRow = catSheet.createRow(0)
            catHeaders.forEachIndexed { i, h ->
                val c = hRow.createCell(i)
                c.setCellValue(h)
                c.setCellStyle(headerStyle)
            }
            data.categories.forEachIndexed { index, cat ->
                val row = catSheet.createRow(index + 1)
                row.createCell(0).apply { setCellValue(cat.name); setCellStyle(dataStyle) }
                row.createCell(1).apply {
                    setCellValue(if (cat.type.name == "EXPENSE") "支出" else "收入")
                    setCellStyle(dataStyle)
                }
                row.createCell(2).apply { setCellValue(cat.icon ?: ""); setCellStyle(dataStyle) }
            }
            (0..2).forEach { catSheet.autoSizeColumn(it) }
        }

        workbook.write(stream)
        workbook.close()
    }

    private fun readExcel(stream: InputStream): ImportResult {
        val workbook = XSSFWorkbook(stream)
        val sheet = workbook.getSheetAt(0)  // 第一个 Sheet
            ?: return ImportResult(false, "Excel 文件格式错误：找不到数据表")

        val transactions = mutableListOf<TransactionEntity>()
        val errors = mutableListOf<String>()
        val dateFormats = listOf(
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()),
            SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
            SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        )

        for (i in 1..sheet.lastRowNum) {
            val row = sheet.getRow(i) ?: continue
            try {
                val dateCell = row.getCell(0)
                val dateMs = when {
                    dateCell?.cellType == CellType.NUMERIC && DateUtil.isCellDateFormatted(dateCell) ->
                        dateCell.dateCellValue.time
                    dateCell?.cellType == CellType.STRING -> {
                        val text = dateCell.stringCellValue.trim()
                        dateFormats.firstNotNullOfOrNull { fmt ->
                            try { fmt.parse(text)?.time } catch (_: Exception) { null }
                        } ?: System.currentTimeMillis()
                    }
                    else -> System.currentTimeMillis()
                }

                val typeStr = row.getCell(1)?.stringCellValue?.trim()?.let { v ->
                    when {
                        v.contains("支") -> "EXPENSE"
                        v.contains("收") -> "INCOME"
                        v.contains("转") -> "TRANSFER"
                        else -> "EXPENSE"
                    }
                } ?: "EXPENSE"

                val amountCell = row.getCell(2)
                val amountYuan = when (amountCell?.cellType) {
                    CellType.NUMERIC -> amountCell.numericCellValue
                    CellType.STRING -> amountCell.stringCellValue.trim().toDoubleOrNull() ?: 0.0
                    else -> 0.0
                }
                val amountCents = (amountYuan * 100).toLong()

                val note = row.getCell(5)?.stringCellValue?.trim()?.takeIf { it.isNotBlank() }

                if (amountCents <= 0) {
                    errors.add("第 ${i + 1} 行：金额无效，已跳过")
                    continue
                }

                transactions.add(
                    TransactionEntity(
                        amount = amountCents,
                        type = typeStr,
                        categoryId = 0,
                        accountId = 1,
                        note = note,
                        date = dateMs,
                    )
                )

            } catch (e: Exception) {
                errors.add("第 ${i + 1} 行解析失败：${e.message}")
            }
        }

        workbook.close()

        return ImportResult(
            success = transactions.isNotEmpty(),
            message = "解析到 ${transactions.size} 条交易记录",
            importedCount = transactions.size,
            skippedCount = errors.size,
            errors = errors,
            transactions = transactions
        )
    }

    // ══════════════════════════════════════════════════
    // 写入数据库
    // ══════════════════════════════════════════════════

    private suspend fun saveImportedTransactions(result: ImportResult) {
        // 按时间排序后逐个插入（触发账户余额更新）
        val sorted = result.transactions // 保持原始顺序
        sorted.forEach { tx ->
            try {
                val domainTx = Transaction(
                    id = tx.id,
                    amount = tx.amount,
                    type = try { TransactionType.valueOf(tx.type) } catch (_: Exception) { TransactionType.EXPENSE },
                    categoryId = tx.categoryId,
                    accountId = if (tx.accountId == 0L) 1 else tx.accountId,
                    targetAccountId = tx.targetAccountId,
                    note = tx.note,
                    date = tx.date,
                    createdAt = tx.createdAt,
                    updatedAt = tx.updatedAt
                )
                transactionRepository.insert(domainTx)
            } catch (_: Exception) { /* 跳过单条失败 */ }
        }
    }
}
