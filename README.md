# 课程表（Timetable）

一个**完全离线**的 Android 课程表应用。原生 Kotlin + Jetpack Compose 实现（非 WebView、非混合应用），
界面采用 **iOS 26 的设计语言**：iOS 原生规范（分组列表、大标题、胶囊控件、系统色板）
叠加 **Liquid Glass** 材质——浮层使用真实背景模糊，配合玻璃边缘折射描边、柔和浮起阴影与柔光背景。

应用仅在「检查更新」时访问 GitHub 接口读取版本号与下载地址；课程数据全部保存在应用私有目录的 SQLite 数据库中，不做任何其他网络通信。

---

## 下载安装

从 [Releases](https://github.com/lyl-creator/timetable/releases) 下载最新的 `timetable-x.y.z.apk`，
在手机上直接安装即可。

- 要求 Android 8.0（API 26）及以上
- 课程数据仅保存在本机；网络权限只用于「检查更新」
- 文件校验值（SHA-256）见对应 Release 的说明

---

## 一、主要功能

| 模块 | 说明 |
| --- | --- |
| 周课表 | 星期 × 节次网格，表头固定、时间列固定，课程块支持跨节显示；点击空白格可按位置直接新建课程 |
| 周次切换 | 顶部横向周次胶囊，自动定位到当前周；切换周次即显示该周的实际安排 |
| 今日 | 时间轴视图，区分「已结束 / 进行中 / 即将开始」，并高亮下一节课 |
| 课程管理 | 增删改课程：名称、教师、地点、星期、节次（可跨节）、周次、配色、备注 |
| 导入课表 | 从本地文件导入（`.xls` / `.xlsx` / csv / 网页表格伪 `.xls`），导入前可预览与核对 |
| 学期设置 | 开学日期、学期总周数、当前周（自动推算或手动指定） |
| 作息设置 | 自选每天节数（上午 / 下午 / 晚间分别设置）；**每节课的时长与每个课间的长短都可单独设置**；一键生成时间表，改动某节或某个课间后会顺延其后的节次 |
| 检查更新 | 在「设置 → 关于」中比对 GitHub 上的最新版本，有更新时可直接前往下载 |
| 外观 | iOS 26 观感 + Liquid Glass：浮层使用**真实背景模糊**（Android 12 及以上），配合玻璃边缘折射描边与柔光背景；浅色 / 深色 / 跟随系统，6 种系统强调色 |

---

## 二、关于「每周课程可能不一样」

这是本应用数据模型的核心设计，已被完整支持：

**每条课程记录 = 一个「上课时段」+ 一份「生效周次掩码」。**
周次掩码是 32 位整数，第 n 位为 1 表示第 n 周上课，因此可以精确表达任意周次组合
（`1-16周`、`单周`、`双周`、`1,3,5,7,9周`……），而不是近似成区间。

由此可以覆盖以下全部情形：

1. **同一门课，前后半学期时间或地点不同**
   例：大学物理第 1-8 周在「理科楼 B203」，第 9-16 周在「理科楼 B205」。
   → 保存为两条记录，周次互不重叠。切换到第 3 周只显示前者，第 12 周只显示后者。

2. **同一门课，不同星期 / 不同节次**
   → 同样保存为多条记录，按各自周次生效。

3. **单双周课程**
   → 直接在编辑页选择「单周 / 双周」。

4. **离散周次 / 临时调整**
   例：某门课只在第 1、3、5、7、9 周上；或某周的课临时停上一次。
   → 编辑页的周次区域切换到「逐周勾选」，可任意勾选周次。

5. **同一门课视觉上保持一致**
   导入时按课程名统一配色；编辑已有课程时可手动指定与其相同的颜色。

> 编辑入口：课表页点击课程块，或在「设置 → 课程与时段」中查看全部课程（同名课程会标注「N 个时段」）。

---

## 三、构建

### 环境要求

- JDK 17
- Android SDK（compileSdk 34、build-tools 34.0.0）
- Gradle 8.7（工程内含 wrapper）

### 命令行构建

```bash
# 生成可安装的调试包
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk

# 生成正式包（使用工程内 keystore 签名，可直接安装）
./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

在 Android Studio 中直接 `Open` 本目录即可（会自动识别 Gradle 工程）。

> 若通过 `local.properties` 指定 SDK 路径，请确认 `sdk.dir` 指向本机 Android SDK。

### 签名配置

release 构建的签名信息从项目根目录的 `keystore.properties` 读取，该文件与
`app/keystore/*.jks`、`local.properties` 均已在 `.gitignore` 中排除，不会进入版本库。

自行签名：

1. 生成密钥库（JDK 17）：

   ```bash
   keytool -genkeypair -v -keystore app/keystore/timetable.jks -alias timetable \
     -keyalg RSA -keysize 2048 -validity 10000 \
     -storepass 你的口令 -keypass 你的口令 \
     -dname "CN=Timetable, OU=Dev, O=Personal, L=Beijing, ST=Beijing, C=CN"
   ```

2. 复制 `keystore.properties.example` 为 `keystore.properties`，填入 `storeFile`、
   `storePassword`、`keyAlias`、`keyPassword`。

3. 执行 `./gradlew assembleRelease`。

未提供 `keystore.properties` 时，release 任务产出未签名包（`app-release-unsigned.apk`），
不影响编译、调试与单元测试。

### 运行单元测试

解析器与导入映射均有单元测试覆盖（JVM，无需设备）：

```bash
./gradlew testDebugUnitTest
# 报告：app/build/reports/tests/testDebugUnitTest/index.html
```

> 注意：Windows 下若工程路径包含中文，Gradle 的测试进程可能无法加载测试类
> （classpath 编码问题，与代码无关）。可将工程复制到纯英文路径后运行测试。

---

## 四、导入说明

### 支持的格式

格式按**文件字节特征**自动识别，与扩展名无关：

| 实际格式 | 常见来源 |
| --- | --- |
| Excel 97-2003 二进制（BIFF8 + OLE2） | 教务系统导出的 `.xls` |
| OOXML | 另存为的 `.xlsx` |
| HTML 表格 | 部分教务系统导出的「伪 xls」（本质是网页） |
| 定界文本 | `.csv` / `.tsv`，支持 UTF-8 / GBK / UTF-16 编码自动识别 |

`.xls` 为自研解析器实现（OLE2 复合文档 + BIFF8 记录流，含 SST 跨 CONTINUE 的字符串重建），
未引入 Apache POI，以避免其在 Android 上的兼容性与体积问题。

### 自动识别的两种表格结构

**① 按行课程清单**（一行一个时段）

表头需包含下列关键词中的至少两个，程序会自动定位各列（关键词可部分匹配）：

| 字段 | 可识别的表头写法 |
| --- | --- |
| 课程名称 | 课程名称、课程名、课程、科目、教学班、名称 |
| 任课教师 | 教师、老师、任课、授课 |
| 上课周次 | 周次、上课周、周数 |
| 星期 | 星期、周几、上课日 |
| 节次 | 节次、节数、上课节、时间 |
| 上课地点 | 地点、教室、上课地、场所 |

**② 周课表网格**（星期为列、节次为行）

程序通过表头行识别星期列（`周一` / `星期一` / `周1` / `Mon` 等写法均可），
通过首列识别节次（`1-2` / `第3节` / `0102` 等），然后拆解每个单元格的复合文本。

单元格内容支持以下常见写法：

```
高等数学                    ← 换行分隔
张伟
教三301

高等数学 张伟 教三301        ← 空格分隔
高等数学(1-16周)张伟 教三301   ← 紧凑写法（周次会被识别并剥离）
高等数学(张伟)教三301         ← 括号标注
```

**国内教务系统最常见的「教师[周次]周地点」写法**也已支持：

```
通用英语B(2026版大一上)                ← 课程名（可自带括号说明）
王鑫[4，5，7-18]周N楼-411             ← 教师 + 方括号周次 + 周 + 地点

习近平新时代中国特色社会主义思想概论     ← 多段周次会取并集
周文康[10-16]周，[4，5，7-9]周
G楼-101

代数与几何X
王卫卫（0602019）[15-18]周M楼-102      ← 教师工号括号会被剥离
```

要点：

- 周次同时支持 `[4，5，7-18]周`（方括号内数字与范围混排）、`1-16周`、`1,3,5周`、`单周`/`双周`，多段周次自动取并集
- 地点支持字母开头楼栋（`N楼-411`、`M楼-102`）、中文楼馆（`理科楼B203`、`体育馆`）
- 教师名后的工号括号（如 `（0602019）`）会在识别后自动去除

合并单元格（`rowspan` / `colspan`）会被展开，重复内容自动去重合并。

### 示例文件

`samples/` 目录下有可直接用于验证导入功能的样例（复制到手机后即可导入）：

| 文件 | 说明 |
| --- | --- |
| `list_style.xls` | 列表式，含单双周、离散周次、同课不同周次不同教室 |
| `matrix_style.xls` | 矩阵式，单元格内含多行文本 |
| `compact_style.xls` | 矩阵式紧凑写法 |
| `html_style.xls` | 网页表格伪 xls，含合并单元格 |
| `modern_style.xlsx` | xlsx 格式，多工作表 |
| `big_sst.xls` | 260 行大数据量，覆盖共享字符串表跨记录场景 |
| `gbk_style.csv` | GBK 编码的 CSV |

### 导入流程

1. 课表页右上角「导入」按钮，或「设置 → 导入课表文件」
2. 选择文件（也可在文件管理器中用「打开方式 → 课程表」直接导入）
3. 查看识别结果：识别到的课程门数与时段数、解析策略、跳过行数、警告信息
4. 需要时可展开「查看原始表格内容」核对，或选择「清空原有课表 / 追加写入」
5. 确认导入

若自动识别失败，通常是表头不在前 30 行内，或表头缺少上述关键词。
可用 Excel / WPS 调整表头后重新导入。

---

## 五、工程结构

```
app/src/main/java/com/lyl/timetable/
├── MainActivity.kt                     入口 Activity（支持以「用课程表打开」接收文件）
├── TimetableApplication.kt
├── data/                               数据层
│   ├── Models.kt                       Course / TimeSlot / AppSettings / 周次掩码运算
│   ├── DbHelper.kt                     SQLite 建表
│   ├── CourseDao.kt                    课程增删改查
│   ├── SettingsStore.kt                设置持久化
│   └── TimetableRepository.kt          统一仓库 + 当前周推算
├── xls/                                表格解析（纯 JVM，不依赖 android.*，可单测）
│   ├── SpreadsheetParser.kt            入口：格式探测与分发
│   ├── Ole2Reader.kt                   OLE2 复合文档（FAT / miniFAT / 目录项）
│   ├── Biff8Reader.kt                  BIFF8 记录流（含 SST 跨 CONTINUE）
│   ├── XlsxReader.kt                   OOXML（ZIP + XML）
│   ├── HtmlTableReader.kt              网页表格（含合并单元格展开）
│   ├── DelimitedTextReader.kt          CSV / TSV
│   └── SheetTable.kt                   解析结果模型
├── importer/
│   └── TimetableImporter.kt            表格 → 课程映射（列表式 / 矩阵式双策略）
└── ui/
    ├── AppRoot.kt                      应用骨架与导航
    ├── Navigation.kt                   底部导航、设置分组
    ├── WeekDates.kt                    周次与日期换算
    ├── theme/                          iOS + Liquid Glass 设计系统（色板 / 字号阶梯 / 形状 / 玻璃材质）
    ├── components/                     玻璃卡片、iOS 分组列表、胶囊按钮、分段控件、课表网格、输入框
    └── screens/                        课表页 / 今日页 / 导入页 / 设置页 / 作息编辑页 / 课程编辑抽屉
```

---

## 六、已知限制

- 导入映射采用启发式规则，表头或单元格写法过于特殊时可能识别不全；此类情况会在导入页给出跳过行数与警告。
- 课程块在同一周同一时段出现冲突时，会在同一格内纵向等分显示（不做冲突消解）。
- 未内置节假日调休表；如需表示调课，可在周次中对该周单独处理（逐周勾选）。
- 周次掩码支持第 1–31 周。
