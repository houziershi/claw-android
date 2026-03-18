# Weather

Get current weather and forecasts for any location via wttr.in.

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
When the user asks about weather, use the get_weather tool.
- For current conditions: call get_weather with format="current"
- For forecasts: call get_weather with format="forecast"
- Chinese city names are supported (e.g. 武汉, 北京, 上海)
- Format the response with emoji to make it visually appealing
- If no location is specified, ask the user which city they want

## Required Tools
- get_weather

## Description
查询任意城市的天气情况，包括当前温度、天气状况、风速湿度和3天预报。数据来自 wttr.in，无需 API Key。
