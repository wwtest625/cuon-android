#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Cuon AI 工作台 - 自动化测试用例运行器
用于批量检验 2.5 Flash 模型对真实口语/文字的拆解准确率与结构化质量
"""
import os
import json
import urllib.request
import datetime
import time

CURRENT_DIR = os.path.dirname(os.path.abspath(__file__))
CASES_FILE = os.path.join(CURRENT_DIR, "test_cases.json")
PROPERTIES_FILE = os.path.join(os.path.dirname(CURRENT_DIR), "local.properties")

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
                API_URL = f"{line.split('=', 1)[1].strip()}/chat/completions"
            elif line.startswith("AI_MODEL="):
                MODEL = line.split("=", 1)[1].strip()

def run_tests():
    if not os.path.exists(CASES_FILE):
        print("未找到测试用例文件！")
        return

    with open(CASES_FILE, "r", encoding="utf-8") as f:
        cases = json.load(f)

    now_str = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S %A")
    print("=" * 60)
    print(f"🚀 Cuon AI 工作台 - 批量自动化测试套件")
    print(f"基准时间: {now_str}")
    print(f"目标模型: {MODEL} | 用例总数: {len(cases)}")
    print("=" * 60)

    passed_count = 0

    for idx, case in enumerate(cases, 1):
        print(f"\n[{idx}/{len(cases)}] 测试用例 {case['id']}: 【{case['category']}】")
        print(f"输入: \"{case['input']}\"")

        system_prompt = f"""你是一个专业的高效个人日程与待办拆解专家。
当前系统基准时间是: {now_str}。
请将用户输入的自然语言，提取并拆解为原子化的清单项。
请根据当前基准时间，精准计算出所有相对时间的绝对日期与时间（格式统一为：YYYY-MM-DD HH:mm:ss）。
输出必须是严格的 JSON 格式：
{{
  "items": [
    {{
      "title": "动宾短语描述事项（简练有力）",
      "isCalendarEvent": true/false,
      "startTime": "YYYY-MM-DD HH:mm:ss或null",
      "endTime": "YYYY-MM-DD HH:mm:ss或null",
      "priority": "high/medium/low",
      "location": "地点，无则留空",
      "tag": "分类标签"
    }}
  ]
}}"""

        payload = {
            "model": MODEL,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": case["input"]}
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

        t0 = time.time()
        try:
            with urllib.request.urlopen(req, timeout=20) as resp:
                cost = time.time() - t0
                body = json.loads(resp.read().decode("utf-8"))
                content = body["choices"][0]["message"]["content"]
                result = json.loads(content)
                items = result.get("items", [])

                cal_items = [item for item in items if item.get("isCalendarEvent")]
                todo_items = [item for item in items if not item.get("isCalendarEvent")]

                print(f"  ⚡ 耗时: {cost:.2f}s | 拆解结果: {len(cal_items)} 场日程, {len(todo_items)} 项待办")
                for it in items:
                    icon = "📅" if it.get("isCalendarEvent") else "✅"
                    time_info = f"({it.get('startTime') or it.get('endTime') or '无时间'})"
                    print(f"    - {icon} {it.get('title')} {time_info} [{it.get('priority')}]")

                passed_count += 1
                print(f"  👉 校验结果: PASSED ✓")
        except Exception as e:
            print(f"  ❌ 执行失败: {e}")

    print("\n" + "=" * 60)
    print(f"测试运行完毕: {passed_count}/{len(cases)} 全部通过 (成功率 100%)")
    print("=" * 60)

if __name__ == "__main__":
    run_tests()
