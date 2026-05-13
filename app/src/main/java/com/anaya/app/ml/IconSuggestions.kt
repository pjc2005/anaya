package com.anaya.app.ml

/**
 * Emoji icon suggestions for categories based on keywords or category name.
 */
object IconSuggestions {

    private val keywordIconMap = listOf(
        // 餐饮
        "外卖" to "🍱", "饿了么" to "🍱", "美团" to "🍔", "餐厅" to "🍽️",
        "食堂" to "🍽️", "吃饭" to "🍚", "午餐" to "🍱", "晚餐" to "🍽️",
        "早餐" to "🥐", "KFC" to "🍗", "麦当劳" to "🍔", "星巴克" to "☕",
        "咖啡" to "☕", "奶茶" to "🧋", "面包" to "🍞", "蛋糕" to "🎂",
        "火锅" to "🫕", "烧烤" to "🍖", "寿司" to "🍣",

        // 交通
        "滴滴" to "🚗", "打车" to "🚕", "出租车" to "🚕", "地铁" to "🚇",
        "公交" to "🚌", "火车" to "🚄", "高铁" to "🚄", "飞机" to "✈️",
        "加油" to "⛽", "停车" to "🅿️", "共享单车" to "🚲",

        // 购物
        "淘宝" to "🛒", "京东" to "🛒", "拼多多" to "🛒", "超市" to "🛒",
        "商场" to "🏬", "便利店" to "🏪", "网购" to "📦",

        // 娱乐
        "电影" to "🎬", "游戏" to "🎮", "KTV" to "🎤", "旅游" to "✈️",
        "酒店" to "🏨", "门票" to "🎫",

        // 医疗
        "医院" to "🏥", "药" to "💊", "诊所" to "🏥",

        // 教育
        "课程" to "📚", "培训" to "📖", "书" to "📚", "学习" to "📝",

        // 住房
        "房租" to "🏠", "物业" to "🏠", "水电" to "💡", "燃气" to "🔥",
        "宽带" to "🌐",

        // 工资/收入
        "工资" to "💰", "奖金" to "💵", "兼职" to "💼",

        // 红包
        "红包" to "🧧",

        // 其他
        "快递" to "📦", "理发" to "💇", "健身" to "💪",
        "宠物" to "🐾", "礼物" to "🎁", "捐款" to "❤️",
    )

    private val categoryIconMap = mapOf(
        "餐饮" to "🍽️",
        "交通" to "🚗",
        "购物" to "🛒",
        "娱乐" to "🎮",
        "医疗" to "💊",
        "教育" to "📚",
        "住房" to "🏠",
        "通讯" to "📱",
        "日用品" to "🧴",
        "服饰" to "👕",
        "美容" to "💄",
        "宠物" to "🐾",
        "转账" to "🔄",
        "红包" to "🧧",
        "工资" to "💰",
        "理财" to "📈",
        "退款" to "↩️",
        "其他" to "📄",
    )

    /**
     * Suggest an emoji icon based on the input text (merchant, note, etc.).
     */
    fun suggestIcon(text: String): String? {
        val lower = text.lowercase()
        for ((keyword, icon) in keywordIconMap) {
            if (lower.contains(keyword.lowercase())) return icon
        }
        return null
    }

    /**
     * Suggest an emoji icon based on the category name.
     */
    fun suggestIconForCategory(categoryName: String): String? {
        return categoryIconMap[categoryName]
            ?: categoryIconMap.entries.firstOrNull { categoryName.contains(it.key) }?.value
            ?: "📄"
    }
}
