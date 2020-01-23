package forge.assets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData;
import com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.badlogic.gdx.utils.Array;
import forge.FThreads;
import forge.Forge;
import forge.properties.ForgeConstants;
import forge.util.FileUtil;
import forge.util.TextBounds;
import forge.util.Utils;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class FSkinFont {
    private static final int MIN_FONT_SIZE = 8;
    private static int MAX_FONT_SIZE = 72;

    private static final int MAX_FONT_SIZE_LESS_GLYPHS = 72;
    private static final int MAX_FONT_SIZE_MANY_GLYPHS = 36;

    private static final String TTF_FILE = "font1.ttf";
    private static final Map<Integer, FSkinFont> fonts = new HashMap<>();

    static {
        FileUtil.ensureDirectoryExists(ForgeConstants.FONTS_DIR);
    }

    public static FSkinFont get(final int unscaledSize) {
        return _get((int)Utils.scale(unscaledSize));
    }
    public static FSkinFont _get(final int scaledSize) {
        FSkinFont skinFont = fonts.get(scaledSize);
        if (skinFont == null) {
            skinFont = new FSkinFont(scaledSize);
            fonts.put(scaledSize, skinFont);
        }
        return skinFont;
    }

    public static FSkinFont forHeight(final float height) {
        int size = MIN_FONT_SIZE + 1;
        while (true) {
            if (_get(size).getLineHeight() > height) {
                return _get(size - 1);
            }
            size++;
        }
    }

    //pre-load all supported font sizes
    public static void preloadAll(String language) {
        //todo:really check the language glyph is a lot
        MAX_FONT_SIZE = (language.equals("zh-CN")) ? MAX_FONT_SIZE_MANY_GLYPHS : MAX_FONT_SIZE_LESS_GLYPHS;
        for (int size = MIN_FONT_SIZE; size <= MAX_FONT_SIZE; size++) {
            _get(size);
        }
    }

    //delete all cached font files
    public static void deleteCachedFiles() {
        FileUtil.deleteDirectory(new File(ForgeConstants.FONTS_DIR));
        FileUtil.ensureDirectoryExists(ForgeConstants.FONTS_DIR);
    }

    public static void updateAll() {
        for (FSkinFont skinFont : fonts.values()) {
            skinFont.updateFont();
        }
    }

    private final int fontSize;
    private final float scale;
    private BitmapFont font;

    private FSkinFont(int fontSize0) {
        if (fontSize0 > MAX_FONT_SIZE) {
            scale = (float)fontSize0 / MAX_FONT_SIZE;
        }
        else if (fontSize0 < MIN_FONT_SIZE) {
            scale = (float)fontSize0 / MIN_FONT_SIZE;
        }
        else {
            scale = 1;
        }
        fontSize = fontSize0;
        updateFont();
    }
    static int indexOf (CharSequence text, char ch, int start) {
        final int n = text.length();
        for (; start < n; start++)
            if (text.charAt(start) == ch) return start;
        return n;

    }
    public int computeVisibleGlyphs (CharSequence str, int start, int end, float availableWidth) {
        BitmapFontData data = font.getData();
        int index = start;
        float width = 0;
        Glyph lastGlyph = null;
        availableWidth /= data.scaleX;

        for (; index < end; index++) {
            char ch = str.charAt(index);
            if (ch == '[' && data.markupEnabled) {
                index++;
                if (!(index < end && str.charAt(index) == '[')) { // non escaped '['
                    while (index < end && str.charAt(index) != ']')
                        index++;
                    continue;
                }
            }

            Glyph g = data.getGlyph(ch);

            if (g != null) {
                if (lastGlyph != null) width += lastGlyph.getKerning(ch);
                if ((width + g.xadvance) - availableWidth > 0.001f) break;
                width += g.xadvance;
                lastGlyph = g;
            }
        }

        return index - start;
    }
    public boolean isBreakChar (char c) {
        BitmapFontData data = font.getData();
        if (data.breakChars == null) return false;
        for (char br : data.breakChars)
            if (c == br) return true;
        return false;
    }
    static boolean isWhitespace (char c) {
        switch (c) {
            case '\n':
            case '\r':
            case '\t':
            case ' ':
                return true;
            default:
                return false;
        }
    }
    // Expose methods from font that updates scale as needed
    public TextBounds getBounds(CharSequence str) {
        updateScale(); //must update scale before measuring text
        return getBounds(str, 0, str.length());
    }
    public TextBounds getBounds(CharSequence str, int start, int end) {
        BitmapFontData data = font.getData();
        //int start = 0;
        //int end = str.length();
        int width = 0;
        Glyph lastGlyph = null;

        while (start < end) {
            char ch = str.charAt(start++);
            if (ch == '[' && data.markupEnabled) {
                if (!(start < end && str.charAt(start) == '[')) { // non escaped '['
                    while (start < end && str.charAt(start) != ']')
                        start++;
                    start++;
                    continue;
                }
                start++;
            }
            lastGlyph = data.getGlyph(ch);
            if (lastGlyph != null) {
                width = lastGlyph.xadvance;
                break;
            }
        }
        while (start < end) {
            char ch = str.charAt(start++);
            if (ch == '[' && data.markupEnabled) {
                if (!(start < end && str.charAt(start) == '[')) { // non escaped '['
                    while (start < end && str.charAt(start) != ']')
                        start++;
                    start++;
                    continue;
                }
                start++;
            }

            Glyph g = data.getGlyph(ch);
            if (g != null) {
                width += lastGlyph.getKerning(ch);
                lastGlyph = g;
                width += g.xadvance;
            }
        }

        return new TextBounds(width * data.scaleX, data.capHeight);

    }
    public TextBounds getMultiLineBounds(CharSequence str) {
        updateScale();
        BitmapFontData data = font.getData();
        int start = 0;
        float maxWidth = 0;
        int numLines = 0;
        int length = str.length();

        while (start < length) {
            int lineEnd = indexOf(str, '\n', start);
            float lineWidth = getBounds(str, start, lineEnd).width;
            maxWidth = Math.max(maxWidth, lineWidth);
            start = lineEnd + 1;
            numLines++;
        }

        return new TextBounds(maxWidth, data.capHeight + (numLines - 1) * data.lineHeight);

    }
    public TextBounds getWrappedBounds(CharSequence str, float wrapWidth) {
        updateScale();
        BitmapFontData data = font.getData();
        if (wrapWidth <= 0) wrapWidth = Integer.MAX_VALUE;
        int start = 0;
        int numLines = 0;
        int length = str.length();
        float maxWidth = 0;
        while (start < length) {
            int newLine = indexOf(str, '\n', start);
            int lineEnd = start + computeVisibleGlyphs(str, start, newLine, wrapWidth);
            int nextStart = lineEnd + 1;
            if (lineEnd < newLine) {
                // Find char to break on.
                while (lineEnd > start) {
                    if (isWhitespace(str.charAt(lineEnd))) break;
                    if (isBreakChar(str.charAt(lineEnd - 1))) break;
                    lineEnd--;
                }

                if (lineEnd == start) {

                    if (nextStart > start + 1) nextStart--;

                    lineEnd = nextStart; // If no characters to break, show all.

                } else {
                    nextStart = lineEnd;

                    // Eat whitespace at start of wrapped line.

                    while (nextStart < length) {
                        char c = str.charAt(nextStart);
                        if (!isWhitespace(c)) break;
                        nextStart++;
                        if (c == '\n') break; // Eat only the first wrapped newline.
                    }

                    // Eat whitespace at end of line.
                    while (lineEnd > start) {

                        if (!isWhitespace(str.charAt(lineEnd - 1))) break;
                        lineEnd--;
                    }
                }
            }

            if (lineEnd > start) {
                float lineWidth = getBounds(str, start, lineEnd).width;
                maxWidth = Math.max(maxWidth, lineWidth);
            }
            start = nextStart;
            numLines++;
        }

        return new TextBounds(maxWidth, data.capHeight + (numLines - 1) * data.lineHeight);
    }
    public float getAscent() {
        updateScale();
        return font.getAscent();
    }
    public float getCapHeight() {
        updateScale();
        return font.getCapHeight();
    }
    public float getLineHeight() {
        updateScale();
        return font.getLineHeight();
    }

    public void draw(SpriteBatch batch, String text, Color color, float x, float y, float w, boolean wrap, int horzAlignment) {
        updateScale();
        font.setColor(color);
        font.draw(batch, text, x, y, w, horzAlignment, wrap);
    }

    //update scale of font if needed
    private void updateScale() {
        if (font.getScaleX() != scale) {
            font.getData().setScale(scale);
        }
    }

    public boolean canShrink() {
        return fontSize > MIN_FONT_SIZE;
    }

    public FSkinFont shrink() {
        return _get(fontSize - 1);
    }

    private void updateFont() {
        if (scale != 1) { //re-use font inside range if possible
            if (fontSize > MAX_FONT_SIZE) {
                font = _get(MAX_FONT_SIZE).font;
            } else {
                font = _get(MIN_FONT_SIZE).font;
            }
            return;
        }

        String fontName = "f" + fontSize;
        FileHandle fontFile = Gdx.files.absolute(ForgeConstants.FONTS_DIR + fontName + ".fnt");
        if (fontFile != null && fontFile.exists()) {
            final BitmapFontData data = new BitmapFontData(fontFile, false);
            FThreads.invokeInEdtNowOrLater(new Runnable() {
                @Override
                public void run() { //font must be initialized on UI thread
                    font = new BitmapFont(data, (TextureRegion)null, true);
                }
            });
        } else {
            generateFont(FSkin.getSkinFile(TTF_FILE), fontName, fontSize);
        }
    }

    private void generateFont(final FileHandle ttfFile, final String fontName, final int fontSize) {
        if (!ttfFile.exists()) { return; }

        final FreeTypeFontGenerator generator = new FreeTypeFontGenerator(ttfFile);

        //approximate optimal page size
        int pageSize;
        if (fontSize >= 28) {
            pageSize = 256;
        }
        else {
            pageSize = 128;
        }

        //only generate images for characters that could be used by Forge
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890\"!?'.,;:()[]{}<>|/@\\^$-%+=#_&*\u2014\u2022";
        chars += "ÁÉÍÓÚáéíóúÀÈÌÒÙàèìòùÑñÄËÏÖÜäëïöüẞß¿¡";
        //generate from zh-CN.properties,and cardnames-zh-CN.txt
        //forge generate 3000+ characters cache need Take some time(MIN_FONT_SIZE - MAX_FONT_SIZE all size)
        if (Forge.locale.equals("zh-CN"))
            chars += "~·âû‘’“”−●、。「」『』一丁七万三上下不与丑专且世丘"
                +  "业丛东丝丢两严丧个中丰临丸丹为主丽举乃久么义之乌乍乎乐乔乖乘"
                +  "乙九也乡书买乱乳乾了予争事二于云互五井亘亚些亡交亥亦产享京亮"
                +  "亲亵人亿什仁仅仆仇今介仍从仑仓仕他仗付仙代令以仪们仰仲件价任"
                +  "份仿伊伍伏伐休众优伙会伟传伤伦伪伯伴伶伸伺似伽但位低住佐佑体"
                +  "何余佚佛作你佣佩佳使例侍侏供依侠侣侦侧侬侮侯侵便促俄俊俐俑俘"
                +  "保信修俯俸個倍倒候借倡倦倨倪债值倾假偏做停偶偷偿傀傍储催傲像"
                +  "僧僭僵僻儒儡儿兀允元充兆先光克免兔兕党入全八公六兰共关兴兵其"
                +  "具典兹养兼兽内册再冒冕写军农冠冢冥冬冰冲决况冶冷冻净准凋凌减"
                +  "凑凛凝几凡凤凭凯凰凶出击凿刀刃分切刈刍刑划列则刚创初删判利别"
                +  "刮到制刷刹刺刻刽剂剃削剌前剎剑剖剜剥剧剩剪副割剽劈力劝办功加"
                +  "务劣动助努劫励劲劳势勃勇勉勋勒勘募勤勾包匍匐匕化北匙匠匪匹区"
                +  "医匿十千升午半华协卑卒卓单卖南博卜占卡卢卦卫印危即却卵卷卸厂"
                +  "厄厅历厉压厚原厢厥厦厨去参叉及友双反发叔取受变叙叛叠口古句另"
                +  "叨只叫召叮可台史右叶号司叹吁吃各合吉吊同名后吏吐向吓吕吗君吞"
                +  "吟否含听吮启吱吸吹吻吼呆告呕员周味呼命咆和咏咒咕咬咯咳咽哀品"
                +  "哈响哑哗哥哨哩哪哭哮哲哺唐唤售唯唱啃啄商啜啪啮啸喀喂善喉喊喋"
                +  "喘喙喜喝喧喷嗅嗔嗜嗡嗣嗫嘉嘎嘘嘲嘴嘶噜噤器噬嚎嚼囊囚四回因团"
                +  "囤园困围固国图圆圈團土圣在地场圾均坊坍坎坏坐坑块坚坛坝坞坟坠"
                +  "坤坦坪坷垂垃型垒垛垠垢垣垦埃埋城域培基堂堆堕堡堤堪堰塌塑塔塘"
                +  "塞填境墓墙增墟墨壁壅壕壤士壬壮声壳壶处备复夏外多夜够大天太夫"
                +  "央失头夷夸夹夺奇奈奉奋奎契奔奖套奠奢奥女奴她好如妃妄妆妇妈妖"
                +  "妙妥妪妮妲妹姆姊始姓姜姥姬姿威娃娅娜娥婆婉婪婴婶媒嫁嫩嬉子孑"
                +  "孔孕字存孚孢季孤学孪孳孵孽宁它宅宇守安完宏宗官宙定宜宝实宠审"
                +  "客宣室宪宫宰害宴家容宾宿寂寄密寇富寒寓寝察寡寨寰寸对寺寻导封"
                +  "射将尉尊小少尔尖尘尚尝尤尬就尸尹尺尼尽尾局层居屈届屋屏屑展属"
                +  "屠履屯山屹岁岑岔岖岗岚岛岩岭岱岳岸峡峭峰峻崇崎崔崖崩崽嵌巅巍"
                +  "川巡巢工左巧巨巩巫差己已巳巴巷巾币市布帅帆师希帕帖帘帚帜帝带"
                +  "席帮帷常帼帽幅幔幕干平年并幸幻幼幽广庄庆庇床序库应底店庙府庞"
                +  "废度座庭庶廉廊延建开异弃弄弊式弑弓引弗弘弟张弥弦弧弩弯弱張弹"
                +  "强归当录彗形彩彰影役彻彼往征径待很徊律後徒徕得徘徙從御復循微"
                +  "徵德徽心必忆忌忍忒志忘忠忧快忱念忽忾忿怀态怎怒怖思急性怨怪怯"
                +  "总恋恍恐恒恕恢恣恨恩恫恭息恰恳恶恸恼悉悍悔悖悟患悦悬悯悲悼情"
                +  "惊惑惘惚惠惧惨惩惫惰想惹愁愈愎意愚感愣愤愧愿慈慌慎慑慕慢慧慨"
                +  "慰慷憎憩懈懦戈戍戏成我戒戕或战戟截戮戳戴户戾房所扁扇扈手才扎"
                +  "扑扒打托扣执扩扫扬扭扮扯扰找承技抄抉把抑抓投抖抗折抚抛抢护报"
                +  "披抱抵抹押抽拂拆拉拍拒拓拔拖拘招拜拟拣拥拦拧拨择括拯拱拳拷拼"
                +  "拽拾拿持挂指按挑挖挚挟挠挡挣挥挪挫振挺挽捆捉捍捕捞损换捣捧据"
                +  "捷捻掀授掉掌掐排掘掠探接控推掩措掮掳掷揍描提插握揭援揽搁搅搏"
                +  "搐搜搞搬搭携摄摆摇摘摧摩摸摹撒撕撞撤撬播撵撼擅操擎擒擞擢擦攀"
                +  "攫支收改攻放政故效敌敏救敕教敞敢散敦敬数敲整文斐斑斓斗斤斥斧"
                +  "斩断斯新方施旁旅旋族旗无既日旧旨早旭时旷旸旺昂昆昌明昏易昔昙"
                +  "星映春昨昭是昵昼显晃晋晓晕晖晚晨普景晰晴晶晷智暂暗暮暴曙曜曝"
                +  "曦曲曳更曼曾替最月有服朗望朝期木未末本札术朵机朽杀杂权杉李村"
                +  "杖杜束条来杨杯杰松板极构析林枚果枝枢枪枭枯架枷柄柏某染柜查柩"
                +  "柯柱柳栅标栈栋栏树栓栖栗株样核根格栽桂框案桌桎桑桓桠档桥桨桩"
                +  "桶梁梅梓梢梣梦梧梨梭梯械检棄棍棒棕棘棚森棱棺椁植椎椒椽楂楔楚"
                +  "楣楼概榄榆榔榨榴槌槛模横樱樵橇橡橫檀檐次欢欣欧欲欺歇歌止正此"
                +  "步武歪死歼殁殆殇殉殊残殍殒殓殖殡殴段殷殿毁毅母每毒比毕毛毡氅"
                +  "氏民氓气氤氦氧氲水永汀汁求汇汉汐汗汛池污汤汨汪汰汲汹汽沃沈沉"
                +  "沌沐沙沟没沥沦沮河沸油治沼沾沿泄泉泊法泛泞泡波泣泥注泪泯泰泽"
                +  "洁洋洒洗洛洞津洪洲活洼派流浅浆浇浊测济浑浓浚浩浪浮浴海浸涅消"
                +  "涉涌涎涛涟涡涤润涨涩液涵淋淘淤淬深混淹添清渊渎渐渔渗渝渠渡渣"
                +  "渥温港渲渴游湍湖湛湮湾湿溃溅源溜溢溪溯溶溺滋滑滓滔滚滞满滤滥"
                +  "滨滩滴漂漏演漠漩漫潘潜潭潮澄澈澹激濑濒瀑瀚灌火灭灯灰灵灶灼灾"
                +  "灿炉炎炙炫炬炭炮炸点炼炽烁烂烈烙烛烟烤烦烧烫烬热烽焉焊焚焦焰"
                +  "然煌煎煞煤照煮煽熄熊熏熔熟熠熵燃燎燕燧爆爪爬爱爵父片版牌牒牙"
                +  "牛牝牡牢牦牧物牲牵特牺犀犁犄犧犬犯状狂狄狈狐狗狙狞狡狩独狭狮"
                +  "狰狱狷狸狼猁猎猛猜猪猫献猴猿獒獠獾玄率玉王玖玛玩玫环现玷玻珀"
                +  "珂珊珍珠班球理琉琐琥琳琴琵琼瑕瑙瑚瑞瑟瑰璃璞璧瓜瓣瓦瓮瓯瓶瓷"
                +  "甘甜生用甩甫田由甲电男画畅界畏留略畸畿疆疏疑疗疚疡疣疤疫疮疯"
                +  "疲疵疹疽疾病症痕痛痞痢痨痪痴痹瘟瘠瘤瘫瘴癣癫癸登白百的皆皇皈"
                +  "皮皱皿盆盈盐监盒盔盖盗盘盛盟目盲直相盾省看真眠眨眩眷眺眼着睁"
                +  "睡督睥睨睿瞄瞒瞥瞪瞬瞭瞰瞳矛矢知矫短矮石矾矿码砂砍研砖砦砧破"
                +  "砸砾础硕硫硬确碍碎碑碟碧碰碳碻碾磁磊磨磷磺礁示礼社祀祈祖祝神"
                +  "祟祠祥票祭祷祸禁禄福离禽私秃秉秋种科秘秣秤秩积称移秽稀程税稚"
                +  "稳稻穆穗穰穴究穷穹空穿突窃窍窒窖窗窘窜窝窟窥立竖站竞章童竭端"
                +  "竹笏笑笔笛笞符第笼等筑筒答策筛筝筹签简箔算箝管箭箱篓篮篱篷簇"
                +  "簧簪籍米类粉粒粗粘粮粹精糊糖糙糟系素索紧紫累繁纂纠红约级纪纬"
                +  "纯纱纳纵纶纷纸纹纺纽线练组绅细织终绊绍经绒结绕绘给绚络绝绞统"
                +  "绥继绩绪续绮绯绳维绵综绽绿缀缄缅缆缇缉缎缓缕编缘缚缝缠缩缪缰"
                +  "缸缺罅网罔罗罚罡罩罪置羁羊美羚羞群羽翁翅翎翔翠翡翰翱翻翼耀老"
                +  "考者而耍耐耕耗耘耙耳耶耸职联聚聪肃肆肇肉肌肖肝肠肢肤肥肩肯育"
                +  "肴肺肿胀胁胃胆背胎胖胜胞胡胧胫胶胸能脂脆脉脊脏脑脓脚脱脸腐腔"
                +  "腕腥腱腹腾腿膂膏膛膜膝臂臃臣自臭至致舌舍舒舞舟航般舰舱船艇良"
                +  "艰色艺艾节芒芙芜芥芬芭芮花芳芽苇苍苏苔苗苛苜苟若苦英茁茂范茉"
                +  "茎茜茧茨茫茸荆草荒荚荡荣荨荫药荷莉莎莓莫莱莲莳获莽菁菇菊菌菜"
                +  "菲萃萌萍萎萝营萦萧萨萼落著葛葬葵蒂蒙蒸蓄蓑蓝蓟蓿蔑蔓蔚蔷蔻蔽"
                +  "蕈蕊蕨蕴蕾薄薇薙薪藏藐藓藤藻虎虏虐虑虔虚虫虱虹蚀蚁蚊蚋蚣蚺蛆"
                +  "蛇蛊蛋蛎蛙蛛蛞蛭蛮蛰蛸蛾蜂蜈蜉蜒蜕蜗蜘蜜蜡蜥蜴蜷蜿蝇蝎蝓蝗蝙"
                +  "蝠蝣蝾螂螅融螫螯螳螺蟀蟋蟑蟒蟹蠕蠢血行衍街衡衣补表衫衰袂袋袍"
                +  "袖被袭裁裂装裔裘褐褛褪褫褴褶襄西要覆见观规觅视览觉觊角解触言"
                +  "詹誉誓警计认讧讨让训议讯记讲许论讽设访诀证评诅识诈诉词译试诗"
                +  "诘诚诛话诞诡该详诫语误诱诲说诵请诸诺读谀谁调谆谈谊谋谍谐谕谗"
                +  "谜谟谢谣谦谧谨谬谭谱谴谵谷豁豆象豪豹豺貂貌贝贞负贡财责贤败货"
                +  "质贩贪贫贬购贮贯贱贴贵贷贸费贺贼贾贿赂赃资赋赌赎赏赐赔赖赘赛"
                +  "赞赠赢赤赦赫走赶起趁超越趋足跃跑跖跚跛距跟跨路跳践跺踏踝踢踩"
                +  "踪踵踽蹂蹄蹊蹋蹒蹦蹬躁躏身躯躲車车轨轩转轭轮软轰轴轻载较辉辑"
                +  "输辖辗辙辛辜辞辟辨辩辫辰辱边达迁迂迅过迈迎运近返还这进远违连"
                +  "迟迦迩迪迫迭述迳迷迸迹追退送适逃逆选逊透逐递途逗通逝逞速造逡"
                +  "逢逮逸逻逼遁遂遇遍遏道遗遣遥遨遭遮遵避邀還邑那邦邪邬邸郊郎部"
                +  "都鄙酋配酒酬酷酸酿醉醒采釉释里重野量金鉴针钉钓钗钙钜钝钟钢钥"
                +  "钦钨钩钮钯钱钳钵钻钽铁铃铅铎铜铠铬铭铲银铸铺链销锁锄锅锈锋锐"
                +  "错锡锢锤锥锦锭键锯锻镇镖镜镬镰镶长間闇门闩闪闭问闯闲间闷闸闹"
                +  "闻阀阁阅队阱防阳阴阵阶阻阿陀附际陆陋降限院除陨险陪陲陵陶陷隆"
                +  "随隐隔隘隙障隧隶隼难雀雄雅集雇雉雏雕雨雪雯雳零雷雹雾需霆震霉"
                +  "霍霓霖霜霞霰露霸霹青靖静非靠靡面革靴靶鞋鞍鞑鞭韧音韵韶页顶项"
                +  "顺须顽顾顿颂预颅领颈颊题颚颜额颠颤风飒飓飘飙飞食餍餐餮饕饥饭"
                +  "饮饰饱饴饵饶饼饿馆馈馐馑首香馨马驭驮驯驰驱驳驹驻驼驽驾驿骁骂"
                +  "骄骆骇验骏骐骑骗骚骤骨骰骷骸骼髅髓高鬃鬓鬣鬼魁魂魄魅魇魈魏魔"
                +  "鰴鱼鲁鲜鲤鲨鲮鲸鲽鳃鳄鳍鳐鳗鳝鳞鸟鸠鸡鸢鸣鸥鸦鸽鹅鹉鹊鹏鹗鹞"
                +  "鹤鹦鹫鹭鹰鹿麋麒麟麦麻黄黎黏黑默黛黜點黠黯鼎鼓鼠鼬鼹鼻齐齑齿"
                +  "龇龙龟！（），／：；？～";

        final PixmapPacker packer = new PixmapPacker(pageSize, pageSize, Pixmap.Format.RGBA8888, 2, false);
        final FreeTypeFontParameter parameter = new FreeTypeFontParameter();
        parameter.characters = chars;
        parameter.size = fontSize;
        parameter.packer = packer;
        final FreeTypeFontGenerator.FreeTypeBitmapFontData fontData = generator.generateData(parameter);
        final Array<PixmapPacker.Page> pages = packer.getPages();

        //finish generating font on UI thread
        FThreads.invokeInEdtNowOrLater(new Runnable() {
            @Override
            public void run() {
                Array<TextureRegion> textureRegions = new Array<>();
                for (int i = 0; i < pages.size; i++) {
                    PixmapPacker.Page p = pages.get(i);
                    Texture texture = new Texture(new PixmapTextureData(p.getPixmap(), p.getPixmap().getFormat(), false, false)) {
                        @Override
                        public void dispose() {
                            super.dispose();
                            getTextureData().consumePixmap().dispose();
                        }
                    };
                    texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
                    textureRegions.addAll(new TextureRegion(texture));
                }

                font = new BitmapFont(fontData, textureRegions, true);

                //create .fnt and .png files for font
                FileHandle pixmapDir = Gdx.files.absolute(ForgeConstants.FONTS_DIR);
                if (pixmapDir != null) {
                    FileHandle fontFile = pixmapDir.child(fontName + ".fnt");
                    BitmapFontWriter.setOutputFormat(BitmapFontWriter.OutputFormat.Text);

                    String[] pageRefs = BitmapFontWriter.writePixmaps(packer.getPages(), pixmapDir, fontName);
                    BitmapFontWriter.writeFont(font.getData(), pageRefs, fontFile, new BitmapFontWriter.FontInfo(fontName, fontSize), 1, 1);
                }

                generator.dispose();
                packer.dispose();
            }
        });
    }
}
