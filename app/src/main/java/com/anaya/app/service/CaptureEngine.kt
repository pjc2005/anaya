package com.anaya.app.service

import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log

/**
 * 规则匹配引擎 — 参考 GKD 设计
 *
 * 每条规则指定：
 * - 目标 App（包名）
 * - 触发条件（节点文本/描述/ID 匹配）
 * - 排除条件（避免在浏览历史时误触发）
 * - 金额提取方式（从匹配节点的上下文中提取）
 * - 交易类型
 * - 分类提示（用于自动分类）
 */
data class CaptureRule(
    val name: String,
    /** 触发条件列表：任一匹配即触发 */
    val triggers: List<TriggerCondition>,
    /** 排除条件列表：任一匹配即跳过（用于避免浏览历史时误触发） */
    val excludes: List<ExcludeCondition> = emptyList(),
    /** 金额提取配置 */
    val amount: AmountExtract,
    /** 商户提取（可选） */
    val merchant: MerchantExtract? = null,
    /** 交易类型：EXPENSE / INCOME / TRANSFER */
    val type: String = "EXPENSE",
    /** 分类提示 — 用于智能分类（如 "餐饮/外卖", "购物"） */
    val categoryHint: String? = null,
    /** 置信度（0~1），默认0.7 */
    val confidence: Float = 0.7f
)

data class TriggerCondition(
    /** 节点文本包含此关键字 */
    val textContains: String? = null,
    /** 节点描述包含此关键字 */
    val descContains: String? = null,
    /** 节点 viewId 包含此字符串 */
    val idContains: String? = null,
    /** 节点 className 包含此字符串 */
    val classNameContains: String? = null,
    /** 如果 true，在整个子树中搜索这个条件 */
    val anyInSubtree: Boolean = false
)

/**
 * 排除条件 — 页面中含有这些文本时不触发
 * 用于避免在浏览账单/历史记录时误记账
 */
data class ExcludeCondition(
    val textContains: String? = null,
    val descContains: String? = null
)

data class AmountExtract(
    /** 从匹配节点的哪个方向取金额：
     *  "self" — 节点自身的文本
     *  "parent" — 父节点文本
     *  "sibling_next" — 下一个兄弟节点
     *  "sibling_prev" — 上一个兄弟节点
     *  "subtree" — 整个子树中搜索金额
     *  "children" — 子节点文本拼接
     */
    val from: String = "subtree",
    /** 正则提取金额（默认用内置金额正则） */
    val pattern: String? = null
)

data class MerchantExtract(
    val from: String = "self",
    val pattern: String? = null
)

/**
 * 全部自动记账规则
 *
 * 组织方式：按平台分组，每个平台一个 CapturingApp，
 * 包含多条规则（支付成功页、红包、转账等）。
 */
object CaptureRules {

    val ALL: List<CapturingApp> = listOf(
        // ═══════════════════════════════════════════════════════
        //  支付宝
        // ═══════════════════════════════════════════════════════
        CapturingApp("com.eg.android.AlipayGphone", "支付宝", listOf(
            CaptureRule(
                name = "支付成功页",
                triggers = listOf(
                    TriggerCondition(textContains = "支付成功"),
                    TriggerCondition(textContains = "付款成功"),
                    TriggerCondition(textContains = "付款详情"),
                    TriggerCondition(textContains = "交易详情"),
                    TriggerCondition(textContains = "成功收款"),
                ),
                excludes = listOf(
                    ExcludeCondition(textContains = "账单"),
                    ExcludeCondition(textContains = "历史记录"),
                ),
                amount = AmountExtract(from = "subtree"),
                merchant = MerchantExtract(from = "subtree"),
                categoryHint = "购物"
            ),
            CaptureRule(
                name = "转账详情",
                triggers = listOf(
                    TriggerCondition(textContains = "转账", anyInSubtree = true),
                    TriggerCondition(textContains = "成功", anyInSubtree = true),
                ),
                amount = AmountExtract(from = "subtree"),
                type = "TRANSFER"
            ),
            CaptureRule(
                name = "收款到账",
                triggers = listOf(
                    TriggerCondition(textContains = "收款到账"),
                    TriggerCondition(textContains = "到账通知", anyInSubtree = true),
                    TriggerCondition(textContains = "你已成功收款", anyInSubtree = true),
                    TriggerCondition(textContains = "转入到余额", anyInSubtree = true),
                ),
                excludes = listOf(
                    ExcludeCondition(textContains = "账单"),
                ),
                amount = AmountExtract(from = "subtree"),
                type = "INCOME",
                categoryHint = "红包/转账"
            ),
        )),

        // ═══════════════════════════════════════════════════════
        //  微信
        // ═══════════════════════════════════════════════════════
        CapturingApp("com.tencent.mm", "微信", listOf(
            CaptureRule(
                name = "红包",
                triggers = listOf(
                    TriggerCondition(textContains = "红包"),
                    TriggerCondition(textContains = "已存入零钱", anyInSubtree = true),
                ),
                excludes = listOf(
                    ExcludeCondition(textContains = "账单"),
                    ExcludeCondition(textContains = "我的红包"),
                ),
                amount = AmountExtract(from = "subtree"),
                type = "INCOME",
                merchant = MerchantExtract(from = "self", pattern = "红包"),
                categoryHint = "红包/转账"
            ),
            CaptureRule(
                name = "转账-已收款",
                triggers = listOf(
                    TriggerCondition(textContains = "已收款"),
                    TriggerCondition(textContains = "转账给你"),
                    TriggerCondition(textContains = "确认收款"),
                    TriggerCondition(textContains = "转账成功"),
                    TriggerCondition(textContains = "对方已收款"),
                ),
                excludes = listOf(
                    ExcludeCondition(textContains = "账单"),
                    ExcludeCondition(textContains = "交易记录"),
                ),
                amount = AmountExtract(from = "subtree"),
                type = "INCOME",
                categoryHint = "红包/转账"
            ),
            CaptureRule(
                name = "支付成功",
                triggers = listOf(
                    TriggerCondition(textContains = "支付成功"),
                    TriggerCondition(textContains = "付款成功"),
                    TriggerCondition(textContains = "微信支付", anyInSubtree = true),
                    TriggerCondition(descContains = "支付成功"),
                    TriggerCondition(descContains = "¥"),
                ),
                excludes = listOf(
                    ExcludeCondition(textContains = "账单"),
                    ExcludeCondition(textContains = "交易记录"),
                ),
                amount = AmountExtract(from = "subtree"),
                type = "EXPENSE",
                categoryHint = "购物"
            ),
            CaptureRule(
                name = "账单详情",
                triggers = listOf(
                    TriggerCondition(textContains = "账单"),
                    TriggerCondition(idContains = "bill", anyInSubtree = true),
                ),
                amount = AmountExtract(from = "subtree", pattern = AMOUNT_PATTERN_SUBTREE),
                type = "EXPENSE"
            ),
        )),

        // ═══════════════════════════════════════════════════════
        //  拼多多
        // ═══════════════════════════════════════════════════════
        CapturingApp("com.xunmeng.pinduoduo", "拼多多", listOf(
            CaptureRule(
                name = "订单详情",
                triggers = listOf(
                    TriggerCondition(textContains = "订单详情"),
                    TriggerCondition(textContains = "实付"),
                ),
                excludes = listOf(
                    ExcludeCondition(textContains = "待付款"),
                    ExcludeCondition(textContains = "全部订单"),
                ),
                amount = AmountExtract(from = "subtree"),
                categoryHint = "购物"
            ),
            CaptureRule(
                name = "支付成功",
                triggers = listOf(
                    TriggerCondition(textContains = "支付成功"),
                    TriggerCondition(textContains = "付款成功"),
                ),
                amount = AmountExtract(from = "subtree"),
                categoryHint = "购物"
            ),
        )),

        // ═══════════════════════════════════════════════════════
        //  抖音
        // ═══════════════════════════════════════════════════════
        CapturingApp("com.ss.android.ugc.aweme", "抖音", listOf(
            CaptureRule(
                name = "支付成功",
                triggers = listOf(
                    TriggerCondition(textContains = "支付成功"),
                    TriggerCondition(textContains = "付款成功"),
                    TriggerCondition(textContains = "购买成功"),
                    TriggerCondition(textContains = "下单成功"),
                    TriggerCondition(textContains = "订单已提交"),
                ),
                amount = AmountExtract(from = "subtree"),
                categoryHint = "购物"
            ),
            CaptureRule(
                name = "直播间打赏",
                triggers = listOf(
                    TriggerCondition(textContains = "打赏成功"),
                    TriggerCondition(textContains = "送出礼物"),
                ),
                amount = AmountExtract(from = "subtree"),
                categoryHint = "娱乐"
            ),
        )),

        // ═══════════════════════════════════════════════════════
        //  美团
        // ═══════════════════════════════════════════════════════
        CapturingApp("com.sankuai.meituan", "美团", listOf(
            CaptureRule(
                name = "支付成功",
                triggers = listOf(
                    TriggerCondition(textContains = "支付成功"),
                    TriggerCondition(textContains = "付款成功"),
                ),
                amount = AmountExtract(from = "subtree"),
                categoryHint = "餐饮"
            ),
            CaptureRule(
                name = "订单详情",
                triggers = listOf(
                    TriggerCondition(textContains = "订单详情"),
                    TriggerCondition(textContains = "实付"),
                ),
                excludes = listOf(
                    ExcludeCondition(textContains = "全部订单"),
                    ExcludeCondition(textContains = "待付款"),
                ),
                amount = AmountExtract(from = "subtree"),
                categoryHint = "餐饮"
            ),
            CaptureRule(
                name = "外卖订单成功",
                triggers = listOf(
                    TriggerCondition(textContains = "下单成功"),
                    TriggerCondition(textContains = "已接单"),
                    TriggerCondition(textContains = "订单已提交"),
                ),
                amount = AmountExtract(from = "subtree"),
                categoryHint = "餐饮/外卖"
            ),
        )),

        // ═══════════════════════════════════════════════════════
        //  京东
        // ═══════════════════════════════════════════════════════
        CapturingApp("com.jingdong.app.mall", "京东", listOf(
            CaptureRule(
                name = "支付成功",
                triggers = listOf(
                    TriggerCondition(textContains = "支付成功"),
                    TriggerCondition(textContains = "付款成功"),
                ),
                amount = AmountExtract(from = "subtree"),
                categoryHint = "购物"
            ),
            CaptureRule(
                name = "订单详情",
                triggers = listOf(
                    TriggerCondition(textContains = "订单详情"),
                    TriggerCondition(textContains = "实付"),
                ),
                excludes = listOf(
                    ExcludeCondition(textContains = "全部订单"),
                ),
                amount = AmountExtract(from = "subtree"),
                categoryHint = "购物"
            ),
        )),

        // ═══════════════════════════════════════════════════════
        //  云闪付
        // ═══════════════════════════════════════════════════════
        CapturingApp("com.unionpay", "云闪付", listOf(
            CaptureRule(
                name = "支付成功",
                triggers = listOf(
                    TriggerCondition(textContains = "支付成功"),
                    TriggerCondition(textContains = "交易完成"),
                ),
                amount = AmountExtract(from = "subtree"),
                categoryHint = "购物"
            ),
            CaptureRule(
                name = "订单详情",
                triggers = listOf(
                    TriggerCondition(textContains = "订单详情"),
                ),
                amount = AmountExtract(from = "subtree"),
                categoryHint = "购物"
            ),
        )),

        // ═══════════════════════════════════════════════════════
        //  淘宝
        // ═══════════════════════════════════════════════════════
        CapturingApp("com.taobao.taobao", "淘宝", listOf(
            CaptureRule(
                name = "支付成功",
                triggers = listOf(
                    TriggerCondition(textContains = "支付成功"),
                    TriggerCondition(textContains = "付款成功"),
                    TriggerCondition(textContains = "交易成功"),
                    TriggerCondition(textContains = "已完成"),
                    TriggerCondition(textContains = "下单成功"),
                    TriggerCondition(textContains = "购买成功"),
                ),
                excludes = listOf(
                    ExcludeCondition(textContains = "全部订单"),
                    ExcludeCondition(textContains = "待付款"),
                    ExcludeCondition(textContains = "待发货"),
                ),
                amount = AmountExtract(from = "subtree"),
                merchant = MerchantExtract(from = "subtree"),
                categoryHint = "购物"
            ),
            CaptureRule(
                name = "订单详情",
                triggers = listOf(
                    TriggerCondition(textContains = "订单详情"),
                    TriggerCondition(textContains = "实付款"),
                ),
                amount = AmountExtract(from = "subtree"),
                categoryHint = "购物"
            ),
        )),

        // ═══════════════════════════════════════════════════════
        //  饿了么
        // ═══════════════════════════════════════════════════════
        CapturingApp("me.ele", "饿了么", listOf(
            CaptureRule(
                name = "支付成功",
                triggers = listOf(
                    TriggerCondition(textContains = "支付成功"),
                    TriggerCondition(textContains = "下单成功"),
                    TriggerCondition(textContains = "订单已提交"),
                    TriggerCondition(textContains = "正在为你配送", anyInSubtree = true),
                ),
                amount = AmountExtract(from = "subtree"),
                categoryHint = "餐饮/外卖"
            ),
            CaptureRule(
                name = "订单详情",
                triggers = listOf(
                    TriggerCondition(textContains = "订单详情"),
                    TriggerCondition(textContains = "实付"),
                ),
                amount = AmountExtract(from = "subtree"),
                categoryHint = "餐饮/外卖"
            ),
        )),
    )
}

data class RuleMatchResult(
    val rule: CaptureRule,
    val amount: Long,
    val merchant: String?
)

/**
 * 金额正则模式
 */
private val AMOUNT_PATTERN_SUBTREE = """(?:¥|￥)?\s*(\d+(?:,\d{3})*(?:\.\d{1,2})?)\s*(?:元)?"""
private val AMOUNT_REGEX = Regex("""(?:¥|￥)?\s*(\d+(?:,\d{3})*(?:\.\d{1,2})?)\s*(?:元)?""")
private val AMOUNT_PURE = Regex("""\d+(?:\.\d{1,2})?""")

/**
 * 分类提示 → 实际分类名映射
 */
private val CATEGORY_HINT_MAP = mapOf(
    "餐饮" to setOf("餐饮", "美食"),
    "餐饮/外卖" to setOf("餐饮", "外卖", "美食"),
    "购物" to setOf("购物", "日用", "百货"),
    "娱乐" to setOf("娱乐", "游戏"),
    "交通" to setOf("交通"),
    "红包/转账" to setOf("红包", "转账"),
)

/**
 * 规则引擎 — 遍历节点树并匹配规则
 */
class CaptureEngine {

    companion object {
        private const val TAG = "CaptureEngine"

        /**
         * 在节点树中运行所有规则，返回第一个匹配结果
         */
        fun runRules(root: AccessibilityNodeInfo, pkg: String): RuleMatchResult? {
            val app = CaptureRules.ALL.find { it.packageName == pkg } ?: return null

            val allText = getAllText(root)

            for (rule in app.rules) {
                // 排除条件检查 — 页面包含排除文本时不触发
                if (isExcluded(allText, rule.excludes)) {
                    Log.d(TAG, "Rule '${rule.name}' excluded by exclude conditions")
                    continue
                }

                // 先检查触发条件
                val matched = findTriggerNode(root, rule.triggers)
                if (matched != null) {
                    Log.d(TAG, "Rule '${rule.name}' triggered on ${app.label}")

                    // 提取金额
                    val amount = extractAmount(allText, root, rule.amount)
                    if (amount != null && amount > 0) {
                        val merchant = extractMerchant(allText, root, rule.merchant)
                        return RuleMatchResult(
                            rule = rule,
                            amount = amount,
                            merchant = merchant ?: app.label
                        )
                    }
                }
            }
            return null
        }

        /**
         * 检查页面文本是否触发了排除条件
         */
        private fun isExcluded(allText: String, excludes: List<ExcludeCondition>): Boolean {
            for (exclude in excludes) {
                if (exclude.textContains != null && allText.contains(exclude.textContains)) {
                    return true
                }
                if (exclude.descContains != null && allText.contains(exclude.descContains)) {
                    return true
                }
            }
            return false
        }

        // ══════════════════════════════════════════════
        //  节点匹配
        // ══════════════════════════════════════════════

        private fun findTriggerNode(
            node: AccessibilityNodeInfo,
            triggers: List<TriggerCondition>
        ): AccessibilityNodeInfo? {
            for (trigger in triggers) {
                val found = findNodeInSubtree(node, trigger)
                if (found != null) return found
            }
            return null
        }

        private fun findNodeInSubtree(
            node: AccessibilityNodeInfo,
            condition: TriggerCondition
        ): AccessibilityNodeInfo? {
            if (matchesCondition(node, condition)) return node
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    val found = findNodeInSubtree(child, condition)
                    if (found != null) return found
                }
            }
            return null
        }

        private fun matchesCondition(
            node: AccessibilityNodeInfo,
            condition: TriggerCondition
        ): Boolean {
            if (condition.textContains != null) {
                val text = node.text?.toString() ?: ""
                if (text.contains(condition.textContains)) return true
            }
            if (condition.descContains != null) {
                val desc = node.contentDescription?.toString() ?: ""
                if (desc.contains(condition.descContains)) return true
            }
            if (condition.idContains != null) {
                val id = node.viewIdResourceName ?: ""
                if (id.contains(condition.idContains)) return true
            }
            if (condition.classNameContains != null) {
                val cn = node.className?.toString() ?: ""
                if (cn.contains(condition.classNameContains)) return true
            }
            return false
        }

        // ══════════════════════════════════════════════
        //  金额提取
        // ══════════════════════════════════════════════

        private fun extractAmount(
            allText: String,
            root: AccessibilityNodeInfo,
            config: AmountExtract
        ): Long? {
            val text = when (config.from) {
                "subtree" -> allText
                "self" -> nodeText(root)
                "parent" -> root.parent?.let { getAllText(it) } ?: ""
                else -> allText
            }

            if (config.pattern != null) {
                val customRegex = Regex(config.pattern)
                val m = customRegex.find(text)
                if (m != null) {
                    val numStr = m.groupValues.last().replace(",", "")
                    val amount = numStr.toDoubleOrNull() ?: return null
                    if (isValidAmount(amount)) return (amount * 100).toLong()
                }
            }

            // 默认金额匹配
            val amtMatch = AMOUNT_REGEX.find(text) ?: AMOUNT_PURE.find(text)
            if (amtMatch != null) {
                val numStr = amtMatch.groupValues.last().replace(",", "")
                val amount = numStr.toDoubleOrNull() ?: return null
                if (isValidAmount(amount)) {
                    return (amount * 100).toLong()
                }
            }
            return null
        }

        /**
         * 验证金额是否有效 — 过滤年份数、过小/过大的值
         */
        private fun isValidAmount(amount: Double): Boolean {
            if (amount <= 0 || amount >= 999_999) return false
            // 过滤年份数字 (2020~2035 且无小数)
            if (amount in 2020.0..2035.0 && amount % 1 == 0.0) return false
            // 过滤明显不是金额的整数（如电话号码片段: 10086, 95588）
            if (amount % 1 == 0.0 && amount in 1000.0..99999.0) return false
            return true
        }

        // ══════════════════════════════════════════════
        //  商户提取
        // ══════════════════════════════════════════════

        private fun extractMerchant(
            allText: String,
            root: AccessibilityNodeInfo,
            config: MerchantExtract?
        ): String? {
            if (config == null) return null
            return when (config.from) {
                "self" -> {
                    val text = nodeText(root)
                    if (text.length in 1..30) text else null
                }
                "subtree" -> {
                    // 尝试匹配商户名模式
                    val merchantMatch = MERCHANT_PATTERN.find(allText)
                    if (merchantMatch != null) {
                        merchantMatch.groupValues.last().trim().take(20)
                    } else {
                        allText.take(20).takeIf { it.isNotBlank() }
                    }
                }
                else -> null
            }
        }

        private val MERCHANT_PATTERN = Regex("""((?:商家|商户|店铺|收款方|对方|向)[：:]?\s*(.{1,15}))""")

        // ══════════════════════════════════════════════
        //  文本工具
        // ══════════════════════════════════════════════

        private fun nodeText(node: AccessibilityNodeInfo): String {
            return node.text?.toString()?.trim() ?: ""
        }

        private fun getAllText(node: AccessibilityNodeInfo): String {
            val sb = StringBuilder()
            collectText(node, sb)
            return sb.toString()
        }

        private fun collectText(node: AccessibilityNodeInfo, sb: StringBuilder) {
            val text = node.text?.toString()?.takeIf { it.isNotBlank() }
            val desc = node.contentDescription?.toString()?.takeIf { it.isNotBlank() }
            if (text != null) sb.append(text).append(" ")
            if (desc != null) sb.append(desc).append(" ")
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { collectText(it, sb) }
            }
        }
    }
}

data class CapturingApp(
    val packageName: String,
    val label: String,
    val rules: List<CaptureRule>
)
