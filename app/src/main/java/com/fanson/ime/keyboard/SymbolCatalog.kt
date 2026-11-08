package com.fanson.ime.keyboard

data class SymbolCategory(val name: String, val symbols: List<String>)

/** Independently curated Unicode entries. Category order and item order are fixed. */
object SymbolCatalog {
    private fun chars(text: String): List<String> = text.codePoints().toArray().map { String(Character.toChars(it)) }
    private fun range(first: Int, last: Int): List<String> = (first..last).map { String(Character.toChars(it)) }
    private fun category(name: String, items: List<String>) = SymbolCategory(name, items.distinct())

    val categories: List<SymbolCategory> = listOf(
        category("常用", listOf("，", "。", "、", "？", "！", "：", "；", "“", "”", "‘", "’", "（", "）", "《", "》", "【", "】", "……", "——", "·", "～", "@", "#", "￥", "%", "&", "*", "+", "-", "=", "/", "\\", "→", "✓", "★", "℃")),
        category("中文", listOf("，", "。", "、", "？", "！", "：", "；", "“", "”", "‘", "’", "（", "）", "《", "》", "〈", "〉", "【", "】", "〔", "〕", "「", "」", "『", "』", "〖", "〗", "……", "——", "…", "—", "·", "～", "﹏", "＿", "﹑", "﹔", "﹕", "﹖", "﹗", "﹙", "﹚", "﹛", "﹜", "﹝", "﹞", "﹁", "﹂", "﹃", "﹄")),
        category("英文", (33..126).filter { !it.toChar().isLetterOrDigit() }.map { it.toChar().toString() }),
        category("箭头", range(0x2190, 0x21FF)),
        category("数学", chars("＋－×÷±∓＝≠≈≡≤≥<>≪≫∞∝∑∏√∛∜∫∬∭∮∯∰∂∇∆∀∃∄∈∉∋∌⊂⊃⊄⊅⊆⊇⊈⊉∪∩∅∧∨¬∠∟⊥∥∦≅≌≒∽⊕⊖⊗⊘⊙⊢⊣⊤⊨∴∵∶∷∎ℕℤℚℝℂ")),
        category("序号", range(0x2460, 0x2473) + range(0x3251, 0x325F) + range(0x32B1, 0x32BF) +
            range(0x2474, 0x2487) + range(0x2160, 0x216B) + range(0x2170, 0x217B) + range(0x3280, 0x3289)),
        category("单位", listOf("℃", "℉", "°", "′", "″", "%", "‰", "‱", "㎜", "㎝", "㎞", "㎡", "㎢", "㎥", "㎎", "㎏", "㏄", "㏎", "㏑", "㏒", "㏕", "ℓ", "m²", "m³", "km/h", "m/s", "μm", "nm", "kPa", "MPa", "Hz", "kHz", "MHz", "GHz", "W", "kW", "MW", "V", "mV", "A", "mA", "Ω", "kΩ", "MΩ", "J", "kJ", "cal", "kcal")),
        category("货币", chars("￥¥\$€£₩₽₹¢₺₫฿₱₪₴₦₡₲₵₸₼₾₿¤")),
        category("括号", chars("()[]{}（）【】［］｛｝《》〈〉「」『』〔〕〖〗〘〙〚〛⟨⟩⟪⟫⌈⌉⌊⌋⦅⦆")),
        category("图形", chars("○●◯◎◉◌◍◐◑◒◓◔◕◇◆◈◊□■▢▣▤▥▦▧▨▩▫▪△▲▽▼◁◀▷▶◂▸☆★✩✪✫✬✭✮✯✰♡♥♢♦♤♠♧♣✓✔✗✘※☑☒•◦‧‖†‡§¶♪♫♬♭♮♯")),
        category("希腊", chars("ΑΒΓΔΕΖΗΘΙΚΛΜΝΞΟΠΡΣΤΥΦΧΨΩαβγδεζηθικλμνξοπρσςτυφχψω")),
        category("制表", chars("┌┬┐├┼┤└┴┘─│═║╔╦╗╠╬╣╚╩╝┏┳┓┣╋┫┗┻┛━┃╭╮╰╯╱╲╳┄┅┆┇┈┉┊┋╞╪╡╟╫╢"))
    )
}
