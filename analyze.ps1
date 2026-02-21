$caseId = 19
$response = Invoke-RestMethod -Uri "http://localhost:8081/cases/$caseId/analyze" -Method Post
$response | ConvertTo-Json -Depth 50 | Out-File -Encoding UTF8 ".\debug\case-$caseId-analyze-response.json"

($response.rawResponseJson) | Out-File -Encoding UTF8 ".\debug\case-$caseId-ultralytics-raw.json"

$summary = [PSCustomObject]@{
  caseId = $response.caseId
  topClassId = $response.topClassId
  confidence = $response.confidence
  parasiteStage = $response.parasiteStage
  drugExposure = $response.drugExposure
  drugType = $response.drugType
  createdAt = $response.createdAt
}
$summary | ConvertTo-Json -Depth 10 | Out-File -Encoding UTF8 ".\debug\case-$caseId-summary.json"

Write-Host "Analyze Complete. Outputs saved to debug folder."
