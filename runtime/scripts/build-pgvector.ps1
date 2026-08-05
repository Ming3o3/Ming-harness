<##
.SYNOPSIS
  Builds the Windows pgvector extension from a checked-out upstream source tree.

.DESCRIPTION
  The project must not publish an unidentified third-party vector.dll. This
  script builds pgvector with PostgreSQL's own Windows headers and libraries,
  installs the extension files into the portable PostgreSQL directory, and
  records the exact source tree and resulting DLL hash in
  share/extension/pgvector-build.json.

  Run it from a Visual Studio Developer PowerShell (x64), where nmake.exe and
  the MSVC linker are available. The PostgreSQL directory is the extracted
  portable PostgreSQL 17 `pgsql` directory, not a machine-wide installation.
##>

[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)]
  [string] $SourceDir,

  [Parameter(Mandatory = $true)]
  [string] $PostgresRoot,

  [string] $Version = '0.8.6',
  [string] $SourceRef = 'v0.8.6'
)

$ErrorActionPreference = 'Stop'

function Resolve-FullPath([string] $PathValue) {
  return [System.IO.Path]::GetFullPath($PathValue)
}

function Assert-File([string] $PathValue, [string] $Label) {
  if (-not (Test-Path -LiteralPath $PathValue -PathType Leaf)) {
    throw "$Label 不存在：$PathValue"
  }
}

$SourceDir = Resolve-FullPath $SourceDir
$PostgresRoot = Resolve-FullPath $PostgresRoot
$make = Get-Command nmake.exe -ErrorAction SilentlyContinue
if (-not $make) {
  throw '未找到 nmake.exe。请从 Visual Studio Developer PowerShell (x64) 运行此脚本。'
}

Assert-File (Join-Path $SourceDir 'Makefile.win') 'pgvector Makefile.win'
Assert-File (Join-Path $PostgresRoot 'bin\pg_config.exe') 'PostgreSQL pg_config.exe'
Assert-File (Join-Path $PostgresRoot 'include\server\postgres.h') 'PostgreSQL server headers（请从 PostgreSQL 17 Windows ZIP 补齐 include\server）'

$previousPgRoot = $env:PGROOT
$env:PGROOT = $PostgresRoot
Push-Location $SourceDir
try {
  # `clean` is best-effort because a fresh checkout has nothing to clean.
  & $make.Source /F Makefile.win clean
  if ($LASTEXITCODE -ne 0) {
    Write-Verbose 'pgvector clean returned a non-zero status; continuing with a fresh build.'
  }

  $git = Get-Command git.exe -ErrorAction SilentlyContinue
  $sourceCommit = $SourceRef
  $sourceTreeSha256 = $null
  if ($git) {
    $sourceCommitValue = (& $git.Source -C $SourceDir rev-parse HEAD 2>$null).Trim()
    if ($LASTEXITCODE -eq 0 -and $sourceCommitValue) {
      $dirty = (& $git.Source -C $SourceDir status --porcelain 2>$null).Trim()
      if ($LASTEXITCODE -eq 0 -and $dirty) {
        throw "pgvector 源码目录存在未提交修改：$SourceDir"
      }
      $sourceCommit = $sourceCommitValue
    }
  }

  # Hash the ordered source-file hashes so the provenance marker remains useful
  # even when the source checkout is shallow or has no .git directory.
  $hashLines = Get-ChildItem -LiteralPath $SourceDir -Recurse -File |
    Where-Object { $_.FullName -notmatch '\\(\.git|build|\.vs)\\' } |
    Sort-Object FullName |
    ForEach-Object {
      $relative = $_.FullName.Substring($SourceDir.Length).TrimStart([char]'\', [char]'/').Replace('\', '/')
      $hash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
      "$relative  $hash"
    }
  $hashPayload = [System.Text.Encoding]::UTF8.GetBytes(($hashLines -join "`n") + "`n")
  $sourceTreeSha256 = ([System.Security.Cryptography.SHA256]::Create().ComputeHash($hashPayload) | ForEach-Object { $_.ToString('x2') }) -join ''

  & $make.Source /F Makefile.win
  if ($LASTEXITCODE -ne 0) { throw "nmake 编译 pgvector 失败，退出码：$LASTEXITCODE" }
  & $make.Source /F Makefile.win install
  if ($LASTEXITCODE -ne 0) { throw "nmake 安装 pgvector 失败，退出码：$LASTEXITCODE" }
}
finally {
  Pop-Location
  if ($null -eq $previousPgRoot) { Remove-Item Env:PGROOT -ErrorAction SilentlyContinue }
  else { $env:PGROOT = $previousPgRoot }
}

$vectorDll = Join-Path $PostgresRoot 'lib\vector.dll'
$vectorControl = Join-Path $PostgresRoot 'share\extension\vector.control'
Assert-File $vectorDll '构建后的 pgvector DLL'
Assert-File $vectorControl '构建后的 pgvector control 文件'

$dllSha256 = (Get-FileHash -LiteralPath $vectorDll -Algorithm SHA256).Hash.ToLowerInvariant()
$marker = [ordered]@{
  component = 'pgvector'
  version = $Version
  source = 'https://github.com/pgvector/pgvector'
  sourceRef = $SourceRef
  sourceCommit = $sourceCommit
  sourceTreeSha256 = $sourceTreeSha256
  postgresMajor = 17
  architecture = 'x64'
  build = 'nmake /F Makefile.win && nmake /F Makefile.win install'
  vectorDllSha256 = $dllSha256
  builtAtUtc = [DateTime]::UtcNow.ToString('o')
}
$markerPath = Join-Path $PostgresRoot 'share\extension\pgvector-build.json'
$markerJson = $marker | ConvertTo-Json -Depth 4
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($markerPath, $markerJson + [Environment]::NewLine, $utf8NoBom)
Write-Host "pgvector $Version 已从源码构建并安装到：$PostgresRoot"
Write-Host "来源提交：$sourceCommit"
Write-Host "vector.dll SHA-256：$dllSha256"
