import os
import sys
import traceback
import time

try:
    import requests
except ImportError:
    import urllib.request as urllib2
    import urllib.parse
    import json
    import mimetypes
    requests_installed = False
    print("Requests not installed. Will use urllib (but multipart is hard). Please install requests if possible.")
else:
    requests_installed = True

api_key = os.environ.get('ULTRALYTICS_API_KEY')
if not api_key:
    # Try reading from registry if on windows
    import winreg
    try:
        key = winreg.OpenKey(winreg.HKEY_CURRENT_USER, r'Environment')
        api_key, _ = winreg.QueryValueEx(key, 'ULTRALYTICS_API_KEY')
    except Exception:
        pass

if not api_key:
    try:
        key = winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE, r'SYSTEM\CurrentControlSet\Control\Session Manager\Environment')
        api_key, _ = winreg.QueryValueEx(key, 'ULTRALYTICS_API_KEY')
    except Exception:
        pass

if not api_key:
    print("WARNING: API Key not found!")
    api_key = ""
else:
    print(f"Using API Key: {api_key[:4]}...{api_key[-4:]}")

url = "https://predict.ultralytics.com/"
headers = {"x-api-key": api_key}
image_path = r"C:\Users\seksa\Pictures\test.png"

def test_variant(name, model_str):
    print(f"\n--- Testing {name} ---")
    print(f"Model ID string sent: {model_str}")
    data = {"model": model_str, "imgsz": 640, "conf": 0.25, "iou": 0.45}
    try:
        if requests_installed:
            with open(image_path, "rb") as f:
                files = {"file": f}
                t0 = time.time()
                response = requests.post(url, headers=headers, data=data, files=files)
                print(f"[{time.strftime('%H:%M:%S')}] HTTP Status: {response.status_code} in {time.time()-t0:.2f}s")
                try:
                    resp_data = response.json()
                    if 'details' in resp_data and 'traceback' in resp_data['details']:
                        print(f"Error Message: {resp_data['message']}")
                        print(f"Traceback snippet: {resp_data['details']['traceback'][:300]}")
                    else:
                        print(f"Response JSON: {resp_data}")
                except:
                    print(f"Response Text: {response.text}")
    except Exception as e:
        traceback.print_exc()

test_variant("Variant A (Full URL)", "https://hub.ultralytics.com/models/BWEuas2HvT4UyVn6IKVz")
test_variant("Variant B (ID Only)", "BWEuas2HvT4UyVn6IKVz")
