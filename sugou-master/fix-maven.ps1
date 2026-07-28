# 清除 spring-cloud 缓存失败标记并重新构建
Write-Host "=== 清除 Maven 本地缓存失败标记 ===" -ForegroundColor Cyan

$lastUpdated = "$env:USERPROFILE\.m2\repository\org\springframework\cloud\spring-cloud-dependencies\2024.0.5"
if (Test-Path $lastUpdated) {
    # 尝试用 cmd 删除（绕过 PowerShell 的权限限制）
    cmd /c "del /f /q `"$lastUpdated\*.lastUpdated`" 2>nul"
    Write-Host "已清理 .lastUpdated 缓存文件" -ForegroundColor Green
}

Write-Host "`n=== 运行 Maven 强制更新 ===" -ForegroundColor Cyan
Write-Host "命令: mvn -U clean install -f sugou-master/pom.xml" -ForegroundColor Yellow
Write-Host "`n建议操作:" -ForegroundColor Cyan
Write-Host "1. 关闭 IntelliJ IDEA" -ForegroundColor White
Write-Host "2. 以管理员身份运行 PowerShell" -ForegroundColor White
Write-Host "3. cd C:\Users\24198\Desktop\work_project\mall-master" -ForegroundColor White
Write-Host "4. 运行: .\fix-maven.ps1" -ForegroundColor White
Write-Host "5. 或者直接运行: mvn -U clean install -f sugou-master/pom.xml" -ForegroundColor White
