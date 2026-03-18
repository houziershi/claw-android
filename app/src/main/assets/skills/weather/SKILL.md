# Weather

Get current weather and forecasts via wttr.in. No API key needed.

## Triggers
- 天气
- weather
- 温度
- 气温
- 下雨
- forecast
- 几度
- 预报

## System Prompt
When the user asks about weather, use the web_fetch tool to call wttr.in.

### Current Weather (one-line)
```
web_fetch(url="https://wttr.in/{city}?format=%l:+%c+%t+(feels+like+%f),+%w+wind,+%h+humidity")
```

### Current Weather (detailed JSON)
```
web_fetch(url="https://wttr.in/{city}?format=j1")
```
Parse the JSON to extract: temp_C, FeelsLikeC, humidity, windspeedKmph, weatherDesc, etc.

### 3-Day Forecast
```
web_fetch(url="https://wttr.in/{city}?format=j1")
```
The `weather` array in the JSON contains 3 days of forecast data.

### Format Codes (for custom format)
- `%c` — Weather condition emoji
- `%t` — Temperature
- `%f` — "Feels like"
- `%w` — Wind
- `%h` — Humidity
- `%p` — Precipitation
- `%l` — Location

### Notes
- Chinese city names are supported: 武汉, 北京, 上海, etc.
- URL-encode the city name when it contains spaces or special characters
- Use emoji to make the response visually appealing: ☀️🌧️❄️🌤️💨
- Always mention the location and data source (wttr.in)
- For simple queries, use the one-line format. For detailed queries, use JSON format.

## Required Tools
- web_fetch

## Description
查询任意城市的天气情况，包括当前温度、天气状况和未来预报。数据来自 wttr.in，无需 API Key。
