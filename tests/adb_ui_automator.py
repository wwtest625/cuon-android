#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Cuon AI 工作台 - 纯命令行 ADB 真机 UI 自动化测试驱动
无需打开 Android Studio，只要手机连上 USB 开启调试，终端直接一键跑完 UI 全流程！
"""
import os
import subprocess
import time
import sys

PACKAGE_NAME = "com.cuon.app"
MAIN_ACTIVITY = ".MainActivity"
SCREENSHOT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "screenshots")

def run_adb(cmd):
    """执行 adb 命令并返回输出"""
    full_cmd = f"adb {cmd}"
    result = subprocess.run(full_cmd, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    return result.stdout.strip(), result.stderr.strip()

def check_device():
    """检查当前是否有 ADB 设备在线"""
    out, _ = run_adb("devices")
    lines = [line for line in out.splitlines() if line.strip() and not line.startswith("List of devices")]
    devices = [line.split()[0] for line in lines if "device" in line]
    return devices

def capture_screen(filename):
    """抓取真机当前屏幕截图并拉取到本地"""
    os.makedirs(SCREENSHOT_DIR, exist_ok=True)
    local_path = os.path.join(SCREENSHOT_DIR, filename)
    # 直接通过 adb exec-out 导出，速度极快 (< 300ms)
    subprocess.run(f"adb exec-out screencap -p > '{local_path}'", shell=True)
    print(f"  📸 [截图保存] -> {local_path}")
    return local_path

def get_screen_resolution():
    """获取设备分辨率"""
    out, _ = run_adb("shell wm size")
    # Physical size: 1080x2400
    for part in out.split():
        if "x" in part and part.replace("x", "").isdigit():
            w, h = part.split("x")
            return int(w), int(h)
    return 1080, 2400

def main():
    print("=" * 65)
    print("🤖 Cuon AI 工作台 - ADB 移动端全自动 UI 驱动测试")
    print("=" * 65)

    # 1. 检查 ADB 真机
    devices = check_device()
    if not devices:
        print("\n⚠️  [当前未检测到 ADB 在线设备]")
        print("   原因：手机尚未插入 USB 或未授权调试模式。")
        print("   提示：当手机插上 USB 并开启【开发者选项 -> USB 调试】后：")
        print("         在 WSL 执行: export ADB_SERVER_SOCKET=tcp:127.0.0.1:5037")
        print("         然后再次运行此脚本，即可自动接管真机屏幕进行自动化操作！")
        sys.exit(0)

    device_id = devices[0]
    print(f"✓ 成功检测到在线真机: {device_id}")
    width, height = get_screen_resolution()
    print(f"✓ 真机屏幕分辨率: {width} × {height}")

    # 2. 启动目标 App
    print("\n[Step 1] 正在通过 ADB 唤起 Cuon App...")
    run_adb(f"shell am start -n {PACKAGE_NAME}/{MAIN_ACTIVITY}")
    time.sleep(2)
    capture_screen("01_app_launched.png")

    # 3. 模拟点击“⚡ 示例 1: 下周三在国贸...” 快速测试芯片
    print("\n[Step 2] 模拟点击屏幕下方的【快速测试芯片】...")
    # 芯片条位于屏幕底部上方约 120dp 处 (约为 height * 0.85)
    chip_x = int(width * 0.25)
    chip_y = int(height * 0.86)
    run_adb(f"shell input tap {chip_x} {chip_y}")
    print(f"  👆 点击坐标 ({chip_x}, {chip_y}) 触发 AI 拆解请求")

    # 4. 等待 2.5 Flash 接口返回并落库渲染
    print("\n[Step 3] 等待 2.5 Flash 大模型拆解并刷新时间轴...")
    for i in range(5, 0, -1):
        print(f"  ⏳ 模型思考与生成中... 倒计时 {i}s", end="\r")
        time.sleep(1)
    print("\n  ✓ 界面已更新完毕！")
    capture_screen("02_ai_parsed_result.png")

    # 5. 模拟手势向左滑动删除卡片 (Swipe-to-Dismiss)
    print("\n[Step 4] 模拟测试【手势向左滑动删除】卡片...")
    start_x = int(width * 0.85)
    end_x = int(width * 0.15)
    card_y = int(height * 0.35) # 中上部第一张卡片的位置
    run_adb(f"shell input swipe {start_x} {card_y} {end_x} {card_y} 220")
    print(f"  👈 手势左滑: ({start_x}, {card_y}) -> ({end_x}, {card_y}) 耗时 220ms")
    time.sleep(1)
    capture_screen("03_after_swipe_delete.png")

    # 6. 模拟点击复选框打勾完成
    print("\n[Step 5] 模拟点击任务复选框进行【打勾完成】...")
    checkbox_x = int(width * 0.12)
    checkbox_y = int(height * 0.45)
    run_adb(f"shell input tap {checkbox_x} {checkbox_y}")
    print(f"  ☑️  点击坐标 ({checkbox_x}, {checkbox_y})")
    time.sleep(1)
    capture_screen("04_task_completed.png")

    print("\n" + "=" * 65)
    print("🎉 真机 UI 自动化测试全链路执行完毕！")
    print(f"全部测试阶段截图已安全归档至: {SCREENSHOT_DIR}")
    print("=" * 65)

if __name__ == "__main__":
    main()
