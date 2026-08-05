# Windows green runtime notices

The packaged runtime retains the license files shipped by each upstream
component under `resources/infra/win-x64` and `resources/runtime/jre`.

- PostgreSQL 17.6: PostgreSQL License, reproduced in `POSTGRESQL-LICENSE.txt`.
  The EDB Windows binary source and checksum are in
  `runtime/win-runtime.manifest.json`; bundled dependency notices must remain
  with the binary distribution.
- pgvector 0.8.6: MIT-licensed project, reproduced in `PGVECTOR-LICENSE.txt`.
  The Windows DLL must be built by the publisher from the upstream source with
  `runtime/scripts/build-pgvector.ps1`; the packaged
  `share/extension/pgvector-build.json` records the source and DLL hashes.
- RabbitMQ 4.3.4: Mozilla Public License and the component notices shipped in
  the RabbitMQ ZIP.
- Erlang/OTP 28.5.0.5: Apache License 2.0 and the notices shipped in the OTP
  distribution.
- Eclipse Temurin JRE 17.0.20+8: bundled legal notices are under
  `resources/runtime/jre/legal`.
- Microsoft Garnet 2.1.1: MIT License. The Garnet server and its bundled
  dependencies are distributed with `GARNET-LICENSE.txt` and
  `GARNET-NOTICE.md`; the server is started with Lua enabled because the
  application uses Redis-compatible Lua lock scripts.
- Microsoft .NET Runtime 8.0.18: the runtime files under
  `resources/infra/win-x64/garnet/dotnet` retain Microsoft's `LICENSE.txt`
  and `ThirdPartyNotices.txt` from the official runtime archive.
- Microsoft native runtime DLLs: see `MSVC-REDIST-NOTICE.md` for the files
  copied beside native executables and the applicable Microsoft terms.

The public build contains only the Garnet Redis-compatible server described
above; no separately licensed Redis server is copied by the runtime preparation
script.
