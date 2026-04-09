---
name: web-data
description: Query structured data from popular websites using built-in adapters
triggers:
  - hackernews
  - hacker news
  - wikipedia
  - arxiv
  - bbc
  - stackoverflow
  - stack overflow
  - yahoo finance
  - 股票
  - bilibili
  - b站
  - B站
  - 知乎
  - 微博
  - v2ex
  - lobsters
  - github trending
  - 热门
  - 热搜
  - 排行榜
  - 论文搜索
required_tools:
  - list_web_adapters
  - query_web
---

# Web Data Source Skill

You have access to structured web data source adapters via two tools:

1. **list_web_adapters** — Discover available data sources and their commands
2. **query_web** — Execute queries against specific sites

## Available Sites

### Public (no login needed)
- **hackernews**: top, new, best, search — Hacker News stories
- **wikipedia**: search, summary, random — Wikipedia articles
- **arxiv**: search — Academic papers
- **bbc**: news — BBC News headlines
- **stackoverflow**: hot, search — Stack Overflow questions
- **yahoofinance**: quote — Stock quotes (symbol: AAPL, MSFT, BTC-USD)
- **v2ex**: hot — V2EX hot topics (中文)
- **lobsters**: hot — Lobste.rs stories
- **github**: trending — GitHub trending repos

### Login Required (use settings to login first)
- **bilibili**: hot, search, ranking — B站视频
- **zhihu**: hot, search — 知乎内容
- **weibo**: hot, search — 微博热搜

## Usage Pattern
1. If you know the site, call query_web directly
2. If unsure what's available, call list_web_adapters first
3. For login-required sites, if the user hasn't logged in, suggest they go to Settings → Site Accounts

## Examples
- "HN 上有什么热门？" → query_web(site="hackernews", command="top", args={limit: 5})
- "搜索关于 Kotlin 的维基百科" → query_web(site="wikipedia", command="summary", args={title: "Kotlin"})
- "苹果股价" → query_web(site="yahoofinance", command="quote", args={symbol: "AAPL"})
- "B站热门" → query_web(site="bilibili", command="hot", args={limit: 10})
- "知乎热榜" → query_web(site="zhihu", command="hot")
- "微博热搜" → query_web(site="weibo", command="hot")
