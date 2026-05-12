package com.anaya.app.ml

object RuleBasedClassifier {
    private data class Rule(val keywords: List<String>, val categoryName: String)

    private val EXPENSE_RULES = listOf(
        Rule(listOf("美团", "外卖", "饿了么", "餐厅", "食堂", "吃饭", "午餐", "晚餐", "KFC", "麦当劳", "星巴克", "咖啡", "奶茶", "面包"), "餐饮"),
        Rule(listOf("滴滴", "打车", "出租车", "地铁", "公交", "火车", "高铁", "飞机", "加油", "加油站", "停车"), "交通"),
        Rule(listOf("超市", "便利店", "淘宝", "京东", "拼多多", "衣服", "鞋", "包", "日用品", "百货"), "购物"),
        Rule(listOf("房租", "水电", "物业", "燃气", "宽带", "网费"), "居住"),
        Rule(listOf("电影", "游戏", "KTV", "旅游", "门票", "视频会员", "直播", "打赏"), "娱乐"),
        Rule(listOf("话费", "流量", "手机"), "通讯"),
        Rule(listOf("药店", "医院", "看病", "挂号", "体检", "牙科", "药"), "医疗"),
        Rule(listOf("理发", "美容", "护肤", "健身", "游泳", "瑜伽"), "美容/健身"),
        Rule(listOf("书", "课程", "培训", "考试", "报名", "教育", "文具"), "教育"),
        Rule(listOf("彩票", "投资", "理财"), "投资/彩票")
    )

    private val INCOME_RULES = listOf(
        Rule(listOf("工资", "薪水", "奖金", "补贴", "绩效"), "工资"),
        Rule(listOf("兼职", "外快", "副业"), "兼职"),
        Rule(listOf("理财收益", "利息", "分红", "基金收益"), "理财"),
        Rule(listOf("红包", "收到红包"), "红包/转账")
    )

    fun classify(merchant: String?, note: String?): ClassificationResult {
        val text = listOfNotNull(merchant, note).joinToString(" ").lowercase()
        if (text.isBlank()) return ClassificationResult()

        for (rule in EXPENSE_RULES) {
            val match = rule.keywords.firstOrNull { text.contains(it, ignoreCase = true) }
            if (match != null) {
                return ClassificationResult(confidence = 0.7f, explanation = match)
            }
        }
        return ClassificationResult(confidence = 0.1f)
    }

    fun suggestCategoryName(merchant: String?, note: String?): String? {
        val text = listOfNotNull(merchant, note).joinToString(" ").lowercase()
        if (text.isBlank()) return null
        (EXPENSE_RULES + INCOME_RULES).forEach { rule ->
            val match = rule.keywords.firstOrNull { text.contains(it, ignoreCase = true) }
            if (match != null) return rule.categoryName
        }
        return null
    }
}
