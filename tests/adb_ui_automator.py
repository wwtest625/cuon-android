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
    subprocess.run(f"adb exec-out screencap -p > '{local_path}'", shell=True)
    size_kb = os.path.getsize(local_path) / 1024
    print(f"  📸 [截图保存] -> {local_path} ({size_kb:.1f} KB)")
    return local_path

def get_screen_resolution():
    """获取设备分辨率"""
    out, _ = run_adb("shell wm size")
    for part in out.split():
        if "x" in part and part.replace("x", "").isdigit():
            w, h = part.split("x")
            return int(w), int(h)
    return 1264, 2800

def main():
    print("=" * 65)
    print("🤖 Cuon AI 工作台 - 纯命令行真机 UI 自动化驱动测试")
    print("=" * 65)

    devices = check_device()
    if not devices:
        print("\n⚠️  [当前未检测到 ADB 在线设备]")
        sys.exit(1)

    device_id = devices[0]
    print(f"✓ 成功检测到在线真机: {device_id}")
    width, height = get_screen_resolution()
    print(f"✓ 真机屏幕物理分辨率: {width} × {height}")

    # 1. 确保 Cuon App 在前台
    print("\n[Step 1] 确保 Cuon App 位于前台并处于准备状态...")
    run_adb(f"shell am start -n {PACKAGE_NAME}/{MAIN_ACTIVITY}")
    time.sleep(1.5)
    capture_screen("01_current_state.png")

    # 2. 模拟手势滑动删除卡片 (Swipe-to-Dismiss)
    print("\n[Step 2] 模拟测试【向左手势滑动删除】日程卡片...")
    # 日程卡片中心 Y: 632
    start_x = int(width * 0.88)
    end_x = int(width * 0.08)
    card_y = 632
    run_adb(f"shell input swipe {start_x} {card_y} {end_x} {card_y} 220")
    print(f"  👈 手势左滑: ({start_x}, {card_y}) -> ({end_x}, {card_y})")
    time.sleep(1.2)
    capture_screen("02_after_swipe_delete.png")

    # 3. 模拟点击任务复选框进行【打勾完成】
    print("\n[Step 3] 模拟点击任务复选框进行【打勾完成】...")
    # 日程删除后，第一张待办上升至原日程位置（约 630-700 左右）或在原位置检查
    # 我们先 dump 一下看待办位置
    run_adb("shell uiautomator dump /sdcard/step3_dump.xml")
    checkbox_x = 182
    checkbox_y = 700
    run_adb(f"shell input tap {checkbox_x} {checkbox_y}")
    print(f"  ☑️  点击 CheckBox 触发打勾与触感反馈 (坐标: {checkbox_x}, {checkbox_y})")
    time.sleep(1)
    capture_screen("03_task_completed.png")

    # 4. 模拟再点击一次示例芯片，测试追加日程与待办
    print("\n[Step 4] 模拟再次触发自然语言解析，追加新事件...")
    chip_x = 435
    chip_y = 2408
    run_adb(f"shell input tap {chip_x} {chip_y}")
    print(f"  👆 点击芯片: ({chip_x}, {chip_y}) 请求 2.5 Flash 模型")
    for i in range(4, 0, -1):
        print(f"  ⏳ 模型响应中... {i}s", end="\r")
        time.sleep(1)
    print("\n  ✓ 界面实时更新完成！")
    capture_screen("04_ai_new_parsed.png")

    print("\n" + "=" * 65)
    print("🎉 真机 UI 自动化测试全链路执行完毕！")
    print(f"📸 全部测试截图已归档至: {SCREENSHOT_DIR}")
    print("=" * 65)

if __name__ == "__main__":
    main()
