package com.cesia.input.engine.rime

import android.content.Context
import android.util.Log

/**
 * Rime JNI 桥接层 - 纯 Kotlin stub 实现
 *
 * 当前不依赖 native 库，所有逻辑在 Kotlin 层实现
 * 后续替换为 JNI native 调用
 */
object RimeJni {

    private const val TAG = "RimeJni"

    @Volatile
    private var initialized = false

    private val sessions = mutableMapOf<Long, StubSession>()
    private var nextSessionId = 1L

    private data class StubSession(
        var composing: String = "",
        var candidates: List<String> = emptyList(),
        var totalCandidates: Int = 0,
        var pageSize: Int = 5,
        var currentPage: Int = 0
    ) {
        val pageCount: Int
            get() = if (totalCandidates <= 0) 0 else (totalCandidates + pageSize - 1) / pageSize
    }

    fun isAvailable(): Boolean = true

    fun unavailableMessage(): String? = null

    fun initialize(context: Context): Boolean {
        if (initialized) return true
        Log.i(TAG, "Rime stub 引擎初始化")
        initialized = true
        return true
    }

    fun shutdown() {
        sessions.clear()
        initialized = false
    }

    fun createSession(): RimeSession {
        val id = nextSessionId++
        sessions[id] = StubSession()
        return RimeSession(id)
    }

    fun destroySession(session: RimeSession) {
        sessions.remove(session.id)
    }

    fun processKey(sessionId: Long, key: String): Boolean {
        val s = sessions[sessionId] ?: return false

        if (key == "BackSpace" || key == "Back") {
            if (s.composing.isNotEmpty()) {
                s.composing = s.composing.dropLast(1)
            }
        } else if (key == "space" || key == "Space") {
            if (s.candidates.isNotEmpty()) {
                s.composing = ""
                s.candidates = emptyList()
                s.totalCandidates = 0
            }
            return true
        } else {
            s.composing += key
        }

        updateCandidates(s)
        return true
    }

    fun getComposingText(sessionId: Long): String {
        return sessions[sessionId]?.composing ?: ""
    }

    fun getCandidates(sessionId: Long): List<String> {
        val s = sessions[sessionId] ?: return emptyList()
        val start = s.currentPage * s.pageSize
        val end = minOf(start + s.pageSize, s.candidates.size)
        if (start >= s.candidates.size) return emptyList()
        return s.candidates.subList(start, end)
    }

    fun commitComposition(sessionId: Long): String {
        val s = sessions[sessionId] ?: return ""
        val result = s.composing
        s.composing = ""
        s.candidates = emptyList()
        s.totalCandidates = 0
        return result
    }

    fun selectCandidate(sessionId: Long, index: Int): String {
        val s = sessions[sessionId] ?: return ""
        val actualIndex = s.currentPage * s.pageSize + index
        if (actualIndex < 0 || actualIndex >= s.candidates.size) return ""
        val result = s.candidates[actualIndex]
        s.composing = ""
        s.candidates = emptyList()
        s.totalCandidates = 0
        return result
    }

    fun clearComposition(sessionId: Long) {
        sessions[sessionId]?.let {
            it.composing = ""
            it.candidates = emptyList()
            it.totalCandidates = 0
            it.currentPage = 0
        }
    }

    fun changePage(sessionId: Long, backward: Boolean): Boolean {
        val s = sessions[sessionId] ?: return false
        if (s.pageCount <= 1) return false
        if (backward) {
            if (s.currentPage > 0) s.currentPage--
        } else {
            if (s.currentPage < s.pageCount - 1) s.currentPage++
        }
        return true
    }

    fun getPageCount(sessionId: Long): Int {
        return sessions[sessionId]?.pageCount ?: 0
    }

    fun getCurrentPage(sessionId: Long): Int {
        return sessions[sessionId]?.currentPage ?: 0
    }

    private fun updateCandidates(s: StubSession) {
        if (s.composing.isEmpty()) {
            s.candidates = emptyList()
            s.totalCandidates = 0
            s.currentPage = 0
            return
        }
        val dict = PinyinDictHelper.getDict()
        val exact = dict[s.composing]
        if (exact != null) {
            s.candidates = exact
        } else {
            val prefixMatches = mutableListOf<String>()
            for ((k, v) in dict) {
                if (k.startsWith(s.composing) && k != s.composing) {
                    prefixMatches.addAll(v)
                }
            }
            s.candidates = prefixMatches.distinct()
        }
        if (s.candidates.isEmpty()) {
            s.candidates = listOf(s.composing)
        }
        s.totalCandidates = s.candidates.size
        s.currentPage = 0
    }
}

/**
 * 内置拼音字典
 */
private object PinyinDictHelper {
    fun getDict(): Map<String, List<String>> {
        return dict
    }

    private val dict: Map<String, List<String>> = mapOf(
        "a" to listOf("啊", "阿", "爱", "安", "按"),
        "b" to listOf("不", "爸", "把", "被", "本"),
        "c" to listOf("从", "才", "次", "此", "错"),
        "d" to listOf("的", "大", "到", "地", "多"),
        "e" to listOf("嗯", "饿", "额"),
        "f" to listOf("发", "放", "非", "分", "方"),
        "g" to listOf("个", "给", "过", "高", "公"),
        "h" to listOf("好", "还", "很", "会", "和"),
        "i" to listOf("一", "有", "要", "也", "以"),
        "j" to listOf("就", "几", "将", "及", "机"),
        "k" to listOf("看", "可", "开", "快", "考"),
        "l" to listOf("了", "来", "里", "两", "老"),
        "m" to listOf("没", "吗", "么", "每", "美"),
        "n" to listOf("你", "那", "呢", "年", "能"),
        "o" to listOf("哦", "噢"),
        "p" to listOf("跑", "怕", "平", "片", "旁"),
        "q" to listOf("去", "请", "前", "且", "其"),
        "r" to listOf("人", "让", "如", "日", "入"),
        "s" to listOf("是", "说", "生", "时", "三"),
        "t" to listOf("他", "她", "它", "听", "天"),
        "u" to listOf("出", "成", "长", "车", "吃"),
        "v" to listOf("这", "着", "只", "中", "之"),
        "w" to listOf("我", "问", "无", "为", "外"),
        "x" to listOf("下", "想", "小", "新", "心"),
        "y" to listOf("一", "有", "要", "也", "以"),
        "z" to listOf("在", "再", "做", "最", "真"),
        "bu" to listOf("不", "部", "步", "布", "补"),
        "ba" to listOf("把", "爸", "吧", "八", "百"),
        "bai" to listOf("白", "百", "拜", "摆", "败"),
        "ban" to listOf("半", "班", "办", "板", "般"),
        "bao" to listOf("报", "保", "包", "宝", "抱"),
        "bei" to listOf("被", "北", "备", "背", "杯"),
        "ben" to listOf("本", "奔"),
        "bi" to listOf("比", "必", "笔", "毕"),
        "bian" to listOf("边", "变", "便", "遍", "编"),
        "bo" to listOf("不", "波", "博", "播"),
        "da" to listOf("大", "打", "达"),
        "dai" to listOf("大", "代", "带", "待"),
        "dan" to listOf("但", "单", "担", "蛋", "当"),
        "dang" to listOf("当", "党"),
        "dao" to listOf("到", "道", "倒", "导"),
        "de" to listOf("的", "得", "德"),
        "di" to listOf("地", "第", "低", "底", "敌"),
        "dou" to listOf("都", "斗"),
        "du" to listOf("读", "独", "度"),
        "dui" to listOf("对", "队"),
        "duo" to listOf("多"),
        "er" to listOf("而", "儿", "二", "耳"),
        "fa" to listOf("发", "法"),
        "fan" to listOf("反", "饭", "翻"),
        "fang" to listOf("方", "放", "房", "访"),
        "fei" to listOf("非", "飞", "费"),
        "fen" to listOf("分", "份"),
        "fu" to listOf("父", "夫", "服", "复", "负"),
        "gai" to listOf("该", "改", "概"),
        "gan" to listOf("干", "感", "敢"),
        "gao" to listOf("高", "告"),
        "ge" to listOf("个", "各", "歌", "格"),
        "gei" to listOf("给"),
        "gong" to listOf("公", "工", "功"),
        "gu" to listOf("古", "故", "顾"),
        "gua" to listOf("挂"),
        "guan" to listOf("关", "观", "管"),
        "gui" to listOf("贵", "鬼"),
        "guo" to listOf("国", "过", "果"),
        "hai" to listOf("还", "海", "害"),
        "han" to listOf("汉", "寒", "喊"),
        "hao" to listOf("好", "号"),
        "he" to listOf("和", "何", "合", "河"),
        "hen" to listOf("很", "恨"),
        "hong" to listOf("红"),
        "hou" to listOf("后", "候"),
        "hu" to listOf("乎", "呼", "胡"),
        "hua" to listOf("话", "花", "化"),
        "huan" to listOf("还", "换", "欢"),
        "huang" to listOf("黄", "皇"),
        "hui" to listOf("会", "回", "灰"),
        "huo" to listOf("或", "火", "活"),
        "ji" to listOf("几", "己", "及", "即", "机"),
        "jia" to listOf("家", "加", "价"),
        "jian" to listOf("见", "间", "件", "建"),
        "jiang" to listOf("将", "讲"),
        "jiao" to listOf("叫", "教", "觉"),
        "jie" to listOf("结", "接", "节", "姐"),
        "jin" to listOf("进", "今", "金"),
        "jing" to listOf("经", "京", "精"),
        "jiu" to listOf("就", "九", "久"),
        "ju" to listOf("举", "句", "具"),
        "ke" to listOf("可", "科", "克"),
        "kong" to listOf("空"),
        "kuai" to listOf("快"),
        "kuang" to listOf("况"),
        "lai" to listOf("来"),
        "lan" to listOf("蓝"),
        "lao" to listOf("老"),
        "le" to listOf("了", "乐"),
        "lei" to listOf("类"),
        "li" to listOf("里", "力", "立", "理"),
        "lian" to listOf("连", "脸"),
        "liang" to listOf("两", "亮"),
        "ling" to listOf("另", "领"),
        "liu" to listOf("六", "流"),
        "lu" to listOf("路"),
        "lv" to listOf("绿"),
        "ma" to listOf("吗", "马", "妈"),
        "mai" to listOf("买", "卖"),
        "man" to listOf("满"),
        "mang" to listOf("忙"),
        "me" to listOf("么"),
        "mei" to listOf("没", "每", "美"),
        "men" to listOf("们", "门"),
        "mi" to listOf("米"),
        "mian" to listOf("面"),
        "ming" to listOf("名", "明"),
        "mo" to listOf("么"),
        "mu" to listOf("母", "目"),
        "na" to listOf("那", "拿"),
        "nan" to listOf("男", "南"),
        "neng" to listOf("能"),
        "ni" to listOf("你"),
        "nian" to listOf("年"),
        "niang" to listOf("娘"),
        "nong" to listOf("农"),
        "nv" to listOf("女"),
        "ou" to listOf("偶"),
        "pai" to listOf("排"),
        "pan" to listOf("盘"),
        "pang" to listOf("旁"),
        "pao" to listOf("跑"),
        "pei" to listOf("配"),
        "pi" to listOf("皮"),
        "pian" to listOf("片"),
        "ping" to listOf("平"),
        "po" to listOf("破"),
        "qi" to listOf("起", "其", "七", "气"),
        "qian" to listOf("前", "千", "钱"),
        "qiang" to listOf("强"),
        "qiao" to listOf("桥"),
        "qin" to listOf("亲"),
        "qing" to listOf("请", "情", "清"),
        "qiu" to listOf("求"),
        "qu" to listOf("去", "取"),
        "quan" to listOf("全"),
        "que" to listOf("却", "确"),
        "ran" to listOf("然"),
        "rang" to listOf("让"),
        "re" to listOf("热"),
        "ren" to listOf("人"),
        "ri" to listOf("日"),
        "rong" to listOf("容"),
        "ru" to listOf("如"),
        "shang" to listOf("上"),
        "shao" to listOf("少"),
        "she" to listOf("社"),
        "shen" to listOf("什", "身"),
        "sheng" to listOf("生", "声"),
        "shi" to listOf("是", "时", "十", "事"),
        "shou" to listOf("手"),
        "shu" to listOf("书"),
        "shui" to listOf("水"),
        "shuo" to listOf("说"),
        "si" to listOf("四", "死"),
        "song" to listOf("送"),
        "su" to listOf("速"),
        "sui" to listOf("虽", "岁"),
        "suo" to listOf("所"),
        "tai" to listOf("太"),
        "tan" to listOf("谈"),
        "tang" to listOf("汤"),
        "tao" to listOf("讨"),
        "te" to listOf("特"),
        "ti" to listOf("题"),
        "tian" to listOf("天"),
        "tiao" to listOf("条"),
        "ting" to listOf("听"),
        "tong" to listOf("同"),
        "tou" to listOf("头"),
        "tu" to listOf("图"),
        "wan" to listOf("完", "万"),
        "wang" to listOf("王", "往"),
        "wei" to listOf("为", "位"),
        "wen" to listOf("文", "问"),
        "wo" to listOf("我"),
        "wu" to listOf("无", "五"),
        "xi" to listOf("西", "希"),
        "xia" to listOf("下"),
        "xian" to listOf("先", "现"),
        "xiang" to listOf("想", "向"),
        "xiao" to listOf("小"),
        "xie" to listOf("写"),
        "xin" to listOf("新"),
        "xing" to listOf("行"),
        "xiu" to listOf("修"),
        "xu" to listOf("需"),
        "xuan" to listOf("选"),
        "xue" to listOf("学"),
        "ya" to listOf("呀"),
        "yan" to listOf("言"),
        "yang" to listOf("样"),
        "yao" to listOf("要"),
        "ye" to listOf("也"),
        "yi" to listOf("一", "以"),
        "yin" to listOf("因"),
        "ying" to listOf("应"),
        "yong" to listOf("用"),
        "you" to listOf("有"),
        "yu" to listOf("与"),
        "yuan" to listOf("元"),
        "yue" to listOf("月"),
        "yun" to listOf("运"),
        "zai" to listOf("在"),
        "zan" to listOf("赞"),
        "zao" to listOf("早"),
        "ze" to listOf("则"),
        "zeng" to listOf("增"),
        "zhao" to listOf("找"),
        "zhe" to listOf("这"),
        "zhen" to listOf("真"),
        "zheng" to listOf("正"),
        "zhi" to listOf("只", "知"),
        "zhong" to listOf("中"),
        "zhou" to listOf("周"),
        "zhu" to listOf("主"),
        "zhua" to listOf("抓"),
        "zhuan" to listOf("转"),
        "zhuang" to listOf("装"),
        "zhui" to listOf("追"),
        "zhun" to listOf("准"),
        "zhuo" to listOf("着"),
        "zi" to listOf("子"),
        "zong" to listOf("总"),
        "zou" to listOf("走"),
        "zu" to listOf("组"),
        "zui" to listOf("最"),
        "zuo" to listOf("做"),
        "women" to listOf("我们"),
        "nimen" to listOf("你们"),
        "tamen" to listOf("他们"),
        "zhege" to listOf("这个"),
        "nage" to listOf("那个"),
        "shenme" to listOf("什么"),
        "zenme" to listOf("怎么"),
        "zaijian" to listOf("再见"),
        "xiexie" to listOf("谢谢"),
        "duibuqi" to listOf("对不起"),
        "meiyou" to listOf("没有"),
        "keyi" to listOf("可以"),
        "yinggai" to listOf("应该"),
        "xuyao" to listOf("需要"),
        "zhidao" to listOf("知道"),
        "renshi" to listOf("认识"),
        "xihuan" to listOf("喜欢"),
        "haochi" to listOf("好吃"),
        "henhao" to listOf("很好"),
        "buhao" to listOf("不好"),
        "taihao" to listOf("太好"),
        "buyao" to listOf("不要"),
        "meiwei" to listOf("美味")
    )
}
