@echo off
cd /d "%~dp0"
if not exist wrangler.jsonc (
  echo Copy these update files into your existing community-server folder first.
  pause
  exit /b 1
)
findstr /C:"REPLACE_WITH_CREATED_DATABASE_ID" wrangler.jsonc >nul
if not errorlevel 1 (
  echo Keep your existing wrangler.jsonc with the real database_id. Do not use the template.
  pause
  exit /b 1
)
where npm.cmd >nul 2>nul
if errorlevel 1 (
  echo Node.js and npm are required. Reopen this window after installing Node.js.
  pause
  exit /b 1
)
if not exist node_modules\wrangler\bin\wrangler.js (
  call npm.cmd ci
  if errorlevel 1 exit /b 1
)
call npx.cmd wrangler d1 migrations apply kaimono-community --remote
if errorlevel 1 (
  echo Migration failed. Deployment was stopped.
  pause
  exit /b 1
)
call npx.cmd wrangler deploy
if errorlevel 1 (
  echo Deployment failed. Please check the error above.
  pause
  exit /b 1
)
echo Update complete. Reopen the app to retry pending uploads.
pause
