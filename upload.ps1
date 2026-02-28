$Uri = "http://localhost:8081/cases"
$File = "C:\Users\seksa\Pictures\test.png"
$patientCode = "LIVE-01"
$technicianId = "TCH-01"
$location = "HQ"
$uploaderId = "1"

# In PowerShell 7+ or robust modern setups we can use -Form. In older, we build Multipart form boundaries safely.
Add-Type -AssemblyName System.Net.Http
$client = New-Object System.Net.Http.HttpClient
$content = New-Object System.Net.Http.MultipartFormDataContent

$content.Add((New-Object System.Net.Http.StringContent($patientCode)), "patientCode")
$content.Add((New-Object System.Net.Http.StringContent($technicianId)), "technicianId")
$content.Add((New-Object System.Net.Http.StringContent($location)), "location")
$content.Add((New-Object System.Net.Http.StringContent($uploaderId)), "uploaderId")

$FileStream = [System.IO.File]::OpenRead($File)
$StreamContent = New-Object System.Net.Http.StreamContent($FileStream)
$StreamContent.Headers.ContentType = New-Object System.Net.Http.Headers.MediaTypeHeaderValue("image/png")
$content.Add($StreamContent, "image", "test.png")

$response = $client.PostAsync($Uri, $content).Result
$result = $response.Content.ReadAsStringAsync().Result

$result | Out-File -Encoding UTF8 ".\debug\upload-response.json"
Write-Host $result

$FileStream.Close()
$client.Dispose()
