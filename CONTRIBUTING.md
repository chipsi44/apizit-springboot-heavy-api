# Contributing

Keep the common HTTP contract and mirror identity intact. Work on a `codex/` branch.

```sh
mvn -B verify
python3 scripts/check_native_abi.py
```

CI exercises the same checks without cloud credentials. Never download model weights
in normal CI. Record local HTTP smoke results and the exact published commit.
Hosted qualification uses disposable APIZIT dev resources and exact commits; record
the measured scan and HTTP results. Production and real payments are outside scope.

OpenCV is pinned to 4.10.0-1.5.11 for the managed glibc 2.34 runtime. The newer
4.14.0-1.5.14 binaries require GLIBC_2.35 and fail when image processing initializes.
Do not update this pin based only on tests running on a newer Ubuntu host: run the
ELF requirement check and the real native image tests, then qualify the exact source
in dev. Image decoding stays in bounded Java ImageIO; OpenCV processes decoded pixels.
