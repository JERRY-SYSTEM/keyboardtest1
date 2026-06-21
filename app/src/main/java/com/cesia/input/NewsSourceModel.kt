package com.cesia.input

/**
 * 新闻源数据模型
 * @param id 唯一标识
 * @param name 网站名称
 * @param url Firecrawl 抓取地址
 * @param category 门类分类（国际、财经、科技、中国、社会）
 * @param language 语言（zh/en）
 * @param tags AI 匹配用的标签关键词
 */
data class NewsSource(
    val id: String,
    val name: String,
    val url: String,
    val category: String,
    val language: String = "zh",
    val tags: List<String> = emptyList()
)

/**
 * 默认新闻源列表
 */
val DEFAULT_NEWS_SOURCES = listOf(
    NewsSource("zaobao", "联合早报", "https://www.zaobao.com.sg/realtime/china", "中国", "zh",
        listOf("中国", "新加坡", "中文", "时事", "社会")),
    NewsSource("bbc_zh", "BBC中文", "https://www.bbc.com/zhongwen/simp", "国际", "zh",
        listOf("国际", "英文", "中文", "政治", "社会")),
    NewsSource("reuters", "路透社", "https://www.reuters.com/world/middle-east/", "国际", "en",
        listOf("国际", "中东", "政治", "战争", "经济", "英文")),
    NewsSource("thepaper", "澎湃新闻", "https://www.thepaper.cn", "中国", "zh",
        listOf("中国", "新闻", "时事", "社会", "法治")),
    NewsSource("caixin", "财新传媒", "https://www.caixin.com", "财经", "zh",
        listOf("财经", "经济", "金融", "股市", "投资", "中国")),
    NewsSource("wsj", "华尔街日报", "https://www.wsj.com/news/types/markets", "财经", "en",
        listOf("财经", "经济", "股市", "投资", "英文", "国际")),
    NewsSource("36kr", "36氪", "https://36kr.com", "科技", "zh",
        listOf("科技", "AI", "创业", "互联网", "人工智能", "软件")),
    NewsSource("hn", "Hacker News", "https://news.ycombinator.com", "科技", "en",
        listOf("科技", "AI", "编程", "软件", "互联网", "英文", "创业")),
    NewsSource("cnn", "CNN", "https://www.cnn.cn/world", "国际", "en",
        listOf("国际", "英文", "政治", "科学", "健康", "社会")),
    NewsSource("bloomberg", "彭博社", "https://www.bloomberg.com/markets", "财经", "en",
        listOf("财经", "经济", "金融", "投资", "英文", "贸易"))
)

/**
 * 门类列表
 */
val NEWS_CATEGORIES = listOf("国际", "财经", "科技", "中国", "社会")
