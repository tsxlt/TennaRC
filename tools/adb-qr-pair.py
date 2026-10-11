#!/usr/bin/env python3
"""Pair a phone over Wi-Fi ADB by QR code, then connect to it.

Same flow as Android Studio's "Pair device with QR code": the phone scans
WIFI:T:ADB;S:<name>;P:<password>;; and advertises _adb-tls-pairing._tcp under
<name>. This script finds it over mDNS itself, since nixpkgs' adb is built
without mdns support.

Run inside `nix develop`:  .venv/bin/python tools/adb-qr-pair.py [out.png]
"""
import secrets
import string
import subprocess
import sys
import time

from zeroconf import ServiceBrowser, ServiceStateChange, Zeroconf

PAIRING = "_adb-tls-pairing._tcp.local."
CONNECT = "_adb-tls-connect._tcp.local."
TIMEOUT_S = 600

svc_name = "tenna-" + secrets.token_hex(4)
password = "".join(secrets.choice(string.ascii_letters + string.digits) for _ in range(12))
out = sys.argv[1] if len(sys.argv) > 1 else "adb-pair-qr.png"

subprocess.run(
    ["qrencode", "-s", "12", "-m", "3", "-o", out, f"WIFI:T:ADB;S:{svc_name};P:{password};;"],
    check=True,
)
print(f"QR ready: {out} (service {svc_name})", flush=True)

state = {"paired_ip": None, "connect": {}, "done": False}

def adb(*args):
    r = subprocess.run(["adb", *args], capture_output=True, text=True)
    return (r.stdout + r.stderr).strip()

def on_change(zeroconf, service_type, name, state_change):
    if state_change is ServiceStateChange.Removed:
        return
    info = zeroconf.get_service_info(service_type, name, timeout=3000)
    if not info:
        return
    ips = [a for a in info.parsed_addresses() if ":" not in a] or info.parsed_addresses()
    if service_type == PAIRING and name.startswith(svc_name + ".") and not state["paired_ip"]:
        ip = ips[0]
        print(f"Phone found at {ip}:{info.port}, pairing...", flush=True)
        res = adb("pair", f"{ip}:{info.port}", password)
        print(f"adb pair: {res}", flush=True)
        if "Successfully paired" in res:
            state["paired_ip"] = ip
    elif service_type == CONNECT:
        for ip in ips:
            state["connect"][ip] = info.port

zc = Zeroconf()
ServiceBrowser(zc, [PAIRING, CONNECT], handlers=[on_change])
deadline = time.time() + TIMEOUT_S
try:
    while time.time() < deadline:
        ip = state["paired_ip"]
        if ip and ip in state["connect"]:
            target = f"{ip}:{state['connect'][ip]}"
            print(f"adb connect: {adb('connect', target)}", flush=True)
            print(adb("devices", "-l"), flush=True)
            sys.exit(0)
        time.sleep(0.5)
    print("TIMEOUT: phone never showed up (same Wi-Fi? scanned the QR?)", flush=True)
    sys.exit(1)
finally:
    zc.close()
