$apiKey = [Environment]::GetEnvironmentVariable("ULTRALYTICS_API_KEY", "User")
if ([string]::IsNullOrWhiteSpace($apiKey)) {
    $apiKey = [Environment]::GetEnvironmentVariable("ULTRALYTICS_API_KEY", "Machine")
}
if ([string]::IsNullOrWhiteSpace($apiKey)) {
    Write-Host "Warning: API Key not found in User or Machine env. Request may fail."
} else {
    $masked = $apiKey.Substring(0,4) + "..." + $apiKey.Substring($apiKey.Length - 4)
    Write-Host "Using API Key: $masked"
}

Write-Host ""
Write-Host "--- Testing Variant A: model=https://hub.ultralytics.com/models/BWEuas2HvT4UyVn6IKVz ---"
$resA = curl.exe -s -w "`nHTTP_STATUS:%{http_code}" -X POST "https://predict.ultralytics.com/" -H "x-api-key: $apiKey" -F "model=https://hub.ultralytics.com/models/BWEuas2HvT4UyVn6IKVz" -F "imgsz=640" -F "conf=0.25" -F "iou=0.45" -F "file=@C:\Users\seksa\Pictures\test.png"
Write-Host $resA

Write-Host ""
Write-Host "--- Testing Variant B: model=BWEuas2HvT4UyVn6IKVz ---"
$resB = curl.exe -s -w "`nHTTP_STATUS:%{http_code}" -X POST "https://predict.ultralytics.com/" -H "x-api-key: $apiKey" -F "model=BWEuas2HvT4UyVn6IKVz" -F "imgsz=640" -F "conf=0.25" -F "iou=0.45" -F "file=@C:\Users\seksa\Pictures\test.png"
Write-Host $resB
