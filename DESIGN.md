# OpenClaw Android — 独立 Agent App 技术方案

> 目标：在 Android 上实现一个独立的 AI Agent App，具备记忆、技能、本地工具调用能力，无需依赖 Gateway。

## 1. 架构总览

```
┌─────────────────────────────────────────────────┐
│                  Android App                     │
│                                                  │
│  ┌───────────┐  ┌───────────┐  ┌──────────────┐ │
│  │  Chat UI  │  │ Settings  │  │  Memory UI   │ │
│  └─────┬─────┘  └───────────┘  └──────────────┘ │
│        │                                         │
│  ┌─────▼─────────────────────────────────────┐   │
│  │           Agent Runtime (核心引擎)          │   │
│  │                                           │   │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────────┐ │   │
│  │  │ Session │ │  Tool   │ │   Skill     │ │   │
│  │  │ Manager │ │ Router  │ │   Engine    │ │   │
│  │  └────┬────┘ └────┬────┘ └──────┬──────┘ │   │
│  │       │           │             │         │   │
│  │  ┌────▼────┐ ┌────▼────┐ ┌─────▼───────┐ │   │
│  │  │ Memory  │ │  Local  │ │   Skill     │ │   │
│  │  │ Store   │ │  Tools  │ │   Store     │ │   │
│  │  └─────────┘ └─────────┘ └─────────────┘ │   │
│  └─────────────────┬─────────────────────────┘   │
│                    │                             │
│  ┌─────────────────▼─────────────────────────┐   │
│  │           LLM Client Layer                │   │
│  │  Claude API | OpenAI API | Gemini API     │   │
│  └───────────────────────────────────────────┘   │
│                                                  │
│  ┌───────────────────────────────────────────┐   │
│  │      可选: Gateway Connector (增强模式)     │   │
│  │      WebSocket → 远程 Gateway              │   │
│  └───────────────────────────────────────────┘   │
└─────────────────────────────────────────────────┘
```

## 2. 技术栈

| 层 | 选择 | 理由 |
|---|---|---|
| **语言** | Kotlin | Android 官方推荐，协程支持好 |
| **UI** | Jetpack Compose | 现代声明式 UI |
| **网络** | OkHttp + Retrofit | 成熟稳定，SSE 支持好 |
| **本地存储** | Room (SQLite) + 文件系统 | 结构化数据用 Room，记忆文件用 FS |
| **DI** | Hilt | Google 官方 DI |
| **异步** | Kotlin Coroutines + Flow | 原生协程 |
| **序列化** | kotlinx.serialization | Kotlin 原生，性能好 |
| **最低版本** | Android 10 (API 29) | 覆盖 90%+ 设备 |

## 3. 核心模块设计

### 3.1 Agent Runtime（核心引擎）

这是整个 App 的大脑，协调 LLM 调用、工具执行和记忆管理。

```kotlin
// 核心接口
interface AgentRuntime {
    // 处理用户消息，返回 Agent 响应流
    fun chat(message: String, session: Session): Flow<AgentEvent>

    // 注册本地工具
    fun registerTool(tool: Tool)

    // 加载技能
    fun loadSkill(skill: Skill)
}

// Agent 事件流
sealed class AgentEvent {
    data class TextChunk(val text: String) : AgentEvent()
    data class ToolCall(val name: String, val args: JsonObject) : AgentEvent()
    data class ToolResult(val name: String, val result: String) : AgentEvent()
    data class Done(val fullText: String, val usage: Usage) : AgentEvent()
    data class Error(val message: String) : AgentEvent()
}
```

**核心循环（Function Calling Loop）：**

```
用户消息
  → 注入 system prompt (SOUL + 记忆 + 可用工具)
  → 发送到 LLM
  → 如果返回 tool_call:
      → 路由到对应工具执行
      → 将结果追加到上下文
      → 再次发送到 LLM（循环）
  → 如果返回文本:
      → 展示给用户
      → 保存到记忆
```

### 3.2 LLM Client Layer

支持多个 Provider，统一接口：

```kotlin
interface LlmClient {
    fun chat(
        messages: List<Message>,
        tools: List<ToolDefinition>,
        model: String,
        stream: Boolean = true
    ): Flow<LlmEvent>
}

// 实现
class ClaudeClient(apiKey: String) : LlmClient    // Anthropic Messages API
class OpenAiClient(apiKey: String) : LlmClient     // OpenAI Chat Completions
class GeminiClient(apiKey: String) : LlmClient     // Google Gemini API
```

**Claude API 重点：**
- 使用 Messages API (`/v1/messages`)
- Function Calling: `tools` 参数定义工具，`tool_use` / `tool_result` 处理调用
- Streaming: SSE (`stream: true`)，逐 chunk 解析

### 3.3 记忆系统

模仿 OpenClaw 的记忆架构，但适配移动端：

```
/data/data/com.openclaw.agent/files/memory/
├── SOUL.md              # 人设
├── MEMORY.md            # 长期记忆（手动/自动更新）
├── USER.md              # 用户信息
├── daily/
│   ├── 2026-03-18.md    # 每日记录
│   └── 2026-03-17.md
└── skills/              # 技能定义文件
    ├── weather/SKILL.md
    └── translate/SKILL.md
```

```kotlin
interface MemoryStore {
    // 读取记忆文件
    suspend fun read(path: String): String?

    // 写入/更新记忆
    suspend fun write(path: String, content: String)

    // 语义搜索记忆（用 embedding 或关键词）
    suspend fun search(query: String, maxResults: Int = 5): List<MemorySnippet>

    // 获取今日 + 昨日的 daily notes
    suspend fun getRecentDailyNotes(): String

    // 构建 system prompt 中的记忆部分
    suspend fun buildMemoryContext(): String
}
```

**记忆注入流程：**
1. 每次对话开始 → 加载 SOUL.md + USER.md + 最近 daily notes
2. 每次请求 → 用关键词从 MEMORY.md 搜索相关片段注入上下文
3. 对话结束 → 自动追加摘要到 daily note
4. 定期 → 从 daily notes 提炼到 MEMORY.md（可手动触发或 AI 自动）

### 3.4 工具系统

```kotlin
// 工具定义
interface Tool {
    val name: String
    val description: String
    val parameters: JsonSchema  // JSON Schema 定义参数

    suspend fun execute(args: JsonObject): ToolResult
}

data class ToolResult(
    val success: Boolean,
    val content: String,        // 返回给 LLM 的文本
    val media: List<MediaItem>? = null  // 图片/视频等附件
)
```

**内置本地工具：**

```kotlin
// Phase 1 - 基础工具
class CameraTool : Tool           // 拍照（前/后摄像头）
class LocationTool : Tool         // 获取 GPS 定位
class CalendarTool : Tool         // 读写日历事件
class ContactsTool : Tool         // 搜索联系人
class NotificationTool : Tool     // 读取通知
class AlarmTool : Tool            // 设置闹钟/提醒
class ClipboardTool : Tool        // 读写剪贴板
class FileTool : Tool             // 读写本地文件
class WebSearchTool : Tool        // 网页搜索（Brave/Tavily API）
class WebFetchTool : Tool         // 抓取网页内容

// Phase 2 - 进阶工具
class SmsTool : Tool              // 发送短信
class PhoneCallTool : Tool        // 拨打电话
class AppLaunchTool : Tool        // 启动其他 App
class ScreenshotTool : Tool       // 截屏（需要无障碍服务）
class PhotoLibraryTool : Tool     // 读取相册
class DeviceInfoTool : Tool       // 电量/网络/存储等信息
class TtsTool : Tool              // 文字转语音
class SttTool : Tool              // 语音转文字
```

### 3.5 技能引擎

简化版技能系统，解析 SKILL.md 并注入到工具路由：

```kotlin
data class Skill(
    val name: String,
    val description: String,
    val triggers: List<String>,       // 触发关键词
    val systemPrompt: String,         // 注入 LLM 的指令
    val requiredTools: List<String>,  // 依赖的工具
    val references: Map<String, String> // 参考文件
)

interface SkillEngine {
    // 加载本地技能
    fun loadSkills(directory: File)

    // 根据用户输入匹配技能
    fun matchSkill(userMessage: String): Skill?

    // 获取技能的 system prompt 注入内容
    fun getSkillContext(skill: Skill): String
}
```

**技能工作流：**
1. App 启动 → 扫描 `skills/` 目录加载所有 SKILL.md
2. 用户发消息 → SkillEngine 匹配最相关的技能
3. 匹配到技能 → 将技能的 system prompt + 工具定义注入 LLM 上下文
4. LLM 根据技能指引调用相应工具

### 3.6 Session Manager

```kotlin
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val model: String,
    val systemPrompt: String?
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String,           // user | assistant | tool
    val content: String,
    val toolCalls: String?,     // JSON
    val toolResults: String?,   // JSON
    val timestamp: Long,
    val tokenCount: Int?
)
```

## 4. 项目结构

```
app/
├── src/main/
│   ├── java/com/openclaw/agent/
│   │   ├── OpenClawApp.kt                 # Application
│   │   ├── di/
│   │   │   └── AppModule.kt               # Hilt DI
│   │   │
│   │   ├── core/
│   │   │   ├── runtime/
│   │   │   │   ├── AgentRuntime.kt         # 核心引擎
│   │   │   │   ├── AgentEvent.kt           # 事件定义
│   │   │   │   └── FunctionCallLoop.kt     # 工具调用循环
│   │   │   ├── llm/
│   │   │   │   ├── LlmClient.kt            # 统一接口
│   │   │   │   ├── ClaudeClient.kt          # Anthropic
│   │   │   │   ├── OpenAiClient.kt          # OpenAI
│   │   │   │   └── models.kt               # 消息/工具模型
│   │   │   ├── memory/
│   │   │   │   ├── MemoryStore.kt           # 记忆接口
│   │   │   │   ├── FileMemoryStore.kt       # 文件实现
│   │   │   │   └── MemoryContextBuilder.kt  # 上下文构建
│   │   │   ├── tools/
│   │   │   │   ├── Tool.kt                  # 工具接口
│   │   │   │   ├── ToolRouter.kt            # 路由分发
│   │   │   │   ├── ToolRegistry.kt          # 注册表
│   │   │   │   └── impl/
│   │   │   │       ├── CameraTool.kt
│   │   │   │       ├── LocationTool.kt
│   │   │   │       ├── CalendarTool.kt
│   │   │   │       ├── ContactsTool.kt
│   │   │   │       ├── WebSearchTool.kt
│   │   │   │       ├── WebFetchTool.kt
│   │   │   │       ├── FileTool.kt
│   │   │   │       └── DeviceInfoTool.kt
│   │   │   ├── skills/
│   │   │   │   ├── Skill.kt                # 技能模型
│   │   │   │   ├── SkillParser.kt           # SKILL.md 解析
│   │   │   │   └── SkillEngine.kt           # 匹配引擎
│   │   │   └── session/
│   │   │       ├── SessionManager.kt
│   │   │       └── SessionRepository.kt
│   │   │
│   │   ├── data/
│   │   │   ├── db/
│   │   │   │   ├── AppDatabase.kt
│   │   │   │   ├── SessionDao.kt
│   │   │   │   └── MessageDao.kt
│   │   │   └── preferences/
│   │   │       └── SettingsStore.kt         # DataStore
│   │   │
│   │   ├── ui/
│   │   │   ├── chat/
│   │   │   │   ├── ChatScreen.kt            # 主聊天界面
│   │   │   │   ├── ChatViewModel.kt
│   │   │   │   ├── MessageBubble.kt         # 消息气泡
│   │   │   │   └── ToolCallCard.kt          # 工具调用展示
│   │   │   ├── sessions/
│   │   │   │   ├── SessionListScreen.kt     # 会话列表
│   │   │   │   └── SessionListViewModel.kt
│   │   │   ├── memory/
│   │   │   │   ├── MemoryScreen.kt          # 记忆管理界面
│   │   │   │   └── MemoryViewModel.kt
│   │   │   ├── settings/
│   │   │   │   ├── SettingsScreen.kt
│   │   │   │   └── SettingsViewModel.kt
│   │   │   ├── skills/
│   │   │   │   ├── SkillListScreen.kt       # 技能管理
│   │   │   │   └── SkillDetailScreen.kt
│   │   │   └── theme/
│   │   │       ├── Theme.kt
│   │   │       └── Colors.kt
│   │   │
│   │   └── gateway/                         # 可选：Gateway 连接
│   │       ├── GatewayConnector.kt
│   │       └── GatewayProtocol.kt
│   │
│   ├── res/
│   │   └── ...
│   └── AndroidManifest.xml
│
├── build.gradle.kts
└── proguard-rules.pro
```

## 5. System Prompt 构建

每次 LLM 调用的 system prompt 动态拼装：

```kotlin
fun buildSystemPrompt(session: Session, memory: MemoryStore, skills: SkillEngine): String {
    return buildString {
        // 1. 人设
        appendLine(memory.read("SOUL.md"))
        appendLine()

        // 2. 用户信息
        appendLine(memory.read("USER.md"))
        appendLine()

        // 3. 长期记忆（相关片段）
        val relevantMemory = memory.search(session.lastUserMessage, maxResults = 5)
        if (relevantMemory.isNotEmpty()) {
            appendLine("## Relevant Memory")
            relevantMemory.forEach { appendLine("- ${it.content}") }
            appendLine()
        }

        // 4. 最近 daily notes
        appendLine("## Recent Context")
        appendLine(memory.getRecentDailyNotes())
        appendLine()

        // 5. 匹配到的技能指令
        val skill = skills.matchSkill(session.lastUserMessage)
        if (skill != null) {
            appendLine("## Active Skill: ${skill.name}")
            appendLine(skill.systemPrompt)
            appendLine()
        }

        // 6. 工具使用说明
        appendLine("## Available Tools")
        appendLine("You have access to the following tools on this Android device.")
        appendLine("Use function calling to invoke them when needed.")

        // 7. 当前时间和设备信息
        appendLine()
        appendLine("Current time: ${LocalDateTime.now()}")
        appendLine("Device: Android ${Build.VERSION.RELEASE}")
    }
}
```

## 6. 关键 API 调用示例

### Claude Function Calling

```kotlin
// 请求
POST https://api.anthropic.com/v1/messages
{
  "model": "claude-sonnet-4-20250514",
  "max_tokens": 4096,
  "system": "<动态构建的 system prompt>",
  "tools": [
    {
      "name": "take_photo",
      "description": "Take a photo using the device camera",
      "input_schema": {
        "type": "object",
        "properties": {
          "facing": {
            "type": "string",
            "enum": ["front", "back"],
            "description": "Which camera to use"
          }
        }
      }
    },
    {
      "name": "get_location",
      "description": "Get current GPS location",
      "input_schema": {
        "type": "object",
        "properties": {}
      }
    }
  ],
  "messages": [
    {"role": "user", "content": "帮我拍张照片看看窗外"}
  ]
}

// 响应 (tool_use)
{
  "content": [
    {
      "type": "tool_use",
      "id": "toolu_xxx",
      "name": "take_photo",
      "input": {"facing": "back"}
    }
  ],
  "stop_reason": "tool_use"
}

// 工具执行后，继续对话
{
  "messages": [
    {"role": "user", "content": "帮我拍张照片看看窗外"},
    {"role": "assistant", "content": [{"type": "tool_use", ...}]},
    {"role": "user", "content": [
      {
        "type": "tool_result",
        "tool_use_id": "toolu_xxx",
        "content": [
          {"type": "image", "source": {"type": "base64", "media_type": "image/jpeg", "data": "..."}}
        ]
      }
    ]}
  ]
}
```

## 7. 开发路线图

### Phase 1: MVP（3 周）
- [ ] 项目脚手架（Compose + Hilt + Room）
- [ ] Claude API 客户端（含 streaming）
- [ ] 基础 Chat UI（消息列表、输入框、Markdown 渲染）
- [ ] Session 管理（新建/切换/删除）
- [ ] Settings（API Key 配置、模型选择）
- [ ] SOUL.md / USER.md 编辑器

### Phase 2: 工具调用（3 周）
- [ ] Function Calling 循环实现
- [ ] Tool Router + Registry
- [ ] 实现核心工具：Camera, Location, Calendar, Contacts, DeviceInfo
- [ ] 工具调用 UI（展示调用过程和结果）
- [ ] 权限管理 UI（引导用户授权）

### Phase 3: 记忆系统（2 周）
- [ ] 文件系统记忆存储
- [ ] Daily notes 自动记录
- [ ] MEMORY.md 读写
- [ ] 记忆上下文注入
- [ ] 记忆管理 UI（查看/编辑/搜索）

### Phase 4: 技能系统（2 周）
- [ ] SKILL.md 解析器
- [ ] 技能匹配引擎
- [ ] 技能上下文注入
- [ ] 内置几个实用技能（天气、翻译、网页搜索）
- [ ] 技能管理 UI

### Phase 5: 增强（2 周）
- [ ] 语音输入（STT）
- [ ] 语音输出（TTS）
- [ ] 多模型支持（OpenAI、Gemini）
- [ ] 可选 Gateway 连接
- [ ] 暗色主题
- [ ] Widget（桌面快捷入口）

## 8. 安全考虑

| 风险 | 对策 |
|------|------|
| API Key 存储 | Android Keystore 加密存储，不明文写文件 |
| 敏感权限 | 运行时权限申请，用户可在 Settings 中逐项开关 |
| 工具调用确认 | 敏感操作（发短信/打电话）弹确认对话框 |
| 数据导出 | 记忆文件可导出/导入，但默认不自动同步到云 |
| LLM 幻觉 | 工具调用结果展示在 UI 上，用户可见 |

## 9. 与 OpenClaw 的兼容性

为将来可能的互通保留兼容：

- **记忆文件格式** — 与 OpenClaw 的 MEMORY.md / daily notes 格式一致
- **SKILL.md 格式** — 遵循 OpenClaw AgentSkills 规范
- **工具命名** — 与 OpenClaw 工具名保持一致（camera, location, calendar...）
- **Gateway 协议** — 预留 Gateway WebSocket 连接模块，随时可接入

---

*设计于 2026-03-18*
