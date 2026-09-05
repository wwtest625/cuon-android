#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
本地 AI 拆解验证工具（从 local.properties 安全读取 API 配置）
"""
import urllib.request
import json
import datetime
import os

PROPERTIES_FILE = os.path.join(os.path.dirname(__file__), "local.properties")
API_URL = "https://apihub.agnes-ai.cn/v1/chat/completions"
API_KEY = ""
MODEL = "agnes-2.5-flash"

if os.path.exists(PROPERTIES_FILE):
    with open(PROPERTIES_FILE, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line.startswith("AI_API_KEY="):
                API_KEY = line.split("=", 1)[1].strip()
            elif line.startswith("AI_BASE_URL="):
                base_url = line.split("=", 1)[1].strip()
                API_URL = f"{base_url}/chat/completions"
            elif line.startswith("AI_MODEL="):
                MODEL = line.split("=", 1)[1].strip()

def test_parse(text):
    now = datetime.datetime.now()
    now_str = now.strftime("%Y-%m-%d %H:%M:%S %A")
    print(f"\n==================================================")
    print(f"当前系统时间基准: {now_str}")
    print(f"用户输入测试原句: \"{text}\"")
    print("--------------------------------------------------")
    print(f"🚀 正在调用 {MODEL} 进行多意图智能拆解...")

    system_prompt = f"""你是一个专业的高效个人日程与待办拆解专家。
当前系统基准时间是: {now_str}。
请将用户输入的自然语言，提取并拆解为原子化的清单项。
请根据当前基准时间，精准计算出所有相对时间的绝对日期与时间（格式统一为：YYYY-MM-DD HH:mm:ss）。
输出必须是严格的 JSON 格式：
{{
  "items": [
    {{
      "title": "动宾短语描述事项（简练有力）",
      "isCalendarEvent": true/false (若是特定时间段的会议、碰头、出行等日程为true；若是只需在截止日前完成的待办事项为false),
      "startTime": "YYYY-MM-DD HH:mm:ss或null",
      "endTime": "YYYY-MM-DD HH:mm:ss或null",
      "priority": "high/medium/low",
      "location": "地点，无则留空",
      "tag": "工作/个人/财务/会议"
    }}
  ]
}}"""

    payload = {
        "model": MODEL,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": text}
        ],
        "response_format": {"type": "json_object"}
    }

    req = urllib.request.Request(
        API_URL,
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {API_KEY}",
            "Content-Type": "application/json"
        },
        method="POST"
    )

    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            content = data["choices"][0]["message"]["content"]
            result = json.loads(content)
            
            print("✅ AI 拆解成功！提取出的原子化清单：")
            for i, item in enumerate(result.get("items", []), 1):
                kind = "📅【日程事件】" if item.get("isCalendarEvent") else "✅【待办任务】"
                print(f"\n  {i}. {kind} {item.get('title')}")
                print(f"     • 优先级: {item.get('priority')} | 分类: #{item.get('tag')}")
                if item.get("startTime"):
                    print(f"     • 开始时间: {item.get('startTime')}")
                if item.get("endTime"):
                    print(f"     • 截止/结束: {item.get('endTime')}")
                if item.get("location"):
                    print(f"     • 地点: {item.get('location')}")
    except Exception as e:
        print(f"❌ 调用出错: {e}")

if __name__ == "__main__":
    test_parse("明天下午两点在星巴克跟张经理碰头聊合作，下周五前务必把初版PRD发他邮箱，另外下班记得买点水果")
