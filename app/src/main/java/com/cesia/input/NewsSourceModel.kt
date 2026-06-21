package com.cesia.input

/**
 * 新闻分类列表
 */
val RSS_CATEGORIES = listOf("国际", "财经", "科技", "中国", "社会", "综合")

/**
 * 每个分类对应的默认 RSS URL
 * key = 分类名, value = RSS URL
 */
val DEFAULT_CATEGORY_RSS_URLS: Map<String, String> = mapOf(
    "科技" to "https://www.ifanr.com/feed",
    "财经" to "https://www.huxiu.com/feed",
    "中国" to "https://www.thepaper.cn/rss",
    "国际" to "https://feeds.bbci.co.uk/zhongwen/simp/rss.xml",
    "社会" to "https://www.smzdm.com/feed",
    "综合" to "https://36kr.com/feed"
)
