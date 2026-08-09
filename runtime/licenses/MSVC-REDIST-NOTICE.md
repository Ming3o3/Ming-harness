# Microsoft native runtime notice

The Windows portable build may copy the following Microsoft native runtime
files beside PostgreSQL, Garnet, and Erlang executables so the green package
does not require a machine-wide Visual C++ installation:

- `msvcp140.dll`
- `vcruntime140.dll`
- `vcruntime140_1.dll`
- `ucrtbase.dll`

These files are taken from the bundled Windows Temurin runtime during staging.
They remain Microsoft redistributable components and are not covered by the
Ming Harness or Garnet MIT licenses. The publisher must retain the applicable
Microsoft Visual C++ Redistributable terms for the exact files and version in
the final release. Reference: <https://learn.microsoft.com/cpp/windows/latest-supported-vc-redist>.
